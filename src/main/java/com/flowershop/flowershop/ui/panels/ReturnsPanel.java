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

    private JLabel lblFormTitle;
    private JLabel lblHint;
    private JComboBox<String> cmbItem;
    private JSpinner spnQty;
    private JTextField txtRefund;
    private JButton btnStart;
    private JButton btnClear;

    // Same order as the table rows, so a row index maps directly to an object
    private List<Dispute> waitingDisputes = new ArrayList<>();
    private List<Returns> shownReturns = new ArrayList<>();

    private Dispute selectedDispute = null;

    // Same order as the item combo box. remaining = bought quantity minus what was already returned
    private List<OrderItem> formItems = new ArrayList<>();
    private List<Integer> formRemaining = new ArrayList<>();

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

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JLabel lblTitle = new JLabel("Returns");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerPanel.add(lblTitle, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 0, 15));
        centerPanel.setOpaque(false);

        JPanel disputeCard = whiteCard();

        JLabel lblDisputes = new JLabel("Valid Disputes (no return yet)");
        lblDisputes.setFont(new Font("Segoe UI", Font.BOLD, 16));

        disputeModel = readOnlyModel(new String[]{"ID", "Customer", "Order", "Complaint"});
        disputeTable = new JTable(disputeModel);
        styleTable(disputeTable);
        disputeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        disputeTable.getColumnModel().getColumn(3).setPreferredWidth(350);

        disputeTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = disputeTable.getSelectedRow();
            if (row < 0 || row >= waitingDisputes.size()) {
                return;
            }
            fillForm(waitingDisputes.get(row));
        });

        disputeCard.add(lblDisputes, BorderLayout.NORTH);
        disputeCard.add(new JScrollPane(disputeTable), BorderLayout.CENTER);
        disputeCard.add(buildFormPanel(), BorderLayout.EAST);

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

    private JPanel buildFormPanel() {
        lblFormTitle = new JLabel("Start Return");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        lblHint = new JLabel();
        lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblHint.setForeground(new Color(110, 118, 130));

        cmbItem = new JComboBox<>();
        spnQty = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        txtRefund = new JTextField();

        cmbItem.addActionListener(e -> onItemChanged());
        spnQty.addChangeListener(e -> fillRefund());

        btnStart = new JButton("Start Return");
        btnStart.addActionListener(e -> startReturn());
        btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnStart);
        buttons.add(btnClear);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(320, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 15, 0, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(lblFormTitle, gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(4, 0, 12, 0);
        panel.add(lblHint, gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 3, 0);
        panel.add(formLabel("Item to Return"), gbc);
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(cmbItem, gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(0, 0, 3, 0);
        panel.add(formLabel("Quantity"), gbc);
        gbc.gridy = 5;
        gbc.insets = new Insets(0, 0, 10, 0);
        panel.add(spnQty, gbc);

        gbc.gridy = 6;
        gbc.insets = new Insets(0, 0, 3, 0);
        panel.add(formLabel("Refund Amount"), gbc);
        gbc.gridy = 7;
        gbc.insets = new Insets(0, 0, 12, 0);
        panel.add(txtRefund, gbc);

        gbc.gridy = 8;
        gbc.insets = new Insets(0, 0, 0, 0);
        panel.add(buttons, gbc);

        gbc.gridy = 9;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        panel.add(filler, gbc);

        setFormEnabled(false);
        return panel;
    }

    private JLabel formLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return l;
    }

    private void setHint(String text) {
        lblHint.setText("<html><div style='width:270px'>" + text + "</div></html>");
    }

    // Everything stays grayed out until a dispute is selected
    private void setFormEnabled(boolean enabled) {
        cmbItem.setEnabled(enabled);
        spnQty.setEnabled(enabled);
        ((JSpinner.DefaultEditor) spnQty.getEditor()).getTextField().setEnabled(enabled);
        txtRefund.setEnabled(enabled);
        btnStart.setEnabled(enabled);
        btnClear.setEnabled(enabled);
    }

    // Quantity can't go above what is still left to return for the chosen item
    private void onItemChanged() {
        int index = cmbItem.getSelectedIndex();
        if (index < 0 || index >= formRemaining.size()) {
            return;
        }
        SpinnerNumberModel model = (SpinnerNumberModel) spnQty.getModel();
        model.setMaximum(formRemaining.get(index));
        model.setValue(1);
        fillRefund();
    }

    private void fillForm(Dispute dispute) {
        selectedDispute = dispute;
        formItems = new ArrayList<>();
        formRemaining = new ArrayList<>();
        cmbItem.removeAllItems();
        spnQty.setValue(1);
        txtRefund.setText("");

        if (dispute.getOrder() == null) {
            setHint(dispute.getCustomerName() + " has no linked order. Link one first (Disputes > Edit Selected).");
            setFormEnabled(false);
            return;
        }

        Long orderId = dispute.getOrder().getOrderId();
        List<OrderItem> items = orderItemRepository.findByOrderOrderId(orderId);
        List<Returns> earlier = returnsRepository.findByOrderOrderId(orderId);

        // Items that are fully returned are left out. "Failed to Return" doesn't count, so it can be tried again
        List<OrderItem> available = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>();
        for (OrderItem item : items) {
            int alreadyReturned = 0;
            for (Returns r : earlier) {
                if (r.getOrderItem() != null
                        && r.getOrderItem().getOrderItemId().equals(item.getOrderItemId())
                        && !FAILED.equals(r.getStatus())) {
                    alreadyReturned += r.getQuantityReturned();
                }
            }
            int left = item.getQuantity() - alreadyReturned;
            if (left > 0) {
                available.add(item);
                remaining.add(left);
            }
        }

        if (available.isEmpty()) {
            setHint(orderLabel(dispute.getOrder()) + " has no items left to return.");
            setFormEnabled(false);
            return;
        }

        // Must be set before addItem, because adding the first item triggers onItemChanged
        formItems = available;
        formRemaining = remaining;
        for (int i = 0; i < available.size(); i++) {
            OrderItem item = available.get(i);
            cmbItem.addItem(item.getProduct().getProductName()
                    + " (x" + remaining.get(i) + " left, " + peso(item.getUnitPrice()) + ")");
        }

        setHint("Customer: " + dispute.getCustomerName() + "<br>Order: " + orderLabel(dispute.getOrder()));
        setFormEnabled(true);
        onItemChanged();
    }

    private void clearForm() {
        selectedDispute = null;
        formItems = new ArrayList<>();
        formRemaining = new ArrayList<>();
        disputeTable.clearSelection();
        cmbItem.removeAllItems();
        spnQty.setValue(1);
        txtRefund.setText("");
        setHint("Click a dispute on the left to start returning.");
        setFormEnabled(false);
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

        List<Long> returnedDisputeIds = new ArrayList<>();
        for (Returns r : allReturns) {
            if (r.getDispute() != null) {
                returnedDisputeIds.add(r.getDispute().getDisputeId());
            }
        }

        // A dispute that already has a return is not listed again
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

        // Newest first
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

        // Rows were rebuilt, so any selection is stale
        clearForm();
    }

    private void startReturn() {
        if (selectedDispute == null || formItems.isEmpty() || cmbItem.getSelectedIndex() < 0) {
            JOptionPane.showMessageDialog(this, "Select a valid dispute first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int index = cmbItem.getSelectedIndex();
        OrderItem item = formItems.get(index);
        int qty = (Integer) spnQty.getValue();
        if (qty > formRemaining.get(index)) {
            JOptionPane.showMessageDialog(this,
                    "Only " + formRemaining.get(index) + " left to return for this item.",
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
        String reason = selectedDispute.getComplaint();
        if (reason.length() > 255) {
            reason = reason.substring(0, 255);
        }

        Returns newReturn = new Returns(selectedDispute.getOrder(), item, reason, qty, refund, TO_RETURN);
        newReturn.setDispute(selectedDispute);

        try {
            returnsRepository.save(newReturn);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Return was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Refund = unit price x quantity, but the user can still edit it
    private void fillRefund() {
        int index = cmbItem.getSelectedIndex();
        if (index < 0 || index >= formItems.size()) {
            return;
        }
        int qty = (Integer) spnQty.getValue();
        BigDecimal amount = formItems.get(index).getUnitPrice().multiply(BigDecimal.valueOf(qty));
        txtRefund.setText(amount.toPlainString());
    }

    private void changeStatus(String newStatus) {
        int row = returnsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a return first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Only "To Return" can be changed, so Returned and Failed are final
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