package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.Category;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.CategoryRepository;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.ui.SpringContext;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryPanel extends JPanel {

    private static final String TYPE_PERISHABLE = "Perishable";
    private static final String TYPE_NON_PERISHABLE = "Non-perishable";
    private static final String TYPE_PRODUCT = "Product";

    private final CategoryRepository categoryRepository;
    private final User currentUser;
    private final boolean readOnly; // view only: the add/edit form is not shown
    private final AuditService auditService = SpringContext.getBean(AuditService.class);

    private JTable table;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
    private List<Category> shownCategories = new ArrayList<>();

    // Form (top)
    private JLabel lblFormTitle;
    private JTextField txtName;
    private JComboBox<String> cboType;
    private JButton btnSave;
    private JLabel lblMessage;
    private Category editing = null; // null = adding a new category

    public CategoryPanel(CategoryRepository categoryRepository, User currentUser, boolean readOnly) {
        this.readOnly = readOnly;
        this.categoryRepository = categoryRepository;
        this.currentUser = currentUser;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        setBackground(Color.WHITE);

        // ---------------- Form ----------------
        lblFormTitle = new JLabel("Add Category");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        txtName = new JTextField();
        // Perishable / Non-perishable = component categories, Product = product categories
        cboType = new JComboBox<>(new String[]{TYPE_NON_PERISHABLE, TYPE_PERISHABLE, TYPE_PRODUCT});

        JPanel fields = new JPanel(new GridLayout(2, 2, 8, 2));
        fields.setOpaque(false);
        fields.add(new JLabel("Category Name"));
        fields.add(new JLabel("Type"));
        fields.add(txtName);
        fields.add(cboType);

        btnSave = new JButton("Add Category");
        btnSave.addActionListener(e -> saveCategory());
        JButton btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearForm());
        lblMessage = new JLabel(" ");
        lblMessage.setFont(new Font("Segoe UI", Font.BOLD, 12));

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(btnSave);
        buttonRow.add(btnClear);
        buttonRow.add(lblMessage);

        JPanel formCard = new JPanel(new BorderLayout(0, 6));
        formCard.setBackground(new Color(248, 249, 251));
        formCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        formCard.add(lblFormTitle, BorderLayout.NORTH);
        formCard.add(fields, BorderLayout.CENTER);
        formCard.add(buttonRow, BorderLayout.SOUTH);

        // ---------------- Table (ID, Category, Type) ----------------
        String[] columns = {"ID", "Category", "Type"};
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

        if (!readOnly) {
            add(formCard, BorderLayout.NORTH);
        }
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(lblSummary, BorderLayout.SOUTH);
    }

    // ---------------------------------------------------------------- Data

    public void loadData() {
        shownCategories.clear();
        tableModel.setRowCount(0);

        for (Category category : categoryRepository.findAll()) {
            shownCategories.add(category);
            tableModel.addRow(new Object[]{
                    category.getCategoryId(),
                    category.getName(),
                    category.getType()
            });
        }

        lblSummary.setText(shownCategories.size() + " categories");
    }

    // ---------------------------------------------------------------- Form

    private void fillForm() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }

        editing = shownCategories.get(row);
        lblFormTitle.setText("Edit Category: " + editing.getName());
        btnSave.setText("Update Category");
        lblMessage.setText(" ");
        txtName.setText(editing.getName());
        cboType.setSelectedItem(editing.getType());
    }

    private void clearForm() {
        editing = null;
        table.clearSelection();
        lblFormTitle.setText("Add Category");
        btnSave.setText("Add Category");
        txtName.setText("");
        cboType.setSelectedItem(TYPE_NON_PERISHABLE);
    }

    private void saveCategory() {
        String name = txtName.getText().trim();
        String type = (String) cboType.getSelectedItem();

        if (name.isEmpty()) {
            showMessage("Category name is required.", true);
            return;
        }
        if (name.length() > 255) {
            showMessage("Category name must be 255 characters or less.", true);
            return;
        }

        // The name must be unique
        for (Category c : categoryRepository.findAll()) {
            boolean sameName = c.getName().equalsIgnoreCase(name);
            boolean otherCategory = editing == null || !c.getCategoryId().equals(editing.getCategoryId());
            if (sameName && otherCategory) {
                showMessage("That category already exists.", true);
                return;
            }
        }

        String oldName = editing == null ? null : editing.getName();
        boolean isNew = editing == null;
        try {
            Category category;
            if (isNew) {
                category = new Category(name, type);
            } else {
                category = editing;
                category.setName(name);
                category.setType(type);
            }
            categoryRepository.save(category);
            if (isNew) {
                auditService.log(currentUser, "Category created", "PRODUCTS", name + " (" + type + ")");
            } else {
                auditService.log(currentUser, "Category edited", "PRODUCTS",
                        oldName.equals(name) ? name + " (" + type + ")" : oldName + " renamed to " + name + " (" + type + ")");
            }

            clearForm();
            loadData();
            showMessage(isNew ? "Category added." : "Category updated.", false);
        } catch (Exception ex) {
            showMessage("Not saved: " + ex.getMessage(), true);
        }
    }

    // Shows a message inside the panel (red = problem, green = success)
    private void showMessage(String text, boolean isError) {
        lblMessage.setForeground(isError ? new Color(220, 53, 69) : new Color(40, 167, 69));
        lblMessage.setText(text);
    }
}