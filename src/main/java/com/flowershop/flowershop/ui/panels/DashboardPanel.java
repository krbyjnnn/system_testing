package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Dispute;
import com.flowershop.flowershop.model.Order;
import com.flowershop.flowershop.model.ProductInventory;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.DisputeRepository;
import com.flowershop.flowershop.repository.OrderItemRepository;
import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;
import com.flowershop.flowershop.repository.ReturnsRepository;
import com.flowershop.flowershop.repository.SalesPoint;
import com.flowershop.flowershop.repository.SpoilageRow;
import com.flowershop.flowershop.ui.SpringContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardPanel extends JPanel {

    private static final int PREVIEW_SIZE = 10;
    private static final int DIALOG_SIZE = 15;
    private static final int LOW_STOCK_LIMIT = 5;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnsRepository returnsRepository;
    private final DisputeRepository disputeRepository;
    private final ComponentInventoryRepository componentInventoryRepository;

    private JLabel lblDailySales;
    private JLabel lblItemsSold;
    private JLabel lblTotalProducts;
    private JLabel lblWeeklySales;
    private JLabel lblMonthlySales;
    private JLabel lblNetFlowTitle;
    private JLabel lblNetFlow;

    private final SalesChart salesChart = new SalesChart();
    private final List<PreviewSection> sections = new ArrayList<>();
    private String chartMode = "DAILY";

    public DashboardPanel(
            ProductRepository productRepository,
            ProductInventoryRepository productInventoryRepository,
            OrderRepository orderRepository
    ) {
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

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("Executive Dashboard & Overview");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerPanel.add(lblTitle, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridy = 0;
        gbc.weighty = 0;
        gbc.insets = new Insets(0, 0, 15, 0);
        centerPanel.add(createCardsRow(), gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.45;
        centerPanel.add(createMiddleRow(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 0.55;
        gbc.insets = new Insets(0, 0, 0, 0);
        centerPanel.add(createBottomRow(), gbc);

        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel createCardsRow() {
        JPanel cardsGrid = new JPanel(new GridLayout(1, 3, 15, 0));
        cardsGrid.setOpaque(false);

        lblDailySales = new JLabel("₱0.00", SwingConstants.CENTER);
        lblItemsSold = new JLabel("0", SwingConstants.CENTER);
        lblTotalProducts = new JLabel("0", SwingConstants.CENTER);

        cardsGrid.add(createKpiCard("Today's Sales", lblDailySales, new Color(40, 167, 69)));
        cardsGrid.add(createKpiCard("Items Sold", lblItemsSold, new Color(23, 162, 184)));
        cardsGrid.add(createKpiCard("Active Products", lblTotalProducts, new Color(255, 193, 7)));

        return cardsGrid;
    }

    private JPanel createMiddleRow() {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 250));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
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
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));

        JLabel lblChartTitle = new JLabel("Sales");
        lblChartTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblChartTitle.setForeground(new Color(40, 40, 40));

        JToggleButton btnDaily = createFilterButton("Daily", "DAILY");
        JToggleButton btnWeekly = createFilterButton("Weekly", "WEEKLY");
        JToggleButton btnMonthly = createFilterButton("Monthly", "MONTHLY");
        btnDaily.setSelected(true);

        ButtonGroup group = new ButtonGroup();
        group.add(btnDaily);
        group.add(btnWeekly);
        group.add(btnMonthly);

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filters.setOpaque(false);
        filters.add(btnDaily);
        filters.add(btnWeekly);
        filters.add(btnMonthly);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(lblChartTitle, BorderLayout.WEST);
        titleRow.add(filters, BorderLayout.EAST);

        panel.add(titleRow, BorderLayout.NORTH);
        panel.add(salesChart, BorderLayout.CENTER);

        return panel;
    }

    private JToggleButton createFilterButton(String text, String mode) {
        JToggleButton button = new JToggleButton(text);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.addActionListener(e -> {
            chartMode = mode;
            runSafely(this::loadChart);
            runSafely(this::loadNetFlow);
        });
        return button;
    }

    private JPanel createBarsPanel() {
        JPanel bars = new JPanel(new GridLayout(3, 1, 0, 12));
        bars.setOpaque(false);

        lblWeeklySales = new JLabel("₱0.00");
        lblMonthlySales = new JLabel("₱0.00");
        lblNetFlowTitle = new JLabel("Net Flow");
        lblNetFlow = new JLabel("₱0.00");

        bars.add(createBar(new JLabel("Weekly Sales"), lblWeeklySales, new Color(23, 162, 184)));
        bars.add(createBar(new JLabel("Monthly Sales"), lblMonthlySales, new Color(111, 66, 193)));
        bars.add(createBar(lblNetFlowTitle, lblNetFlow, new Color(40, 167, 69)));

        return bars;
    }

    private JPanel createBar(JLabel titleLabel, JLabel valueLabel, Color accentColor) {
        JPanel bar = new JPanel(new BorderLayout(10, 0));
        bar.setBackground(Color.WHITE);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                        BorderFactory.createEmptyBorder(8, 14, 8, 14)
                )
        ));

        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        titleLabel.setForeground(Color.GRAY);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valueLabel.setForeground(new Color(40, 40, 40));

        bar.add(titleLabel, BorderLayout.WEST);
        bar.add(valueLabel, BorderLayout.EAST);

        return bar;
    }

    private JPanel createBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 15, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 300));

        sections.add(new PreviewSection(
                "Low Stock",
                new String[]{"ID", "Product Name", "Category", "Quantity"},
                new int[]{1, 3},
                this::loadLowStock
        ));
        sections.add(new PreviewSection(
                "Spoilage",
                new String[]{"Type", "Item", "Quantity", "Expiry Date"},
                new int[]{1, 3},
                this::loadSpoilage
        ));
        sections.add(new PreviewSection(
                "Transactions",
                new String[]{"Transaction No.", "Date & Time", "Type", "Cashier", "Total", "Status"},
                new int[]{0, 4, 5},
                this::loadTransactions
        ));
        sections.add(new PreviewSection(
                "Disputes",
                new String[]{"ID", "Transaction No.", "Customer", "Complaint", "Status", "Date Filed"},
                new int[]{2, 4},
                this::loadDisputes
        ));

        for (PreviewSection section : sections) {
            row.add(section.panel);
        }

        return row;
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(4, 0, 0, 0, accentColor),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                        BorderFactory.createEmptyBorder(12, 12, 12, 12)
                )
        ));

        JLabel lblCardTitle = new JLabel(title, SwingConstants.CENTER);
        lblCardTitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblCardTitle.setForeground(Color.GRAY);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valueLabel.setForeground(new Color(40, 40, 40));

        card.add(lblCardTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    public void refreshDashboardData() {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal daily = orderRepository != null ? orderRepository.calculateRevenueBetween(startOfDay, now) : BigDecimal.ZERO;
        lblDailySales.setText("₱" + String.format("%.2f", daily != null ? daily : BigDecimal.ZERO));

        LocalDateTime startOfWeek = LocalDate.now().minusDays(7).atStartOfDay();
        BigDecimal weekly = orderRepository != null ? orderRepository.calculateRevenueBetween(startOfWeek, now) : BigDecimal.ZERO;
        lblWeeklySales.setText("₱" + String.format("%.2f", weekly != null ? weekly : BigDecimal.ZERO));

        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal monthly = orderRepository != null ? orderRepository.calculateRevenueBetween(startOfMonth, now) : BigDecimal.ZERO;
        lblMonthlySales.setText("₱" + String.format("%.2f", monthly != null ? monthly : BigDecimal.ZERO));

        long count = productRepository != null ? productRepository.countActiveProducts() : 0;
        lblTotalProducts.setText(String.valueOf(count));

        runSafely(() -> {
            Long sold = orderItemRepository != null ? orderItemRepository.countItemsSoldToday() : 0L;
            lblItemsSold.setText(String.valueOf(sold != null ? sold : 0L));
        });
        runSafely(this::loadChart);
        runSafely(this::loadNetFlow);
        for (PreviewSection section : sections) {
            runSafely(section::load);
        }
    }

    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void loadChart() {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();

        if (orderRepository != null) {
            LocalDate today = LocalDate.now();

            if ("WEEKLY".equals(chartMode)) {
                Map<String, BigDecimal> found = toMap(orderRepository.findWeeklySales());
                LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MMM d");
                for (int i = 7; i >= 0; i--) {
                    LocalDate weekStart = thisMonday.minusWeeks(i);
                    labels.add(weekStart.format(labelFormat));
                    values.add(found.getOrDefault(weekStart.toString(), BigDecimal.ZERO));
                }
            } else if ("MONTHLY".equals(chartMode)) {
                Map<String, BigDecimal> found = toMap(orderRepository.findMonthlySales());
                YearMonth thisMonth = YearMonth.from(today);
                DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MMM yy");
                for (int i = 11; i >= 0; i--) {
                    YearMonth month = thisMonth.minusMonths(i);
                    labels.add(month.format(labelFormat));
                    values.add(found.getOrDefault(month.toString(), BigDecimal.ZERO));
                }
            } else {
                Map<String, BigDecimal> found = toMap(orderRepository.findDailySales());
                DateTimeFormatter labelFormat = DateTimeFormatter.ofPattern("MMM d");
                for (int i = 6; i >= 0; i--) {
                    LocalDate day = today.minusDays(i);
                    labels.add(day.format(labelFormat));
                    values.add(found.getOrDefault(day.toString(), BigDecimal.ZERO));
                }
            }
        }

        salesChart.setData(labels, values);
    }

    private Map<String, BigDecimal> toMap(List<SalesPoint> points) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (SalesPoint point : points) {
            map.put(point.getPeriod(), point.getSales() != null ? point.getSales() : BigDecimal.ZERO);
        }
        return map;
    }

    private void loadNetFlow() {
        LocalDate today = LocalDate.now();
        LocalDateTime start;
        String periodName;

        if ("WEEKLY".equals(chartMode)) {
            start = today.minusDays(7).atStartOfDay();
            periodName = "Last 7 Days";
        } else if ("MONTHLY".equals(chartMode)) {
            start = today.withDayOfMonth(1).atStartOfDay();
            periodName = "This Month";
        } else {
            start = today.atStartOfDay();
            periodName = "Today";
        }
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        BigDecimal moneyIn = orderRepository != null ? orderRepository.calculateMoneyInBetween(start, end) : BigDecimal.ZERO;
        BigDecimal moneyOut = returnsRepository != null ? returnsRepository.sumRefundsBetween(start, end) : BigDecimal.ZERO;
        BigDecimal net = (moneyIn != null ? moneyIn : BigDecimal.ZERO).subtract(moneyOut != null ? moneyOut : BigDecimal.ZERO);

        lblNetFlowTitle.setText("Net Flow (" + periodName + ")");
        lblNetFlow.setText((net.signum() < 0 ? "-₱" : "₱") + String.format("%.2f", net.abs()));
        lblNetFlow.setForeground(net.signum() < 0 ? new Color(220, 53, 69) : new Color(40, 167, 69));
    }

    private PageData loadLowStock(int page, int size) {
        List<Object[]> rows = new ArrayList<>();
        int totalPages = 0;

        if (productInventoryRepository != null) {
            Page<ProductInventory> result = productInventoryRepository.findLowStockPage(LOW_STOCK_LIMIT, PageRequest.of(page, size));
            for (ProductInventory item : result.getContent()) {
                rows.add(new Object[]{
                    item.getPbatchId(),
                    item.getProduct() != null ? item.getProduct().getProductName() : "N/A",
                    (item.getProduct() != null && item.getProduct().getCategory() != null)
                        ? item.getProduct().getCategory().getName() : "N/A",
                    item.getQuantityOnHand()
                });
            }
            totalPages = result.getTotalPages();
        }

        return new PageData(rows, totalPages);
    }

    private PageData loadSpoilage(int page, int size) {
        List<Object[]> rows = new ArrayList<>();
        int totalPages = 0;

        if (componentInventoryRepository != null) {
            Page<SpoilageRow> result = componentInventoryRepository.findSpoilage(PageRequest.of(page, size));
            for (SpoilageRow item : result.getContent()) {
                rows.add(new Object[]{
                    item.getItemType(),
                    item.getItemName(),
                    item.getQuantityOnHand(),
                    item.getExpiryDate()
                });
            }
            totalPages = result.getTotalPages();
        }

        return new PageData(rows, totalPages);
    }

    private PageData loadTransactions(int page, int size) {
        List<Object[]> rows = new ArrayList<>();
        int totalPages = 0;

        if (orderRepository != null) {
            Page<Order> result = orderRepository.findAllByOrderByOrderDateDesc(PageRequest.of(page, size));
            for (Order order : result.getContent()) {
                rows.add(new Object[]{
                    order.getTransactionNumber(),
                    order.getOrderDate() != null ? order.getOrderDate().format(DATE_TIME) : "",
                    order.getOrderType(),
                    order.getUser() != null ? order.getUser().getFullName() : "N/A",
                    "₱" + String.format("%.2f", order.getTotalAmount()),
                    order.getOrderStatus()
                });
            }
            totalPages = result.getTotalPages();
        }

        return new PageData(rows, totalPages);
    }

    private PageData loadDisputes(int page, int size) {
        List<Object[]> rows = new ArrayList<>();
        int totalPages = 0;

        if (disputeRepository != null) {
            Page<Dispute> result = disputeRepository.findAllByOrderByDateFiledDesc(PageRequest.of(page, size));
            for (Dispute dispute : result.getContent()) {
                rows.add(new Object[]{
                    dispute.getDisputeId(),
                    dispute.getOrder() != null ? dispute.getOrder().getTransactionNumber() : "N/A",
                    dispute.getCustomerName(),
                    dispute.getComplaint(),
                    dispute.getStatus(),
                    dispute.getDateFiled() != null ? dispute.getDateFiled().format(DATE_TIME) : ""
                });
            }
            totalPages = result.getTotalPages();
        }

        return new PageData(rows, totalPages);
    }

    private interface PageLoader {
        PageData load(int page, int size);
    }

    private static class PageData {
        private final List<Object[]> rows;
        private final int totalPages;

        PageData(List<Object[]> rows, int totalPages) {
            this.rows = rows;
            this.totalPages = totalPages;
        }
    }

    private class PreviewSection {
        private final String title;
        private final String[] columns;
        private final int[] previewColumns;
        private final PageLoader loader;
        private final DefaultTableModel model;
        private final JPanel panel;

        PreviewSection(String title, String[] columns, int[] previewColumns, PageLoader loader) {
            this.title = title;
            this.columns = columns;
            this.previewColumns = previewColumns;
            this.loader = loader;

            String[] previewNames = new String[previewColumns.length];
            for (int i = 0; i < previewColumns.length; i++) {
                previewNames[i] = columns[previewColumns[i]];
            }

            model = new DefaultTableModel(previewNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            JTable table = new JTable(model);
            table.setRowHeight(24);
            table.setFocusable(false);
            table.setRowSelectionAllowed(false);
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
            table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JScrollPane scrollPane = new JScrollPane(table);
            scrollPane.setBorder(BorderFactory.createEmptyBorder());
            scrollPane.getViewport().setBackground(Color.WHITE);
            scrollPane.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JLabel lblTitle = new JLabel(title);
            lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
            lblTitle.setForeground(new Color(40, 40, 40));

            JLabel lblHint = new JLabel("View all >");
            lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblHint.setForeground(new Color(13, 110, 253));

            JPanel titleRow = new JPanel(new BorderLayout());
            titleRow.setOpaque(false);
            titleRow.add(lblTitle, BorderLayout.WEST);
            titleRow.add(lblHint, BorderLayout.EAST);

            panel = new JPanel(new BorderLayout(0, 8));
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                    BorderFactory.createEmptyBorder(12, 12, 12, 12)
            ));
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            panel.add(titleRow, BorderLayout.NORTH);
            panel.add(scrollPane, BorderLayout.CENTER);

            MouseAdapter opener = new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openDialog();
                }
            };
            table.addMouseListener(opener);
            scrollPane.addMouseListener(opener);
            scrollPane.getViewport().addMouseListener(opener);
            panel.addMouseListener(opener);
            titleRow.addMouseListener(opener);
            lblTitle.addMouseListener(opener);
            lblHint.addMouseListener(opener);
        }

        void load() {
            PageData data = loader.load(0, PREVIEW_SIZE);
            model.setRowCount(0);
            for (Object[] row : data.rows) {
                Object[] previewRow = new Object[previewColumns.length];
                for (int i = 0; i < previewColumns.length; i++) {
                    previewRow[i] = row[previewColumns[i]];
                }
                model.addRow(previewRow);
            }
        }

        void openDialog() {
            Window owner = SwingUtilities.getWindowAncestor(DashboardPanel.this);
            new PagedDialog(owner, title, columns, loader).setVisible(true);
        }
    }

    private class PagedDialog extends JDialog {
        private final PageLoader loader;
        private final DefaultTableModel model;
        private final JButton btnPrevious = new JButton("Previous");
        private final JButton btnNext = new JButton("Next");
        private final JLabel lblPage = new JLabel("Page 1 of 1", SwingConstants.CENTER);
        private int page = 0;

        PagedDialog(Window owner, String title, String[] columns, PageLoader loader) {
            super(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
            this.loader = loader;

            setLayout(new BorderLayout(0, 10));
            getRootPane().setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

            JLabel lblTitle = new JLabel(title);
            lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
            lblTitle.setForeground(new Color(40, 40, 40));

            model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            JTable table = new JTable(model);
            table.setRowHeight(28);
            table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
            table.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            btnPrevious.addActionListener(e -> {
                page--;
                load();
            });
            btnNext.addActionListener(e -> {
                page++;
                load();
            });

            lblPage.setFont(new Font("Segoe UI", Font.PLAIN, 13));

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
            controls.add(btnPrevious);
            controls.add(lblPage);
            controls.add(btnNext);

            add(lblTitle, BorderLayout.NORTH);
            add(new JScrollPane(table), BorderLayout.CENTER);
            add(controls, BorderLayout.SOUTH);

            setSize(900, 560);
            setLocationRelativeTo(owner);
            load();
        }

        private void load() {
            PageData data = loader.load(page, DIALOG_SIZE);
            model.setRowCount(0);
            for (Object[] row : data.rows) {
                model.addRow(row);
            }

            int totalPages = Math.max(1, data.totalPages);
            lblPage.setText("Page " + (page + 1) + " of " + totalPages);
            btnPrevious.setEnabled(page > 0);
            btnNext.setEnabled(page + 1 < totalPages);
        }
    }

    private static class SalesChart extends JPanel {
        private List<String> labels = new ArrayList<>();
        private List<BigDecimal> values = new ArrayList<>();

        SalesChart() {
            setBackground(Color.WHITE);
            setOpaque(true);
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
            int left = 70;
            int right = 20;
            int top = 20;
            int bottom = 30;
            int plotW = getWidth() - left - right;
            int plotH = getHeight() - top - bottom;
            if (n == 0 || plotW <= 0 || plotH <= 0) {
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            double max = 0;
            for (BigDecimal value : values) {
                max = Math.max(max, value.doubleValue());
            }
            double niceMax = max <= 0 ? 4.0 : niceCeil(max);

            int gridLines = 4;
            for (int i = 0; i <= gridLines; i++) {
                int y = top + plotH - (int) Math.round(plotH * i / (double) gridLines);
                g2.setColor(new Color(230, 233, 238));
                g2.drawLine(left, y, left + plotW, y);
                String text = "₱" + String.format("%,.0f", niceMax * i / gridLines);
                g2.setColor(Color.GRAY);
                g2.drawString(text, left - 8 - fm.stringWidth(text), y + fm.getAscent() / 2 - 1);
            }

            int[] xs = new int[n];
            int[] ys = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = n == 1 ? left + plotW / 2 : left + (int) Math.round(plotW * i / (double) (n - 1));
                ys[i] = top + plotH - (int) Math.round(plotH * values.get(i).doubleValue() / niceMax);
            }

            g2.setColor(new Color(13, 110, 253));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < n; i++) {
                g2.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
            }

            for (int i = 0; i < n; i++) {
                g2.setColor(Color.WHITE);
                g2.fillOval(xs[i] - 5, ys[i] - 5, 10, 10);
                g2.setColor(new Color(13, 110, 253));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(xs[i] - 5, ys[i] - 5, 10, 10);

                g2.setColor(Color.GRAY);
                String label = labels.get(i);
                g2.drawString(label, xs[i] - fm.stringWidth(label) / 2, top + plotH + 18);

                if (n <= 12 && values.get(i).signum() > 0) {
                    String valueText = String.format("%,.0f", values.get(i));
                    g2.setColor(new Color(40, 40, 40));
                    g2.drawString(valueText, xs[i] - fm.stringWidth(valueText) / 2, ys[i] - 10);
                }
            }

            g2.dispose();
        }

        private double niceCeil(double value) {
            double exp = Math.pow(10, Math.floor(Math.log10(value)));
            double f = value / exp;
            double nf;
            if (f <= 1) {
                nf = 1;
            } else if (f <= 2) {
                nf = 2;
            } else if (f <= 4) {
                nf = 4;
            } else if (f <= 8) {
                nf = 8;
            } else {
                nf = 10;
            }
            return nf * exp;
        }
    }
}