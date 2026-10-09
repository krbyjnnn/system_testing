package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DashboardPanel extends JPanel {

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final OrderRepository orderRepository;

    private JLabel lblDailySales;
    private JLabel lblWeeklySales;
    private JLabel lblMonthlySales;
    private JLabel lblTotalProducts;

    private JTable transactionTable;
    private DefaultTableModel tableModel;
    private int currentPage = 0;

    public DashboardPanel(
            ProductRepository productRepository,
            ProductInventoryRepository productInventoryRepository,
            OrderRepository orderRepository
    ) {
        this.productRepository = productRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.orderRepository = orderRepository;

        initUI();
        refreshDashboardData();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("Executive Dashboard & Overview");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));

        headerPanel.add(lblTitle, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Center Area
        JPanel centerPanel = new JPanel(new BorderLayout(0, 20));
        centerPanel.setOpaque(false);

        // 1. KPI Cards Row
        JPanel cardsGrid = new JPanel(new GridLayout(1, 4, 15, 0));
        cardsGrid.setOpaque(false);

        lblDailySales = new JLabel("₱0.00", SwingConstants.CENTER);
        lblWeeklySales = new JLabel("₱0.00", SwingConstants.CENTER);
        lblMonthlySales = new JLabel("₱0.00", SwingConstants.CENTER);
        lblTotalProducts = new JLabel("0", SwingConstants.CENTER);

        cardsGrid.add(createKpiCard("Daily Sales (Money In)", lblDailySales, new Color(40, 167, 69)));
        cardsGrid.add(createKpiCard("Weekly Sales", lblWeeklySales, new Color(23, 162, 184)));
        cardsGrid.add(createKpiCard("Monthly Sales", lblMonthlySales, new Color(111, 66, 193)));
        cardsGrid.add(createKpiCard("Active Products", lblTotalProducts, new Color(255, 193, 7)));

        centerPanel.add(cardsGrid, BorderLayout.NORTH);

        // 2. Low Stock Table Section
        JPanel transactionPanel = new JPanel(new BorderLayout(0, 10));
        transactionPanel.setBackground(Color.WHITE);
        transactionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel lblTransactionTitle = new JLabel("Transaction History");
        lblTransactionTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTransactionTitle.setForeground(new Color(40, 40, 40));

        String[] columns = {"Transaction No.", "Date & Time", "Type", "Cashier", "Total", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        transactionTable = new JTable(tableModel);
        transactionTable.setRowHeight(28);
        transactionTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        transactionTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JScrollPane scrollPane = new JScrollPane(transactionTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        transactionPanel.add(lblTransactionTitle, BorderLayout.NORTH);
        transactionPanel.add(scrollPane, BorderLayout.CENTER);

        centerPanel.add(transactionPanel, BorderLayout.CENTER);
        add(centerPanel, BorderLayout.CENTER);
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

        // 1. Daily Sales
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        BigDecimal daily = orderRepository != null ? orderRepository.calculateRevenueBetween(startOfDay, now) : BigDecimal.ZERO;
        lblDailySales.setText("₱" + String.format("%.2f", daily != null ? daily : BigDecimal.ZERO));

        // 2. Weekly Sales
        LocalDateTime startOfWeek = LocalDate.now().minusDays(7).atStartOfDay();
        BigDecimal weekly = orderRepository != null ? orderRepository.calculateRevenueBetween(startOfWeek, now) : BigDecimal.ZERO;
        lblWeeklySales.setText("₱" + String.format("%.2f", weekly != null ? weekly : BigDecimal.ZERO));

        // 3. Monthly Sales
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        BigDecimal monthly = orderRepository != null ? orderRepository.calculateRevenueBetween(startOfMonth, now) : BigDecimal.ZERO;
        lblMonthlySales.setText("₱" + String.format("%.2f", monthly != null ? monthly : BigDecimal.ZERO));

        // 4. Product Count
        long count = productRepository != null ? productRepository.countActiveProducts() : 0;
        lblTotalProducts.setText(String.valueOf(count));

        currentPage = 0;
        loadTransaction();
        }

        private void loadTransaction() {
            // Populate the transaction table with the latest 15 orders
            tableModel.setRowCount(0);
            if (orderRepository != null) {
            Page<Order> ordersPage = orderRepository.findAllByOrderByOrderDateDesc(PageRequest.of(currentPage, 15));
            DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            for (Order order : ordersPage.getContent()) {
                tableModel.addRow(new Object[]{
                    order.getTransactionNumber(),
                    order.getOrderDate().format(dateFormat),
                    order.getOrderType(),
                    order.getUser() != null ? order.getUser().getFullName() : "N/A",
                    "₱" + String.format("%.2f", order.getTotalAmount()),
                    order.getOrderStatus()
                });
            }
        }
    }
}