package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Category;
import com.flowershop.flowershop.model.Component;
import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.repository.CategoryRepository;
import com.flowershop.flowershop.repository.ComponentRepository;
import com.flowershop.flowershop.repository.ProductRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class CategoryPanel extends JPanel {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ComponentRepository componentRepository;

    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    public CategoryPanel(
            CategoryRepository categoryRepository,
            ProductRepository productRepository,
            ComponentRepository componentRepository
    ) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.componentRepository = componentRepository;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        // Top bar
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("Categories of Products and Components");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> loadData());
        topPanel.add(lblTitle, BorderLayout.WEST);
        topPanel.add(btnRefresh, BorderLayout.EAST);

        // Table
        String[] columns = {"ID", "Category", "Type", "Products", "Components"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(lblSummary, BorderLayout.SOUTH);
    }

    public void loadData() {
        // 1. Count how many products and components each category has
        Map<Long, Integer> productCount = new HashMap<>();
        for (Product p : productRepository.findAll()) {
            Long id = p.getCategory().getCategoryId();
            int count = productCount.containsKey(id) ? productCount.get(id) : 0;
            productCount.put(id, count + 1);
        }

        Map<Long, Integer> componentCount = new HashMap<>();
        for (Component c : componentRepository.findAll()) {
            Long id = c.getCategory().getCategoryId();
            int count = componentCount.containsKey(id) ? componentCount.get(id) : 0;
            componentCount.put(id, count + 1);
        }

        // 2. Fill the table
        tableModel.setRowCount(0);
        int total = 0;
        for (Category category : categoryRepository.findAll()) {
            Long id = category.getCategoryId();
            tableModel.addRow(new Object[]{
                    id,
                    category.getName(),
                    category.getType(),
                    productCount.containsKey(id) ? productCount.get(id) : 0,
                    componentCount.containsKey(id) ? componentCount.get(id) : 0
            });
            total++;
        }

        lblSummary.setText(total + " categories");
    }
}