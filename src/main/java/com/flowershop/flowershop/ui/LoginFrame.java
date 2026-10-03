package com.flowershop.flowershop.ui;

import com.flowershop.flowershop.repository.UserRepository;

import javax.swing.*;
import java.awt.*;

public class LoginFrame extends JFrame {

    private final UserRepository userRepository;

    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnLogin;
    private JButton btnCancel;

    public LoginFrame(UserRepository userRepository) {
        this.userRepository = userRepository;
        initUI();
    }

    private void initUI() {
        setTitle("Flower Shop Management System - Login");
        setSize(400, 280);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel lblTitle = new JLabel("Welcome Back", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        mainPanel.add(lblTitle, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Username:"), gbc);

        txtUsername = new JTextField(15);
        gbc.gridx = 1;
        formPanel.add(txtUsername, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Password:"), gbc);

        txtPassword = new JPasswordField(15);
        gbc.gridx = 1;
        formPanel.add(txtPassword, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnLogin = new JButton("Login");
        btnCancel = new JButton("Cancel");

        buttonPanel.add(btnLogin);
        buttonPanel.add(btnCancel);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(mainPanel);

        btnLogin.addActionListener(e -> handleLogin());
        btnCancel.addActionListener(e -> System.exit(0));
    }

    private void handleLogin() {
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both username and password.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            if (user.getPasswordHash().equals(password)) {
                JOptionPane.showMessageDialog(this, "Welcome, " + user.getFullName() + " [" + user.getRole() + "]", "Login Successful", JOptionPane.INFORMATION_MESSAGE);
                
                // 1. Close the login window safely
                this.dispose();

                // 2. Open the MainFrame
                SwingUtilities.invokeLater(() -> {
                    MainFrame mainFrame = new MainFrame(user, userRepository);
                    mainFrame.setVisible(true);
                });

            } else {
                JOptionPane.showMessageDialog(this, "Invalid password.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }, () -> {
            JOptionPane.showMessageDialog(this, "User not found.", "Error", JOptionPane.ERROR_MESSAGE);
    });
}
}