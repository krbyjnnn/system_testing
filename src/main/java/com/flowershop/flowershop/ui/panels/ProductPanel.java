package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.model.ProductInventory;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ProductPanel extends JPanel {

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;

    private JTextField txtSearch;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    public ProductPanel(ProductRepository productRepository, ProductInventoryRepository productInventoryRepository) {
        this.productRepository = productRepository;
        this.productInventoryRepository = productInventoryRepository;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        // Search bar
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        txtSearch = new JTextField();
        txtSearch.addActionListener(e -> loadData());
        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> loadData());
        JButton btnRefresh = new JButton("Refresh");
        btnRefresh.addActionListener(e -> {
            txtSearch.setText("");
            loadData();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnSearch);
        buttons.add(btnRefresh);
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        searchPanel.add(buttons, BorderLayout.EAST);

        // Table
        String[] columns = {"Code", "Product", "Category", "Qty On Hand", "Reorder Level", "Status"};
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

        add(searchPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(lblSummary, BorderLayout.SOUTH);
    }

    public void loadData() {
        // 1. Add up every batch per product
        Map<Long, Integer> quantity = new HashMap<>();
        Map<Long, Integer> reorder = new HashMap<>();
        for (ProductInventory batch : productInventoryRepository.findAll()) {
            Long id = batch.getProduct().getProductId();
            int qty = quantity.containsKey(id) ? quantity.get(id) : 0;
            quantity.put(id, qty + batch.getQuantityOnHand());

            int level = reorder.containsKey(id) ? reorder.get(id) : 0;
            reorder.put(id, Math.max(level, batch.getReorderLevel()));
        }

        // 2. Fill the table
        String keyword = txtSearch.getText().trim().toLowerCase();
        tableModel.setRowCount(0);
        int total = 0;
        int low = 0;
        int out = 0;

        for (Product p : productRepository.findAll()) {
            boolean matches = p.getProductName().toLowerCase().contains(keyword)
                    || p.getProductCode().toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            int qty = quantity.containsKey(p.getProductId()) ? quantity.get(p.getProductId()) : 0;
            int level = reorder.containsKey(p.getProductId()) ? reorder.get(p.getProductId()) : 0;

            String status;
            if (qty == 0) {
                status = "Out of Stock";
                out++;
            } else if (qty <= level) {
                status = "Low Stock";
                low++;
            } else {
                status = "In Stock";
            }
            total++;

            tableModel.addRow(new Object[]{
                    p.getProductCode(),
                    p.getProductName(),
                    p.getCategory() != null ? p.getCategory().getName() : "N/A",
                    qty,
                    level,
                    status
            });
        }

        lblSummary.setText(total + " products  |  " + low + " low stock  |  " + out + " out of stock");
    }
}