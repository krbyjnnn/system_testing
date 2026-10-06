package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Dispute;
import com.flowershop.flowershop.model.Order;
import com.flowershop.flowershop.model.OrderItem;
import com.flowershop.flowershop.model.Returns;
import com.flowershop.flowershop.repository.DisputeRepository;
import com.flowershop.flowershop.repository.OrderItemRepository;
import com.flowershop.flowershop.repository.ReturnsRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ReturnsPanel extends JPanel {

    private static final String TO_RETURN = "To Return";
    private static final String RETURNED = "Returned";
    private static final String FAILED = "Failed to Return";

    private final ReturnsRepository returnsRepository;
    private final DisputeRepository disputeRepository;
    private final OrderItemRepository orderItemRepository;

    private JTable disputeTable;
    private DefaultTableModel disputeModel;
    private JTable returnsTable;
    private DefaultTableModel returnsModel;
    private JComboBox<String> cmbFilter;
    private JLabel lblSummary;

    // Valid disputes shown in the top table (same order as the rows)
    private List<Dispute> waitingDisputes = new ArrayList<>();
    // Returns shown in the bottom table (same order as the rows)
    private List<Returns> shownReturns = new ArrayList<>();

    public ReturnsPanel(
            ReturnsRepository returnsRepository,
            DisputeRepository disputeRepository,
            OrderItemRepository orderItemRepository
    ) {
        this.returnsRepository = returnsRepository;
        this.disputeRepository = disputeRepository;
        this.orderItemRepository = orderItemRepository;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("Returns");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerPanel.add(lblTitle, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Two cards: valid disputes on top, returns below
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 15));
        centerPanel.setOpaque(false);

        // ---- Card 1: valid disputes waiting for a return ----
        JPanel disputeCard = whiteCard();

        JLabel lblDisputes = new JLabel("Valid Disputes (no return yet)");
        lblDisputes.setFont(new Font("Segoe UI", Font.BOLD, 16));

        disputeModel = readOnlyModel(new String[]{"ID", "Customer", "Order", "Complaint"});
        disputeTable = new JTable(disputeModel);
        styleTable(disputeTable);
        disputeTable.getColumnModel().getColumn(3).setPreferredWidth(350);

        JButton btnStart = new JButton("Start Return");
        btnStart.addActionListener(e -> startReturn());
        JPanel startPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        startPanel.setOpaque(false);
        startPanel.add(btnStart);

        disputeCard.add(lblDisputes, BorderLayout.NORTH);
        disputeCard.add(new JScrollPane(disputeTable), BorderLayout.CENTER);
        disputeCard.add(startPanel, BorderLayout.SOUTH);

        // ---- Card 2: returns ----
        JPanel returnsCard = whiteCard();

        JPanel returnsTop = new JPanel(new BorderLayout());
        returnsTop.setOpaque(false);
        JLabel lblReturns = new JLabel("Returns");
        lblReturns.setFont(new Font("Segoe UI", Font.BOLD, 16));
        cmbFilter = new JComboBox<>(new String[]{"All", TO_RETURN, RETURNED, FAILED});
        cmbFilter.addActionListener(e -> loadData());
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterPanel.setOpaque(false);
        filterPanel.add(new JLabel("Status:"));
        filterPanel.add(cmbFilter);
        returnsTop.add(lblReturns, BorderLayout.WEST);
        returnsTop.add(filterPanel, BorderLayout.EAST);

        returnsModel = readOnlyModel(new String[]{"ID", "Date", "Customer", "Order", "Item", "Qty", "Refund", "Status"});
        returnsTable = new JTable(returnsModel);
        styleTable(returnsTable);
        returnsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnReturned = new JButton("Mark as Returned");
        btnReturned.addActionListener(e -> changeStatus(RETURNED));
        JButton btnFailed = new JButton("Mark as Failed to Return");
        btnFailed.addActionListener(e -> changeStatus(FAILED));
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(btnReturned);
        actionPanel.add(btnFailed);

        JPanel returnsBottom = new JPanel(new BorderLayout());
        returnsBottom.setOpaque(false);
        returnsBottom.add(lblSummary, BorderLayout.WEST);
        returnsBottom.add(actionPanel, BorderLayout.EAST);

        returnsCard.add(returnsTop, BorderLayout.NORTH);
        returnsCard.add(new JScrollPane(returnsTable), BorderLayout.CENTER);
        returnsCard.add(returnsBottom, BorderLayout.SOUTH);

        centerPanel.add(disputeCard);
        centerPanel.add(returnsCard);
        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel whiteCard() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        return card;
    }

    private DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private void styleTable(JTable table) {
        table.setRowHeight(28);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
    }

    public void loadData() {
        List<Returns> allReturns = returnsRepository.findAll();

        // Disputes that already have a return
        List<Long> returnedDisputeIds = new ArrayList<>();
        for (Returns r : allReturns) {
            if (r.getDispute() != null) {
                returnedDisputeIds.add(r.getDispute().getDisputeId());
            }
        }

        // 1. Valid disputes that do not have a return yet
        waitingDisputes.clear();
        disputeModel.setRowCount(0);
        for (Dispute d : disputeRepository.findByStatus("Valid")) {
            if (returnedDisputeIds.contains(d.getDisputeId())) {
                continue;
            }
            waitingDisputes.add(d);
            disputeModel.addRow(new Object[]{
                    d.getDisputeId(),
                    d.getCustomerName(),
                    d.getOrder() != null ? orderLabel(d.getOrder()) : "No order linked",
                    d.getComplaint()
            });
        }

        // 2. All returns (newest first), filtered by status
        String filter = (String) cmbFilter.getSelectedItem();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
        shownReturns.clear();
        returnsModel.setRowCount(0);
        int toReturn = 0;
        int returned = 0;
        int failed = 0;

        for (int i = allReturns.size() - 1; i >= 0; i--) {
            Returns r = allReturns.get(i);
            if (!filter.equals("All") && !filter.equals(r.getStatus())) {
                continue;
            }

            if (TO_RETURN.equals(r.getStatus())) {
                toReturn++;
            } else if (RETURNED.equals(r.getStatus())) {
                returned++;
            } else if (FAILED.equals(r.getStatus())) {
                failed++;
            }

            shownReturns.add(r);
            returnsModel.addRow(new Object[]{
                    r.getReturnId(),
                    r.getReturnDate() != null ? r.getReturnDate().format(formatter) : "",
                    r.getDispute() != null ? r.getDispute().getCustomerName() : "-",
                    orderLabel(r.getOrder()),
                    r.getOrderItem() != null ? r.getOrderItem().getProduct().getProductName() : "-",
                    r.getQuantityReturned(),
                    peso(r.getRefundAmount()),
                    r.getStatus()
            });
        }

        lblSummary.setText(shownReturns.size() + " returns  |  "
                + toReturn + " to return  |  " + returned + " returned  |  " + failed + " failed");
    }

    // Turn the selected valid dispute into a return with status "To Return"
    private void startReturn() {
        int row = disputeTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a valid dispute first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Dispute dispute = waitingDisputes.get(row);
        if (dispute.getOrder() == null) {
            JOptionPane.showMessageDialog(this,
                    "This dispute has no related order. Link an order first (Disputes > Edit Selected).",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        List<OrderItem> items = orderItemRepository.findByOrderOrderId(dispute.getOrder().getOrderId());
        if (items.isEmpty()) {
            JOptionPane.showMessageDialog(this, "That order has no items.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Form: item, quantity, refund amount
        JComboBox<String> cmbItem = new JComboBox<>();
        for (OrderItem item : items) {
            cmbItem.addItem(item.getProduct().getProductName()
                    + " (x" + item.getQuantity() + ", " + peso(item.getUnitPrice()) + ")");
        }
        JSpinner spnQty = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        JTextField txtRefund = new JTextField();

        cmbItem.addActionListener(e -> fillRefund(items, cmbItem, spnQty, txtRefund));
        spnQty.addChangeListener(e -> fillRefund(items, cmbItem, spnQty, txtRefund));
        fillRefund(items, cmbItem, spnQty, txtRefund);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Customer:"));
        form.add(new JLabel(dispute.getCustomerName()));
        form.add(new JLabel("Order:"));
        form.add(new JLabel(orderLabel(dispute.getOrder())));
        form.add(new JLabel("Item to return:"));
        form.add(cmbItem);
        form.add(new JLabel("Quantity:"));
        form.add(spnQty);
        form.add(new JLabel("Refund Amount:"));
        form.add(txtRefund);

        int result = JOptionPane.showConfirmDialog(this, form, "Start Return",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        // Check the inputs
        OrderItem item = items.get(cmbItem.getSelectedIndex());
        int qty = (Integer) spnQty.getValue();
        if (qty > item.getQuantity()) {
            JOptionPane.showMessageDialog(this,
                    "Quantity is more than the " + item.getQuantity() + " bought.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        BigDecimal refund;
        try {
            refund = new BigDecimal(txtRefund.getText().replace(",", "").trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Enter a valid refund amount.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (refund.compareTo(BigDecimal.ZERO) < 0) {
            JOptionPane.showMessageDialog(this, "Refund amount cannot be negative.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // The reason column holds 255 characters, the complaint can be longer
        String reason = dispute.getComplaint();
        if (reason.length() > 255) {
            reason = reason.substring(0, 255);
        }

        Returns newReturn = new Returns(dispute.getOrder(), item, reason, qty, refund, TO_RETURN);
        newReturn.setDispute(dispute);

        try {
            returnsRepository.save(newReturn);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Return was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Refund amount = unit price x quantity (the user can still edit it)
    private void fillRefund(List<OrderItem> items, JComboBox<String> cmbItem, JSpinner spnQty, JTextField txtRefund) {
        int index = cmbItem.getSelectedIndex();
        if (index < 0) {
            return;
        }
        int qty = (Integer) spnQty.getValue();
        BigDecimal amount = items.get(index).getUnitPrice().multiply(BigDecimal.valueOf(qty));
        txtRefund.setText(amount.toPlainString());
    }

    // Change a "To Return" return to Returned or Failed to Return
    private void changeStatus(String newStatus) {
        int row = returnsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a return first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Returns r = shownReturns.get(row);
        if (!TO_RETURN.equals(r.getStatus())) {
            JOptionPane.showMessageDialog(this,
                    "Only returns with status \"To Return\" can be changed.",
                    "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Mark this return as \"" + newStatus + "\"?",
                "Change Status", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        r.setStatus(newStatus);
        try {
            returnsRepository.save(r);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Status was not changed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Shows the transaction number, or "Order #id" if there is none
    private String orderLabel(Order o) {
        if (o.getTransactionNumber() != null) {
            return o.getTransactionNumber();
        }
        return "Order #" + o.getOrderId();
    }

    private String peso(BigDecimal value) {
        return "₱" + String.format("%,.2f", value);
    }
}