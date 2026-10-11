package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Category;
import com.flowershop.flowershop.model.Component;
import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.model.ProductInventory;
import com.flowershop.flowershop.model.Recipe;
import com.flowershop.flowershop.model.Supplier;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.*;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.ProductionService;
import com.flowershop.flowershop.ui.SpringContext;
import com.flowershop.flowershop.util.ImageHelper;
import com.flowershop.flowershop.util.InputRules;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductPanel extends JPanel {

    private static final String TYPE_PRODUCT = "Product";
    private static final String CODE_PREFIX = "FLW-";
    private static final String CARD_HINT = "hint";
    private static final String CARD_RECIPE = "recipe";
    private static final String CARD_STOCK = "stock";
    private static final int PHOTO_SIZE = 160;

    private final ProductRepository productRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final CategoryRepository categoryRepository;
    private final SupplierRepository supplierRepository;
    private final ComponentRepository componentRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final RecipeRepository recipeRepository;
    private final ProductionService productionService;
    private final User currentUser;
    private final boolean readOnly; // view only: the add/edit, recipe, stock and photo cards are not shown
    private final AuditService auditService = SpringContext.getBean(AuditService.class);

    // Table
    private JTextField txtSearch;
    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
    private List<Product> shownProducts = new ArrayList<>();

    // Product form (top)
    private JLabel lblFormTitle;
    private JTextField txtCode;
    private JTextField txtName;
    private JComboBox<String> cmbCategory;
    private JTextField txtPrice;
    private JComboBox<String> cmbStatus;
    private JCheckBox chkComposite;
    private JButton btnSave;
    private JLabel lblMessage;
    private List<Category> productCategories = new ArrayList<>();
    private Product editing = null; // null = adding a new product

    // Middle area: shows a hint, the recipe, or the stock form
    private CardLayout detailLayout;
    private JPanel detailPanel;

    private JPanel photoCard;
    private JLabel lblPhoto;
    private JLabel lblPhotoMessage;

    // Recipe (for products made from components)
    private JLabel lblRecipeTitle;
    private JComboBox<String> cmbComponent;
    private JSpinner spnRecipeQty;
    private JTable recipeTable;
    private DefaultTableModel recipeTableModel;
    private JLabel lblRecipeMessage;
    private List<Component> components = new ArrayList<>();
    private List<Recipe> recipeLines = new ArrayList<>();

    // Make (arrange pieces now and deduct the components)
    private JSpinner spnMakeQty;
    private JLabel lblMakeMessage;

    // Stock form (for products bought already finished)
    private JLabel lblStockTitle;
    private JComboBox<String> cmbSupplier;
    private JSpinner spnQty;
    private JSpinner spnReorder;
    private JTextField txtCost;
    private JLabel lblStockMessage;
    private List<Supplier> suppliers = new ArrayList<>();

    public ProductPanel(
            ProductRepository productRepository,
            ProductInventoryRepository productInventoryRepository,
            CategoryRepository categoryRepository,
            SupplierRepository supplierRepository,
            ComponentRepository componentRepository,
            ComponentInventoryRepository componentInventoryRepository,
            RecipeRepository recipeRepository,
            ProductionService productionService,
            User currentUser,
            boolean readOnly
    ) {
        this.readOnly = readOnly;
        this.productRepository = productRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.categoryRepository = categoryRepository;
        this.supplierRepository = supplierRepository;
        this.componentRepository = componentRepository;
        this.componentInventoryRepository = componentInventoryRepository;
        this.recipeRepository = recipeRepository;
        this.productionService = productionService;
        this.currentUser = currentUser;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        // ---------------- Product form ----------------
        lblFormTitle = new JLabel("Add Product");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        // The code is generated by the system, so it can't be typed in
        txtCode = new JTextField(nextProductCode());
        txtCode.setEditable(false);
        txtName = new JTextField();
        cmbCategory = new JComboBox<>();
        txtPrice = new JTextField();
        InputRules.decimalOnly(txtPrice);
        cmbStatus = new JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
        chkComposite = new JCheckBox("Made from components");

        JPanel fields = new JPanel(new GridLayout(2, 6, 8, 2));
        fields.setOpaque(false);
        fields.add(new JLabel("Code"));
        fields.add(new JLabel("Name"));
        fields.add(new JLabel("Category"));
        fields.add(new JLabel("Selling Price"));
        fields.add(new JLabel("Status"));
        fields.add(new JLabel("Composite"));
        fields.add(txtCode);
        fields.add(txtName);
        fields.add(cmbCategory);
        fields.add(txtPrice);
        fields.add(cmbStatus);
        fields.add(chkComposite);

        btnSave = new JButton("Add Product");
        btnSave.addActionListener(e -> saveProduct());
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

        // ---------------- Hint card (nothing selected) ----------------
        JLabel lblHint = new JLabel("Select a product in the table to set up its recipe or add stock.");
        lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JPanel hintCard = createCard();
        hintCard.add(lblHint, BorderLayout.CENTER);

        // ---------------- Recipe card (products made from components) ----------------
        lblRecipeTitle = new JLabel("Recipe");
        lblRecipeTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        cmbComponent = new JComboBox<>();
        cmbComponent.setPreferredSize(new Dimension(220, 26));
        spnRecipeQty = new JSpinner(new SpinnerNumberModel(1, 1, 99999, 1));
        spnRecipeQty.setPreferredSize(new Dimension(80, 26));

        JButton btnAddRecipe = new JButton("Add to Recipe");
        btnAddRecipe.addActionListener(e -> addRecipeLine());
        JButton btnRemoveRecipe = new JButton("Remove Selected");
        btnRemoveRecipe.addActionListener(e -> removeRecipeLine());
        lblRecipeMessage = new JLabel(" ");
        lblRecipeMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel recipeControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        recipeControls.setOpaque(false);
        recipeControls.add(new JLabel("Component"));
        recipeControls.add(cmbComponent);
        recipeControls.add(new JLabel("Quantity"));
        recipeControls.add(spnRecipeQty);
        recipeControls.add(btnAddRecipe);
        recipeControls.add(btnRemoveRecipe);
        recipeControls.add(lblRecipeMessage);

        recipeTableModel = new DefaultTableModel(new String[]{"Component", "Quantity", "Unit"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        recipeTable = new JTable(recipeTableModel);
        recipeTable.setRowHeight(24);
        recipeTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        recipeTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        recipeTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane recipeScroll = new JScrollPane(recipeTable);
        recipeScroll.setPreferredSize(new Dimension(0, 90));

        JPanel recipeBody = new JPanel(new BorderLayout(0, 6));
        recipeBody.setOpaque(false);
        recipeBody.add(recipeControls, BorderLayout.NORTH);
        recipeBody.add(recipeScroll, BorderLayout.CENTER);

        // Make row: arranging pieces uses up the components right away
        spnMakeQty = new JSpinner(new SpinnerNumberModel(1, 1, 99999, 1));
        spnMakeQty.setPreferredSize(new Dimension(80, 26));
        JButton btnMake = new JButton("Make Product");
        btnMake.addActionListener(e -> makeProduct());
        lblMakeMessage = new JLabel(" ");
        lblMakeMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel makeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        makeRow.setOpaque(false);
        makeRow.add(new JLabel("Arrange"));
        makeRow.add(spnMakeQty);
        makeRow.add(new JLabel("piece(s) now (uses up the components)"));
        makeRow.add(btnMake);
        makeRow.add(lblMakeMessage);

        JPanel recipeCard = createCard();
        recipeCard.add(lblRecipeTitle, BorderLayout.NORTH);
        recipeCard.add(recipeBody, BorderLayout.CENTER);
        recipeCard.add(makeRow, BorderLayout.SOUTH);

        // ---------------- Stock card (products bought already finished) ----------------
        lblStockTitle = new JLabel("Add Stock");
        lblStockTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        cmbSupplier = new JComboBox<>();
        spnQty = new JSpinner(new SpinnerNumberModel(1, 1, 99999, 1));
        spnReorder = new JSpinner(new SpinnerNumberModel(5, 0, 99999, 1));
        txtCost = new JTextField();
        InputRules.decimalOnly(txtCost);

        JPanel stockFields = new JPanel(new GridLayout(2, 6, 8, 2));
        stockFields.setOpaque(false);
        stockFields.add(new JLabel("Supplier"));
        stockFields.add(new JLabel("Quantity"));
        stockFields.add(new JLabel("Reorder Level"));
        stockFields.add(new JLabel("Unit Cost"));
        stockFields.add(new JLabel());
        stockFields.add(new JLabel());
        stockFields.add(cmbSupplier);
        stockFields.add(spnQty);
        stockFields.add(spnReorder);
        stockFields.add(txtCost);
        stockFields.add(new JLabel());
        stockFields.add(new JLabel());

        JButton btnAddStock = new JButton("Add Stock");
        btnAddStock.addActionListener(e -> addStock());
        lblStockMessage = new JLabel(" ");
        lblStockMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel stockButtonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        stockButtonRow.setOpaque(false);
        stockButtonRow.add(btnAddStock);
        stockButtonRow.add(lblStockMessage);

        JPanel stockCard = createCard();
        stockCard.add(lblStockTitle, BorderLayout.NORTH);
        stockCard.add(stockFields, BorderLayout.CENTER);
        stockCard.add(stockButtonRow, BorderLayout.SOUTH);

        // ---------------- Photo card (shown beside the recipe or stock form) ----------------
        lblPhoto = new JLabel(ImageHelper.placeholder(PHOTO_SIZE, PHOTO_SIZE));
        lblPhoto.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230), 1));
        JPanel previewWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        previewWrap.setOpaque(false);
        previewWrap.add(lblPhoto);

        JButton btnUploadPhoto = new JButton("Upload Photo");
        btnUploadPhoto.addActionListener(e -> uploadPhoto());
        JButton btnRemovePhoto = new JButton("Remove Photo");
        btnRemovePhoto.addActionListener(e -> removePhoto());
        JPanel photoButtons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        photoButtons.setOpaque(false);
        photoButtons.add(btnUploadPhoto);
        photoButtons.add(btnRemovePhoto);

        lblPhotoMessage = new JLabel(" ", SwingConstants.CENTER);
        lblPhotoMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel photoSouth = new JPanel(new GridLayout(2, 1, 0, 2));
        photoSouth.setOpaque(false);
        photoSouth.add(photoButtons);
        photoSouth.add(lblPhotoMessage);

        photoCard = createCard();
        photoCard.add(previewWrap, BorderLayout.CENTER);
        photoCard.add(photoSouth, BorderLayout.SOUTH);
        photoCard.setVisible(false);

        // ---------------- Middle area: one card at a time ----------------
        detailLayout = new CardLayout();
        detailPanel = new JPanel(detailLayout);
        detailPanel.setOpaque(false);
        detailPanel.add(hintCard, CARD_HINT);
        detailPanel.add(recipeCard, CARD_RECIPE);
        detailPanel.add(stockCard, CARD_STOCK);
        detailLayout.show(detailPanel, CARD_HINT);

        JPanel detailRow = new JPanel(new BorderLayout(10, 0));
        detailRow.setOpaque(false);
        detailRow.add(detailPanel, BorderLayout.CENTER);
        detailRow.add(photoCard, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(formCard, BorderLayout.NORTH);
        top.add(detailRow, BorderLayout.CENTER);

        // ---------------- Search + table ----------------
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

        String[] columns = {"Code", "Product", "Category", "Price", "Available", "Status"};
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
        // Clicking a row loads it into the form above so it can be edited
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                fillForm();
            }
        });

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(searchPanel, BorderLayout.NORTH);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(lblSummary, BorderLayout.SOUTH);

        if (!readOnly) {
            add(top, BorderLayout.NORTH);
        }
        add(center, BorderLayout.CENTER);
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

    // ---------------------------------------------------------------- Data

    public void loadData() {
        loadDropdowns();

        // 1. Stock of the product itself (finished pieces: made here or bought ready-made)
        Map<Long, Integer> quantity = new HashMap<>();
        Map<Long, Integer> reorder = new HashMap<>();
        for (ProductInventory batch : productInventoryRepository.findAll()) {
            Long id = batch.getProduct().getProductId();
            int qty = quantity.containsKey(id) ? quantity.get(id) : 0;
            quantity.put(id, qty + batch.getQuantityOnHand());

            int level = reorder.containsKey(id) ? reorder.get(id) : 0;
            reorder.put(id, Math.max(level, batch.getReorderLevel()));
        }

        // 2. Usable component stock (expired batches are not counted), to work out how many more can be made
        LocalDate today = LocalDate.now();
        Map<Long, Integer> componentStock = new HashMap<>();
        for (ComponentInventory batch : componentInventoryRepository.findAll()) {
            if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)) {
                continue;
            }
            Long id = batch.getComponent().getComponentId();
            int qty = componentStock.containsKey(id) ? componentStock.get(id) : 0;
            componentStock.put(id, qty + batch.getQuantityOnHand());
        }

        // 3. Recipe lines grouped by product
        Map<Long, List<Recipe>> recipes = new HashMap<>();
        for (Recipe r : recipeRepository.findAll()) {
            Long id = r.getProduct().getProductId();
            if (!recipes.containsKey(id)) {
                recipes.put(id, new ArrayList<>());
            }
            recipes.get(id).add(r);
        }

        // 4. Fill the table
        String keyword = txtSearch.getText().trim().toLowerCase();
        shownProducts.clear();
        tableModel.setRowCount(0);
        int total = 0;
        int low = 0;
        int out = 0;

        for (Product p : productRepository.findAll()) {
            boolean matches = p.getProductName().toLowerCase().contains(keyword)
                    || p.getProductCode().toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            int qty = quantity.containsKey(p.getProductId()) ? quantity.get(p.getProductId()) : 0;
            int level = reorder.containsKey(p.getProductId()) ? reorder.get(p.getProductId()) : 0;
            String available;
            String status;

            if (Boolean.TRUE.equals(p.getIsComposite())) {
                List<Recipe> lines = recipes.get(p.getProductId());
                if (lines == null || lines.isEmpty()) {
                    available = String.valueOf(qty);
                    status = "No Recipe";
                } else {
                    int canMake = canMake(lines, componentStock);
                    available = qty + " ready";
                    if (qty == 0 && canMake == 0) {
                        status = "Out of Stock";
                        out++;
                    } else if (qty == 0) {
                        status = "Not Made Yet";
                    } else if (qty <= level) {
                        status = "Low Stock";
                        low++;
                    } else {
                        status = "In Stock";
                    }
                }
            } else {
                available = String.valueOf(qty);
                if (qty == 0) {
                    status = "Out of Stock";
                    out++;
                } else if (qty <= level) {
                    status = "Low Stock";
                    low++;
                } else {
                    status = "In Stock";
                }
            }
            total++;

            shownProducts.add(p);
            tableModel.addRow(new Object[]{
                    p.getProductCode(),
                    p.getProductName(),
                    p.getCategory() != null ? p.getCategory().getName() : "N/A",
                    p.getSellingPrice(),
                    available,
                    status
            });
        }

        lblSummary.setText(total + " products  |  " + low + " low stock  |  " + out + " out of stock");

        // Keep the product that was being edited selected
        if (editing != null) {
            selectProduct(editing.getProductId());
        }
    }

    // How many more pieces the current component stock can make (limited by the scarcest component)
    private int canMake(List<Recipe> lines, Map<Long, Integer> componentStock) {
        int most = Integer.MAX_VALUE;
        for (Recipe r : lines) {
            int needed = r.getQuantityNeeded();
            if (needed <= 0) {
                continue;
            }
            Long id = r.getComponent().getComponentId();
            int have = componentStock.containsKey(id) ? componentStock.get(id) : 0;
            most = Math.min(most, have / needed);
        }
        return most == Integer.MAX_VALUE ? 0 : most;
    }

    private void selectProduct(Long productId) {
        for (int i = 0; i < shownProducts.size(); i++) {
            if (shownProducts.get(i).getProductId().equals(productId)) {
                table.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    // Next code in the FLW-0001 series. Older codes that don't follow the format (e.g. FB-D) are ignored.
    private String nextProductCode() {
        int highest = 0;
        for (Product p : productRepository.findAll()) {
            String code = p.getProductCode();
            if (code == null || !code.startsWith(CODE_PREFIX)) {
                continue;
            }
            try {
                highest = Math.max(highest, Integer.parseInt(code.substring(CODE_PREFIX.length())));
            } catch (NumberFormatException ignored) {
            }
        }
        return CODE_PREFIX + String.format("%04d", highest + 1);
    }

    // Refreshes the category, supplier and component dropdowns (keeps what was selected)
    private void loadDropdowns() {
        // Only categories with the type "Product" are shown here
        int categoryIndex = cmbCategory.getSelectedIndex();
        productCategories = new ArrayList<>();
        for (Category c : categoryRepository.findAll()) {
            if (TYPE_PRODUCT.equals(c.getType())) {
                productCategories.add(c);
            }
        }
        cmbCategory.removeAllItems();
        for (Category c : productCategories) {
            cmbCategory.addItem(c.getName());
        }
        if (categoryIndex >= 0 && categoryIndex < productCategories.size()) {
            cmbCategory.setSelectedIndex(categoryIndex);
        }

        int supplierIndex = cmbSupplier.getSelectedIndex();
        suppliers = supplierRepository.findAll();
        cmbSupplier.removeAllItems();
        cmbSupplier.addItem("None");
        for (Supplier s : suppliers) {
            cmbSupplier.addItem(s.getSupplierName());
        }
        if (supplierIndex >= 0 && supplierIndex <= suppliers.size()) {
            cmbSupplier.setSelectedIndex(supplierIndex);
        }

        int componentIndex = cmbComponent.getSelectedIndex();
        components = componentRepository.findAll();
        cmbComponent.removeAllItems();
        for (Component c : components) {
            cmbComponent.addItem(c.getName());
        }
        if (componentIndex >= 0 && componentIndex < components.size()) {
            cmbComponent.setSelectedIndex(componentIndex);
        }
    }

    // ---------------------------------------------------------------- Product form

    private void fillForm() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }

        Product selected = shownProducts.get(row);
        boolean sameProduct = editing != null && editing.getProductId().equals(selected.getProductId());

        editing = selected;
        lblFormTitle.setText("Edit Product: " + editing.getProductCode());
        btnSave.setText("Update Product");
        lblMessage.setText(" ");
        if (!sameProduct) {
            lblStockMessage.setText(" ");
            lblRecipeMessage.setText(" ");
            lblMakeMessage.setText(" ");
            lblPhotoMessage.setText(" ");
        }

        txtCode.setText(editing.getProductCode());
        txtName.setText(editing.getProductName());
        txtPrice.setText(editing.getSellingPrice().toString());
        cmbStatus.setSelectedItem(editing.getStatus());
        chkComposite.setSelected(Boolean.TRUE.equals(editing.getIsComposite()));
        for (int i = 0; i < productCategories.size(); i++) {
            if (productCategories.get(i).getCategoryId().equals(editing.getCategory().getCategoryId())) {
                cmbCategory.setSelectedIndex(i);
            }
        }

        showDetail();
    }

    // Shows the recipe (made from components), the stock form (bought finished), or the hint
    private void showDetail() {
        if (editing == null) {
            photoCard.setVisible(false);
            detailLayout.show(detailPanel, CARD_HINT);
            return;
        }

        photoCard.setVisible(true);
        lblPhoto.setIcon(ImageHelper.loadIcon(editing.getImagePath(), PHOTO_SIZE, PHOTO_SIZE));

        if (Boolean.TRUE.equals(editing.getIsComposite())) {
            lblRecipeTitle.setText("Recipe for: " + editing.getProductName() + "  (what one piece uses)");
            loadRecipe();
            detailLayout.show(detailPanel, CARD_RECIPE);
        } else {
            lblStockTitle.setText("Add Stock to: " + editing.getProductCode() + " - " + editing.getProductName());
            detailLayout.show(detailPanel, CARD_STOCK);
        }
    }

    private void clearForm() {
        editing = null;
        table.clearSelection();
        lblFormTitle.setText("Add Product");
        btnSave.setText("Add Product");
        txtCode.setText(nextProductCode());
        txtName.setText("");
        txtPrice.setText("");
        cmbStatus.setSelectedIndex(0);
        chkComposite.setSelected(false);
        recipeLines.clear();
        recipeTableModel.setRowCount(0);
        showDetail();
    }

    private void saveProduct() {
        String name = txtName.getText().trim();
        BigDecimal price = parseMoney(txtPrice.getText());

        if (productCategories.isEmpty()) {
            showMessage(lblMessage, "Add a category with Type \"Product\" first (Category tab).", true);
            return;
        }
        if (name.isEmpty()) {
            showMessage(lblMessage, "Product name is required.", true);
            return;
        }
        if (name.length() > 255) {
            showMessage(lblMessage, "Name must be 255 characters or less.", true);
            return;
        }
        if (price == null || price.compareTo(new BigDecimal("99999999.99")) > 0) {
            showMessage(lblMessage, "Enter a valid selling price.", true);
            return;
        }

        Category category = productCategories.get(cmbCategory.getSelectedIndex());
        String status = (String) cmbStatus.getSelectedItem();
        boolean composite = chkComposite.isSelected();
        boolean isNew = editing == null;
        BigDecimal oldPrice = isNew ? null : editing.getSellingPrice();
        String oldStatus = isNew ? null : editing.getStatus();

        try {
            Product product;
            if (isNew) {
                // Generated again here so the code is never stale
                product = new Product(category, nextProductCode(), name, price, composite, status);
            } else {
                product = editing;
                product.setCategory(category);
                product.setProductName(name);
                product.setSellingPrice(price);
                product.setIsComposite(composite);
                product.setStatus(status);
            }
            // Stay on the saved product so its recipe or stock can be set up right away
            editing = productRepository.save(product);
            if (isNew) {
                auditService.log(currentUser, "Product created", "PRODUCTS",
                        editing.getProductCode() + " " + name + ", price ₱" + price + ", status " + status);
            } else {
                auditService.log(currentUser, "Product edited", "PRODUCTS",
                        editing.getProductCode() + " " + name);
                if (oldPrice != null && oldPrice.compareTo(price) != 0) {
                    auditService.log(currentUser, "Price changed", "PRODUCTS",
                            editing.getProductCode() + " " + name + ": ₱" + oldPrice + " to ₱" + price);
                }
                if (oldStatus != null && !oldStatus.equals(status)) {
                    auditService.log(currentUser, "ACTIVE".equals(status) ? "Product activated" : "Product deactivated", "PRODUCTS",
                            editing.getProductCode() + " " + name);
                }
            }

            loadData();
            if (isNew && composite) {
                showMessage(lblMessage, "Product added. Add its components below.", false);
            } else {
                showMessage(lblMessage, isNew ? "Product added." : "Product updated.", false);
            }
        } catch (Exception ex) {
            showMessage(lblMessage, "Not saved: " + ex.getMessage(), true);
        }
    }

    // ---------------------------------------------------------------- Photo

    private void uploadPhoto() {
        if (editing == null) {
            showMessage(lblPhotoMessage, "Select a product first.", true);
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images (jpg, jpeg, png)", "jpg", "jpeg", "png"));

        JLabel preview = new JLabel("Select a photo", SwingConstants.CENTER);
        preview.setPreferredSize(new Dimension(220, 220));
        preview.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230), 1));
        chooser.setAccessory(preview);
        chooser.addPropertyChangeListener(JFileChooser.SELECTED_FILE_CHANGED_PROPERTY, evt -> {
            File chosen = chooser.getSelectedFile();
            if (chosen != null && chosen.isFile()) {
                preview.setText(null);
                preview.setIcon(ImageHelper.loadIconFromFile(chosen, 220, 220));
            } else {
                preview.setIcon(null);
                preview.setText("Select a photo");
            }
        });

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file.length() > 5 * 1024 * 1024) {
            showMessage(lblPhotoMessage, "Photo must be 5 MB or less.", true);
            return;
        }

        try {
            editing.setImagePath(ImageHelper.saveImage(file, editing.getProductCode()));
            editing = productRepository.save(editing);
            loadData();
            showMessage(lblPhotoMessage, "Photo saved.", false);
        } catch (Exception ex) {
            showMessage(lblPhotoMessage, "Photo not saved.", true);
        }
    }

    private void removePhoto() {
        if (editing == null) {
            showMessage(lblPhotoMessage, "Select a product first.", true);
            return;
        }

        try {
            editing.setImagePath(null);
            editing = productRepository.save(editing);
            loadData();
            showMessage(lblPhotoMessage, "Photo removed.", false);
        } catch (Exception ex) {
            showMessage(lblPhotoMessage, "Photo not removed.", true);
        }
    }

    // ---------------------------------------------------------------- Recipe

    private void loadRecipe() {
        recipeLines.clear();
        recipeTableModel.setRowCount(0);
        if (editing == null) {
            return;
        }

        for (Recipe r : recipeRepository.findByProductProductId(editing.getProductId())) {
            recipeLines.add(r);
            recipeTableModel.addRow(new Object[]{
                    r.getComponent().getName(),
                    r.getQuantityNeeded(),
                    r.getComponent().getUnitOfMeasure()
            });
        }
    }

    private void addRecipeLine() {
        if (editing == null) {
            showMessage(lblRecipeMessage, "Select a product in the table first.", true);
            return;
        }
        if (components.isEmpty()) {
            showMessage(lblRecipeMessage, "Add a component first (Components tab).", true);
            return;
        }

        Component component = components.get(cmbComponent.getSelectedIndex());
        int qty = (Integer) spnRecipeQty.getValue();

        try {
            // If the component is already in the recipe, update its quantity instead of adding it twice
            Recipe line = null;
            for (Recipe r : recipeLines) {
                if (r.getComponent().getComponentId().equals(component.getComponentId())) {
                    line = r;
                }
            }
            boolean existed = line != null;
            if (existed) {
                line.setQuantityNeeded(qty);
            } else {
                line = new Recipe(editing, component, qty);
            }
            recipeRepository.save(line);
            auditService.log(currentUser, "Recipe changed", "PRODUCTS",
                    editing.getProductName() + ": " + (existed ? "updated " : "added ") + component.getName() + " x" + qty);

            spnRecipeQty.setValue(1);
            loadData();
            showMessage(lblRecipeMessage, existed ? "Recipe line updated." : "Component added to the recipe.", false);
        } catch (Exception ex) {
            showMessage(lblRecipeMessage, "Not saved: " + ex.getMessage(), true);
        }
    }

    private void removeRecipeLine() {
        int row = recipeTable.getSelectedRow();
        if (row < 0) {
            showMessage(lblRecipeMessage, "Select a recipe line first.", true);
            return;
        }

        try {
            Recipe removed = recipeLines.get(row);
            recipeRepository.delete(removed);
            auditService.log(currentUser, "Recipe changed", "PRODUCTS",
                    editing.getProductName() + ": removed " + removed.getComponent().getName());
            loadData();
            showMessage(lblRecipeMessage, "Removed from the recipe.", false);
        } catch (Exception ex) {
            showMessage(lblRecipeMessage, "Not removed: " + ex.getMessage(), true);
        }
    }

    // ---------------------------------------------------------------- Make (deducts the components)

    private void makeProduct() {
        if (editing == null) {
            showMessage(lblMakeMessage, "Select a product in the table first.", true);
            return;
        }

        int qty = (Integer) spnMakeQty.getValue();
        try {
            productionService.makeProduct(editing, qty);
            auditService.log(currentUser, "Product made", "INVENTORY",
                    qty + " x " + editing.getProductName() + ", components deducted from stock");

            spnMakeQty.setValue(1);
            loadData();
            showMessage(lblMakeMessage, qty + " made. Components deducted from stock.", false);
        } catch (Exception ex) {
            showMessage(lblMakeMessage, ex.getMessage(), true);
        }
    }

    // ---------------------------------------------------------------- Stock form

    private void addStock() {
        if (editing == null) {
            showMessage(lblStockMessage, "Select a product in the table first.", true);
            return;
        }

        BigDecimal cost = parseMoney(txtCost.getText());
        if (cost == null) {
            showMessage(lblStockMessage, "Enter a valid unit cost.", true);
            return;
        }

        int supplierIndex = cmbSupplier.getSelectedIndex();
        Supplier supplier = supplierIndex <= 0 ? null : suppliers.get(supplierIndex - 1);
        int qty = (Integer) spnQty.getValue();
        int reorder = (Integer) spnReorder.getValue();

        try {
            productInventoryRepository.save(new ProductInventory(
                    supplier, editing, qty, reorder, cost, LocalDate.now(), null));
            auditService.log(currentUser, "Stock added", "INVENTORY",
                    qty + " x " + editing.getProductName() + ", unit cost ₱" + cost + ", reorder level " + reorder);

            spnQty.setValue(1);
            txtCost.setText("");
            loadData();
            showMessage(lblStockMessage, "Stock added.", false);
        } catch (Exception ex) {
            showMessage(lblStockMessage, "Not saved: " + ex.getMessage(), true);
        }
    }

    // ---------------------------------------------------------------- Helpers

    // Shows a message inside the panel (red = problem, green = success)
    private void showMessage(JLabel label, String text, boolean isError) {
        label.setForeground(isError ? new Color(220, 53, 69) : new Color(40, 167, 69));
        label.setText(text);
    }

    // Returns the amount, or null if it is not a valid number (or is negative)
    private BigDecimal parseMoney(String text) {
        try {
            BigDecimal value = new BigDecimal(text.replace(",", "").trim());
            return value.compareTo(BigDecimal.ZERO) < 0 ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}