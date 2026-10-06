package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Dispute;
import com.flowershop.flowershop.model.Order;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.DisputeRepository;
import com.flowershop.flowershop.repository.OrderRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class DisputePanel extends JPanel {

    private static final String[] STATUSES = {"Under Review", "Valid", "Invalid"};

    private final User currentUser;
    private final DisputeRepository disputeRepository;
    private final OrderRepository orderRepository;

    private JTextField txtSearch;
    private JComboBox<String> cmbFilter;
    private JTable disputeTable;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    // Disputes shown in the table (same order as the rows)
    private List<Dispute> shownDisputes = new ArrayList<>();

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

        // Bottom: summary on the left, action buttons on the right
        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnAdd = new JButton("Add Complaint");
        btnAdd.addActionListener(e -> openForm(null));
        JButton btnEdit = new JButton("Edit Selected");
        btnEdit.addActionListener(e -> editSelected());
        JButton btnRemove = new JButton("Remove Selected");
        btnRemove.addActionListener(e -> removeSelected());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(btnAdd);
        actionPanel.add(btnEdit);
        actionPanel.add(btnRemove);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(lblSummary, BorderLayout.WEST);
        bottomPanel.add(actionPanel, BorderLayout.EAST);

        card.add(searchPanel, BorderLayout.NORTH);
        card.add(new JScrollPane(disputeTable), BorderLayout.CENTER);
        card.add(bottomPanel, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    public void loadData() {
        String keyword = txtSearch.getText().trim().toLowerCase();
        String filter = (String) cmbFilter.getSelectedItem();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");

        shownDisputes.clear();
        tableModel.setRowCount(0);
        int underReview = 0;
        int valid = 0;
        int invalid = 0;

        // Newest complaints first
        List<Dispute> all = disputeRepository.findAll();
        for (int i = all.size() - 1; i >= 0; i--) {
            Dispute d = all.get(i);

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
    }

    private void editSelected() {
        int row = disputeTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a complaint first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        openForm(shownDisputes.get(row));
    }

    private void removeSelected() {
        int row = disputeTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a complaint first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Dispute dispute = shownDisputes.get(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove the complaint of " + dispute.getCustomerName() + "?",
                "Remove Complaint", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            disputeRepository.delete(dispute);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Complaint was not removed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Add form (dispute = null) or edit form (dispute = the one to edit)
    private void openForm(Dispute dispute) {
        boolean isNew = dispute == null;

        // Orders the complaint can be linked to (first choice = no order)
        List<Order> orders = orderRepository.findAll();
        JComboBox<String> cmbOrder = new JComboBox<>();
        cmbOrder.addItem("None");
        for (Order o : orders) {
            cmbOrder.addItem(orderLabel(o));
        }
        if (!isNew && dispute.getOrder() != null) {
            for (int i = 0; i < orders.size(); i++) {
                if (orders.get(i).getOrderId().equals(dispute.getOrder().getOrderId())) {
                    cmbOrder.setSelectedIndex(i + 1);
                }
            }
        }

        JTextField txtCustomer = new JTextField(isNew ? "" : dispute.getCustomerName());
        JComboBox<String> cmbStatus = new JComboBox<>(STATUSES);
        if (!isNew) {
            cmbStatus.setSelectedItem(dispute.getStatus());
        }
        JTextArea txtComplaint = new JTextArea(isNew ? "" : dispute.getComplaint(), 5, 25);
        txtComplaint.setLineWrap(true);
        txtComplaint.setWrapStyleWord(true);

        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        fields.add(new JLabel("Customer Name:"));
        fields.add(txtCustomer);
        fields.add(new JLabel("Related Order:"));
        fields.add(cmbOrder);
        fields.add(new JLabel("Status:"));
        fields.add(cmbStatus);

        JPanel complaintPanel = new JPanel(new BorderLayout(0, 4));
        complaintPanel.add(new JLabel("Complaint:"), BorderLayout.NORTH);
        complaintPanel.add(new JScrollPane(txtComplaint), BorderLayout.CENTER);

        JPanel form = new JPanel(new BorderLayout(0, 10));
        form.add(fields, BorderLayout.NORTH);
        form.add(complaintPanel, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, form,
                isNew ? "Add Complaint" : "Edit Complaint",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        // Check the inputs
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

        // Save
        if (isNew) {
            dispute = new Dispute();
            dispute.setDateFiled(LocalDateTime.now());
            dispute.setUser(currentUser);
        }
        dispute.setCustomerName(customer);
        dispute.setComplaint(complaint);
        dispute.setStatus((String) cmbStatus.getSelectedItem());
        int orderIndex = cmbOrder.getSelectedIndex();
        dispute.setOrder(orderIndex == 0 ? null : orders.get(orderIndex - 1));

        try {
            disputeRepository.save(dispute);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Complaint was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Shows the transaction number, or "Order #id" if there is none
    private String orderLabel(Order o) {
        if (o.getTransactionNumber() != null) {
            return o.getTransactionNumber();
        }
        return "Order #" + o.getOrderId();
    }
}