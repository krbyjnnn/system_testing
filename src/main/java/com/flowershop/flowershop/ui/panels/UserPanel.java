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

    private static final String[] ROLES = {"OWNER", "CASHIER"};

    private final User currentUser;
    private final UserRepository userRepository;

    private JTextField txtSearch;
    private JTable userTable;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    // Users shown in the table (same order as the rows)
    private List<User> shownUsers = new ArrayList<>();

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
        String[] columns = {"ID", "Full Name", "Username", "Role"};
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

        // Bottom: summary on the left, action buttons on the right
        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JButton btnAdd = new JButton("Add User");
        btnAdd.addActionListener(e -> openForm(null));
        JButton btnEdit = new JButton("Edit Selected");
        btnEdit.addActionListener(e -> editSelected());
        JButton btnDelete = new JButton("Delete Selected");
        btnDelete.addActionListener(e -> deleteSelected());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.setOpaque(false);
        actionPanel.add(btnAdd);
        actionPanel.add(btnEdit);
        actionPanel.add(btnDelete);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        bottomPanel.add(lblSummary, BorderLayout.WEST);
        bottomPanel.add(actionPanel, BorderLayout.EAST);

        card.add(searchPanel, BorderLayout.NORTH);
        card.add(new JScrollPane(userTable), BorderLayout.CENTER);
        card.add(bottomPanel, BorderLayout.SOUTH);
        add(card, BorderLayout.CENTER);
    }

    public void loadData() {
        String keyword = txtSearch.getText().trim().toLowerCase();
        shownUsers.clear();
        tableModel.setRowCount(0);
        int owners = 0;
        int cashiers = 0;

        for (User u : userRepository.findAll()) {
            boolean matches = u.getFullName().toLowerCase().contains(keyword)
                    || u.getUsername().toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            if (u.getRole().equals("OWNER")) {
                owners++;
            } else if (u.getRole().equals("CASHIER")) {
                cashiers++;
            }

            shownUsers.add(u);
            tableModel.addRow(new Object[]{
                    u.getUserId(),
                    u.getFullName(),
                    u.getUsername(),
                    u.getRole()
            });
        }

        lblSummary.setText(shownUsers.size() + " users  |  " + owners + " owners  |  " + cashiers + " cashiers");
    }

    private void editSelected() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        openForm(shownUsers.get(row));
    }

    private void deleteSelected() {
        int row = userTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a user first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        User user = shownUsers.get(row);
        if (user.getUserId().equals(currentUser.getUserId())) {
            JOptionPane.showMessageDialog(this, "You cannot delete the account you are logged in with.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete the user " + user.getUsername() + "?",
                "Delete User", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            userRepository.delete(user);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "This user cannot be deleted because it already has orders or other records.",
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Add form (user = null) or edit form (user = the one to edit)
    private void openForm(User user) {
        boolean isNew = user == null;

        JTextField txtName = new JTextField(isNew ? "" : user.getFullName());
        JTextField txtUsername = new JTextField(isNew ? "" : user.getUsername());
        JPasswordField txtPassword = new JPasswordField();
        JComboBox<String> cmbRole = new JComboBox<>(ROLES);
        if (!isNew) {
            cmbRole.setSelectedItem(user.getRole());
        }

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Full Name:"));
        form.add(txtName);
        form.add(new JLabel("Username:"));
        form.add(txtUsername);
        form.add(new JLabel(isNew ? "Password:" : "New Password (blank = keep):"));
        form.add(txtPassword);
        form.add(new JLabel("Role:"));
        form.add(cmbRole);

        int result = JOptionPane.showConfirmDialog(this, form,
                isNew ? "Add User" : "Edit User",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

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
        if (existing.isPresent() && (isNew || !existing.get().getUserId().equals(user.getUserId()))) {
            JOptionPane.showMessageDialog(this, "That username is already taken.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Save
        if (isNew) {
            user = new User();
        }
        user.setFullName(fullName);
        user.setUsername(username);
        if (!password.isEmpty()) {
            user.setPasswordHash(password);
        }
        user.setRole((String) cmbRole.getSelectedItem());

        try {
            userRepository.save(user);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "User was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}