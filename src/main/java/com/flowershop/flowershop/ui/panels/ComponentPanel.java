package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Category;
import com.flowershop.flowershop.model.Component;
import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.*;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.StockReceivingService;
import com.flowershop.flowershop.ui.SpringContext;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Components tab: add components and see what is in stock.
// Stock comes in through Procurement Details in the Suppliers screen.
public class ComponentPanel extends JPanel {

    private final ComponentRepository componentRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockReceivingService stockReceivingService;
    private final User currentUser;
    private final boolean readOnly; // view only: the add/edit form is not shown
    private final AuditService auditService = SpringContext.getBean(AuditService.class);

    private JTextField txtSearch;
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
    private List<Component> shownComponents = new ArrayList<>();

    private DefaultTableModel batchModel;
    private JLabel lblBatchTitle;
    // Every batch by component id, already in the order production uses them
    private Map<Long, List<ComponentInventory>> batchesByComponent = new HashMap<>();

    private JLabel lblFormTitle;
    private JTextField txtName;
    private JComboBox<String> cmbCategory;
    private JTextField txtUnit;
    private JSpinner spnComponentReorder;
    private JButton btnSave;
    private JLabel lblMessage;
    private List<Category> categories = new ArrayList<>();
    private Component editing = null; // null = adding a new component

