package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.OrderItemRepository;
import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.repository.SalesReportRow;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.ui.DatePickerField;
import com.flowershop.flowershop.ui.DatePickerField.Mode;
import com.flowershop.flowershop.util.PdfReport;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportsPanel extends JPanel {

    private enum ReportType {
        DAILY("Daily Sales", "Date", Mode.DAY),
        WEEKLY("Weekly Sales", "Week", Mode.WEEK),
        MONTHLY("Monthly Sales", "Month", Mode.MONTH),
        PERIOD("Period-specific Sales", "From", Mode.DAY);

        private final String label;
        private final String fromLabel;
        private final Mode mode;

        ReportType(String label, String fromLabel, Mode mode) {
            this.label = label;
            this.fromLabel = fromLabel;
            this.mode = mode;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter ROW_DAY = DateTimeFormatter.ofPattern("EEE, MMM dd, yyyy");
    private static final String[] COLUMNS = {"Day", "Orders", "Items Sold", "Revenue"};

    private final User currentUser;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditService auditService;

    private final JComboBox<ReportType> cmbType = new JComboBox<>(ReportType.values());
    private final JLabel lblFrom = boldLabel("Date");
    private final JLabel lblTo = boldLabel("To");
    private final DatePickerField pickFrom = new DatePickerField();
    private final DatePickerField pickTo = new DatePickerField();
    private final JButton btnExport = new JButton("Export PDF");
    private final JLabel lblRevenue = new JLabel("₱0.00");
    private final JLabel lblOrders = new JLabel("0");
    private final JLabel lblItems = new JLabel("0");
    private final JLabel lblStatus = new JLabel(" ");
    private final DefaultTableModel model;

    private boolean adjusting;
    private ReportType shownType = ReportType.DAILY;
    private LocalDate shownFrom = LocalDate.now();
    private LocalDate shownTo = LocalDate.now();
    private final List<Object[]> shownRows = new ArrayList<>();
    private String[] shownTotals = {"₱0.00", "0", "0"};

    public ReportsPanel(User currentUser, OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository, AuditService auditService) {
        this.currentUser = currentUser;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.auditService = auditService;

        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("Reports");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));

        JLabel lblSection = new JLabel("Sales Reports");
        lblSection.setFont(new Font("Segoe UI", Font.BOLD, 15));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        controls.setBackground(Color.WHITE);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        controls.add(boldLabel("Report"));
        controls.add(cmbType);
        controls.add(lblFrom);
        controls.add(pickFrom);
        controls.add(lblTo);
        controls.add(pickTo);
        controls.add(btnExport);

        btnExport.setBackground(new Color(25, 135, 84));
        btnExport.setForeground(Color.WHITE);
        btnExport.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnExport.setFocusPainted(false);

        JPanel head = new JPanel(new BorderLayout(0, 10));
        head.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        titles.add(lblTitle);
        titles.add(lblSection);
        head.add(titles, BorderLayout.NORTH);
        head.add(controls, BorderLayout.CENTER);

        JPanel cards = new JPanel(new GridLayout(1, 3, 15, 0));
        cards.setOpaque(false);
        cards.add(card("Total Revenue", lblRevenue, new Color(13, 110, 253)));
        cards.add(card("Orders", lblOrders, new Color(25, 135, 84)));
        cards.add(card("Items Sold", lblItems, new Color(214, 51, 132)));

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(head, BorderLayout.NORTH);
        top.add(cards, BorderLayout.CENTER);

        model = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        for (int i = 1; i < COLUMNS.length; i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(right);
        }
        table.getColumnModel().getColumn(0).setPreferredWidth(280);

        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(lblStatus, BorderLayout.SOUTH);

        cmbType.addActionListener(e -> {
            applyType();
            generate();
        });
        pickFrom.addChangeListener(() -> {
            if (cmbType.getSelectedItem() == ReportType.PERIOD) {
                pickTo.setMinDate(pickFrom.getDate());
                if (pickTo.getDate().isBefore(pickFrom.getDate())) {
                    pickTo.setDate(pickFrom.getDate());
                }
            }
            generate();
        });
        pickTo.addChangeListener(this::generate);
        btnExport.addActionListener(e -> exportPdf());

        applyType();
        generate();
    }

    public void loadData() {
        LocalDate today = LocalDate.now();
        pickFrom.setMaxDate(today);
        pickTo.setMaxDate(today);
        generate();
    }

    private static JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        return label;
    }

    private JPanel card(String title, JLabel value, Color accent) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 224, 230)),
                        BorderFactory.createEmptyBorder(12, 14, 12, 14))));
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTitle.setForeground(new Color(110, 110, 110));
        value.setFont(new Font("Segoe UI", Font.BOLD, 24));
        panel.add(lblTitle, BorderLayout.NORTH);
        panel.add(value, BorderLayout.CENTER);
        return panel;
    }

    private void applyType() {
        ReportType type = (ReportType) cmbType.getSelectedItem();
        LocalDate today = LocalDate.now();
        boolean period = type == ReportType.PERIOD;

        adjusting = true;
        lblFrom.setText(type.fromLabel);
        lblTo.setVisible(period);
        pickTo.setVisible(period);
        pickFrom.setMode(type.mode);
        pickFrom.setMaxDate(today);
        pickTo.setMaxDate(today);
        pickTo.setMinDate(null);
        if (period) {
            pickFrom.setDate(today.withDayOfMonth(1));
            pickTo.setDate(today);
            pickTo.setMinDate(pickFrom.getDate());
        } else {
            pickFrom.setDate(today);
        }
        adjusting = false;
    }

    private LocalDate endOf(ReportType type, LocalDate from) {
        switch (type) {
            case WEEKLY:
                return from.plusDays(6);
            case MONTHLY:
                return from.withDayOfMonth(from.lengthOfMonth());
            case PERIOD:
                return pickTo.getDate();
            default:
                return from;
        }
    }

    private void generate() {
        if (adjusting) {
            return;
        }
        ReportType type = (ReportType) cmbType.getSelectedItem();
        LocalDate today = LocalDate.now();
        LocalDate from = pickFrom.getDate();
        LocalDate to = endOf(type, from);
        LocalDate last = to.isAfter(today) ? today : to;

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime endExclusive = to.plusDays(1).atStartOfDay();
        LocalDateTime endInclusive = endExclusive.minusNanos(1000);

        Map<LocalDate, SalesReportRow> byDay = new HashMap<>();
        for (SalesReportRow row : orderRepository.findDailyReport(start, endExclusive)) {
            byDay.put(LocalDate.parse(row.getPeriod()), row);
        }
        BigDecimal revenue = orderRepository.calculateRevenueBetween(start, endInclusive);
        Long items = orderItemRepository.countItemsSoldBetween(start, endInclusive);
        long orders = 0;

        model.setRowCount(0);
        shownRows.clear();
        for (LocalDate day = from; !day.isAfter(last); day = day.plusDays(1)) {
            SalesReportRow row = byDay.get(day);
            long dayOrders = row == null ? 0 : row.getOrders();
            long dayItems = row == null ? 0 : row.getItemsSold();
            BigDecimal dayRevenue = row == null ? BigDecimal.ZERO : row.getRevenue();
            Object[] line = {day.format(ROW_DAY), dayOrders, dayItems, money(dayRevenue)};
            model.addRow(line);
            shownRows.add(line);
            orders += dayOrders;
        }

        lblRevenue.setText(money(revenue));
        lblOrders.setText(String.valueOf(orders));
        lblItems.setText(String.valueOf(items == null ? 0 : items));
        shownTotals = new String[]{lblRevenue.getText(), lblOrders.getText(), lblItems.getText()};
        shownType = type;
        shownFrom = from;
        shownTo = to;
        lblStatus.setText(type + ": " + rangeText(from, to));
    }

    private String rangeText(LocalDate from, LocalDate to) {
        return from.equals(to) ? from.format(DAY) : from.format(DAY) + " - " + to.format(DAY);
    }

    private String money(BigDecimal value) {
        return "₱" + String.format("%,.2f", value == null ? BigDecimal.ZERO : value);
    }

    private void exportPdf() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save report");
        chooser.setSelectedFile(new File(shownType.name().toLowerCase() + "-sales-report-"
                + shownFrom + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }

        List<String[]> summary = new ArrayList<>();
        summary.add(new String[]{"Report", shownType.toString()});
        summary.add(new String[]{"Range", rangeText(shownFrom, shownTo)});
        summary.add(new String[]{"Total revenue", shownTotals[0]});
        summary.add(new String[]{"Orders", shownTotals[1]});
        summary.add(new String[]{"Items sold", shownTotals[2]});

        List<PdfReport.Section> sections = List.of(
                new PdfReport.Section(null, COLUMNS, new ArrayList<>(shownRows)));

        try {
            PdfReport.write(file, shownType + " Report", summary, sections);
            auditService.log(currentUser, "Export Report", "REPORTS",
                    shownType + " " + shownFrom + " to " + shownTo + " -> " + file.getName());
            lblStatus.setText("Saved: " + file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "Report saved to:\n" + file.getAbsolutePath(),
                    "Reports", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save the report: " + ex.getMessage(),
                    "Reports", JOptionPane.ERROR_MESSAGE);
        }
    }
}