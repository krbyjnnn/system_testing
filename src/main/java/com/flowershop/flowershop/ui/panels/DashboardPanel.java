package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.DisputeRepository;
import com.flowershop.flowershop.repository.OrderItemRepository;
import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.repository.ProcurementRepository;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;
import com.flowershop.flowershop.repository.ReturnsRepository;
import com.flowershop.flowershop.repository.SalesPoint;
import com.flowershop.flowershop.ui.SpringContext;
import org.springframework.data.domain.PageRequest;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
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
import java.util.function.Consumer;

/** Owner dashboard: KPI cards, alert cards, sales trend line chart and top selling bar chart. */
public class DashboardPanel extends JPanel {

    private static final Color BORDER = new Color(220, 224, 230);
    private static final Color TEXT = new Color(40, 40, 40);
    private static final Color GREEN = new Color(40, 167, 69);
    private static final Color BLUE = new Color(13, 110, 253);
    private static final Color RED = new Color(220, 53, 69);

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnsRepository returnsRepository;
    private final DisputeRepository disputeRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final ProcurementRepository procurementRepository;

    private final JLabel lblGrossTitle = new JLabel("Total Gross Sales (Daily)");
    private final JLabel lblGrossValue = new JLabel("₱0.00");
    private final JLabel lblGrossSub = new JLabel(" ");
    private final JLabel lblProfitTitle = new JLabel("Net Profit & Margin (Daily)");
    private final JLabel lblProfitValue = new JLabel("₱0.00");
    private final JLabel lblProfitSub = new JLabel(" ");
    private final JLabel lblItemsTitle = new JLabel("Total Items Sold (Daily)");
    private final JLabel lblItemsValue = new JLabel("0");
    private final JLabel lblItemsSub = new JLabel(" ");
    private final JLabel lblSpoilTitle = new JLabel("Spoilage Cost & Rate (Daily)");
    private final JLabel lblSpoilValue = new JLabel("₱0.00");
    private final JLabel lblSpoilSub = new JLabel(" ");
    private final JLabel lblChartTitle = new JLabel("Sales Trend (Line Chart - Daily View)");
    private final JLabel lblTopTitle = new JLabel("Top Selling Products (Bar Chart - Daily View)");
    private final JLabel lblLowStock = new JLabel("0 Items");
    private final JLabel lblSpoilage = new JLabel("0 Batches");
    private final JLabel lblDisputes = new JLabel("0 Cases");
    private final JLabel lblSupplierOrders = new JLabel("0 Inbound");

    private final SalesChart salesChart = new SalesChart();
    private final BarChart barChart = new BarChart();
    private String chartMode = "DAILY"; // DAILY, WEEKLY or MONTHLY (changes the four cards and both charts)
    private Consumer<String> onNavigate;

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
        this.procurementRepository = SpringContext.getBean(ProcurementRepository.class);

