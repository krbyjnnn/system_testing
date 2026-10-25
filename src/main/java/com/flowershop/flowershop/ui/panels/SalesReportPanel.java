package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.OrderItemRepository;
import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.repository.PaymentRepository;
import com.flowershop.flowershop.repository.ReturnsRepository;
import com.flowershop.flowershop.repository.SalesPoint;
import com.flowershop.flowershop.repository.SalesReportRow;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.ui.DatePickerField;
import com.flowershop.flowershop.ui.DatePickerField.Mode;
import com.flowershop.flowershop.ui.SpringContext;
import com.flowershop.flowershop.util.SalesReportPdf;

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

class SalesReportPanel extends JPanel {

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
    private static final String[] COLUMNS = {"Day", "Orders", "Items Sold", "Gross Revenue", "Refunds",
            "COGS (Cost of Goods Sold)", "Spoilage Cost", "Net Flow"};
    private static final int NET_COLUMN = COLUMNS.length - 1;

    private static final Color PAGE_BACKGROUND = new Color(245, 247, 250);
    private static final Color BORDER = new Color(220, 224, 230);
    private static final Color HEADER_BACKGROUND = new Color(230, 234, 240);
    private static final Color TEXT = new Color(40, 40, 40);
    private static final Color BLUE = new Color(13, 110, 253);
    private static final Color RED = new Color(220, 53, 69);
    private static final Color PURPLE = new Color(111, 66, 193);
    private static final Color GREEN = new Color(40, 167, 69);

    private final User currentUser;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnsRepository returnsRepository;
    private final PaymentRepository paymentRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final AuditService auditService;

    private final JComboBox<ReportType> cmbType = new JComboBox<>(ReportType.values());
    private final JLabel lblFrom = boldLabel("Date");
    private final JLabel lblTo = boldLabel("To");
    private final DatePickerField pickFrom = new DatePickerField();
    private final DatePickerField pickTo = new DatePickerField();
    private final JButton btnExport = new JButton("Export PDF");
    private final JLabel lblRevenue = new JLabel("₱0.00");
    private final JLabel lblRevenueSub = new JLabel("0 Orders | 0 Items Sold");
    private final JLabel lblRefunds = new JLabel("₱0.00");
    private final JLabel lblRefundsSub = new JLabel("Returned to customers");
    private final JLabel lblSpoilage = new JLabel("₱0.00");
    private final JLabel lblSpoilageSub = new JLabel("Expired and wilted stock");
    private final JLabel lblNet = new JLabel("₱0.00");
    private final JLabel lblNetSub = new JLabel("After refunds, COGS and spoilage");
    private final JLabel lblStatus = new JLabel(" ");
    private final DefaultTableModel model;

    private boolean adjusting;
    private ReportType shownType = ReportType.DAILY;
    private LocalDate shownFrom = LocalDate.now();
    private LocalDate shownTo = LocalDate.now();
    private final List<Object[]> shownRows = new ArrayList<>();
    private BigDecimal shownRevenue = BigDecimal.ZERO;
    private BigDecimal shownRefunds = BigDecimal.ZERO;
    private BigDecimal shownCogs = BigDecimal.ZERO;
    private BigDecimal shownSpoilage = BigDecimal.ZERO;
    private long shownOrders;
    private long shownItems;

