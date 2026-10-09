package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.Supplier;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.SupplierRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupplierPanel extends JPanel {

    private final SupplierRepository supplierRepository;
    private final ComponentInventoryRepository componentInventoryRepository;

    private JTextField txtSearch;
    private JTable supplierTable;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    private JLabel lblFormTitle;
    private JTextField txtName;
    private JTextField txtContact;
    private JTextField txtPhone;
    private JTextField txtEmail;
    private JTextField txtAddress;
    private JButton btnAdd;
    private JButton btnUpdate;
    private JButton btnClear;
    private JButton btnToggleStatus;

    // Same order as the table rows, so a row index maps directly to a supplier
    private List<Supplier> shownSuppliers = new ArrayList<>();

    // The supplier loaded in the form. null means the form is in add mode.
    private Supplier selectedSupplier = null;

    public SupplierPanel(SupplierRepository supplierRepository, ComponentInventoryRepository componentInventoryRepository) {
        this.supplierRepository = supplierRepository;
        this.componentInventoryRepository = componentInventoryRepository;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("Suppliers");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);
        JPanel formPanel = buildFormPanel();
        JPanel searchPanel = buildSearchPanel();
        formPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(formPanel);
        topPanel.add(Box.createVerticalStrut(12));
        topPanel.add(searchPanel);

        String[] columns = {"ID", "Supplier", "Contact Person", "Phone", "Email", "Address", "Components Supplied", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        supplierTable = new JTable(tableModel);
        supplierTable.setRowHeight(28);
        supplierTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        supplierTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        supplierTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Clicking a row loads it into the form (edit mode)
        supplierTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = supplierTable.getSelectedRow();
            if (row < 0 || row >= shownSuppliers.size()) {
                return;
            }
            fillForm(shownSuppliers.get(row));
        });

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        btnToggleStatus = new JButton("Set Inactive");
        btnToggleStatus.setEnabled(false);
        btnToggleStatus.addActionListener(e -> toggleStatus());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(btnToggleStatus);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(lblSummary, BorderLayout.WEST);
        bottomPanel.add(actionPanel, BorderLayout.EAST);

        card.add(topPanel, BorderLayout.NORTH);
        card.add(new JScrollPane(supplierTable), BorderLayout.CENTER);
        card.add(bottomPanel, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        lblFormTitle = new JLabel("Add Supplier");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        txtName = new JTextField();
        txtContact = new JTextField();
        txtPhone = new JTextField();
        txtEmail = new JTextField();
        txtAddress = new JTextField();

        // Row 1 = labels, row 2 = inputs
        JPanel grid = new JPanel(new GridLayout(2, 5, 10, 3));
        grid.setOpaque(false);
        String[] labels = {"Supplier Name", "Contact Person", "Phone", "Email", "Address"};
        for (String label : labels) {
            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            grid.add(l);
        }
        grid.add(txtName);
        grid.add(txtContact);
        grid.add(txtPhone);
        grid.add(txtEmail);
        grid.add(txtAddress);

        btnAdd = new JButton("Add Supplier");
        btnAdd.addActionListener(e -> saveSupplier(true));
        btnUpdate = new JButton("Update Supplier");
        btnUpdate.addActionListener(e -> saveSupplier(false));
        btnUpdate.setEnabled(false);
        btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnAdd);
        buttons.add(btnUpdate);
        buttons.add(btnClear);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(grid, BorderLayout.NORTH);
        center.add(buttons, BorderLayout.SOUTH);

        panel.add(lblFormTitle, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSearchPanel() {
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
        return searchPanel;
    }

    // Edit mode: Add is disabled so a loaded supplier can't be duplicated by accident
    private void fillForm(Supplier supplier) {
        selectedSupplier = supplier;
        txtName.setText(text(supplier.getSupplierName()));
        txtContact.setText(text(supplier.getContactPerson()));
        txtPhone.setText(text(supplier.getPhone()));
        txtEmail.setText(text(supplier.getEmail()));
        txtAddress.setText(text(supplier.getAddress()));

        lblFormTitle.setText("Edit Supplier: " + supplier.getSupplierName());
        btnAdd.setEnabled(false);
        btnUpdate.setEnabled(true);

        boolean active = "ACTIVE".equals(supplier.getStatus());
        btnToggleStatus.setText(active ? "Set Inactive" : "Set Active");
        btnToggleStatus.setEnabled(true);
    }

    // Back to add mode
    private void clearForm() {
        selectedSupplier = null;
        supplierTable.clearSelection();
        txtName.setText("");
        txtContact.setText("");
        txtPhone.setText("");
        txtEmail.setText("");
        txtAddress.setText("");

        lblFormTitle.setText("Add Supplier");
        btnAdd.setEnabled(true);
        btnUpdate.setEnabled(false);

        btnToggleStatus.setText("Set Inactive");
        btnToggleStatus.setEnabled(false);
    }

    public void loadData() {
        // "Components Supplied" isn't stored on the supplier. It is built from the component batches.
        Map<Long, String> components = new HashMap<>();
        for (ComponentInventory batch : componentInventoryRepository.findAll()) {
            // Non-perishable items have no supplier
            if (batch.getSupplier() == null) {
                continue;
            }
            Long supplierId = batch.getSupplier().getSupplierId();
            String name = batch.getComponent().getName();
            String current = components.containsKey(supplierId) ? components.get(supplierId) : "";

            if (current.isEmpty()) {
                components.put(supplierId, name);
            } else if (!(", " + current + ", ").contains(", " + name + ", ")) {
                components.put(supplierId, current + ", " + name);
            }
        }

        String keyword = txtSearch.getText().trim().toLowerCase();
        shownSuppliers.clear();
        tableModel.setRowCount(0);

        // Inactive suppliers are still listed so they can be reactivated
        for (Supplier s : supplierRepository.findAll()) {
            boolean matches = s.getSupplierName().toLowerCase().contains(keyword)
                    || text(s.getContactPerson()).toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            shownSuppliers.add(s);
            tableModel.addRow(new Object[]{
                    s.getSupplierId(),
                    s.getSupplierName(),
                    text(s.getContactPerson()),
                    text(s.getPhone()),
                    text(s.getEmail()),
                    text(s.getAddress()),
                    components.containsKey(s.getSupplierId()) ? components.get(s.getSupplierId()) : "None yet",
                    s.getStatus()
            });
        }

        lblSummary.setText(shownSuppliers.size() + " suppliers");

        // Rows were rebuilt, so any selection or edit state is stale
        clearForm();
    }

    // Suppliers are never deleted, so old inventory batches keep their supplier history
    private void toggleStatus() {
        if (selectedSupplier == null) {
            JOptionPane.showMessageDialog(this, "Select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean active = "ACTIVE".equals(selectedSupplier.getStatus());
        String action = active ? "deactivate" : "activate";
        int confirm = JOptionPane.showConfirmDialog(this,
                "Do you want to " + action + " " + selectedSupplier.getSupplierName() + "?",
                "Change Status", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            selectedSupplier.setStatus(active ? "INACTIVE" : "ACTIVE");
            supplierRepository.save(selectedSupplier);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Status was not changed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            loadData(); // discard the unsaved status change on the in-memory object
        }
    }

    // isNew = true creates a supplier, false updates the one loaded in the form
    private void saveSupplier(boolean isNew) {
        if (txtName.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Supplier name is required.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Supplier supplier = isNew ? new Supplier() : selectedSupplier;
        if (supplier == null) {
            JOptionPane.showMessageDialog(this, "Select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        supplier.setSupplierName(txtName.getText().trim());
        supplier.setContactPerson(txtContact.getText().trim());
        supplier.setPhone(txtPhone.getText().trim());
        supplier.setEmail(txtEmail.getText().trim());
        supplier.setAddress(txtAddress.getText().trim());

        try {
            supplierRepository.save(supplier);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Supplier was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Avoids showing "null" in the UI
    private String text(String value) {
        return value == null ? "" : value;
    }
}