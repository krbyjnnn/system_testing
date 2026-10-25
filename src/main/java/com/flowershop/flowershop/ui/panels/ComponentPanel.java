package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Category;
import com.flowershop.flowershop.model.Component;
import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.*;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.SpoilageService;
import com.flowershop.flowershop.service.StockReceivingService;
import com.flowershop.flowershop.ui.SpringContext;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

// Components tab: add components and see what is in stock.
// Stock comes in through Procurement Details in the Suppliers screen.
public class ComponentPanel extends JPanel {

    private static final Preferences PREFS = Preferences.userNodeForPackage(ComponentPanel.class);
    private static final String EXPIRING_DAYS_KEY = "componentExpiringDays";
    private static final int DEFAULT_EXPIRING_DAYS = 3;

    private static final int BATCH_STATUS_COL = 6;
    private static final int BATCH_ACTION_COL = 7;
    private static final String ACTION_LABEL = "Log Spoilage";
    private static final String ALL_CATEGORIES = "All Categories";

    private static final Color ORANGE_BG = new Color(255, 213, 153);
    private static final Color ORANGE_FG = new Color(166, 77, 0);

    private final ComponentRepository componentRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final StockReceivingService stockReceivingService;
    private final User currentUser;
    private final boolean readOnly; // view only: the add/edit form and the spoilage button are not shown
    private final AuditService auditService = SpringContext.getBean(AuditService.class);
    private final SpoilageService spoilageService = SpringContext.getBean(SpoilageService.class);

    private JTextField txtSearch;
    private JComboBox<String> cmbFilter;
    private boolean updatingFilter = false; // true while the filter list is rebuilt, so it does not reload the table
    private JSpinner spnExpiringDays;
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
    private List<Component> shownComponents = new ArrayList<>();

    private JTable batchTable;
    private DefaultTableModel batchModel;
    private JLabel lblBatchTitle;
    // The batches shown in the bottom table, row for row
    private List<ComponentInventory> shownBatches = new ArrayList<>();
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

