package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.repository.*;

import javax.swing.*;
import java.awt.*;

public class InventoryPanel extends JPanel {

    private final ProductPanel productPanel;
    private final ComponentPanel componentPanel;
    private final CategoryPanel categoryPanel;

    public InventoryPanel(
            ProductRepository productRepository,
            ProductInventoryRepository productInventoryRepository,
            ComponentRepository componentRepository,
            ComponentInventoryRepository componentInventoryRepository,
            CategoryRepository categoryRepository
    ) {
        productPanel = new ProductPanel(productRepository, productInventoryRepository);
        componentPanel = new ComponentPanel(componentRepository, componentInventoryRepository);
        categoryPanel = new CategoryPanel(categoryRepository, productRepository, componentRepository);

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        // Top Header
        JLabel lblTitle = new JLabel("Inventory");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        // Tabs: Products, Components, Category (submodule)
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("Products", productPanel);
        tabs.addTab("Components", componentPanel);
        tabs.addTab("Category", categoryPanel);

        // Reload the data of the tab that was just opened
        tabs.addChangeListener(e -> {
            int selected = tabs.getSelectedIndex();
            if (selected == 0) {
                productPanel.loadData();
            } else if (selected == 1) {
                componentPanel.loadData();
            } else {
                categoryPanel.loadData();
            }
        });

        add(tabs, BorderLayout.CENTER);
    }
}