    SalesReportPanel(User currentUser, OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository, AuditService auditService) {
        this.currentUser = currentUser;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.auditService = auditService;
        // Taken from the Spring context so MainFrame keeps building this panel the same way
        this.returnsRepository = SpringContext.getBean(ReturnsRepository.class);
        this.paymentRepository = SpringContext.getBean(PaymentRepository.class);
        this.componentInventoryRepository = SpringContext.getBean(ComponentInventoryRepository.class);

        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
        setBackground(PAGE_BACKGROUND);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        controls.setBackground(Color.WHITE);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
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
        head.add(controls, BorderLayout.CENTER);

        JPanel cards = new JPanel(new GridLayout(1, 4, 15, 0));
        cards.setOpaque(false);
        cards.add(card(BLUE, "₱", "Gross Revenue", lblRevenue, lblRevenueSub));
        cards.add(card(RED, "↩", "Total Refunds", lblRefunds, lblRefundsSub));
        cards.add(card(PURPLE, "!", "Spoilage Cost", lblSpoilage, lblSpoilageSub));
        cards.add(card(GREEN, "↗", "Net Cash Flow", lblNet, lblNetSub));

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
        styleTable(table);

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

    // Same card look as the dashboard (round icon, small title, big value, small caption) with a coloured left edge
    private JPanel card(Color accent, String glyph, String title, JLabel value, JLabel caption) {
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(Color.GRAY);

        value.setFont(new Font("Segoe UI", Font.BOLD, 20));
        value.setForeground(TEXT);

        caption.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        caption.setForeground(Color.GRAY);

        JPanel text = new JPanel(new GridLayout(3, 1, 0, 2));
        text.setOpaque(false);
        text.add(lblTitle);
        text.add(value);
        text.add(caption);

        JPanel icon = new JPanel(new GridBagLayout());
        icon.setOpaque(false);
        icon.add(new IconCircle(accent, glyph));

        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        BorderFactory.createEmptyBorder(12, 12, 12, 12))));
        panel.add(icon, BorderLayout.WEST);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private void styleTable(JTable table) {
        table.setRowHeight(30);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setGridColor(BORDER);
        table.setFillsViewportHeight(true);
        table.setSelectionBackground(new Color(232, 240, 254));
        table.setSelectionForeground(TEXT);
        table.getTableHeader().setReorderingAllowed(false);

        DefaultTableCellRenderer header = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, false, false, row, column);
                setOpaque(true);
                setBackground(HEADER_BACKGROUND);
                setForeground(TEXT);
                setFont(new Font("Segoe UI", Font.BOLD, 13));
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)));
                return this;
            }
        };

        // Net Flow is bold and turns red when the period lost money
        DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, false, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
                setFont(new Font("Segoe UI", column == NET_COLUMN ? Font.BOLD : Font.PLAIN, 13));
                if (column == NET_COLUMN) {
                    boolean loss = String.valueOf(value).startsWith("-");
                    setForeground(loss ? RED : GREEN);
                } else {
                    setForeground(TEXT);
                }
                return this;
            }
        };

        int[] widths = {170, 70, 90, 120, 100, 190, 110, 120};
        for (int i = 0; i < COLUMNS.length; i++) {
            table.getColumnModel().getColumn(i).setHeaderRenderer(header);
            table.getColumnModel().getColumn(i).setCellRenderer(cell);
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
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

        Map<LocalDate, SalesReportRow> byDay = new HashMap<>();
        for (SalesReportRow row : orderRepository.findDailyReport(start, endExclusive)) {
            byDay.put(LocalDate.parse(row.getPeriod()), row);
        }
        Map<String, BigDecimal> refunds = toMap(returnsRepository.findDailyRefunds(start, endExclusive));
        Map<String, BigDecimal> cogs = toMap(orderItemRepository.findDailyCogs(start, endExclusive));
        Map<String, BigDecimal> spoilage = toMap(
                componentInventoryRepository.findDailySpoilage(from, to.plusDays(1)));

        long orders = 0;
        long items = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal refundTotal = BigDecimal.ZERO;
        BigDecimal cogsTotal = BigDecimal.ZERO;
        BigDecimal spoilageTotal = BigDecimal.ZERO;

        model.setRowCount(0);
        shownRows.clear();
        for (LocalDate day = from; !day.isAfter(last); day = day.plusDays(1)) {
            SalesReportRow row = byDay.get(day);
            long dayOrders = row == null ? 0 : row.getOrders();
            long dayItems = row == null ? 0 : row.getItemsSold();
            BigDecimal dayRevenue = row == null ? BigDecimal.ZERO : nz(row.getRevenue());
            BigDecimal dayRefunds = refunds.getOrDefault(day.toString(), BigDecimal.ZERO);
            BigDecimal dayCogs = cogs.getOrDefault(day.toString(), BigDecimal.ZERO);
            BigDecimal daySpoilage = spoilage.getOrDefault(day.toString(), BigDecimal.ZERO);
            BigDecimal dayNet = dayRevenue.subtract(dayRefunds).subtract(dayCogs).subtract(daySpoilage);

            Object[] line = {day.format(ROW_DAY), dayOrders, dayItems, money(dayRevenue), money(dayRefunds),
                    money(dayCogs), money(daySpoilage), money(dayNet)};
            model.addRow(line);
            shownRows.add(line);

            orders += dayOrders;
            items += dayItems;
            revenue = revenue.add(dayRevenue);
            refundTotal = refundTotal.add(dayRefunds);
            cogsTotal = cogsTotal.add(dayCogs);
            spoilageTotal = spoilageTotal.add(daySpoilage);
        }
        BigDecimal net = revenue.subtract(refundTotal).subtract(cogsTotal).subtract(spoilageTotal);

        lblRevenue.setText(money(revenue));
        lblRevenueSub.setText(orders + (orders == 1 ? " Order" : " Orders") + " | " + items + " Items Sold");
        lblRefunds.setText(money(refundTotal));
        lblSpoilage.setText(money(spoilageTotal));
        lblNet.setText(money(net));
        lblNet.setForeground(net.signum() < 0 ? RED : TEXT);
        lblNetSub.setText("After " + money(cogsTotal) + " COGS");

        shownRevenue = revenue;
        shownRefunds = refundTotal;
        shownCogs = cogsTotal;
        shownSpoilage = spoilageTotal;
        shownOrders = orders;
        shownItems = items;
        shownType = type;
        shownFrom = from;
        shownTo = to;
        lblStatus.setText(type + ": " + rangeText(from, to));
    }

    // Turns repository rows into a "yyyy-MM-dd" -> amount lookup
    private Map<String, BigDecimal> toMap(List<SalesPoint> points) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (SalesPoint point : points) {
            map.put(point.getPeriod(), nz(point.getSales()));
        }
        return map;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String rangeText(LocalDate from, LocalDate to) {
        return from.equals(to) ? from.format(DAY) : from.format(DAY) + " - " + to.format(DAY);
    }

    private String money(BigDecimal value) {
        BigDecimal amount = nz(value);
        return (amount.signum() < 0 ? "-" : "") + "₱" + String.format("%,.2f", amount.abs());
    }

    // Native queries return the order time as a LocalDateTime or a java.sql.Timestamp depending on the driver
    private String orderTime(Object value) {
        LocalDateTime time = value instanceof java.sql.Timestamp
                ? ((java.sql.Timestamp) value).toLocalDateTime()
                : (LocalDateTime) value;
        return time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a"));
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

        String preparedBy = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        LocalDateTime start = shownFrom.atStartOfDay();
        LocalDateTime endExclusive = shownTo.plusDays(1).atStartOfDay();

        List<Object[]> payments = new ArrayList<>();
        for (Object[] row : paymentRepository.sumPaidByMethod(shownFrom, shownTo.plusDays(1))) {
            payments.add(new Object[]{String.valueOf(row[0]), ((Number) row[1]).longValue(),
                    new BigDecimal(String.valueOf(row[2]))});
        }

        List<Object[]> orderLines = new ArrayList<>();
        for (Object[] row : orderRepository.findCompletedOrdersBetween(start, endExclusive)) {
            orderLines.add(new Object[]{row[0] == null ? "-" : row[0], orderTime(row[1]), row[2], row[3],
                    ((Number) row[4]).longValue(), row[5], money(new BigDecimal(String.valueOf(row[6])))});
        }

        SalesReportPdf.Data data = new SalesReportPdf.Data(shownType.toString(), rangeText(shownFrom, shownTo),
                preparedBy, shownRevenue, shownRefunds, shownCogs, shownSpoilage, shownOrders, shownItems,
                COLUMNS, new ArrayList<>(shownRows), payments, orderLines);

        try {
            SalesReportPdf.write(file, data);
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

    // Round tinted icon used on the cards, drawn the same way as on the dashboard
    private static class IconCircle extends JPanel {
        private final Color color;
        private final String glyph;

        IconCircle(Color color, String glyph) {
            this.color = color;
            this.glyph = glyph;
            setOpaque(false);
            setPreferredSize(new Dimension(52, 52));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
            g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
            g2.setColor(color);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(glyph, (getWidth() - fm.stringWidth(glyph)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }
}