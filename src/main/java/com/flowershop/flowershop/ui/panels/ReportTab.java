package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.ui.DatePickerField;
import com.flowershop.flowershop.ui.DatePickerField.Mode;
import com.flowershop.flowershop.util.ListReportPdf;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

// Shared look and behaviour of the Inventory, Procurement, Spoilage and Returns report tabs:
// a filter bar (date range + the tab's own filters), summary cards, a table, and PDF export.
// A tab only says what its columns are and how to fetch its rows.
abstract class ReportTab extends JPanel {

    // What one run of a report produces.
    // summary: {label, value, caption} cards (up to 4). totals: last row of the PDF table, or null.
    protected record Result(List<String[]> summary, List<String[]> rows, String[] totals, List<String> notes) {
    }

    protected static final DateTimeFormatter DISPLAY_DAY = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter RANGE_DAY = DateTimeFormatter.ofPattern("MMM dd, yyyy");

    protected static final Color PAGE_BACKGROUND = new Color(245, 247, 250);
    protected static final Color BORDER = new Color(220, 224, 230);
    protected static final Color HEADER_BACKGROUND = new Color(230, 234, 240);
    protected static final Color TEXT = new Color(40, 40, 40);
    protected static final Color RED = new Color(220, 53, 69);
    protected static final Color GREEN = new Color(40, 167, 69);
    private static final Color[] CARD_COLORS = {
            new Color(13, 110, 253), new Color(40, 167, 69), new Color(214, 150, 0), new Color(111, 66, 193)};

    protected final User currentUser;
    protected final AuditService auditService;
    protected final DatePickerField pickFrom = new DatePickerField();
    protected final DatePickerField pickTo = new DatePickerField();

    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
    private final JButton btnExport = new JButton("Export PDF");
    private final JPanel cards = new JPanel(new GridLayout(1, 4, 15, 0));
    private final JLabel lblStatus = new JLabel(" ");
    private DefaultTableModel model;
    private boolean adjusting;

    // What is on screen now, so the PDF prints exactly that
    private Result shown;
    private LocalDate shownFrom = LocalDate.now();
    private LocalDate shownTo = LocalDate.now();
    private String shownFilter = "";

    protected ReportTab(User currentUser, AuditService auditService) {
        this.currentUser = currentUser;
        this.auditService = auditService;
    }

    // ---- What a tab has to provide ----

    // Banner title in the PDF, e.g. "INVENTORY REPORT"
    protected abstract String pdfTitle();

    // Start of the reference number and of the file name, e.g. "INV"
    protected abstract String refPrefix();

    protected abstract String[] columns();

    // Column widths: pixels on screen, and the relative widths in the PDF
    protected abstract int[] widths();

    protected abstract boolean[] rightAligned();

    protected abstract Result query(LocalDate from, LocalDate to);

    // ---- Optional hooks ----

    // The tab's own filters (a drop-down, ...), added to the filter bar before the date range
    protected void addFilters(JPanel bar) {
    }

    // Shown in the PDF details row and the status line
    protected String filterText() {
        return "None";
    }

    protected String emptyMessage() {
        return "No records in this period.";
    }

    // Text colour of a cell, or null for the normal colour
    protected Color cellColor(int column, String value) {
        return null;
    }

    // ---- Building ----

    // Called by the tab's constructor once its own fields exist (this class cannot do it, because the tab's
    // fields are not created yet while this class's constructor runs)
    protected final void build() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
        setBackground(PAGE_BACKGROUND);

        controls.setBackground(Color.WHITE);
        controls.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        addFilters(controls);
        controls.add(boldLabel("From"));
        controls.add(pickFrom);
        controls.add(boldLabel("To"));
        controls.add(pickTo);
        controls.add(btnExport);

        btnExport.setBackground(new Color(25, 135, 84));
        btnExport.setForeground(Color.WHITE);
        btnExport.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnExport.setFocusPainted(false);

        cards.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(controls, BorderLayout.NORTH);
        top.add(cards, BorderLayout.CENTER);

        model = new DefaultTableModel(columns(), 0) {
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

        // Default range: the 1st of this month up to today
        LocalDate today = LocalDate.now();
        adjusting = true;
        pickFrom.setMode(Mode.DAY);
        pickTo.setMode(Mode.DAY);
        pickFrom.setMaxDate(today);
        pickTo.setMaxDate(today);
        pickFrom.setDate(today.withDayOfMonth(1));
        pickTo.setDate(today);
        pickTo.setMinDate(pickFrom.getDate());
        adjusting = false;

        pickFrom.addChangeListener(() -> {
            if (adjusting) {
                return;
            }
            adjusting = true;
            pickTo.setMinDate(pickFrom.getDate());
            if (pickTo.getDate().isBefore(pickFrom.getDate())) {
                pickTo.setDate(pickFrom.getDate());
            }
            adjusting = false;
            generate();
        });
        pickTo.addChangeListener(this::generate);
        btnExport.addActionListener(e -> exportPdf());

        generate();
    }

    // Called when the user opens the tab, so the figures are never stale
    public void loadData() {
        LocalDate today = LocalDate.now();
        pickFrom.setMaxDate(today);
        pickTo.setMaxDate(today);
        generate();
    }