        initUI();
        refreshDashboardData();
    }

    public void setOnNavigate(Consumer<String> onNavigate) {
        this.onNavigate = onNavigate;
    }

    // ------------------------------------------------------------------ UI building

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel title = new JLabel("Executive Dashboard & Overview");
        title.setFont(font(Font.BOLD, 22));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(createFilters(), BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Three stacked rows: KPI cards, alert cards, line chart + bar chart
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
        center.add(createAlertsRow(), gbc);

        gbc.gridy = 2;
        gbc.weighty = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        center.add(createBottomRow(), gbc);

        add(center, BorderLayout.CENTER);
    }

    private JPanel createFilters() {
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filters.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (String[] f : new String[][]{{"Daily", "DAILY"}, {"Weekly", "WEEKLY"}, {"Monthly", "MONTHLY"}}) {
            JToggleButton button = createFilterButton(f[0], f[1]);
            group.add(button);
            filters.add(button);
        }
        return filters;
    }

    private JPanel createCardsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 15, 0));
        row.setOpaque(false);
        row.add(createKpiCard(new Color(13, 110, 253), "₱", lblGrossTitle, lblGrossValue, lblGrossSub));
        row.add(createKpiCard(GREEN, "↗", lblProfitTitle, lblProfitValue, lblProfitSub));
        row.add(createKpiCard(new Color(23, 162, 184), "#", lblItemsTitle, lblItemsValue, lblItemsSub));
        row.add(createKpiCard(new Color(111, 66, 193), "!", lblSpoilTitle, lblSpoilValue, lblSpoilSub));
        return row;
    }

    private JPanel createKpiCard(Color iconColor, String glyph, JLabel lblTitle, JLabel value, JLabel lblSub) {
        lblTitle.setFont(font(Font.PLAIN, 12));
        lblTitle.setForeground(Color.GRAY);

        value.setFont(font(Font.BOLD, 20));
        value.setForeground(TEXT);

        lblSub.setFont(font(Font.PLAIN, 12));
        lblSub.setForeground(Color.GRAY);

        JPanel text = new JPanel(new GridLayout(3, 1, 0, 2));
        text.setOpaque(false);
        text.add(lblTitle);
        text.add(value);
        text.add(lblSub);

        JPanel icon = new JPanel(new GridBagLayout());
        icon.setOpaque(false);
        icon.add(new IconCircle(iconColor, glyph));

        JPanel card = new JPanel(new BorderLayout(12, 0));
        card.setBackground(Color.WHITE);
        card.setBorder(border(null, 12, 12));
        card.add(icon, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel createAlertsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 15, 0));
        row.setOpaque(false);
        row.add(createAlertCard(lblLowStock, "Low Stock Alerts", "Below safety threshold",
                "View Inventory →", "INVENTORY", new Color(220, 53, 69)));
        row.add(createAlertCard(lblSpoilage, "Spoilage Risks / Review Batches", "Expired or nearing expiry",
                "Review Batches →", "INVENTORY", new Color(255, 193, 7)));
        row.add(createAlertCard(lblDisputes, "Active Customer Disputes", "Requires owner attention",
                "Resolve Disputes →", "DISPUTES", BLUE));
        row.add(createAlertCard(lblSupplierOrders, "Pending Supplier Orders", "Track shipments",
                "Check Status →", "SUPPLIERS", new Color(111, 66, 193)));
        return row;
    }

    private JPanel createAlertCard(JLabel lblCount, String title, String subtitle, String buttonText,
                                   String card, Color accent) {
        lblCount.setFont(font(Font.BOLD, 20));
        lblCount.setForeground(TEXT);

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(font(Font.BOLD, 14));
        lblTitle.setForeground(TEXT);

        JLabel lblSubtitle = new JLabel(subtitle);
        lblSubtitle.setFont(font(Font.PLAIN, 12));
        lblSubtitle.setForeground(Color.GRAY);

        JButton button = new JButton(buttonText);
        button.setFont(font(Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.addActionListener(e -> {
            if (onNavigate != null) {
                onNavigate.accept(card);
            }
        });

        JPanel text = new JPanel(new GridLayout(3, 1, 0, 2));
        text.setOpaque(false);
        text.add(lblCount);
        text.add(lblTitle);
        text.add(lblSubtitle);

        JPanel panel = new JPanel(new BorderLayout(5, 10));
        panel.setBackground(Color.WHITE);
        panel.setBorder(border(BorderFactory.createMatteBorder(4, 0, 0, 0, accent), 12, 12));
        panel.add(text, BorderLayout.NORTH);
        panel.add(button, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, 15, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(100, 250));
        row.add(createChartPanel(lblChartTitle, salesChart));
        row.add(createChartPanel(lblTopTitle, barChart));
        return row;
    }

    private JPanel createChartPanel(JLabel lblTitle, JPanel chart) {
        lblTitle.setFont(font(Font.BOLD, 16));
        lblTitle.setForeground(TEXT);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(Color.WHITE);
        panel.setBorder(border(null, 12, 15));
        panel.add(lblTitle, BorderLayout.NORTH);
        panel.add(chart, BorderLayout.CENTER);
        return panel;
    }

    /** Chart range button; it changes the cards and both charts. */
    private JToggleButton createFilterButton(String text, String mode) {
        JToggleButton button = new JToggleButton(text, mode.equals(chartMode));
        button.setFont(font(Font.PLAIN, 12));
        button.setFocusPainted(false);
        button.addActionListener(e -> {
            chartMode = mode;
            runSafely(this::loadChart);
            runSafely(this::loadTopSelling);
            runSafely(this::loadPeriodCards);
        });
        return button;
    }

    // ------------------------------------------------------------------ data loading

    /** Reloads every number and both charts. Call this after any sale/return/dispute. */
    public void refreshDashboardData() {
        runSafely(this::loadPeriodCards);
        runSafely(this::loadChart);
        runSafely(this::loadTopSelling);
        runSafely(this::loadAlertCards);
    }

    private void loadAlertCards() {
        long lowStock = componentInventoryRepository.findLowStock(PageRequest.of(0, 1)).getTotalElements();
        long spoilage = componentInventoryRepository.findSpoilage(PageRequest.of(0, 1)).getTotalElements();
        long disputes = disputeRepository.findByStatus("Under Review").size();
        long supplierOrders = procurementRepository.countByPaymentStatus("UNPAID");

        lblLowStock.setText(lowStock + (lowStock == 1 ? " Item" : " Items"));
        lblSpoilage.setText(spoilage + (spoilage == 1 ? " Batch" : " Batches"));
        lblDisputes.setText(disputes + (disputes == 1 ? " Case" : " Cases"));
        lblSupplierOrders.setText(supplierOrders + " Inbound");
    }

    private String periodName() {
        if ("WEEKLY".equals(chartMode)) return "Weekly";
        if ("MONTHLY".equals(chartMode)) return "Monthly";
        return "Daily";
    }

    /** First day of the selected period: today, this week (Monday start) or this month. */
    private LocalDate periodStart() {
        LocalDate today = LocalDate.now();
        if ("WEEKLY".equals(chartMode)) return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if ("MONTHLY".equals(chartMode)) return today.withDayOfMonth(1);
        return today;
    }

    /** The four top cards follow the selected filter and are compared with the period before it. */
    private void loadPeriodCards() {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDate start = periodStart();
        LocalDate prevStart = start.minusDays(1);
        String name = periodName();
        String prevName = "day";
        if ("WEEKLY".equals(chartMode)) {
            prevStart = start.minusWeeks(1);
            prevName = "week";
        } else if ("MONTHLY".equals(chartMode)) {
            prevStart = start.minusMonths(1);
            prevName = "month";
        }
        LocalDateTime from = start.atStartOfDay();
        LocalDateTime prevFrom = prevStart.atStartOfDay();
        LocalDateTime prevTo = from.minusSeconds(1);

        BigDecimal sales = nz(orderRepository.calculateRevenueBetween(from, now));
        BigDecimal prevSales = nz(orderRepository.calculateRevenueBetween(prevFrom, prevTo));
        Long sold = orderItemRepository.countItemsSoldBetween(from, now);
        Long prevSold = orderItemRepository.countItemsSoldBetween(prevFrom, prevTo);

        lblGrossTitle.setText("Total Gross Sales (" + name + ")");
        lblGrossValue.setText(money(sales));
        setTrend(lblGrossSub, sales.doubleValue(), prevSales.doubleValue(), prevName);

        lblItemsTitle.setText("Total Items Sold (" + name + ")");
        lblItemsValue.setText(String.valueOf(sold != null ? sold : 0L));
        setTrend(lblItemsSub, sold != null ? sold : 0L, prevSold != null ? prevSold : 0L, prevName);

        BigDecimal purchases = nz(procurementRepository.sumPurchasesBetween(start, today));
        BigDecimal refunds = nz(returnsRepository.sumRefundsBetween(from, today.plusDays(1).atStartOfDay()));
        BigDecimal net = sales.subtract(refunds).subtract(purchases);
        double margin = sales.signum() > 0 ? net.doubleValue() * 100 / sales.doubleValue() : 0;

        lblProfitTitle.setText("Net Profit & Margin (" + name + ")");
        lblProfitValue.setText((net.signum() < 0 ? "-" : "") + money(net.abs()));
        lblProfitValue.setForeground(net.signum() < 0 ? RED : TEXT);
        lblProfitSub.setText(String.format("%.1f%% Margin", margin));

        BigDecimal spoilCost = nz(componentInventoryRepository.sumSpoilageCostSince(start));
        double lossRate = sales.signum() > 0 ? spoilCost.doubleValue() * 100 / sales.doubleValue() : 0;

        lblSpoilTitle.setText("Spoilage Cost & Rate (" + name + ")");
        lblSpoilValue.setText(money(spoilCost));
        lblSpoilSub.setText(String.format("%.0f%% Loss Rate", lossRate));
    }

    private void setTrend(JLabel label, double current, double previous, String prevName) {
        if (previous <= 0) {
            label.setText("- vs. prev " + prevName);
            label.setForeground(Color.GRAY);
            return;
        }
        double change = (current - previous) * 100 / previous;
        label.setText((change >= 0 ? "▲ " : "▼ ") + String.format("%.1f%%", Math.abs(change)) + " vs. prev " + prevName);
        label.setForeground(change >= 0 ? GREEN : RED);
    }

    /** Best selling products of the selected period (completed orders only), highest first. */
    private void loadTopSelling() {
        lblTopTitle.setText("Top Selling Products (Bar Chart - " + periodName() + " View)");

        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        for (Object[] row : orderItemRepository.findTopSelling(periodStart().atStartOfDay(), LocalDateTime.now())) {
            names.add(String.valueOf(row[0]));
            counts.add(((Number) row[1]).intValue());
        }
        barChart.setData(names, counts);
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
        lblChartTitle.setText("Sales Trend (Line Chart - " + periodName() + " View)");
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

    /** One bad query shouldn't blank the whole dashboard, so each section loads in isolation. */
    private void runSafely(Runnable task) {
        try {
            task.run();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /** Rounds up to 1, 2, 4, 8 or 10 times a power of ten (e.g. 2,998 -> 4,000). */
    private static double niceCeil(double value) {
        double exp = Math.pow(10, Math.floor(Math.log10(value)));
        for (double step : new double[]{1, 2, 4, 8}) {
            if (value / exp <= step) return step * exp;
        }
        return 10 * exp;
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
    }

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
            g2.setFont(font(Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(glyph, (getWidth() - fm.stringWidth(glyph)) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    /** Hand-drawn bar chart: one blue bar per product with its count above and its name below. */
    private static class BarChart extends JPanel {
        private List<String> labels = new ArrayList<>();
        private List<Integer> values = new ArrayList<>();

        BarChart() {
            setBackground(Color.WHITE);
        }

        void setData(List<String> labels, List<Integer> values) {
            this.labels = labels;
            this.values = values;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int n = values.size();
            int left = 50, right = 20, top = 20, bottom = 30;
            int plotW = getWidth() - left - right;
            int plotH = getHeight() - top - bottom;
            if (plotW <= 0 || plotH <= 0) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(font(Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            if (n == 0) {
                String empty = "No sales in this period";
                g2.setColor(Color.GRAY);
                g2.drawString(empty, (getWidth() - fm.stringWidth(empty)) / 2, getHeight() / 2);
                g2.dispose();
                return;
            }

            int max = 0;
            for (int value : values) max = Math.max(max, value);
            int topValue = Math.max(4, (max + 3) / 4 * 4);

            int gridLines = 4;
            for (int i = 0; i <= gridLines; i++) {
                int y = top + plotH - (int) Math.round(plotH * i / (double) gridLines);
                g2.setColor(new Color(230, 233, 238));
                g2.drawLine(left, y, left + plotW, y);
                String text = String.valueOf(topValue * i / gridLines);
                g2.setColor(Color.GRAY);
                g2.drawString(text, left - 8 - fm.stringWidth(text), y + fm.getAscent() / 2 - 1);
            }

            int slot = plotW / n;
            int barW = Math.max(4, slot * 6 / 10);
            for (int i = 0; i < n; i++) {
                int x = left + i * slot + (slot - barW) / 2;
                int h = (int) Math.round(plotH * values.get(i) / (double) topValue);

                g2.setColor(BLUE);
                g2.fillRect(x, top + plotH - h, barW, h);

                String valueText = String.valueOf(values.get(i));
                g2.setColor(TEXT);
                g2.drawString(valueText, x + (barW - fm.stringWidth(valueText)) / 2, top + plotH - h - 5);

                String name = labels.get(i);
                if (fm.stringWidth(name) > slot - 4) {
                    while (name.length() > 1 && fm.stringWidth(name + "..") > slot - 4) {
                        name = name.substring(0, name.length() - 1);
                    }
                    name = name + "..";
                }
                g2.setColor(Color.GRAY);
                g2.drawString(name, left + i * slot + (slot - fm.stringWidth(name)) / 2, top + plotH + 18);
            }
            g2.dispose();
        }
    }
}