package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Dispute;
import com.flowershop.flowershop.model.Order;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.DisputeRepository;
import com.flowershop.flowershop.repository.OrderItemRepository;
import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;
import com.flowershop.flowershop.repository.ReturnsRepository;
import com.flowershop.flowershop.repository.SalesPoint;
import com.flowershop.flowershop.ui.SpringContext;
import com.flowershop.flowershop.util.PdfReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Owner dashboard: KPI cards, sales chart, monthly net flow and four "view all" preview tables. */
public class DashboardPanel extends JPanel {

    private static final int PREVIEW_SIZE = 10;   // rows shown in each dashboard preview
    private static final int DIALOG_SIZE = 15;    // rows per page in the "View all" dialog
    private static final int EXPORT_SIZE = 100;   // rows read per query when a table is exported to PDF
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Color BORDER = new Color(220, 224, 230);
    private static final Color TEXT = new Color(40, 40, 40);
    private static final Color GREEN = new Color(40, 167, 69);
    private static final Color BLUE = new Color(13, 110, 253);

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnsRepository returnsRepository;
    private final DisputeRepository disputeRepository;
    private final ComponentInventoryRepository componentInventoryRepository;

    private final JLabel lblDailySales = new JLabel("₱0.00", SwingConstants.CENTER);
    private final JLabel lblItemsSold = new JLabel("0", SwingConstants.CENTER);
    private final JLabel lblSalesTitle = new JLabel("Today's Sales", SwingConstants.CENTER);
    private final JLabel lblItemsTitle = new JLabel("Items Sold Today", SwingConstants.CENTER);
    private final JLabel lblTotalProducts = new JLabel("0", SwingConstants.CENTER);
    private final JLabel lblWeeklySales = new JLabel("₱0.00");
    private final JLabel lblMonthlySales = new JLabel("₱0.00");
    private final JLabel lblNetFlow = new JLabel("₱0.00");

    private final SalesChart salesChart = new SalesChart();
    private final List<PreviewSection> sections = new ArrayList<>();
    private String chartMode = "DAILY"; // DAILY, WEEKLY or MONTHLY (changes the chart and the first two cards)
    // The points currently drawn in the chart, kept so the PDF report can list the same figures
    private List<String> chartLabels = new ArrayList<>();
    private List<BigDecimal> chartValues = new ArrayList<>();

    public DashboardPanel(ProductRepository productRepository,
                          ProductInventoryRepository productInventoryRepository,
                          OrderRepository orderRepository) {
        this.productRepository = productRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = SpringContext.getBean(OrderItemRepository.class);
        this.returnsRepository = SpringContext.getBean(ReturnsRepository.class);
        this.disputeRepository = SpringContext.getBean(DisputeRepository.class);
        this.componentInventoryRepository = SpringContext.getBean(ComponentInventoryRepository.class);

        initUI();
        refreshDashboardData();
    }

