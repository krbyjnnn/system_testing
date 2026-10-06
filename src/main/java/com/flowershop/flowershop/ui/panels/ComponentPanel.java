package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Category;
import com.flowershop.flowershop.model.Component;
import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.Supplier;
import com.flowershop.flowershop.repository.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ComponentPanel extends JPanel {

    private final ComponentRepository componentRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;

    // Table
    private JTextField txtSearch;
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
    private List<Component> shownComponents = new ArrayList<>();

    // Component form (top)
    private JLabel lblFormTitle;
    private JTextField txtName;
    private JComboBox<String> cmbCategory;
    private JTextField txtUnit;
    private JTextField txtCost;
    private JSpinner spnComponentReorder;
    private JButton btnSave;
    private JLabel lblMessage;
    private List<Category> categories = new ArrayList<>();
    private Component editing = null; // null = adding a new component

    // Stock form (top)
    private JLabel lblStockTitle;
    private JComboBox<String> cmbSupplier;
    private JSpinner spnQty;
    private JSpinner spnBatchReorder;
    private JTextField txtBatchCost;
    private JTextField txtReceived;
    private JTextField txtExpiry;
    private JLabel lblStockMessage;
    private List<Supplier> suppliers = new ArrayList<>();

    public ComponentPanel(
            ComponentRepository componentRepository,
            ComponentInventoryRepository componentInventoryRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository
    ) {
        this.componentRepository = componentRepository;
        this.componentInventoryRepository = componentInventoryRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        // ---------------- Component form ----------------
        lblFormTitle = new JLabel("Add Component");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        txtName = new JTextField();
        cmbCategory = new JComboBox<>();
        txtUnit = new JTextField("pcs");
        txtCost = new JTextField();
        spnComponentReorder = new JSpinner(new SpinnerNumberModel(10, 0, 99999, 1));

        JPanel fields = new JPanel(new GridLayout(2, 5, 8, 2));
        fields.setOpaque(false);
        fields.add(new JLabel("Name"));
        fields.add(new JLabel("Category"));
        fields.add(new JLabel("Unit (pcs, stem, ...)"));
        fields.add(new JLabel("Cost"));
        fields.add(new JLabel("Reorder Level"));
        fields.add(txtName);
        fields.add(cmbCategory);
        fields.add(txtUnit);
        fields.add(txtCost);
        fields.add(spnComponentReorder);

        btnSave = new JButton("Add Component");
        btnSave.addActionListener(e -> saveComponent());
        JButton btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearForm());
        lblMessage = new JLabel(" ");
        lblMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(btnSave);
        buttonRow.add(btnClear);
        buttonRow.add(lblMessage);

        JPanel formCard = createCard();
        formCard.add(lblFormTitle, BorderLayout.NORTH);
        formCard.add(fields, BorderLayout.CENTER);
        formCard.add(buttonRow, BorderLayout.SOUTH);

        // ---------------- Stock form ----------------
        lblStockTitle = new JLabel("Add Stock (select a component in the table first)");
        lblStockTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        cmbSupplier = new JComboBox<>();
        spnQty = new JSpinner(new SpinnerNumberModel(1, 1, 99999, 1));
        spnBatchReorder = new JSpinner(new SpinnerNumberModel(10, 0, 99999, 1));
        txtBatchCost = new JTextField();
        txtReceived = new JTextField(LocalDate.now().toString());
        txtExpiry = new JTextField();

        JPanel stockFields = new JPanel(new GridLayout(2, 6, 8, 2));
        stockFields.setOpaque(false);
        stockFields.add(new JLabel("Supplier (perishables only)"));
        stockFields.add(new JLabel("Quantity"));
        stockFields.add(new JLabel("Reorder Level"));
        stockFields.add(new JLabel("Unit Cost"));
        stockFields.add(new JLabel("Date Received (yyyy-MM-dd)"));
        stockFields.add(new JLabel("Expiry (perishables only)"));
        stockFields.add(cmbSupplier);
        stockFields.add(spnQty);
        stockFields.add(spnBatchReorder);
        stockFields.add(txtBatchCost);
        stockFields.add(txtReceived);
        stockFields.add(txtExpiry);

        JButton btnAddStock = new JButton("Add Stock");
        btnAddStock.addActionListener(e -> addStock());
        lblStockMessage = new JLabel(" ");
        lblStockMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel stockButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        stockButtonRow.setOpaque(false);
        stockButtonRow.add(btnAddStock);
        stockButtonRow.add(lblStockMessage);

        JPanel stockCard = createCard();
        stockCard.add(lblStockTitle, BorderLayout.NORTH);
        stockCard.add(stockFields, BorderLayout.CENTER);
        stockCard.add(stockButtonRow, BorderLayout.SOUTH);

        JPanel top = new JPanel(new GridLayout(0, 1, 0, 8));
        top.setOpaque(false);
        top.add(formCard);
        top.add(stockCard);

        // ---------------- Search + table ----------------
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        txtSearch = new JTextField();
        txtSearch.addActionListener(e -> loadData());
        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> loadData());
        JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchButtons.setOpaque(false);
        searchButtons.add(btnSearch);
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        searchPanel.add(searchButtons, BorderLayout.EAST);

        String[] columns = {"Component", "Category", "Unit", "Qty On Hand", "Reorder Level", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Clicking a row loads it into the form above so it can be edited
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                fillForm();
            }
        });

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(searchPanel, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(lblSummary, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);

        updateStockFields();
    }

    private JPanel createCard() {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(new Color(248, 249, 251));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        return card;
    }

    // ---------------------------------------------------------------- Data

    public void loadData() {
        loadDropdowns();

        // 1. Add up every batch per component
        Map<Long, Integer> quantity = new HashMap<>();
        for (ComponentInventory batch : componentInventoryRepository.findAll()) {
            Long id = batch.getComponent().getComponentId();
            int qty = quantity.containsKey(id) ? quantity.get(id) : 0;
            quantity.put(id, qty + batch.getQuantityOnHand());
        }

        // 2. Fill the table
        String keyword = txtSearch.getText().trim().toLowerCase();
        shownComponents.clear();
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

            shownComponents.add(c);
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

    // Refreshes the category and supplier dropdowns (keeps what was selected)
    private void loadDropdowns() {
        // Product categories (type "Product") are not used for components, so they are left out
        int categoryIndex = cmbCategory.getSelectedIndex();
        categories = new ArrayList<>();
        for (Category c : categoryRepository.findAll()) {
            if (!"Product".equals(c.getType())) {
                categories.add(c);
            }
        }
        cmbCategory.removeAllItems();
        for (Category c : categories) {
            cmbCategory.addItem(c.getName());
        }
        if (categoryIndex >= 0 && categoryIndex < categories.size()) {
            cmbCategory.setSelectedIndex(categoryIndex);
        }

        int supplierIndex = cmbSupplier.getSelectedIndex();
        suppliers = supplierRepository.findAll();
        cmbSupplier.removeAllItems();
        for (Supplier s : suppliers) {
            cmbSupplier.addItem(s.getSupplierName());
        }
        if (supplierIndex >= 0 && supplierIndex < suppliers.size()) {
            cmbSupplier.setSelectedIndex(supplierIndex);
        }
    }

    // ---------------------------------------------------------------- Component form

    private void fillForm() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }

        editing = shownComponents.get(row);
        lblFormTitle.setText("Edit Component: " + editing.getName());
        btnSave.setText("Update Component");
        lblMessage.setText(" ");

        txtName.setText(editing.getName());
        txtUnit.setText(editing.getUnitOfMeasure());
        txtCost.setText(editing.getCost().toString());
        spnComponentReorder.setValue(editing.getReorderLevel());
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getCategoryId().equals(editing.getCategory().getCategoryId())) {
                cmbCategory.setSelectedIndex(i);
            }
        }

        lblStockTitle.setText("Add Stock to: " + editing.getName());
        lblStockMessage.setText(" ");
        updateStockFields();
    }

    private void clearForm() {
        editing = null;
        table.clearSelection();
        lblFormTitle.setText("Add Component");
        btnSave.setText("Add Component");
        txtName.setText("");
        txtUnit.setText("pcs");
        txtCost.setText("");
        spnComponentReorder.setValue(10);
        lblStockTitle.setText("Add Stock (select a component in the table first)");
        updateStockFields();
    }

    private void saveComponent() {
        String name = txtName.getText().trim();
        String unit = txtUnit.getText().trim();
        BigDecimal cost = parseMoney(txtCost.getText());

        if (categories.isEmpty()) {
            showMessage(lblMessage, "Add a component category first (Category tab).", true);
            return;
        }
        if (name.isEmpty() || unit.isEmpty()) {
            showMessage(lblMessage, "Component name and unit are required.", true);
            return;
        }
        if (name.length() > 255 || unit.length() > 50) {
            showMessage(lblMessage, "Name must be 255 characters or less, and the unit 50 or less.", true);
            return;
        }
        if (cost == null) {
            showMessage(lblMessage, "Enter a valid cost.", true);
            return;
        }

        Category category = categories.get(cmbCategory.getSelectedIndex());
        int reorder = (Integer) spnComponentReorder.getValue();
        boolean isNew = editing == null;

        try {
            Component component;
            if (isNew) {
                component = new Component(category, name, unit, cost, reorder);
            } else {
                component = editing;
                component.setCategory(category);
                component.setName(name);
                component.setUnitOfMeasure(unit);
                component.setCost(cost);
                component.setReorderLevel(reorder);
            }
            componentRepository.save(component);

            clearForm();
            loadData();
            showMessage(lblMessage, isNew ? "Component added." : "Component updated.", false);
        } catch (Exception ex) {
            showMessage(lblMessage, "Not saved: " + ex.getMessage(), true);
        }
    }

    // ---------------------------------------------------------------- Stock form

    // Supplier and Expiry are only used for perishable components.
    // Greyed out when nothing is selected or the component is non-perishable.
    private void updateStockFields() {
        boolean perishable = editing != null
                && editing.getCategory() != null
                && editing.getCategory().isPerishable();

        cmbSupplier.setEnabled(perishable);
        txtExpiry.setEnabled(perishable);
        if (!perishable) {
            txtExpiry.setText("");
        }
    }

    private void addStock() {
        if (editing == null) {
            showMessage(lblStockMessage, "Select a component in the table first.", true);
            return;
        }

        boolean perishable = editing.getCategory() != null && editing.getCategory().isPerishable();

        // Only perishable items have a supplier
        Supplier supplier = null;
        if (perishable) {
            if (suppliers.isEmpty()) {
                showMessage(lblStockMessage, "Add a supplier first (Suppliers screen).", true);
                return;
            }
            supplier = suppliers.get(cmbSupplier.getSelectedIndex());
        }

        BigDecimal cost = parseMoney(txtBatchCost.getText());
        if (cost == null) {
            showMessage(lblStockMessage, "Enter a valid unit cost.", true);
            return;
        }

        LocalDate received;
        LocalDate expiry = null;
        try {
            received = LocalDate.parse(txtReceived.getText().trim());
            if (perishable) {
                if (txtExpiry.getText().trim().isEmpty()) {
                    showMessage(lblStockMessage, "Expiry date is required for perishable items.", true);
                    return;
                }
                expiry = LocalDate.parse(txtExpiry.getText().trim());
            }
        } catch (DateTimeParseException ex) {
            showMessage(lblStockMessage, "Dates must look like 2026-10-31.", true);
            return;
        }
        if (expiry != null && expiry.isBefore(received)) {
            showMessage(lblStockMessage, "Expiry date cannot be before the date received.", true);
            return;
        }

        int qty = (Integer) spnQty.getValue();
        int reorder = (Integer) spnBatchReorder.getValue();

        try {
            componentInventoryRepository.save(new ComponentInventory(
                    supplier, editing, qty, reorder, cost, received, expiry));

            spnQty.setValue(1);
            txtBatchCost.setText("");
            txtReceived.setText(LocalDate.now().toString());
            txtExpiry.setText("");
            loadData();
            showMessage(lblStockMessage, "Stock added.", false);
        } catch (Exception ex) {
            showMessage(lblStockMessage, "Not saved: " + ex.getMessage(), true);
        }
    }

    // ---------------------------------------------------------------- Helpers

    // Shows a message inside the panel (red = problem, green = success)
    private void showMessage(JLabel label, String text, boolean isError) {
        label.setForeground(isError ? new Color(220, 53, 69) : new Color(40, 167, 69));
        label.setText(text);
    }

    // Returns the amount, or null if it is not a valid number (or is negative)
    private BigDecimal parseMoney(String text) {
        try {
            BigDecimal value = new BigDecimal(text.replace(",", "").trim());
            return value.compareTo(BigDecimal.ZERO) < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}