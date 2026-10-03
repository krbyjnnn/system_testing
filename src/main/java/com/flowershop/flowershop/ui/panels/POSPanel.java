package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.model.ProductInventory;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.ProductRepository;
import com.flowershop.flowershop.service.SaleService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class POSPanel extends JPanel {

    private final User currentUser;
    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final SaleService saleService;

    private JTextField txtSearch;
    private JTable productTable;
    private DefaultTableModel productModel;
    private JSpinner spnQty;

    private JTable cartTable;
    private DefaultTableModel cartModel;
    private JLabel lblTotal;
    private JComboBox<String> cmbMethod;
    private JTextField txtAmount;
    private JTextField txtReference;

    // Products shown in the product table (same order as the rows)
    private List<Product> shownProducts = new ArrayList<>();
    // Total stock per product id
    private Map<Long, Integer> stock = new HashMap<>();
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

        // Top Header
        JLabel lblTitle = new JLabel("Point of Sale");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        // Center: products on the left, cart on the right
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 15, 0));
        centerPanel.setOpaque(false);

        // ---- Left: products ----
        JPanel productPanel = new JPanel(new BorderLayout(0, 10));
        productPanel.setBackground(Color.WHITE);
        productPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
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

        String[] productColumns = {"Code", "Product", "Category", "Price", "Stock"};
        productModel = new DefaultTableModel(productColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        productTable = new JTable(productModel);
        productTable.setRowHeight(28);
        productTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        productTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        addPanel.setOpaque(false);
        spnQty = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        JButton btnAdd = new JButton("Add to Cart");
        btnAdd.addActionListener(e -> addToCart());
        addPanel.add(new JLabel("Qty:"));
        addPanel.add(spnQty);
        addPanel.add(btnAdd);

        productPanel.add(searchPanel, BorderLayout.NORTH);
        productPanel.add(new JScrollPane(productTable), BorderLayout.CENTER);
        productPanel.add(addPanel, BorderLayout.SOUTH);

        // ---- Right: cart and payment ----
        JPanel cartPanel = new JPanel(new BorderLayout(0, 10));
        cartPanel.setBackground(Color.WHITE);
        cartPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
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

        JPanel formPanel = new JPanel(new GridLayout(4, 2, 8, 8));
        formPanel.setOpaque(false);
        lblTotal = new JLabel("₱0.00");
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 20));
        cmbMethod = new JComboBox<>(new String[]{"Cash", "GCash", "Card"});
        txtAmount = new JTextField();
        txtReference = new JTextField();
        formPanel.add(new JLabel("Total:"));
        formPanel.add(lblTotal);
        formPanel.add(new JLabel("Payment Method:"));
        formPanel.add(cmbMethod);
        formPanel.add(new JLabel("Amount Received:"));
        formPanel.add(txtAmount);
        formPanel.add(new JLabel("Reference No. (GCash/Card):"));
        formPanel.add(txtReference);

        JButton btnCheckout = new JButton("Complete Sale");
        btnCheckout.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnCheckout.setBackground(new Color(40, 167, 69));
        btnCheckout.setForeground(Color.WHITE);
        btnCheckout.addActionListener(e -> checkout());

        bottomPanel.add(removePanel, BorderLayout.NORTH);
        bottomPanel.add(formPanel, BorderLayout.CENTER);
        bottomPanel.add(btnCheckout, BorderLayout.SOUTH);

        cartPanel.add(lblCart, BorderLayout.NORTH);
        cartPanel.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        cartPanel.add(bottomPanel, BorderLayout.SOUTH);

        centerPanel.add(productPanel);
        centerPanel.add(cartPanel);
        add(centerPanel, BorderLayout.CENTER);
    }

    // Load active products (and their stock) into the product table
    public void loadProducts() {
        // 1. Add up the stock of every batch, per product
        stock.clear();
        for (ProductInventory batch : productInventoryRepository.findAll()) {
            Long id = batch.getProduct().getProductId();
            int current = stock.containsKey(id) ? stock.get(id) : 0;
            stock.put(id, current + batch.getQuantityOnHand());
        }

        // 2. Fill the table with the products that match the search
        String keyword = txtSearch.getText().trim().toLowerCase();
        shownProducts.clear();
        productModel.setRowCount(0);

        for (Product p : productRepository.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(p.getStatus())) {
                continue;
            }
            boolean matches = p.getProductName().toLowerCase().contains(keyword)
                    || p.getProductCode().toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            shownProducts.add(p);
            productModel.addRow(new Object[]{
                    p.getProductCode(),
                    p.getProductName(),
                    p.getCategory() != null ? p.getCategory().getName() : "N/A",
                    peso(p.getSellingPrice()),
                    stock.containsKey(p.getProductId()) ? stock.get(p.getProductId()) : 0
            });
        }
    }

    private void addToCart() {
        int row = productTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a product first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Product product = shownProducts.get(row);
        int qty = (Integer) spnQty.getValue();
        int available = stock.containsKey(product.getProductId()) ? stock.get(product.getProductId()) : 0;

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
        spnQty.setValue(1);
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
        BigDecimal total = BigDecimal.ZERO;

        for (int i = 0; i < cartProducts.size(); i++) {
            Product p = cartProducts.get(i);
            int qty = cartQuantities.get(i);
            BigDecimal subtotal = p.getSellingPrice().multiply(BigDecimal.valueOf(qty));
            total = total.add(subtotal);

            cartModel.addRow(new Object[]{
                    p.getProductName(),
                    qty,
                    peso(p.getSellingPrice()),
                    peso(subtotal)
            });
        }
        lblTotal.setText(peso(total));
    }

    private void checkout() {
        if (cartProducts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "The cart is empty.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Total of the cart
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < cartProducts.size(); i++) {
            BigDecimal subtotal = cartProducts.get(i).getSellingPrice().multiply(BigDecimal.valueOf(cartQuantities.get(i)));
            total = total.add(subtotal);
        }

        // Check the payment details
        String method = (String) cmbMethod.getSelectedItem();
        String reference = txtReference.getText().trim();
        BigDecimal received;
        try {
            received = new BigDecimal(txtAmount.getText().replace(",", "").trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Enter a valid amount received.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (received.compareTo(total) < 0) {
            JOptionPane.showMessageDialog(this, "Amount received is less than the total.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!method.equals("Cash") && reference.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter the reference number for " + method + ".", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Save the sale
        try {
            String transactionNumber = saleService.completeSale(
                    currentUser, cartProducts, cartQuantities, method, reference);

            JOptionPane.showMessageDialog(this,
                    "Sale completed!\n"
                            + "Transaction: " + transactionNumber + "\n"
                            + "Total: " + peso(total) + "\n"
                            + "Change: " + peso(received.subtract(total)),
                    "Success", JOptionPane.INFORMATION_MESSAGE);

            cartProducts.clear();
            cartQuantities.clear();
            txtAmount.setText("");
            txtReference.setText("");
            refreshCart();
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