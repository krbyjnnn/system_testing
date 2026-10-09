package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.UserRepository;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UserPanel extends JPanel {

    private static final String[] ROLES = {"OWNER", "MANAGER", "STAFF"};

    private final User currentUser;
    private final UserRepository userRepository;

    private JTextField txtSearch;
    private JTable userTable;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;
    private JButton btnToggleStatus;

    // Form fields
    private JLabel lblFormTitle;
    private JLabel lblPassword;
    private JTextField txtName;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JComboBox<String> cmbRole;
    private JButton btnSave;

    // Users shown in the table (same order as the rows)
    private List<User> shownUsers = new ArrayList<>();

    // User being edited (null = adding a new user)
    private User editingUser = null;

    public UserPanel(User currentUser, UserRepository userRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        // Top Header
        JLabel lblTitle = new JLabel("Users");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        // White card
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // Form (add / edit)
        lblFormTitle = new JLabel("Add User");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblPassword = new JLabel("Password");
        txtName = new JTextField();
        txtUsername = new JTextField();
        txtPassword = new JPasswordField();
        cmbRole = new JComboBox<>(ROLES);

        btnSave = new JButton("Add User");
        btnSave.addActionListener(e -> saveUser());
        JButton btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearForm());

        JPanel fields = new JPanel(new GridLayout(2, 4, 12, 2));
        fields.setOpaque(false);
        fields.add(new JLabel("Full Name"));
        fields.add(new JLabel("Username"));
        fields.add(lblPassword);
        fields.add(new JLabel("Role"));
        fields.add(txtName);
        fields.add(txtUsername);
        fields.add(txtPassword);
        fields.add(cmbRole);

        JPanel formButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        formButtons.setOpaque(false);
        formButtons.add(btnSave);
        formButtons.add(btnClear);

        JPanel formPanel = new JPanel(new BorderLayout(0, 8));
        formPanel.setBackground(new Color(250, 251, 253));
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 233, 238), 1),
                BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));
        formPanel.add(lblFormTitle, BorderLayout.NORTH);
        formPanel.add(fields, BorderLayout.CENTER);
        formPanel.add(formButtons, BorderLayout.SOUTH);

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

        // Table (the password is never shown)
        String[] columns = {"ID", "Full Name", "Username", "Role", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        userTable = new JTable(tableModel);
        userTable.setRowHeight(28);
        userTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Selecting a row loads that user into the form
        userTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = userTable.getSelectedRow();
            if (row >= 0 && row < shownUsers.size()) {
                startEditing(shownUsers.get(row));
            } else {
                btnToggleStatus.setEnabled(false);
            }
        });

        JPanel tablePanel = new JPanel(new BorderLayout(0, 8));
        tablePanel.setOpaque(false);
        tablePanel.add(searchPanel, BorderLayout.NORTH);
        tablePanel.add(new JScrollPane(userTable), BorderLayout.CENTER);

        // Bottom: summary on the left, status button on the right
        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Grayed out until a user is selected
        btnToggleStatus = new JButton("Set Inactive");
        btnToggleStatus.setEnabled(false);
        btnToggleStatus.addActionListener(e -> toggleStatus());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(btnToggleStatus);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(lblSummary, BorderLayout.WEST);
        bottomPanel.add(actionPanel, BorderLayout.EAST);

        card.add(formPanel, BorderLayout.NORTH);
        card.add(tablePanel, BorderLayout.CENTER);
        card.add(bottomPanel, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    public void loadData() {
        String keyword = txtSearch.getText().trim().toLowerCase();
        shownUsers.clear();
        tableModel.setRowCount(0);
        int owners = 0;
        int managers = 0;
        int staff = 0;

        for (User u : userRepository.findAll()) {
            boolean matches = u.getFullName().toLowerCase().contains(keyword)
                    || u.getUsername().toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            if (u.getRole().equals("OWNER")) {
                owners++;
            } else if (u.getRole().equals("MANAGER")) {
                managers++;
            } else if (u.getRole().equals("STAFF")) {
                staff++;
            }

            shownUsers.add(u);
            tableModel.addRow(new Object[]{
                    u.getUserId(),
                    u.getFullName(),
                    u.getUsername(),
                    u.getRole(),
                    u.getStatus()
            });
        }

        lblSummary.setText(shownUsers.size() + " users  |  " + owners + " owners  |  " + managers + " managers"  + "  |  " + staff + " staff");
    }

    // Edit mode: fill the form with the chosen user
    private void startEditing(User user) {
        editingUser = user;
        lblFormTitle.setText("Edit User: " + user.getUsername());
        lblPassword.setText("New Password (blank = keep)");
        btnSave.setText("Update User");
        txtName.setText(user.getFullName());
        txtUsername.setText(user.getUsername());
        txtPassword.setText("");
        cmbRole.setSelectedItem(user.getRole());

        btnToggleStatus.setText(user.getStatus().equals("ACTIVE") ? "Set Inactive" : "Set Active");
        btnToggleStatus.setEnabled(true);
    }

    // Add mode: empty the form
    private void clearForm() {
        editingUser = null;
        userTable.clearSelection();
        lblFormTitle.setText("Add User");
        lblPassword.setText("Password");
        btnSave.setText("Add User");
        txtName.setText("");
        txtUsername.setText("");
        txtPassword.setText("");
        cmbRole.setSelectedIndex(0);

        btnToggleStatus.setText("Set Inactive");
        btnToggleStatus.setEnabled(false);
    }

    // Switches the selected user between ACTIVE and INACTIVE
    private void toggleStatus() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            return;
        }

        User user = shownUsers.get(row);
        boolean isActive = user.getStatus().equals("ACTIVE");

        // The account in use cannot be set to inactive
        if (isActive && user.getUserId().equals(currentUser.getUserId())) {
            JOptionPane.showMessageDialog(this, "You cannot set the account you are logged in with to inactive.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        user.setStatus(isActive ? "INACTIVE" : "ACTIVE");

        try {
            userRepository.save(user);
            loadData();
            clearForm();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Status was not changed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Adds a new user or updates the one being edited
    private void saveUser() {
        boolean isNew = editingUser == null;

        // Check the inputs
        String fullName = txtName.getText().trim();
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (fullName.isEmpty() || username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Full name and username are required.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (isNew && password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "A password is required for a new user.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (fullName.length() > 255 || username.length() > 255 || password.length() > 255) {
            JOptionPane.showMessageDialog(this, "Each field must be 255 characters or less.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // The username must be unique
        Optional<User> existing = userRepository.findByUsername(username);
        if (existing.isPresent() && (isNew || !existing.get().getUserId().equals(editingUser.getUserId()))) {
            JOptionPane.showMessageDialog(this, "That username is already taken.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Save
        User user = isNew ? new User() : editingUser;
        user.setFullName(fullName);
        user.setUsername(username);
        if (!password.isEmpty()) {
            user.setPasswordHash(password);
        }
        user.setRole((String) cmbRole.getSelectedItem());

        try {
            userRepository.save(user);
            loadData();
            clearForm();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "User was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}