    public ComponentPanel(
            ComponentRepository componentRepository,
            ComponentInventoryRepository componentInventoryRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            StockReceivingService stockReceivingService,
            User currentUser,
            boolean readOnly
    ) {
        this.readOnly = readOnly;
        this.componentRepository = componentRepository;
        this.componentInventoryRepository = componentInventoryRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.stockReceivingService = stockReceivingService;
        this.currentUser = currentUser;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        lblFormTitle = new JLabel("Add Component");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        txtName = new JTextField();
        cmbCategory = new JComboBox<>();
        txtUnit = new JTextField("pcs");
        spnComponentReorder = new JSpinner(new SpinnerNumberModel(10, 0, 99999, 1));

        JPanel fields = new JPanel(new GridLayout(2, 4, 8, 2));
        fields.setOpaque(false);
        fields.add(new JLabel("Name"));
        fields.add(new JLabel("Category"));
        fields.add(new JLabel("Unit (pcs, stem, ...)"));
        fields.add(new JLabel("Reorder Level"));
        fields.add(txtName);
        fields.add(cmbCategory);
        fields.add(txtUnit);
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

        JPanel top = new JPanel(new GridLayout(0, 1, 0, 8));
        top.setOpaque(false);
        top.add(formCard);

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
                showBatches();
            }
        });

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(searchPanel, BorderLayout.NORTH);
        center.add(buildSplit(), BorderLayout.CENTER);
        center.add(lblSummary, BorderLayout.SOUTH);

        if (!readOnly) {
            add(top, BorderLayout.NORTH);
        }
        add(center, BorderLayout.CENTER);
    }

    // Components on top, and below them the batches of the selected component
    private JSplitPane buildSplit() {
        String[] batchColumns = {"Use Order", "Date Received", "Expiry", "Qty On Hand", "Unit Cost", "Supplier"};
        batchModel = new DefaultTableModel(batchColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable batchTable = new JTable(batchModel);
        batchTable.setRowHeight(28);
        batchTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        batchTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        lblBatchTitle = new JLabel(" ");
        lblBatchTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel batchPanel = new JPanel(new BorderLayout(0, 6));
        batchPanel.setOpaque(false);
        batchPanel.add(lblBatchTitle, BorderLayout.NORTH);
        batchPanel.add(new JScrollPane(batchTable), BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), batchPanel);
        split.setResizeWeight(0.6);
        split.setBorder(null);
        return split;
    }

    // Lists the batches of the selected component in the order production uses them:
    // earliest expiry first. Expired batches are never used, so they get no use order.
    private void showBatches() {
        batchModel.setRowCount(0);
        int row = table.getSelectedRow();
        if (row < 0 || row >= shownComponents.size()) {
            lblBatchTitle.setText("Stock Batches: select a component above");
            return;
        }

        Component component = shownComponents.get(row);
        lblBatchTitle.setText("Stock Batches: " + component.getName());

        List<ComponentInventory> batches = batchesByComponent.get(component.getComponentId());
        if (batches == null) {
            return;
        }

        LocalDate today = LocalDate.now();
        int useOrder = 0;
        for (ComponentInventory batch : batches) {
            if (batch.getQuantityOnHand() <= 0) {
                continue;
            }
            boolean expired = batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today);
            String expiry = batch.getExpiryDate() == null ? "-"
                    : batch.getExpiryDate() + (expired ? " (expired)" : "");
            batchModel.addRow(new Object[]{
                    expired ? "-" : String.valueOf(++useOrder),
                    batch.getDateReceived().toString(),
                    expiry,
                    batch.getQuantityOnHand(),
                    "₱" + String.format("%.2f", batch.getUnitCost()),
                    batch.getSupplier() != null ? batch.getSupplier().getSupplierName() : "-"
            });
        }
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

    // One row per stock batch, so batches with different prices stay separate.
    // Status (Low Stock / Out of Stock) uses the total of all batches of that component.
    public void loadData() {
        loadDropdowns();

        batchesByComponent = new HashMap<>();
        for (ComponentInventory batch : componentInventoryRepository.findAll()) {
            Long id = batch.getComponent().getComponentId();
            if (!batchesByComponent.containsKey(id)) {
                batchesByComponent.put(id, new ArrayList<>());
            }
            batchesByComponent.get(id).add(batch);
        }

        Comparator<ComponentInventory> usageOrder = Comparator
                .comparing(ComponentInventory::getExpiryDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(ComponentInventory::getDateReceived)
                .thenComparing(ComponentInventory::getCbatchId);

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

            List<ComponentInventory> batches = batchesByComponent.containsKey(c.getComponentId())
                    ? batchesByComponent.get(c.getComponentId())
                    : new ArrayList<>();
            batches.sort(usageOrder);

            int qty = 0;
            for (ComponentInventory batch : batches) {
                qty += batch.getQuantityOnHand();
            }
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

            String categoryName = c.getCategory() != null ? c.getCategory().getName() : "N/A";
            shownComponents.add(c);
            tableModel.addRow(new Object[]{
                    c.getName(),
                    categoryName,
                    c.getUnitOfMeasure(),
                    qty,
                    level,
                    status
            });
        }

        lblSummary.setText(total + " components  |  " + low + " low stock  |  " + out + " out of stock");
        showBatches();
    }

    // Refreshes the category dropdown (keeps what was selected)
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
    }

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
        spnComponentReorder.setValue(editing.getReorderLevel());
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).getCategoryId().equals(editing.getCategory().getCategoryId())) {
                cmbCategory.setSelectedIndex(i);
            }
        }
    }

    private void clearForm() {
        editing = null;
        table.clearSelection();
        lblFormTitle.setText("Add Component");
        btnSave.setText("Add Component");
        txtName.setText("");
        txtUnit.setText("pcs");
        spnComponentReorder.setValue(10);
    }

    private void saveComponent() {
        String name = txtName.getText().trim();
        String unit = txtUnit.getText().trim();

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

        Category category = categories.get(cmbCategory.getSelectedIndex());
        int reorder = (Integer) spnComponentReorder.getValue();
        String oldName = editing == null ? null : editing.getName();
        int oldReorder = editing == null ? reorder : editing.getReorderLevel();
        boolean isNew = editing == null;

        try {
            Component component;
            if (isNew) {
                // component.cost is NOT NULL; the real cost lives on each stock batch (unit_cost)
                component = new Component(category, name, unit, BigDecimal.ZERO, reorder);
            } else {
                component = editing;
                component.setCategory(category);
                component.setName(name);
                component.setUnitOfMeasure(unit);
                component.setReorderLevel(reorder);
            }
            componentRepository.save(component);
            if (isNew) {
                auditService.log(currentUser, "Component created", "PRODUCTS",
                        name + ", unit " + unit + ", reorder level " + reorder);
            } else {
                auditService.log(currentUser, "Component edited", "PRODUCTS",
                        oldName.equals(name) ? name : oldName + " renamed to " + name);
                if (oldReorder != reorder) {
                    auditService.log(currentUser, "Reorder level changed", "INVENTORY",
                            name + ": " + oldReorder + " to " + reorder);
                }
            }

            clearForm();
            loadData();
            showMessage(lblMessage, isNew ? "Component added." : "Component updated.", false);
        } catch (Exception ex) {
            showMessage(lblMessage, "Not saved: " + ex.getMessage(), true);
        }
    }

    // Shows a message inside the panel (red = problem, green = success)
    private void showMessage(JLabel label, String text, boolean isError) {
        label.setForeground(isError ? new Color(220, 53, 69) : new Color(40, 167, 69));
        label.setText(text);
    }
}