    // Runs the report for the chosen filters and shows it
    protected final void generate() {
        if (adjusting || model == null) {
            return;
        }
        LocalDate from = pickFrom.getDate();
        LocalDate to = pickTo.getDate();
        if (to.isBefore(from)) {
            to = from;
        }

        Result result;
        try {
            result = query(from, to);
        } catch (Exception ex) {
            lblStatus.setText("Could not load the report: " + ex.getMessage());
            return;
        }

        shown = result;
        shownFrom = from;
        shownTo = to;
        shownFilter = filterText();

        model.setRowCount(0);
        for (String[] row : result.rows()) {
            model.addRow(row);
        }

        cards.removeAll();
        for (int i = 0; i < 4; i++) {
            if (i < result.summary().size()) {
                cards.add(card(CARD_COLORS[i], result.summary().get(i)));
            } else {
                JPanel empty = new JPanel();
                empty.setOpaque(false);
                cards.add(empty);
            }
        }
        cards.revalidate();
        cards.repaint();

        lblStatus.setText(result.rows().size() + (result.rows().size() == 1 ? " record" : " records") + " | "
                + rangeText(from, to) + " | Filter: " + shownFilter);
    }

    private void exportPdf() {
        if (shown == null) {
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save report");
        chooser.setSelectedFile(new File(refPrefix().toLowerCase() + "-report-" + shownFrom + "-to-" + shownTo
                + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }

        String preparedBy = currentUser.getFullName() != null && !currentUser.getFullName().isBlank()
                ? currentUser.getFullName() : currentUser.getUsername();

        float[] widths = new float[widths().length];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = widths()[i];
        }

        ListReportPdf.Data data = new ListReportPdf.Data(pdfTitle(), refPrefix(), rangeText(shownFrom, shownTo),
                shownFilter, preparedBy, shown.summary(), columns(), widths, rightAligned(), shown.rows(),
                shown.totals(), emptyMessage(), shown.notes());
        try {
            ListReportPdf.write(file, data);
            auditService.log(currentUser, "Export Report", "REPORTS",
                    pdfTitle() + " " + shownFrom + " to " + shownTo + " -> " + file.getName());
            lblStatus.setText("Saved: " + file.getAbsolutePath());
            JOptionPane.showMessageDialog(this, "Report saved to:\n" + file.getAbsolutePath(),
                    "Reports", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Could not save the report: " + ex.getMessage(),
                    "Reports", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---- Look ----

    protected static JLabel boldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        return label;
    }

    // Same card look as the Sales tab: small title, big value, small caption, coloured left edge
    private JPanel card(Color accent, String[] values) {
        JLabel lblTitle = new JLabel(values[0]);
        lblTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblTitle.setForeground(Color.GRAY);

        JLabel lblValue = new JLabel(values[1]);
        lblValue.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblValue.setForeground(TEXT);

        JLabel lblCaption = new JLabel(values.length > 2 ? values[2] : " ");
        lblCaption.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblCaption.setForeground(Color.GRAY);

        JPanel text = new JPanel(new GridLayout(3, 1, 0, 2));
        text.setOpaque(false);
        text.add(lblTitle);
        text.add(lblValue);
        text.add(lblCaption);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        BorderFactory.createEmptyBorder(12, 14, 12, 12))));
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

        boolean[] right = rightAligned();

        DefaultTableCellRenderer header = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, false, false, row, column);
                setOpaque(true);
                setBackground(HEADER_BACKGROUND);
                setForeground(TEXT);
                setFont(new Font("Segoe UI", Font.BOLD, 13));
                setHorizontalAlignment(right[column] ? SwingConstants.RIGHT : SwingConstants.LEFT);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER),
                        BorderFactory.createEmptyBorder(6, 8, 6, 8)));
                return this;
            }
        };

        DefaultTableCellRenderer cell = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                           boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, false, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                setHorizontalAlignment(right[column] ? SwingConstants.RIGHT : SwingConstants.LEFT);
                Color color = cellColor(column, String.valueOf(value));
                setForeground(color == null ? TEXT : color);
                setFont(new Font("Segoe UI", color == null ? Font.PLAIN : Font.BOLD, 13));
                return this;
            }
        };

        int[] widths = widths();
        for (int i = 0; i < columns().length; i++) {
            table.getColumnModel().getColumn(i).setHeaderRenderer(header);
            table.getColumnModel().getColumn(i).setCellRenderer(cell);
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    // ---- Helpers for the tabs ----

    protected static String rangeText(LocalDate from, LocalDate to) {
        return from.equals(to) ? from.format(RANGE_DAY) : from.format(RANGE_DAY) + " - " + to.format(RANGE_DAY);
    }

    // "2026-10-25" -> "Oct 25, 2026"
    protected static String day(Object isoDate) {
        return LocalDate.parse(String.valueOf(isoDate)).format(DISPLAY_DAY);
    }

    protected static BigDecimal big(Object value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(value));
    }

    protected static long whole(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    protected static String money(BigDecimal value) {
        return (value.signum() < 0 ? "-" : "") + "₱" + String.format("%,.2f", value.abs());
    }

    protected static String text(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }

    protected static List<String[]> newRows() {
        return new ArrayList<>();
    }
}