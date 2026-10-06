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

    // Suppliers shown in the table (same order as the rows)
    private List<Supplier> shownSuppliers = new ArrayList<>();

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

        // Top Header
        JLabel lblTitle = new JLabel("Suppliers");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        // White card
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // Search bar
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

        // Table
        String[] columns = {"ID", "Supplier", "Contact Person", "Phone", "Email", "Address", "Components Supplied"};
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

        // Bottom: summary on the left, action buttons on the right
        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnAdd = new JButton("Add Supplier");
        btnAdd.addActionListener(e -> openForm(null));
        JButton btnEdit = new JButton("Edit Selected");
        btnEdit.addActionListener(e -> editSelected());
        JButton btnDelete = new JButton("Delete Selected");
        btnDelete.addActionListener(e -> deleteSelected());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(btnAdd);
        actionPanel.add(btnEdit);
        actionPanel.add(btnDelete);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(lblSummary, BorderLayout.WEST);
        bottomPanel.add(actionPanel, BorderLayout.EAST);

        card.add(searchPanel, BorderLayout.NORTH);
        card.add(new JScrollPane(supplierTable), BorderLayout.CENTER);
        card.add(bottomPanel, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    public void loadData() {
        // 1. Find which components each supplier provides (from the component batches)
        Map<Long, String> components = new HashMap<>();
        for (ComponentInventory batch : componentInventoryRepository.findAll()) {
            // Batches with no supplier (non-perishable items) don't belong to any supplier
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

        // 2. Fill the table with the suppliers that match the search
        String keyword = txtSearch.getText().trim().toLowerCase();
        shownSuppliers.clear();
        tableModel.setRowCount(0);

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
                    components.containsKey(s.getSupplierId()) ? components.get(s.getSupplierId()) : "None yet"
            });
        }

        lblSummary.setText(shownSuppliers.size() + " suppliers");
    }

    private void editSelected() {
        int row = supplierTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        openForm(shownSuppliers.get(row));
    }

    private void deleteSelected() {
        int row = supplierTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Supplier supplier = shownSuppliers.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete " + supplier.getSupplierName() + "?",
                "Delete Supplier", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            supplierRepository.delete(supplier);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "This supplier cannot be deleted because it is used by inventory records.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Add form (supplier = null) or edit form (supplier = the one to edit)
    private void openForm(Supplier supplier) {
        boolean isNew = supplier == null;

        JTextField txtName = new JTextField(isNew ? "" : supplier.getSupplierName());
        JTextField txtContact = new JTextField(isNew ? "" : text(supplier.getContactPerson()));
        JTextField txtPhone = new JTextField(isNew ? "" : text(supplier.getPhone()));
        JTextField txtEmail = new JTextField(isNew ? "" : text(supplier.getEmail()));
        JTextField txtAddress = new JTextField(isNew ? "" : text(supplier.getAddress()));

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Supplier Name:"));
        form.add(txtName);
        form.add(new JLabel("Contact Person:"));
        form.add(txtContact);
        form.add(new JLabel("Phone:"));
        form.add(txtPhone);
        form.add(new JLabel("Email:"));
        form.add(txtEmail);
        form.add(new JLabel("Address:"));
        form.add(txtAddress);

        int result = JOptionPane.showConfirmDialog(this, form,
                isNew ? "Add Supplier" : "Edit Supplier",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        if (txtName.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Supplier name is required.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (isNew) {
            supplier = new Supplier();
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

    // Shows an empty text instead of "null"
    private String text(String value) {
        return value == null ? "" : value;
    }
}