    // ------------------------------------------------------------------ UI building

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel title = new JLabel("Executive Dashboard & Overview");
        title.setFont(font(Font.BOLD, 22));
        JButton btnExport = new JButton("Export PDF");
        btnExport.setFont(font(Font.PLAIN, 12));
        btnExport.setFocusPainted(false);
        btnExport.addActionListener(e -> exportDashboard());

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(btnExport, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Three stacked rows: KPI cards, chart + side bars, preview tables
        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 15, 0);
        center.add(createCardsRow(), gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.45;
        center.add(createMiddleRow(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.55;
        gbc.insets = new Insets(0, 0, 0, 0);
        center.add(createBottomRow(), gbc);

        add(center, BorderLayout.CENTER);
    }

    private JPanel createCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 15, 0));
        row.setOpaque(false);
        row.add(createKpiCard(lblSalesTitle, lblDailySales, GREEN));
        row.add(createKpiCard(lblItemsTitle, lblItemsSold, new Color(23, 162, 184)));
        row.add(createKpiCard(new JLabel("Active Products", SwingConstants.CENTER), lblTotalProducts, new Color(255, 193, 7)));
        return row;
    }

    private JPanel createKpiCard(JLabel lblTitle, JLabel value, Color accent) {
        lblTitle.setFont(font(Font.PLAIN, 12));
        lblTitle.setForeground(Color.GRAY);

        value.setFont(font(Font.BOLD, 18));
        value.setForeground(TEXT);

        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(border(BorderFactory.createMatteBorder(4, 0, 0, 0, accent), 12, 12));
        card.add(lblTitle, BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JPanel createMiddleRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 250));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.weightx = 0.65;
        gbc.insets = new Insets(0, 0, 15, 15);
        row.add(createChartPanel(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.35;
        gbc.insets = new Insets(0, 0, 15, 0);
        row.add(createBarsPanel(), gbc);
        return row;
    }

    private JPanel createChartPanel() {
        JLabel lblTitle = new JLabel("Sales");
        lblTitle.setFont(font(Font.BOLD, 16));
        lblTitle.setForeground(TEXT);

        // Daily / Weekly / Monthly toggle buttons (only one can be selected)
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filters.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (String[] f : new String[][]{{"Daily", "DAILY"}, {"Weekly", "WEEKLY"}, {"Monthly", "MONTHLY"}}) {
            JToggleButton button = createFilterButton(f[0], f[1]);
            group.add(button);
            filters.add(button);
        }

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(lblTitle, BorderLayout.WEST);
        titleRow.add(filters, BorderLayout.EAST);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(border(null, 12, 15));
        panel.add(titleRow, BorderLayout.NORTH);
        panel.add(salesChart, BorderLayout.CENTER);
        return panel;
    }

    /** Chart range button; it changes the chart and the sales / items sold cards, not the net flow. */
    private JToggleButton createFilterButton(String text, String mode) {
        JToggleButton button = new JToggleButton(text, mode.equals(chartMode));
        button.setFont(font(Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.addActionListener(e -> {
            chartMode = mode;
            runSafely(this::loadChart);
            runSafely(this::loadPeriodCards);
        });
        return button;
    }

    private JPanel createBarsPanel() {
        JPanel bars = new JPanel(new GridLayout(3, 1, 0, 12));
        bars.setOpaque(false);
        bars.add(createBar("Weekly Sales", lblWeeklySales, new Color(23, 162, 184)));
        bars.add(createBar("Monthly Sales", lblMonthlySales, new Color(111, 66, 193)));
        bars.add(createBar("Monthly Net Flow", lblNetFlow, GREEN));
        return bars;
    }

    private JPanel createBar(String title, JLabel value, Color accent) {
        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(font(Font.PLAIN, 14));
        lblTitle.setForeground(Color.GRAY);

        value.setFont(font(Font.BOLD, 18));
        value.setForeground(TEXT);

        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(Color.WHITE);
        bar.setBorder(border(BorderFactory.createMatteBorder(0, 4, 0, 0, accent), 8, 14));
        bar.add(lblTitle, BorderLayout.WEST);
        bar.add(value, BorderLayout.EAST);
        return bar;
    }

    private JPanel createBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 15, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 300));

        // previewColumns = indexes of the columns shown in the small dashboard table;
        // the "View all" dialog shows every column.
        sections.add(new PreviewSection("Low Stock",
                new String[]{"Type", "Item", "Category", "Quantity"}, new int[]{0, 1, 3}, this::loadLowStock));
        sections.add(new PreviewSection("Spoilage",
                new String[]{"Type", "Item", "Quantity", "Expiry Date"}, new int[]{1, 3}, this::loadSpoilage));
        sections.add(new PreviewSection("Transactions",
                new String[]{"Transaction No.", "Date & Time", "Type", "Cashier", "Total", "Status"},
                new int[]{0, 4, 5}, this::loadTransactions));
        sections.add(new PreviewSection("Disputes",
                new String[]{"ID", "Transaction No.", "Customer", "Complaint", "Status", "Date Filed"},
                new int[]{2, 4}, this::loadDisputes));

        sections.forEach(s -> row.add(s.panel));
        return row;
    }

    // ------------------------------------------------------------------ data loading

    /** Reloads every number, the chart and all preview tables. Call this after any sale/return/dispute. */
    public void refreshDashboardData() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();

        lblWeeklySales.setText(money(orderRepository.calculateRevenueBetween(today.minusDays(7).atStartOfDay(), now)));
        lblMonthlySales.setText(money(orderRepository.calculateRevenueBetween(today.withDayOfMonth(1).atStartOfDay(), now)));
        lblTotalProducts.setText(String.valueOf(productRepository.countActiveProducts()));

        runSafely(this::loadPeriodCards);
        runSafely(this::loadChart);
        runSafely(this::loadNetFlow);
        sections.forEach(s -> runSafely(s::load));
    }

    /** Sales and items sold cards follow the selected filter: today, this week (Monday start) or this month. */
    private void loadPeriodCards() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate start = today;
        String name = "Today";
        if ("WEEKLY".equals(chartMode)) {
            start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            name = "This Week";
        } else if ("MONTHLY".equals(chartMode)) {
            start = today.withDayOfMonth(1);
            name = "This Month";
        }
        lblSalesTitle.setText(name + "'s Sales");
        lblItemsTitle.setText("Items Sold " + name);
        lblDailySales.setText(money(orderRepository.calculateRevenueBetween(start.atStartOfDay(), now)));
        Long sold = orderItemRepository.countItemsSoldBetween(start.atStartOfDay(), now);
        lblItemsSold.setText(String.valueOf(sold != null ? sold : 0L));
    }

    /** Monthly net flow = money received this month minus refunds issued this month. */
    private void loadNetFlow() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        BigDecimal net = nz(orderRepository.calculateMoneyInBetween(start, end))
                .subtract(nz(returnsRepository.sumRefundsBetween(start, end)));

        lblNetFlow.setText((net.signum() < 0 ? "-" : "") + money(net.abs()));
        lblNetFlow.setForeground(net.signum() < 0 ? new Color(220, 53, 69) : GREEN);
    }

    /** Builds the chart points: 7 days, 8 weeks (Monday start) or 12 months, filling gaps with zero. */
    private void loadChart() {
        boolean weekly = "WEEKLY".equals(chartMode);
        boolean monthly = "MONTHLY".equals(chartMode);

        Map<String, BigDecimal> found = toMap(monthly ? orderRepository.findMonthlySales()
                : weekly ? orderRepository.findWeeklySales() : orderRepository.findDailySales());

        int count = monthly ? 12 : weekly ? 8 : 7;
        DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern(monthly ? "MMM yy" : "MMM d");
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        for (int i = count - 1; i >= 0; i--) {
            Temporal period;
            if (monthly) period = YearMonth.from(today).minusMonths(i);
            else if (weekly) period = thisMonday.minusWeeks(i);
            else period = today.minusDays(i);

            labels.add(labelFormat.format(period));
            values.add(found.getOrDefault(period.toString(), BigDecimal.ZERO)); // key matches the repository's period string
        }
        chartLabels = labels;
        chartValues = values;
        salesChart.setData(labels, values);
    }

    /** Turns repository rows into a period -> sales lookup (e.g. "2026-10-09" -> 2998.00). */
    private Map<String, BigDecimal> toMap(List<SalesPoint> points) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (SalesPoint point : points) {
            map.put(point.getPeriod(), nz(point.getSales()));
        }
        return map;
    }

    private PageData loadLowStock(int page, int size) {
        return toData(componentInventoryRepository.findLowStock(PageRequest.of(page, size)),
                i -> new Object[]{i.getItemType(), i.getItemName(),
                        i.getCategoryName() != null ? i.getCategoryName() : "N/A", i.getQuantityOnHand()});
    }

    private PageData loadSpoilage(int page, int size) {
        return toData(componentInventoryRepository.findSpoilage(PageRequest.of(page, size)),
                i -> new Object[]{i.getItemType(), i.getItemName(), i.getQuantityOnHand(), i.getExpiryDate()});
    }

    private PageData loadTransactions(int page, int size) {
        return toData(orderRepository.findAllByOrderByOrderDateDesc(PageRequest.of(page, size)),
                (Order o) -> new Object[]{
                        o.getTransactionNumber(),
                        o.getOrderDate() != null ? o.getOrderDate().format(DATE_TIME) : "",
                        o.getOrderType(),
                        o.getUser() != null ? o.getUser().getFullName() : "N/A",
                        money(o.getTotalAmount()),
                        o.getOrderStatus()});
    }

    private PageData loadDisputes(int page, int size) {
        return toData(disputeRepository.findAllByOrderByDateFiledDesc(PageRequest.of(page, size)),
                (Dispute d) -> new Object[]{
                        d.getDisputeId(),
                        d.getOrder() != null ? d.getOrder().getTransactionNumber() : "N/A",
                        d.getCustomerName(),
                        d.getComplaint(),
                        d.getStatus(),
                        d.getDateFiled() != null ? d.getDateFiled().format(DATE_TIME) : ""});
    }

    // ------------------------------------------------------------------ PDF export

    /** Whole dashboard: key figures, the sales shown in the chart, and every row of the four tables. */
    private void exportDashboard() {
        File file = chooseFile("Dashboard Report");
        if (file == null) {
            return;
        }

        try {
            List<String[]> summary = List.of(
                    new String[]{lblSalesTitle.getText(), lblDailySales.getText()},
                    new String[]{lblItemsTitle.getText(), lblItemsSold.getText()},
                    new String[]{"Active Products", lblTotalProducts.getText()},
                    new String[]{"Weekly Sales", lblWeeklySales.getText()},
                    new String[]{"Monthly Sales", lblMonthlySales.getText()},
                    new String[]{"Monthly Net Flow", lblNetFlow.getText()});

            List<Object[]> salesRows = new ArrayList<>();
            for (int i = 0; i < chartLabels.size(); i++) {
                salesRows.add(new Object[]{chartLabels.get(i), money(chartValues.get(i))});
            }
            String period = chartMode.charAt(0) + chartMode.substring(1).toLowerCase();

            List<PdfReport.Section> report = new ArrayList<>();
            report.add(new PdfReport.Section("Sales (" + period + ")", new String[]{"Period", "Sales"}, salesRows));
            for (PreviewSection section : sections) {
                report.add(new PdfReport.Section(section.title, section.columns, loadAllRows(section.loader)));
            }

            PdfReport.write(file, "Dashboard Report", summary, report);
            offerToOpen(file);
        } catch (Exception ex) {
            showExportError(ex);
        }
    }

    /** One dashboard table with all of its rows, not just the ones shown in the small preview. */
    private void exportSection(PreviewSection section) {
        File file = chooseFile(section.title);
        if (file == null) {
            return;
        }

        try {
            PdfReport.write(file, section.title, List.of(),
                    List.of(new PdfReport.Section(null, section.columns, loadAllRows(section.loader))));
            offerToOpen(file);
        } catch (Exception ex) {
            showExportError(ex);
        }
    }

    /** Reads page after page until the table has no more rows. */
    private List<Object[]> loadAllRows(PageLoader loader) {
        List<Object[]> all = new ArrayList<>();
        int page = 0;
        int totalPages;
        do {
            PageData data = loader.load(page, EXPORT_SIZE);
            all.addAll(data.rows);
            totalPages = data.totalPages;
            page++;
        } while (page < totalPages);
        return all;
    }

    /** Returns the file to write, or null when the user cancels or does not want to replace an existing file. */
    private File chooseFile(String name) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save PDF");
        chooser.setFileFilter(new FileNameExtensionFilter("PDF files", "pdf"));
        chooser.setSelectedFile(new File(name.replace(' ', '-') + "-" + LocalDate.now() + ".pdf"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".pdf")) {
            file = new File(file.getParentFile(), file.getName() + ".pdf");
        }
        if (file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Replace it?",
                "Save PDF", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }
        return file;
    }

    private void offerToOpen(File file) {
        int answer = JOptionPane.showConfirmDialog(this,
                "PDF saved to:\n" + file.getAbsolutePath() + "\n\nOpen it now?",
                "PDF Saved", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION && Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
            } catch (Exception ex) {
                showExportError(ex);
            }
        }
    }

    private void showExportError(Exception ex) {
        JOptionPane.showMessageDialog(this, "The PDF was not created: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
    }

    /** Converts one page of entities into table rows using the given row mapper. */
    private <T> PageData toData(Page<T> result, Function<T, Object[]> toRow) {
        List<Object[]> rows = new ArrayList<>();
        result.getContent().forEach(item -> rows.add(toRow.apply(item)));
        return new PageData(rows, result.getTotalPages());
    }

    // ------------------------------------------------------------------ small helpers

    private static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String money(BigDecimal value) {
        return "₱" + String.format("%.2f", nz(value));
    }

    /** Light-grey outline with padding, optionally preceded by a coloured accent edge. */
    private static Border border(Border accent, int vertical, int horizontal) {
        Border box = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(vertical, horizontal, vertical, horizontal));
        return accent == null ? box : BorderFactory.createCompoundBorder(accent, box);
    }

    private static JTable readOnlyTable(DefaultTableModel model, int rowHeight, int fontSize) {
        JTable table = new JTable(model);
        table.setRowHeight(rowHeight);
        table.setFont(font(Font.PLAIN, fontSize));
        table.getTableHeader().setFont(font(Font.BOLD, fontSize));
        return table;
    }

    private static DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /** One bad query shouldn't blank the whole dashboard, so each section loads in isolation. */
    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // ------------------------------------------------------------------ preview + dialog

    private interface PageLoader {
        PageData load(int page, int size);
    }

    private static class PageData {
        final List<Object[]> rows;
        final int totalPages;

        PageData(List<Object[]> rows, int totalPages) {
            this.rows = rows;
            this.totalPages = totalPages;
        }
    }

    /** A clickable dashboard card with a small table; clicking anywhere on it opens the full paged list. */
    private class PreviewSection {
        private final String title;
        private final String[] columns;
        private final int[] previewColumns;
        private final PageLoader loader;
        private final DefaultTableModel model;
        private final JPanel panel = new JPanel(new BorderLayout(0, 8));

        PreviewSection(String title, String[] columns, int[] previewColumns, PageLoader loader) {
            this.title = title;
            this.columns = columns;
            this.previewColumns = previewColumns;
            this.loader = loader;

            String[] previewNames = new String[previewColumns.length];
            for (int i = 0; i < previewColumns.length; i++) {
                previewNames[i] = columns[previewColumns[i]];
            }
            model = readOnlyModel(previewNames);

            JTable table = readOnlyTable(model, 24, 12);
            table.setFocusable(false);
            table.setRowSelectionAllowed(false);

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(Color.WHITE);

            JLabel lblTitle = new JLabel(title);
            lblTitle.setFont(font(Font.BOLD, 15));
            lblTitle.setForeground(TEXT);
            JLabel lblHint = new JLabel("View all >");
            lblHint.setFont(font(Font.PLAIN, 12));
            lblHint.setForeground(BLUE);

            // The PDF button handles its own click, so it does not open the "View all" list
            JButton btnPdf = new JButton("PDF");
            btnPdf.setFont(font(Font.PLAIN, 11));
            btnPdf.setMargin(new Insets(1, 6, 1, 6));
            btnPdf.setFocusPainted(false);
            btnPdf.addActionListener(e -> exportSection(this));

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            actions.setOpaque(false);
            actions.add(btnPdf);
            actions.add(lblHint);

            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);
            titleRow.add(lblTitle, BorderLayout.WEST);
            titleRow.add(actions, BorderLayout.EAST);

            panel.setBackground(Color.WHITE);
            panel.setBorder(border(null, 12, 12));
            panel.add(titleRow, BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            // Every child swallows clicks, so attach the same opener to all of them
            MouseAdapter opener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openDialog();
                }
            };
            for (JComponent c : new JComponent[]{panel, titleRow, actions, lblTitle, lblHint, table, scrollPane, scrollPane.getViewport()}) {
                c.addMouseListener(opener);
                c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        }

        void load() {
            model.setRowCount(0);
            for (Object[] row : loader.load(0, PREVIEW_SIZE).rows) {
                Object[] preview = new Object[previewColumns.length];
                for (int i = 0; i < previewColumns.length; i++) {
                    preview[i] = row[previewColumns[i]];
                }
                model.addRow(preview);
            }
        }

        void openDialog() {
            new PagedDialog(SwingUtilities.getWindowAncestor(DashboardPanel.this), title, columns, loader).setVisible(true);
        }
    }

    /** Full-size modal table with Previous / Next paging. */
    private static class PagedDialog extends JDialog {
        private final PageLoader loader;
        private final DefaultTableModel model;
        private final JButton btnPrevious = new JButton("Previous");
        private final JButton btnNext = new JButton("Next");
        private final JLabel lblPage = new JLabel("Page 1 of 1", SwingConstants.CENTER);
        private int page = 0;

        PagedDialog(Window owner, String title, String[] columns, PageLoader loader) {
            super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
            this.loader = loader;
            this.model = readOnlyModel(columns);

            setLayout(new BorderLayout(0, 10));
            getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            JLabel lblTitle = new JLabel(title);
            lblTitle.setFont(font(Font.BOLD, 18));
            lblTitle.setForeground(TEXT);
            lblPage.setFont(font(Font.PLAIN, 13));

            btnPrevious.addActionListener(e -> { page--; load(); });
            btnNext.addActionListener(e -> { page++; load(); });

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
            controls.add(btnPrevious);
            controls.add(lblPage);
            controls.add(btnNext);

            add(lblTitle, BorderLayout.NORTH);
            add(new JScrollPane(readOnlyTable(model, 28, 13)), BorderLayout.CENTER);
            add(controls, BorderLayout.SOUTH);

            setSize(900, 560);
            setLocationRelativeTo(owner);
            load();
        }

        private void load() {
            PageData data = loader.load(page, DIALOG_SIZE);
            model.setRowCount(0);
            data.rows.forEach(model::addRow);

            int totalPages = Math.max(1, data.totalPages);
            lblPage.setText("Page " + (page + 1) + " of " + totalPages);
            btnPrevious.setEnabled(page > 0);
            btnNext.setEnabled(page + 1 < totalPages);
        }
    }

    // ------------------------------------------------------------------ chart

    /** Hand-drawn line chart: grid + peso axis on the left, one labelled dot per period. */
    private static class SalesChart extends JPanel {
        private List<String> labels = new ArrayList<>();
        private List<BigDecimal> values = new ArrayList<>();

        SalesChart() {
            setBackground(Color.WHITE);
        }

        void setData(List<String> labels, List<BigDecimal> values) {
            this.labels = labels;
            this.values = values;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int n = values.size();
            int left = 70, right = 20, top = 20, bottom = 30; // plot-area margins
            int plotW = getWidth() - left - right;
            int plotH = getHeight() - top - bottom;
            if (n == 0 || plotW <= 0 || plotH <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font(Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            double max = values.stream().mapToDouble(BigDecimal::doubleValue).max().orElse(0);
            double niceMax = max <= 0 ? 4.0 : niceCeil(max); // top of the y-axis, rounded to a tidy number

            // Horizontal grid lines with peso labels
            int gridLines = 4;
            for (int i = 0; i <= gridLines; i++) {
                int y = top + plotH - (int) Math.round(plotH * i / (double) gridLines);
                g2.setColor(new Color(230, 233, 238));
                g2.drawLine(left, y, left + plotW, y);
                String text = "₱" + String.format("%,.0f", niceMax * i / gridLines);
                g2.setColor(Color.GRAY);
                g2.drawString(text, left - 8 - fm.stringWidth(text), y + fm.getAscent() / 2 - 1);
            }

            // Pixel position of every data point
            int[] xs = new int[n], ys = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = n == 1 ? left + plotW / 2 : left + (int) Math.round(plotW * i / (double) (n - 1));
                ys[i] = top + plotH - (int) Math.round(plotH * values.get(i).doubleValue() / niceMax);
            }

            g2.setColor(BLUE);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < n; i++) {
                g2.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
            }

            for (int i = 0; i < n; i++) {
                g2.setColor(Color.WHITE);
                g2.fillOval(xs[i] - 5, ys[i] - 5, 10, 10);
                g2.setColor(BLUE);
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(xs[i] - 5, ys[i] - 5, 10, 10);

                g2.setColor(Color.GRAY);
                g2.drawString(labels.get(i), xs[i] - fm.stringWidth(labels.get(i)) / 2, top + plotH + 18);

                // Value above the dot (skipped when crowded or zero)
                if (n <= 12 && values.get(i).signum() > 0) {
                    String valueText = String.format("%,.0f", values.get(i));
                    g2.setColor(TEXT);
                    g2.drawString(valueText, xs[i] - fm.stringWidth(valueText) / 2, ys[i] - 10);
                }
            }
            g2.dispose();
        }

        /** Rounds up to 1, 2, 4, 8 or 10 times a power of ten (e.g. 2,998 -> 4,000). */
        private double niceCeil(double value) {
            double exp = Math.pow(10, Math.floor(Math.log10(value)));
            for (double step : new double[]{1, 2, 4, 8}) {
                if (value / exp <= step) return step * exp;
            }
            return 10 * exp;
        }
    }
}