package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.model.ProductInventory;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;
import com.flowershop.flowershop.service.SaleService;
import com.flowershop.flowershop.util.ImageHelper;

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
    private JComboBox<String> cmbMethod;
    private JTextField txtAmount;
    private JTextField txtReference;
    private JLabel lblReference;

    // Order type, payment timing and pick-up / delivery details
    private JPanel formPanel;
    private JComboBox<String> cmbType;
    private JComboBox<String> cmbTiming;
    private JTextField txtName;
    private JTextField txtPhone;
    private JTextField txtAddress;
    private JTextField txtFee;
    private JTextField txtNotes;
    private JSpinner spnDate;
    private JLabel lblDate;
    // Form rows, kept so they can be shown or hidden depending on the order type
    private JPanel rowName, rowPhone, rowAddress, rowDate, rowFee, rowNotes;
    private JPanel rowMethod, rowAmount, rowReference;

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

        JButton btnRemove = new JButton("Remove Selected");
        btnRemove.addActionListener(e -> removeFromCart());
        JPanel removePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        removePanel.setOpaque(false);
        removePanel.add(btnRemove);

        // ---- Form fields ----
        lblTotal = new JLabel("₱0.00");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        cmbType = new JComboBox<>(TYPE_LABELS);
        cmbTiming = new JComboBox<>(new String[]{"Pay Now", "Pay Later"});
        cmbMethod = new JComboBox<>(new String[]{"Cash", "GCash", "Card"});
        txtAmount = new JTextField();
        txtReference = new JTextField();
        lblReference = new JLabel("Reference No. (GCash/Card):");

        txtName = new JTextField();
        txtPhone = new JTextField();
        txtAddress = new JTextField();
        txtFee = new JTextField();
        txtNotes = new JTextField();
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
        rowMethod = formRow(new JLabel("Payment Method:"), cmbMethod);
        rowAmount = formRow(new JLabel("Amount Received:"), txtAmount);
        rowReference = formRow(lblReference, txtReference);

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
        formPanel.add(formRow(new JLabel("Payment:"), cmbTiming));
        formPanel.add(rowMethod);
        formPanel.add(rowAmount);
        formPanel.add(rowReference);

        // Scrolls when the delivery fields make the form taller than the space available
        JScrollPane formScroll = new JScrollPane(formPanel);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.getViewport().setOpaque(false);
        formScroll.setOpaque(false);
        formScroll.setPreferredSize(new Dimension(100, 250));
        formScroll.getVerticalScrollBar().setUnitIncrement(16);

        cmbType.addActionListener(e -> onTypeChanged());
        cmbTiming.addActionListener(e -> updateFormVisibility());
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
        JLabel photo = new JLabel(ImageHelper.loadIcon(p.getImagePath(), CARD_PHOTO, CARD_PHOTO), SwingConstants.CENTER);

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
        int qty = 1;
        int available = stockOf(product);

        // Is this product already in the cart?
        int cartIndex = -1;
        for (int i = 0; i < cartProducts.size(); i++) {
            if (cartProducts.get(i).getProductId().equals(product.getProductId())) {
                cartIndex = i;
            }
        }
        int inCart = cartIndex >= 0 ? cartQuantities.get(cartIndex) : 0;

        if (inCart + qty > available) {
            JOptionPane.showMessageDialog(this,
                    "Not enough stock. Available: " + available + ", already in cart: " + inCart,
                    "Insufficient Stock", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (cartIndex >= 0) {
            cartQuantities.set(cartIndex, inCart + qty);
        } else {
            cartProducts.add(product);
            cartQuantities.add(qty);
        }
        refreshCart();
    }

    private void removeFromCart() {
        int row = cartTable.getSelectedRow();
        if (row < 0) {
            return;
        }
        cartProducts.remove(row);
        cartQuantities.remove(row);
        refreshCart();
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

    // Total the customer pays: items + delivery fee
    private void updateTotal() {
        lblTotal.setText(peso(itemsTotal().add(feeForTotal())));
    }

    private LocalDate selectedDate() {
        return ((Date) spnDate.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    // Walk-in is always paid right away, so the Pay Later option is only for pick-up and delivery
    private void onTypeChanged() {
        if (isWalkIn()) {
            cmbTiming.setSelectedItem("Pay Now");
        }
        updateFormVisibility();
    }

    // Shows only the fields that apply to the chosen order type and payment timing
    private void updateFormVisibility() {
        boolean walkIn = isWalkIn();
        boolean delivery = "DELIVERY".equals(orderType());
        boolean payNow = "Pay Now".equals(cmbTiming.getSelectedItem());

        rowName.setVisible(!walkIn);
        rowPhone.setVisible(!walkIn);
        rowDate.setVisible(!walkIn);
        rowNotes.setVisible(!walkIn);
        rowAddress.setVisible(delivery);
        rowFee.setVisible(delivery);
        lblDate.setText(delivery ? "Delivery Date:" : "Pick-up Date:");

        cmbTiming.setEnabled(!walkIn);
        // Nothing is collected now when paying later
        rowMethod.setVisible(payNow);
        rowAmount.setVisible(payNow);
        rowReference.setVisible(payNow);

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
        cmbTiming.setSelectedItem("Pay Now");
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
        boolean payNow = "Pay Now".equals(cmbTiming.getSelectedItem());

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
        String method = (String) cmbMethod.getSelectedItem();
        String reference = txtReference.getText().trim();
        BigDecimal received = total;

        // Payment is only checked when money is collected now
        if (payNow) {
            try {
                received = new BigDecimal(txtAmount.getText().replace(",", "").trim());
            } catch (NumberFormatException e) {
                warn("Enter a valid amount received.");
                return;
            }
            if (received.compareTo(total) < 0) {
                warn("Amount received is less than the total.");
                return;
            }
            if (!method.equals("Cash") && reference.isEmpty()) {
                warn("Enter the reference number for " + method + ".");
                return;
            }
        }

        try {
            String transactionNumber = saleService.completeSale(
                    currentUser, cartProducts, cartQuantities, orderType, payNow, method, reference, details);

            String message = "Sale completed!\n"
                    + "Transaction: " + transactionNumber + "\n"
                    + "Total: " + peso(total);
            if (payNow) {
                message += "\nChange: " + peso(received.subtract(total));
            } else {
                message += "\nPayment: to be collected on " + (delivery ? "delivery" : "pick-up");
            }
            JOptionPane.showMessageDialog(this, message, "Success", JOptionPane.INFORMATION_MESSAGE);

            resetForm();
            loadProducts();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Sale was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            loadProducts();
        }
    }

    private String peso(BigDecimal value) {
        return "₱" + String.format("%,.2f", value);
    }
}