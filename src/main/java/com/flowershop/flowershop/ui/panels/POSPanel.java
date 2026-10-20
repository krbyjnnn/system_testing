package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Delivery;
import com.flowershop.flowershop.model.Order;
import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.model.ProductInventory;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.DeliveryRepository;
import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;
import com.flowershop.flowershop.service.SaleService;
import com.flowershop.flowershop.service.VoidService;
import com.flowershop.flowershop.ui.SpringContext;
import com.flowershop.flowershop.util.ImageHelper;
import com.flowershop.flowershop.util.InputRules;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class POSPanel extends JPanel {

    private static final int CARD_WIDTH = 160;
    private static final int CARD_HEIGHT = 262;
    private static final int CARD_PHOTO = 130;
    private static final int GRID_GAP = 10;

    private static final Color BORDER_COLOR = new Color(220, 224, 230);
    private static final Color OUT_COLOR = new Color(220, 53, 69);
    private static final Color OUT_BACKGROUND = new Color(255, 232, 235);

    // What the cashier sees, and the value saved in orders.order_type (same index in both)
    private static final String[] TYPE_LABELS = {"Walk-in", "Pick-up", "Delivery"};
    private static final String[] TYPE_VALUES = {"WALK-IN", "PICK-UP", "DELIVERY"};

    private final User currentUser;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final SaleService saleService;

    private JTextField txtSearch;
    private JPanel cardGrid;
    private JScrollPane cardScroll;

    private JTable cartTable;
    private DefaultTableModel cartModel;
    private JLabel lblTotal;
    private JLabel lblDown;
    private JLabel lblBalance;
    private JComboBox<String> cmbMethod;
    private JTextField txtAmount;
    private JTextField txtReference;
    private JLabel lblReference;

    // Order type and pick-up / delivery details
    private JPanel formPanel;
    private JComboBox<String> cmbType;
    private JTextField txtName;
    private JTextField txtPhone;
    private JTextField txtAddress;
    private JTextField txtFee;
    private JTextField txtNotes;
    private JSpinner spnDate;
    private JLabel lblDate;
    // Form rows, kept so they can be shown or hidden depending on the order type
    private JPanel rowName, rowPhone, rowAddress, rowDate, rowFee, rowNotes;
    private JPanel rowDown, rowBalance;

    // Total stock per product id
    private Map<Long, Integer> stock = new HashMap<>();
    private Map<Long, CardView> cardViews = new HashMap<>();
    // Cart: the same index in both lists is the same item
    private List<Product> cartProducts = new ArrayList<>();
    private List<Integer> cartQuantities = new ArrayList<>();

    public POSPanel(
            User currentUser,
            ProductRepository productRepository,
            ProductInventoryRepository productInventoryRepository,
            SaleService saleService
    ) {
        this.currentUser = currentUser;
        this.productRepository = productRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.saleService = saleService;

        initUI();
        loadProducts();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("Point of Sale");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        centerPanel.setOpaque(false);

        // ---- Left: search + product cards ----
        JPanel productPanel = new JPanel(new BorderLayout(0, 10));
        productPanel.setBackground(Color.WHITE);
        productPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        txtSearch = new JTextField();
        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> loadProducts());
        txtSearch.addActionListener(e -> loadProducts());
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        searchPanel.add(btnSearch, BorderLayout.EAST);

        cardGrid = new JPanel(new GridLayout(0, 3, GRID_GAP, GRID_GAP));
        cardGrid.setBackground(Color.WHITE);
        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setBackground(Color.WHITE);
        gridHolder.add(cardGrid, BorderLayout.NORTH);

        cardScroll = new JScrollPane(gridHolder);
        cardScroll.setBorder(BorderFactory.createEmptyBorder());
        cardScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        cardScroll.getVerticalScrollBar().setUnitIncrement(20);
        // As many cards per row as fit
        cardScroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                updateColumns();
            }
        });

        productPanel.add(searchPanel, BorderLayout.NORTH);
        productPanel.add(cardScroll, BorderLayout.CENTER);

        centerPanel.add(productPanel);
        centerPanel.add(buildCartPanel());
        add(centerPanel, BorderLayout.CENTER);
    }

    private JPanel buildCartPanel() {
        JPanel cartPanel = new JPanel(new BorderLayout(0, 10));
        cartPanel.setBackground(Color.WHITE);
        cartPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel lblCart = new JLabel("Current Order");
        lblCart.setFont(new Font("Segoe UI", Font.BOLD, 16));

        String[] cartColumns = {"Product", "Qty", "Price", "Subtotal"};
        cartModel = new DefaultTableModel(cartColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(28);
        cartTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        cartTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel bottomPanel = new JPanel(new BorderLayout(0, 10));
        bottomPanel.setOpaque(false);

        JButton btnRemove = new JButton("Void Item");
        btnRemove.setToolTipText("Needs the void passkey of a Manager");
        btnRemove.addActionListener(e -> voidSelectedItem());
        JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        removePanel.setOpaque(false);
        removePanel.add(btnRemove);

        JButton btnPending = new JButton("Pending Orders");
        btnPending.addActionListener(e -> openPendingOrders());
        removePanel.add(Box.createHorizontalStrut(8));
        removePanel.add(btnPending);

        // ---- Form fields ----
        lblTotal = new JLabel("₱0.00");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblDown = new JLabel("₱0.00");
        lblDown.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblBalance = new JLabel("₱0.00");
        lblBalance.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cmbType = new JComboBox<>(TYPE_LABELS);
        cmbMethod = new JComboBox<>(new String[]{"Cash", "GCash", "Card"});
        txtAmount = new JTextField();
        txtReference = new JTextField();
        lblReference = new JLabel("Reference No. (GCash/Card):");

        txtName = new JTextField();
        txtPhone = new JTextField();
        txtAddress = new JTextField();
        txtFee = new JTextField();
        txtNotes = new JTextField();
        InputRules.decimalOnly(txtAmount);
        InputRules.digitsOnly(txtPhone, 11);
        InputRules.decimalOnly(txtFee);
        lblDate = new JLabel("Date:");
        // Date picker: today or later (the time part is ignored)
        Date todayStart = Date.from(LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant());
        spnDate = new JSpinner(new SpinnerDateModel(new Date(), todayStart, null, Calendar.DAY_OF_MONTH));
        spnDate.setEditor(new JSpinner.DateEditor(spnDate, "yyyy-MM-dd"));

        // ---- Form rows (BoxLayout so hidden rows take no space) ----
        rowName = formRow(new JLabel("Name:"), txtName);
        rowPhone = formRow(new JLabel("Phone:"), txtPhone);
        rowAddress = formRow(new JLabel("Address:"), txtAddress);
        rowDate = formRow(lblDate, spnDate);
        rowFee = formRow(new JLabel("Delivery Fee:"), txtFee);
        rowNotes = formRow(new JLabel("Notes:"), txtNotes);
        rowDown = formRow(new JLabel("Down Payment (30%):"), lblDown);
        rowBalance = formRow(new JLabel("Balance:"), lblBalance);

        formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setOpaque(false);
        formPanel.add(formRow(new JLabel("Order Type:"), cmbType));
        formPanel.add(rowName);
        formPanel.add(rowPhone);
        formPanel.add(rowAddress);
        formPanel.add(rowDate);
        formPanel.add(rowFee);
        formPanel.add(rowNotes);
        formPanel.add(formRow(new JLabel("Total:"), lblTotal));
        formPanel.add(rowDown);
        formPanel.add(rowBalance);
        formPanel.add(formRow(new JLabel("Payment Method:"), cmbMethod));
        formPanel.add(formRow(new JLabel("Amount Received:"), txtAmount));
        formPanel.add(formRow(lblReference, txtReference));

        // Scrolls when the delivery fields make the form taller than the space available
        JScrollPane formScroll = new JScrollPane(formPanel);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getViewport().setOpaque(false);
        formScroll.setOpaque(false);
        formScroll.setPreferredSize(new Dimension(100, 250));
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        cmbType.addActionListener(e -> updateFormVisibility());
        cmbMethod.addActionListener(e -> updateReferenceField());
        // The delivery fee is part of the total, so keep the total label in sync while typing
        txtFee.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateTotal();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateTotal();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateTotal();
            }
        });
        updateReferenceField();
        updateFormVisibility();

        JButton btnCheckout = new JButton("Complete Sale");
        btnCheckout.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnCheckout.setBackground(new Color(40, 167, 69));
        btnCheckout.setForeground(Color.WHITE);
        btnCheckout.addActionListener(e -> checkout());

        bottomPanel.add(removePanel, BorderLayout.NORTH);
        bottomPanel.add(formScroll, BorderLayout.CENTER);
        bottomPanel.add(btnCheckout, BorderLayout.SOUTH);

        cartPanel.add(lblCart, BorderLayout.NORTH);
        cartPanel.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        cartPanel.add(bottomPanel, BorderLayout.SOUTH);
        return cartPanel;
    }

    // One "label | field" line of the form; max height is fixed so rows are not stretched
    private JPanel formRow(JComponent label, JComponent field) {
        JPanel row = new JPanel(new GridLayout(1, 2, 8, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        row.add(label);
        row.add(field);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        return row;
    }

    private void updateColumns() {
        int width = cardScroll.getViewport().getWidth();
        int columns = Math.max(1, width / (CARD_WIDTH + GRID_GAP));
        GridLayout layout = (GridLayout) cardGrid.getLayout();
        if (layout.getColumns() != columns) {
            layout.setColumns(columns);
            cardGrid.revalidate();
        }
    }

    // Load active products (and their stock) as cards
    public void loadProducts() {
        stock.clear();
        for (ProductInventory batch : productInventoryRepository.findAll()) {
            Long id = batch.getProduct().getProductId();
            int current = stock.containsKey(id) ? stock.get(id) : 0;
            stock.put(id, current + batch.getQuantityOnHand());
        }

        String keyword = txtSearch.getText().trim().toLowerCase();
        cardGrid.removeAll();
        cardViews.clear();

        for (Product p : productRepository.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(p.getStatus())) {
                continue;
            }
            boolean matches = p.getProductName().toLowerCase().contains(keyword)
                    || p.getProductCode().toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            cardGrid.add(createCard(p));
        }

        updateColumns();
        cardGrid.revalidate();
        cardGrid.repaint();
    }

    private static class CardView {
        final Product product;
        final JPanel card;
        final JLabel stockLabel;

        CardView(Product product, JPanel card, JLabel stockLabel) {
            this.product = product;
            this.card = card;
            this.stockLabel = stockLabel;
        }
    }

    private JPanel createCard(Product p) {
        JLabel photo = new JLabel(ImageHelper.loadIcon(p, CARD_PHOTO, CARD_PHOTO), SwingConstants.CENTER);

        JLabel name = new JLabel(p.getProductName(), SwingConstants.CENTER);
        name.setFont(new Font("Segoe UI", Font.BOLD, 13));
        name.setToolTipText(p.getProductName());

        JLabel category = new JLabel(p.getCategory() != null ? p.getCategory().getName() : "N/A", SwingConstants.CENTER);
        category.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        category.setForeground(new Color(110, 110, 110));

        JLabel price = new JLabel(peso(p.getSellingPrice()), SwingConstants.CENTER);
        price.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        price.setForeground(new Color(40, 167, 69));

        JLabel stockLabel = new JLabel(" ", SwingConstants.CENTER);
        stockLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel text = new JPanel(new GridLayout(4, 1, 0, 2));
        text.setOpaque(false);
        text.add(name);
        text.add(category);
        text.add(price);
        text.add(stockLabel);

        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setPreferredSize(new Dimension(CARD_WIDTH, CARD_HEIGHT));
        card.add(photo, BorderLayout.CENTER);
        card.add(text, BorderLayout.SOUTH);

        MouseAdapter click = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                addToCart(p);
            }
        };
        card.addMouseListener(click);
        photo.addMouseListener(click);
        name.addMouseListener(click);
        category.addMouseListener(click);
        price.addMouseListener(click);
        stockLabel.addMouseListener(click);
        text.addMouseListener(click);

        CardView view = new CardView(p, card, stockLabel);
        cardViews.put(p.getProductId(), view);
        updateCard(view);
        return card;
    }

    // Pieces still available: stock minus what is already in the cart
    private int remaining(Product p) {
        int inCart = 0;
        for (int i = 0; i < cartProducts.size(); i++) {
            if (cartProducts.get(i).getProductId().equals(p.getProductId())) {
                inCart = cartQuantities.get(i);
            }
        }
        return stockOf(p) - inCart;
    }

    // Red card when nothing is left, otherwise shows how many are left
    private void updateCard(CardView view) {
        int left = remaining(view.product);
        boolean out = left <= 0;

        view.card.setBackground(out ? OUT_BACKGROUND : Color.WHITE);
        view.card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(out ? OUT_COLOR : BORDER_COLOR, 2),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        view.card.setCursor(Cursor.getPredefinedCursor(out ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        view.stockLabel.setText(out ? "Out of stock" : "Stock: " + left);
        view.stockLabel.setForeground(out ? OUT_COLOR : new Color(90, 90, 90));
    }

    private void updateAllCards() {
        for (CardView view : cardViews.values()) {
            updateCard(view);
        }
    }

    private int stockOf(Product p) {
        return stock.containsKey(p.getProductId()) ? stock.get(p.getProductId()) : 0;
    }

    // Clicking a card adds one piece of that product to the cart
    private void addToCart(Product product) {
        int available = stockOf(product);

        int cartIndex = -1;
        for (int i = 0; i < cartProducts.size(); i++) {
            if (cartProducts.get(i).getProductId().equals(product.getProductId())) {
                cartIndex = i;
            }
        }
        int inCart = cartIndex >= 0 ? cartQuantities.get(cartIndex) : 0;
        int left = available - inCart;

        if (left <= 0) {
            JOptionPane.showMessageDialog(this,
                    "Not enough stock. Available: " + available + ", already in cart: " + inCart,
                    "Insufficient Stock", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JSpinner spnQty = new JSpinner(new SpinnerNumberModel(1, 1, left, 1));
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Add " + product.getProductName() + " (" + peso(product.getSellingPrice()) + ") to the order?"));
        form.add(new JLabel("Quantity (max " + left + "):"));
        form.add(spnQty);

        int choice = JOptionPane.showConfirmDialog(this, form, "Confirm Add",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        int qty = (int) spnQty.getValue();

        if (cartIndex >= 0) {
            cartQuantities.set(cartIndex, inCart + qty);
        } else {
            cartProducts.add(product);
            cartQuantities.add(qty);
        }
        refreshCart();
}

    // Voiding takes a line out of the cart. Like a cashier system, it needs a supervisor:
    // a Manager enters their void passkey, and the void is written to the audit trail.
    private void voidSelectedItem() {
        int row = cartTable.getSelectedRow();
        if (row < 0 || row >= cartProducts.size()) {
            warn("Select the item in the cart that you want to void.");
            return;
        }

        Product product = cartProducts.get(row);
        int qty = cartQuantities.get(row);
        BigDecimal amount = product.getSellingPrice().multiply(BigDecimal.valueOf(qty));

        JPasswordField txtApproverPassword = new JPasswordField(16);
        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Void: " + product.getProductName() + " x" + qty + "  (" + String.format("₱%,.2f", amount) + ")"));
        form.add(new JLabel("A Manager must approve this void."));
        form.add(new JLabel("Manager passkey"));
        form.add(txtApproverPassword);

        int choice = JOptionPane.showConfirmDialog(this, form, "Void Approval",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }

        VoidService voidService = SpringContext.getBean(VoidService.class);
        try {
            User approver = voidService.authorize(currentUser, new String(txtApproverPassword.getPassword()));
            voidService.logVoid(currentUser, approver, product.getProductName(), qty, amount);

            cartProducts.remove(row);
            cartQuantities.remove(row);
            refreshCart();
        } catch (IllegalArgumentException | SecurityException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Void Denied", JOptionPane.ERROR_MESSAGE);
        } finally {
            txtApproverPassword.setText("");
        }
    }

    private void refreshCart() {
        cartModel.setRowCount(0);

        for (int i = 0; i < cartProducts.size(); i++) {
            Product p = cartProducts.get(i);
            int qty = cartQuantities.get(i);
            BigDecimal subtotal = p.getSellingPrice().multiply(BigDecimal.valueOf(qty));

            cartModel.addRow(new Object[]{
                    p.getProductName(),
                    qty,
                    peso(p.getSellingPrice()),
                    peso(subtotal)
            });
        }
        updateTotal();
        updateAllCards();
    }

    // ---- Order type, delivery fee and total ----

    private String orderType() {
        return TYPE_VALUES[cmbType.getSelectedIndex()];
    }

    private boolean isWalkIn() {
        return "WALK-IN".equals(orderType());
    }

    private BigDecimal itemsTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < cartProducts.size(); i++) {
            total = total.add(cartProducts.get(i).getSellingPrice().multiply(BigDecimal.valueOf(cartQuantities.get(i))));
        }
        return total;
    }

    // Throws NumberFormatException when the text is not a number; empty means no fee
    private BigDecimal parseFee() {
        String text = txtFee.getText().replace(",", "").trim();
        return text.isEmpty() ? BigDecimal.ZERO : new BigDecimal(text);
    }

    // Only delivery orders have a fee; a half-typed or invalid fee counts as 0 on screen
    private BigDecimal feeForTotal() {
        if (!"DELIVERY".equals(orderType())) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal fee = parseFee();
            return fee.signum() < 0 ? BigDecimal.ZERO : fee;
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    // Total the customer pays: items + delivery fee, plus the 30% down payment and the balance
    private void updateTotal() {
        BigDecimal total = itemsTotal().add(feeForTotal());
        BigDecimal down = SaleService.downPaymentOf(total);
        lblTotal.setText(peso(total));
        lblDown.setText(peso(down));
        lblBalance.setText(peso(total.subtract(down)));
    }

    private LocalDate selectedDate() {
        return ((Date) spnDate.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // Shows only the fields that apply to the chosen order type
    private void updateFormVisibility() {
        boolean walkIn = isWalkIn();
        boolean delivery = "DELIVERY".equals(orderType());

        rowName.setVisible(!walkIn);
        rowPhone.setVisible(!walkIn);
        rowDate.setVisible(!walkIn);
        rowNotes.setVisible(!walkIn);
        rowAddress.setVisible(delivery);
        rowFee.setVisible(delivery);
        rowDown.setVisible(!walkIn);
        rowBalance.setVisible(!walkIn);
        lblDate.setText(delivery ? "Delivery Date:" : "Pick-up Date:");

        updateTotal();
        formPanel.revalidate();
        formPanel.repaint();
    }

    // Cash has no reference number, so the field is greyed out
    private void updateReferenceField() {
        boolean needsReference = !"Cash".equals(cmbMethod.getSelectedItem());
        txtReference.setEnabled(needsReference);
        lblReference.setEnabled(needsReference);
        if (!needsReference) {
            txtReference.setText("");
        }
    }

    private void warn(String message) {
        JOptionPane.showMessageDialog(this, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    // Back to a clean walk-in form after a sale is saved
    private void resetForm() {
        cartProducts.clear();
        cartQuantities.clear();
        txtAmount.setText("");
        txtReference.setText("");
        txtName.setText("");
        txtPhone.setText("");
        txtAddress.setText("");
        txtFee.setText("");
        txtNotes.setText("");
        spnDate.setValue(new Date());
        cmbType.setSelectedIndex(0);
        refreshCart();
    }

    private void checkout() {
        if (cartProducts.isEmpty()) {
            warn("The cart is empty.");
            return;
        }

        String orderType = orderType();
        boolean walkIn = isWalkIn();
        boolean delivery = "DELIVERY".equals(orderType);

        // Pick-up and delivery need contact details (and an address for delivery)
        SaleService.DeliveryDetails details = null;
        BigDecimal fee = BigDecimal.ZERO;
        if (!walkIn) {
            String name = txtName.getText().trim();
            String phone = txtPhone.getText().trim();
            String address = txtAddress.getText().trim();
            String notes = txtNotes.getText().trim();
            LocalDate date = selectedDate();

            if (name.isEmpty() || phone.isEmpty()) {
                warn("Enter the customer's name and phone number.");
                return;
            }
            if (delivery && address.isEmpty()) {
                warn("Enter the delivery address.");
                return;
            }
            if (date.isBefore(LocalDate.now())) {
                warn("The date cannot be in the past.");
                return;
            }
            if (delivery) {
                try {
                    fee = parseFee();
                } catch (NumberFormatException e) {
                    warn("Enter a valid delivery fee.");
                    return;
                }
                if (fee.signum() < 0) {
                    warn("The delivery fee cannot be negative.");
                    return;
                }
            }
            details = new SaleService.DeliveryDetails(name, phone,
                    delivery ? address : "Store pick-up", date, fee, notes.isEmpty() ? null : notes);
        }

        BigDecimal total = itemsTotal().add(fee);
        // Walk-in pays everything now; pick-up and delivery pay the 30% down payment now
        BigDecimal dueNow = walkIn ? total : SaleService.downPaymentOf(total);
        String method = (String) cmbMethod.getSelectedItem();
        String reference = txtReference.getText().trim();

        BigDecimal received;
        try {
            received = new BigDecimal(txtAmount.getText().replace(",", "").trim());
        } catch (NumberFormatException e) {
            warn("Enter a valid amount received.");
            return;
        }
        if (received.compareTo(dueNow) < 0) {
            warn(walkIn ? "Amount received is less than the total."
                    : "Amount received is less than the down payment of " + peso(dueNow) + ".");
            return;
        }
        if (!method.equals("Cash") && reference.isEmpty()) {
            warn("Enter the reference number for " + method + ".");
            return;
        }

        try {
            String transactionNumber = saleService.completeSale(
                    currentUser, cartProducts, cartQuantities, orderType, method, reference, details);

            String message = "Sale completed!\n"
                    + "Transaction: " + transactionNumber + "\n"
                    + "Total: " + peso(total);
            if (!walkIn) {
                message += "\nDown payment: " + peso(dueNow)
                        + "\nBalance: " + peso(total.subtract(dueNow))
                        + " (to be paid on " + (delivery ? "delivery" : "pick-up") + ")";
            }
            message += "\nChange: " + peso(received.subtract(dueNow));
            JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);

            resetForm();
            loadProducts();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Sale was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            loadProducts();
        }
    }

    private void openPendingOrders() {
        DefaultTableModel model = new DefaultTableModel(
                new String[]{"Transaction No.", "Date & Time", "Type", "Total", "Paid", "Balance",
                        "Name", "Phone", "Address", "Date", "Notes"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(28);
        List<Order> pending = new ArrayList<>();
        List<BigDecimal> balances = new ArrayList<>();

        JComboBox<String> pendingMethod = new JComboBox<>(new String[]{"Cash", "GCash", "Card"});
        JTextField pendingAmount = new JTextField();
        InputRules.decimalOnly(pendingAmount);
        JTextField pendingReference = new JTextField();
        JLabel pendingRefLabel = new JLabel("Reference No. (GCash/Card):");
        JLabel pendingBalance = new JLabel("₱0.00");
        pendingBalance.setFont(new Font("Segoe UI", Font.BOLD, 20));
        pendingReference.setEnabled(false);
        pendingRefLabel.setEnabled(false);

        pendingMethod.addActionListener(e -> {
            boolean needsReference = !"Cash".equals(pendingMethod.getSelectedItem());
            pendingReference.setEnabled(needsReference);
            pendingRefLabel.setEnabled(needsReference);
            if (!needsReference) {
                pendingReference.setText("");
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0 && row < balances.size()) {
                pendingBalance.setText(peso(balances.get(row)));
            } else {
                pendingBalance.setText("₱0.00");
            }
        });

        loadPending(model, pending, balances);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
        form.add(new JLabel("Balance Due:"));
        form.add(pendingBalance);
        form.add(new JLabel("Payment Method:"));
        form.add(pendingMethod);
        form.add(new JLabel("Amount Received:"));
        form.add(pendingAmount);
        form.add(pendingRefLabel);
        form.add(pendingReference);

        JButton btnComplete = new JButton("Collect Balance & Complete");
        btnComplete.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnComplete.setBackground(new Color(40, 167, 69));
        btnComplete.setForeground(Color.WHITE);
        btnComplete.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                warn("Select an order first.");
                return;
            }
            boolean done = completePending(pending.get(row), balances.get(row),
                    (String) pendingMethod.getSelectedItem(), pendingAmount.getText(), pendingReference.getText().trim());
            if (done) {
                pendingAmount.setText("");
                pendingReference.setText("");
                pendingMethod.setSelectedIndex(0);
                loadPending(model, pending, balances);
                pendingBalance.setText("₱0.00");
            }
        });

        JPanel bottom = new JPanel(new BorderLayout(0, 10));
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        bottom.add(form, BorderLayout.NORTH);
        bottom.add(btnComplete, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Pending Orders", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(0, 10));
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);
        dialog.add(bottom, BorderLayout.SOUTH);
        dialog.setSize(1300, 560);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void loadPending(DefaultTableModel model, List<Order> pending, List<BigDecimal> balances) {
        pending.clear();
        balances.clear();
        pending.addAll(SpringContext.getBean(OrderRepository.class).findByOrderStatus("PENDING"));
        DeliveryRepository deliveryRepository = SpringContext.getBean(DeliveryRepository.class);
        model.setRowCount(0);
        for (Order o : pending) {
            BigDecimal balance = saleService.balanceDue(o);
            balances.add(balance);

            List<Delivery> deliveries = deliveryRepository.findByOrderOrderId(o.getOrderId());
            Delivery d = deliveries.isEmpty() ? null : deliveries.get(0);
            model.addRow(new Object[]{
                    o.getTransactionNumber(),
                    o.getOrderDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    o.getOrderType(),
                    peso(o.getTotalAmount()),
                    peso(o.getTotalAmount().subtract(balance)),
                    peso(balance),
                    d != null ? d.getRecipientName() : "",
                    d != null ? d.getRecipientPhone() : "",
                    d != null ? d.getDeliveryAddress() : "",
                    d != null ? d.getDeliveryDate() : "",
                    d != null && d.getNotes() != null ? d.getNotes() : ""
            });
        }
    }

    private boolean completePending(Order order, BigDecimal balance, String method, String amountText, String reference) {
        BigDecimal received;
        try {
            received = new BigDecimal(amountText.replace(",", "").trim());
        } catch (NumberFormatException e) {
            warn("Enter a valid amount received.");
            return false;
        }
        if (received.compareTo(balance) < 0) {
            warn("Amount received is less than the balance of " + peso(balance) + ".");
            return false;
        }
        if (!method.equals("Cash") && reference.isEmpty()) {
            warn("Enter the reference number for " + method + ".");
            return false;
        }

        try {
            saleService.completePendingOrder(currentUser, order.getOrderId(), method, reference);
            JOptionPane.showMessageDialog(this,
                    "Order " + order.getTransactionNumber() + " completed.\n"
                            + "Total: " + peso(order.getTotalAmount()) + "\n"
                            + "Balance paid: " + peso(balance) + "\n"
                            + "Change: " + peso(received.subtract(balance)),
                    "Success", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Order was not updated: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private String peso(BigDecimal value) {
        return "₱" + String.format("%,.2f", value);
    }
}