    // Colors the Status cells of both tables
    private static class StatusRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                                boolean hasFocus, int row, int column) {
            java.awt.Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            cell.setFont(cell.getFont().deriveFont(Font.BOLD));
            if (!isSelected) {
                if ("In Stock".equals(value) || "Fresh".equals(value)) {
                    cell.setBackground(new Color(212, 237, 222));
                    cell.setForeground(new Color(21, 87, 36));
                } else if ("Low Stock".equals(value)) {
                    cell.setBackground(new Color(255, 243, 205));
                    cell.setForeground(new Color(133, 100, 4));
                } else if ("Expiring Batch".equals(value) || "Expiring Soon".equals(value)) {
                    cell.setBackground(ORANGE_BG);
                    cell.setForeground(ORANGE_FG);
                } else if ("Out of Stock".equals(value) || "Expired".equals(value)) {
                    cell.setBackground(new Color(248, 215, 218));
                    cell.setForeground(new Color(114, 28, 36));
                } else {
                    cell.setBackground(Color.WHITE);
                    cell.setForeground(Color.BLACK);
                }
            }
            return cell;
        }
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

        txtSearch = new JTextField();
        txtSearch.addActionListener(e -> loadData());
        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> loadData());

        JPanel searchBox = new JPanel(new BorderLayout(8, 0));
        searchBox.setOpaque(false);
        searchBox.add(txtSearch, BorderLayout.CENTER);
        searchBox.add(btnSearch, BorderLayout.EAST);

        cmbFilter = new JComboBox<>();
        cmbFilter.addActionListener(e -> {
            if (!updatingFilter) {
                loadData();
            }
        });
        spnExpiringDays = new JSpinner(new SpinnerNumberModel(
                PREFS.getInt(EXPIRING_DAYS_KEY, DEFAULT_EXPIRING_DAYS), 1, 60, 1));
        spnExpiringDays.addChangeListener(e -> {
            PREFS.putInt(EXPIRING_DAYS_KEY, expiringDays());
            reloadKeepingSelection();
        });

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        filters.add(new JLabel("Category:"));
        filters.add(cmbFilter);
        filters.add(Box.createHorizontalStrut(8));
        filters.add(new JLabel("Expiring within"));
        filters.add(spnExpiringDays);
        filters.add(new JLabel("days"));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(searchBox, BorderLayout.CENTER);
        searchPanel.add(filters, BorderLayout.EAST);

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
        table.getColumnModel().getColumn(5).setCellRenderer(new StatusRenderer());
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
        String[] batchColumns = {"Use Order", "Date Received", "Expiry", "Qty On Hand", "Unit Cost", "Supplier",
                "Status", "Actions"};
        batchModel = new DefaultTableModel(batchColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        batchTable = new JTable(batchModel);
        batchTable.setRowHeight(28);
        batchTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        batchTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        batchTable.getColumnModel().getColumn(BATCH_STATUS_COL).setCellRenderer(new StatusRenderer());

        JButton renderButton = new JButton(ACTION_LABEL);
        renderButton.setFocusable(false);
        batchTable.getColumnModel().getColumn(BATCH_ACTION_COL).setCellRenderer(
                (tbl, value, isSelected, hasFocus, row, column) -> {
                    if (ACTION_LABEL.equals(value)) {
                        return renderButton;
                    }
                    return new JLabel("--", SwingConstants.CENTER);
                });
        batchTable.getColumnModel().getColumn(BATCH_ACTION_COL).setPreferredWidth(120);
        batchTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = batchTable.rowAtPoint(e.getPoint());
                int col = batchTable.columnAtPoint(e.getPoint());
                if (row >= 0 && col == BATCH_ACTION_COL
                        && ACTION_LABEL.equals(batchModel.getValueAt(row, BATCH_ACTION_COL))) {
                    logSpoilage(row);
                }
            }
        });

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

    private int expiringDays() {
        return (Integer) spnExpiringDays.getValue();
    }

    // Reloads the tables and keeps the selected component selected
    private void reloadKeepingSelection() {
        int row = table.getSelectedRow();
        Long selectedId = row >= 0 && row < shownComponents.size() ? shownComponents.get(row).getComponentId() : null;
        loadData();
        if (selectedId != null) {
            selectComponent(selectedId);
        }
    }

    // Usable batches first, in the order production uses them (earliest expiry first).
    // Expired batches are never used, so they go last and get no use order.
    private void showBatches() {
        batchModel.setRowCount(0);
        shownBatches.clear();
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
        LocalDate warnLimit = today.plusDays(expiringDays());
        List<ComponentInventory> usable = new ArrayList<>();
        List<ComponentInventory> expired = new ArrayList<>();
        for (ComponentInventory batch : batches) {
            if (batch.getQuantityOnHand() <= 0) {
                continue;
            }
            if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)) {
                expired.add(batch);
            } else {
                usable.add(batch);
            }
        }

        int useOrder = 0;
        for (ComponentInventory batch : usable) {
            addBatchRow(batch, String.valueOf(++useOrder), warnLimit, false);
        }
        for (ComponentInventory batch : expired) {
            addBatchRow(batch, "-", warnLimit, true);
        }
    }

    private void addBatchRow(ComponentInventory batch, String useOrder, LocalDate warnLimit, boolean expired) {
        String status = expired ? "Expired" : expiresWithin(batch, warnLimit) ? "Expiring Soon" : "Fresh";
        String action = !readOnly && !"Fresh".equals(status) ? ACTION_LABEL : "--";

        shownBatches.add(batch);
        batchModel.addRow(new Object[]{
                useOrder,
                batch.getDateReceived().toString(),
                batch.getExpiryDate() == null ? "-" : batch.getExpiryDate().toString(),
                batch.getQuantityOnHand(),
                "₱" + String.format("%.2f", batch.getUnitCost()),
                batch.getSupplier() != null ? batch.getSupplier().getSupplierName() : "-",
                status,
                action
        });
    }

    private boolean expiresWithin(ComponentInventory batch, LocalDate limit) {
        return batch.getExpiryDate() != null && !batch.getExpiryDate().isAfter(limit);
    }

    private void logSpoilage(int row) {
        if (row >= shownBatches.size()) {
            return;
        }
        ComponentInventory batch = shownBatches.get(row);
        int available = batch.getQuantityOnHand();
        boolean expired = batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(LocalDate.now());
        String componentName = batch.getComponent().getName();
        Long componentId = batch.getComponent().getComponentId();

        JSpinner spnQty = new JSpinner(new SpinnerNumberModel(available, 1, available, 1));
        JComboBox<String> cmbReason = new JComboBox<>(SpoilageService.REASONS);
        cmbReason.setSelectedItem(expired ? "Expired" : "Wilted");

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Quantity spoiled (max " + available + ")"));
        form.add(spnQty);
        form.add(new JLabel("Reason"));
        form.add(cmbReason);

        int choice = JOptionPane.showConfirmDialog(this, form, "Log Spoilage: " + componentName,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        int qty = (Integer) spnQty.getValue();
        try {
            spoilageService.logSpoilage(currentUser, batch.getCbatchId(), qty, (String) cmbReason.getSelectedItem());
            loadData();
            selectComponent(componentId);
            showMessage(lblMessage, "Spoilage logged: " + qty + " " + componentName + ".", false);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Spoilage not logged", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Keeps the same component selected after the table reloads
    private void selectComponent(Long componentId) {
        for (int i = 0; i < shownComponents.size(); i++) {
            if (shownComponents.get(i).getComponentId().equals(componentId)) {
                table.setRowSelectionInterval(i, i);
                return;
            }
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

    // One row per component. Status uses the total of all its batches:
    // Out of Stock, Low Stock, then Expiring Batch (a batch with stock expires within the chosen days or already expired).
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
        String categoryFilter = (String) cmbFilter.getSelectedItem();
        boolean filterByCategory = categoryFilter != null && !ALL_CATEGORIES.equals(categoryFilter);
        LocalDate warnLimit = LocalDate.now().plusDays(expiringDays());
        shownComponents.clear();
        tableModel.setRowCount(0);
        int total = 0;
        int low = 0;
        int out = 0;
        int expiring = 0;

        for (Component c : componentRepository.findAll()) {
            if (!c.getName().toLowerCase().contains(keyword)) {
                continue;
            }
            String categoryName = c.getCategory() != null ? c.getCategory().getName() : "N/A";
            if (filterByCategory && !categoryName.equals(categoryFilter)) {
                continue;
            }

            List<ComponentInventory> batches = batchesByComponent.containsKey(c.getComponentId())
                    ? batchesByComponent.get(c.getComponentId())
                    : new ArrayList<>();
            batches.sort(usageOrder);

            int qty = 0;
            boolean hasExpiring = false;
            for (ComponentInventory batch : batches) {
                qty += batch.getQuantityOnHand();
                if (batch.getQuantityOnHand() > 0 && expiresWithin(batch, warnLimit)) {
                    hasExpiring = true;
                }
            }
            int level = c.getReorderLevel();

            String status;
            if (qty == 0) {
                status = "Out of Stock";
                out++;
            } else if (qty <= level) {
                status = "Low Stock";
                low++;
            } else if (hasExpiring) {
                status = "Expiring Batch";
                expiring++;
            } else {
                status = "In Stock";
            }
            total++;

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

        lblSummary.setText(total + " components  |  " + low + " low stock  |  " + out + " out of stock  |  "
                + expiring + " expiring component" + (expiring == 1 ? "" : "s"));
        showBatches();
    }

    // Refreshes the category dropdowns (keeps what was selected)
    private void loadDropdowns() {
        // Product categories (type "Product") are not used for components, so they are left out
        int categoryIndex = cmbCategory.getSelectedIndex();
        String selectedFilter = (String) cmbFilter.getSelectedItem();
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

        updatingFilter = true;
        cmbFilter.removeAllItems();
        cmbFilter.addItem(ALL_CATEGORIES);
        for (Category c : categories) {
            cmbFilter.addItem(c.getName());
        }
        if (selectedFilter != null) {
            cmbFilter.setSelectedItem(selectedFilter);
        }
        updatingFilter = false;
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