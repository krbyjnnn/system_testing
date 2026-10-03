package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Component;
import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.ComponentRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class ComponentPanel extends JPanel {

    private final ComponentRepository componentRepository;
    private final ComponentInventoryRepository componentInventoryRepository;

    private JTextField txtSearch;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    public ComponentPanel(ComponentRepository componentRepository, ComponentInventoryRepository componentInventoryRepository) {
        this.componentRepository = componentRepository;
        this.componentInventoryRepository = componentInventoryRepository;

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
        String[] columns = {"Component", "Category", "Unit", "Qty On Hand", "Reorder Level", "Status"};
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
        // 1. Add up every batch per component
        Map<Long, Integer> quantity = new HashMap<>();
        for (ComponentInventory batch : componentInventoryRepository.findAll()) {
            Long id = batch.getComponent().getComponentId();
            int qty = quantity.containsKey(id) ? quantity.get(id) : 0;
            quantity.put(id, qty + batch.getQuantityOnHand());
        }

        // 2. Fill the table
        String keyword = txtSearch.getText().trim().toLowerCase();
        tableModel.setRowCount(0);
        int total = 0;
        int low = 0;
        int out = 0;

        for (Component c : componentRepository.findAll()) {
            if (!c.getName().toLowerCase().contains(keyword)) {
                continue;
            }

            int qty = quantity.containsKey(c.getComponentId()) ? quantity.get(c.getComponentId()) : 0;
            int level = c.getReorderLevel();

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
                    c.getName(),
                    c.getCategory() != null ? c.getCategory().getName() : "N/A",
                    c.getUnitOfMeasure(),
                    qty,
                    level,
                    status
            });
        }

        lblSummary.setText(total + " components  |  " + low + " low stock  |  " + out + " out of stock");
    }
}