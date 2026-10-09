package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Dispute;
import com.flowershop.flowershop.model.Order;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.DisputeRepository;
import com.flowershop.flowershop.repository.OrderRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DisputePanel extends JPanel {

    private static final String[] STATUSES = {"Under Review", "Valid", "Invalid"};

    private final User currentUser;
    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;

    // Search + table
    private JTextField txtSearch;
    private JComboBox<String> cmbFilter;
    private JTable disputeTable;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    // Inline form
    private JLabel lblFormTitle;
    private JTextField txtCustomer;
    private JComboBox<String> cmbOrder;
    private JComboBox<String> cmbStatus;
    private JTextField txtComplaint;
    private JButton btnSave;

    // Disputes shown in the table (same order as the rows)
    private List<Dispute> shownDisputes = new ArrayList<>();
    // Orders in the "Related Order" dropdown (index + 1, because index 0 is "None")
    private List<Order> orders = new ArrayList<>();
    // The complaint being edited, or null when the form is in "add" mode
    private Dispute editingDispute = null;
    // True while the code fills the form/combo, so listeners ignore it
    private boolean updatingUI = false;

    public DisputePanel(User currentUser, DisputeRepository disputeRepository, OrderRepository orderRepository) {
        this.currentUser = currentUser;
        this.disputeRepository = disputeRepository;
        this.orderRepository = orderRepository;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        // Top Header
        JLabel lblTitle = new JLabel("Customer Disputes");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        // White card
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // Form on top, search + table below
        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(buildFormPanel(), BorderLayout.NORTH);
        center.add(buildTablePanel(), BorderLayout.CENTER);
        card.add(center, BorderLayout.CENTER);

        // Bottom: summary on the left, remove button on the right
        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnRemove = new JButton("Remove Selected");
        btnRemove.addActionListener(e -> removeSelected());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(btnRemove);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(lblSummary, BorderLayout.WEST);
        bottomPanel.add(actionPanel, BorderLayout.EAST);
        card.add(bottomPanel, BorderLayout.SOUTH);

        add(card, BorderLayout.CENTER);
    }

    // The add/edit form that sits above the table
    private JPanel buildFormPanel() {
        JPanel formBox = new JPanel(new BorderLayout(0, 8));
        formBox.setBackground(Color.WHITE);
        formBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)
        ));

        lblFormTitle = new JLabel("Add Complaint");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));

        txtCustomer = new JTextField();
        cmbOrder = new JComboBox<>();
        cmbOrder.addItem("None");
        cmbStatus = new JComboBox<>(STATUSES);
        txtComplaint = new JTextField();

        // Labels on the first row, inputs on the second row
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        String[] labels = {"Customer Name", "Related Order", "Status", "Complaint"};
        Component[] inputs = {txtCustomer, cmbOrder, cmbStatus, txtComplaint};
        double[] weights = {1.0, 1.0, 0.6, 2.5};

        for (int i = 0; i < labels.length; i++) {
            GridBagConstraints lc = new GridBagConstraints();
            lc.gridx = i;
            lc.gridy = 0;
            lc.anchor = GridBagConstraints.WEST;
            lc.insets = new Insets(0, i == 0 ? 0 : 6, 2, 6);
            JLabel label = new JLabel(labels[i]);
            label.setFont(new Font("Segoe UI", Font.BOLD, 13));
            fields.add(label, lc);

            GridBagConstraints ic = new GridBagConstraints();
            ic.gridx = i;
            ic.gridy = 1;
            ic.weightx = weights[i];
            ic.fill = GridBagConstraints.HORIZONTAL;
            ic.insets = new Insets(0, i == 0 ? 0 : 6, 0, 6);
            fields.add(inputs[i], ic);
        }

        btnSave = new JButton("Add Complaint");
        btnSave.addActionListener(e -> saveForm());
        JButton btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnSave);
        buttons.add(btnClear);

        formBox.add(lblFormTitle, BorderLayout.NORTH);
        formBox.add(fields, BorderLayout.CENTER);
        formBox.add(buttons, BorderLayout.SOUTH);
        return formBox;
    }

    // Search bar + table
    private JPanel buildTablePanel() {
        JPanel tablePanel = new JPanel(new BorderLayout(0, 10));
        tablePanel.setOpaque(false);

        // Search + status filter
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        txtSearch = new JTextField();
        txtSearch.addActionListener(e -> loadData());

        cmbFilter = new JComboBox<>(new String[]{"All", "Under Review", "Valid", "Invalid"});
        cmbFilter.addActionListener(e -> loadData());
        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> loadData());

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setOpaque(false);
        filterPanel.add(new JLabel("Status:"));
        filterPanel.add(cmbFilter);
        filterPanel.add(btnSearch);

        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        searchPanel.add(filterPanel, BorderLayout.EAST);

        // Table
        String[] columns = {"ID", "Date Filed", "Customer", "Order", "Complaint", "Status", "Filed By"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        disputeTable = new JTable(tableModel);
        disputeTable.setRowHeight(28);
        disputeTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        disputeTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        disputeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        disputeTable.getColumnModel().getColumn(4).setPreferredWidth(300);

        // Colors the Status cell
        disputeTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                Dispute d = row < shownDisputes.size() ? shownDisputes.get(row) : null;
                setFont(new Font("Segoe UI", Font.PLAIN, 13));

                if (!isSelected) {
                    setBackground(Color.WHITE);
                    setForeground(Color.BLACK);
                }

                if (d != null) {
                    // Status colors
                    if (column == 5 && !isSelected) {
                        setFont(new Font("Segoe UI", Font.BOLD, 13));
                        if ("Under Review".equals(d.getStatus())) {
                            setBackground(new Color(255, 243, 205));
                            setForeground(new Color(133, 100, 4));
                        } else if ("Valid".equals(d.getStatus())) {
                            setBackground(new Color(212, 237, 218));
                            setForeground(new Color(21, 87, 36));
                        } else if ("Invalid".equals(d.getStatus())) {
                            setBackground(new Color(248, 215, 218));
                            setForeground(new Color(114, 28, 36));
                        }
                    }
                }
                return c;
            }
        });

        // Selecting a row loads it into the form for editing
        disputeTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || updatingUI) {
                return;
            }
            int row = disputeTable.getSelectedRow();
            if (row >= 0 && row < shownDisputes.size()) {
                fillForm(shownDisputes.get(row));
            }
        });

        tablePanel.add(searchPanel, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(disputeTable), BorderLayout.CENTER);
        return tablePanel;
    }

    public void loadData() {
        updatingUI = true;
        try {
            refreshOrderChoices();

            String keyword = txtSearch.getText().trim().toLowerCase();
            String filter = (String) cmbFilter.getSelectedItem();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

            shownDisputes.clear();
            tableModel.setRowCount(0);
            int underReview = 0;
            int valid = 0;
            int invalid = 0;

            // "Under Review" complaints first, then the resolved ones. Newest first in each group.
            List<Dispute> all = new ArrayList<>(disputeRepository.findAll());
            all.sort((a, b) -> {
                boolean aOpen = "Under Review".equals(a.getStatus());
                boolean bOpen = "Under Review".equals(b.getStatus());
                if (aOpen != bOpen) {
                    return aOpen ? -1 : 1;
                }
                return b.getDisputeId().compareTo(a.getDisputeId());
            });
            for (Dispute d : all) {

                boolean matches = d.getCustomerName().toLowerCase().contains(keyword)
                        || d.getComplaint().toLowerCase().contains(keyword);
                if (!matches) {
                    continue;
                }
                if (!filter.equals("All") && !filter.equals(d.getStatus())) {
                    continue;
                }

                if (d.getStatus().equals("Under Review")) {
                    underReview++;
                } else if (d.getStatus().equals("Valid")) {
                    valid++;
                } else if (d.getStatus().equals("Invalid")) {
                    invalid++;
                }

                shownDisputes.add(d);
                tableModel.addRow(new Object[]{
                        d.getDisputeId(),
                        d.getDateFiled().format(formatter),
                        d.getCustomerName(),
                        d.getOrder() != null ? orderLabel(d.getOrder()) : "None",
                        d.getComplaint(),
                        d.getStatus(),
                        d.getUser().getFullName()
                });
            }

            lblSummary.setText(shownDisputes.size() + " complaints  |  "
                    + underReview + " under review  |  " + valid + " valid  |  " + invalid + " invalid");
        } finally {
            updatingUI = false;
        }
    }

    // Reloads the "Related Order" dropdown, keeping the current choice
    private void refreshOrderChoices() {
        Long selectedOrderId = null;
        int idx = cmbOrder.getSelectedIndex();
        if (idx > 0 && idx - 1 < orders.size()) {
            selectedOrderId = orders.get(idx - 1).getOrderId();
        }

        // Orders already used by another complaint can't be picked again.
        // (The complaint being edited keeps its own order.)
        Set<Long> usedOrderIds = new HashSet<>();
        for (Dispute d : disputeRepository.findAll()) {
            if (d.getOrder() == null) {
                continue;
            }
            boolean isBeingEdited = editingDispute != null
                    && d.getDisputeId().equals(editingDispute.getDisputeId());
            if (!isBeingEdited) {
                usedOrderIds.add(d.getOrder().getOrderId());
            }
        }
        // The complaint being edited always keeps its own order in the list
        Long ownOrderId = (editingDispute != null && editingDispute.getOrder() != null)
                ? editingDispute.getOrder().getOrderId() : null;
        orders = new ArrayList<>();
        for (Order o : orderRepository.findAll()) {
            if (!usedOrderIds.contains(o.getOrderId()) || o.getOrderId().equals(ownOrderId)) {
                orders.add(o);
            }
        }

        cmbOrder.removeAllItems();
        cmbOrder.addItem("None");
        for (Order o : orders) {
            cmbOrder.addItem(orderLabel(o));
        }
        selectOrder(selectedOrderId);
    }

    private void selectOrder(Long orderId) {
        cmbOrder.setSelectedIndex(0);
        if (orderId == null) {
            return;
        }
        for (int i = 0; i < orders.size(); i++) {
            if (orders.get(i).getOrderId().equals(orderId)) {
                cmbOrder.setSelectedIndex(i + 1);
                return;
            }
        }
    }

    // Put a complaint into the form (edit mode)
    private void fillForm(Dispute dispute) {
        editingDispute = dispute;
        lblFormTitle.setText("Edit Complaint: #" + dispute.getDisputeId());
        btnSave.setText("Update Complaint");

        txtCustomer.setText(dispute.getCustomerName());
        txtComplaint.setText(dispute.getComplaint());
        cmbStatus.setSelectedItem(dispute.getStatus());
        refreshOrderChoices(); // now includes this complaint's own order
        selectOrder(dispute.getOrder() != null ? dispute.getOrder().getOrderId() : null);

        // Resolved complaints (Valid / Invalid) are locked: viewable but not editable
        boolean locked = !"Under Review".equals(dispute.getStatus());
        setFormEnabled(!locked);
        if (locked) {
            lblFormTitle.setText("View Complaint: #" + dispute.getDisputeId()
                    + "  (" + dispute.getStatus() + ", locked)");
        }
    }

    // Empty the form (add mode)
    private void clearForm() {
        editingDispute = null;
        lblFormTitle.setText("Add Complaint");
        btnSave.setText("Add Complaint");

        txtCustomer.setText("");
        txtComplaint.setText("");
        cmbStatus.setSelectedIndex(0);
        setFormEnabled(true);
        refreshOrderChoices(); // back to only the unused orders
        cmbOrder.setSelectedIndex(0);

        updatingUI = true;
        disputeTable.clearSelection();
        updatingUI = false;
    }

    // Turns the form inputs and the save button on or off
    private void setFormEnabled(boolean enabled) {
        txtCustomer.setEnabled(enabled);
        cmbOrder.setEnabled(enabled);
        cmbStatus.setEnabled(enabled);
        txtComplaint.setEnabled(enabled);
        btnSave.setEnabled(enabled);
    }

    private void saveForm() {
        String customer = txtCustomer.getText().trim();
        String complaint = txtComplaint.getText().trim();
        if (customer.isEmpty() || complaint.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name and complaint are required.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (customer.length() > 255 || complaint.length() > 500) {
            JOptionPane.showMessageDialog(this, "Customer name must be 255 characters or less, and the complaint 500 or less.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Dispute dispute = editingDispute;
        if (dispute == null) {
            dispute = new Dispute();
            dispute.setDateFiled(LocalDateTime.now());
            dispute.setUser(currentUser);
        }
        dispute.setCustomerName(customer);
        dispute.setComplaint(complaint);
        dispute.setStatus((String) cmbStatus.getSelectedItem());
        int orderIndex = cmbOrder.getSelectedIndex();
        dispute.setOrder(orderIndex <= 0 ? null : orders.get(orderIndex - 1));

        try {
            disputeRepository.save(dispute);
            clearForm();
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Complaint was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Only clears the text boxes and the table selection. Nothing is deleted from the database.
    private void removeSelected() {
        clearForm();
    }

    // Shows the transaction number, or "Order #id" if there is none
    private String orderLabel(Order o) {
        if (o.getTransactionNumber() != null) {
            return o.getTransactionNumber();
        }
        return "Order #" + o.getOrderId();
    }
}