package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.*;
import com.flowershop.flowershop.service.StockReceivingService;
import com.flowershop.flowershop.service.ProductionService;

import javax.swing.*;
import java.awt.*;

public class InventoryPanel extends JPanel {

    private final ProductPanel productPanel;
    private final ComponentPanel componentPanel;
    private final CategoryPanel categoryPanel;
    private final boolean readOnly; // true for Staff: they can look but not change anything

    public InventoryPanel(
            ProductRepository productRepository,
            ProductInventoryRepository productInventoryRepository,
            ComponentRepository componentRepository,
            ComponentInventoryRepository componentInventoryRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            RecipeRepository recipeRepository,
            ProductionService productionService,
            StockReceivingService stockReceivingService,
            User currentUser,
            boolean readOnly
    ) {
        this.readOnly = readOnly;
        productPanel = new ProductPanel(productRepository, productInventoryRepository, categoryRepository,
                supplierRepository, componentRepository, componentInventoryRepository, recipeRepository,
                productionService, currentUser, readOnly);
        componentPanel = new ComponentPanel(componentRepository, componentInventoryRepository, categoryRepository,
                supplierRepository, stockReceivingService, currentUser, readOnly);
        categoryPanel = new CategoryPanel(categoryRepository, currentUser, readOnly);

        initUI();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        // Top Header
        JLabel lblTitle = new JLabel(readOnly ? "Inventory" : "Inventory");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        // Tabs: Products, Components, Category (submodule)
        // Each tab has its own add/edit form at the top, so there are no pop-up windows.
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

    // Reloads every tab (called automatically when this screen is opened)
    public void refresh() {
        productPanel.loadData();
        componentPanel.loadData();
        categoryPanel.loadData();
    }
}