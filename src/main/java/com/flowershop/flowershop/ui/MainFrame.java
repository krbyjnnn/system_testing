package com.flowershop.flowershop.ui;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.*;
import com.flowershop.flowershop.service.SaleService;
import com.flowershop.flowershop.ui.panels.*;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MainFrame extends JFrame {

    private final User currentUser;
    private final UserRepository userRepository;

    private CardLayout cardLayout;
    private JPanel contentArea;
    private final List<JButton> navButtons = new ArrayList<>();

    public MainFrame(User currentUser, UserRepository userRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        initUI();
    }

    private void initUI() {
        setTitle("Junaie's Flower Shop Management System");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // 1. Sidebar Menu (West)
        JPanel sidebar = createSidebar();
        add(sidebar, BorderLayout.WEST);

        // 2. Card Content Container (Center)
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);

        // View panels linked to card names
        contentArea.add(
                new DashboardPanel(
                        SpringContext.getBean(ProductRepository.class),
                        SpringContext.getBean(ProductInventoryRepository.class),
                        SpringContext.getBean(OrderRepository.class)
                ),
                "DASHBOARD"
        );
        contentArea.add(
                new POSPanel(
                        currentUser,
                        SpringContext.getBean(ProductRepository.class),
                        SpringContext.getBean(ProductInventoryRepository.class),
                        SpringContext.getBean(SaleService.class)
                ),
                "POS"
        );
        contentArea.add(
                new InventoryPanel(
                        SpringContext.getBean(ProductRepository.class),
                        SpringContext.getBean(ProductInventoryRepository.class),
                        SpringContext.getBean(ComponentRepository.class),
                        SpringContext.getBean(ComponentInventoryRepository.class),
                        SpringContext.getBean(CategoryRepository.class)
                ),
                "INVENTORY"
        );
        contentArea.add(
                new SupplierPanel(
                        SpringContext.getBean(SupplierRepository.class),
                        SpringContext.getBean(ComponentInventoryRepository.class)
                ),
                "SUPPLIERS"
        );
        contentArea.add(
                new DisputePanel(
                        currentUser,
                        SpringContext.getBean(DisputeRepository.class),
                        SpringContext.getBean(OrderRepository.class)
                ),
                "DISPUTES"
        );
        contentArea.add(
                new ReturnsPanel(
                        SpringContext.getBean(ReturnsRepository.class),
                        SpringContext.getBean(DisputeRepository.class),
                        SpringContext.getBean(OrderItemRepository.class)
                ),
                "RETURNS"
        );
        contentArea.add(new UserPanel(currentUser, userRepository), "USERS");

        add(contentArea, BorderLayout.CENTER);

        // 3. Cashier Status Bar (South)
        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);

        // Force layout revalidation to guarantee full rendering on show
        revalidate();
        repaint();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(220, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(15, 10, 15, 10));
        sidebar.setBackground(new Color(240, 242, 245));

        // 1. Logo Header Section
        JLabel lblLogo = new JLabel();
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        try {
            ImageIcon originalIcon = new ImageIcon(getClass().getResource("/static/logo.jpg"));
            Image scaledImage = originalIcon.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH);
            lblLogo.setIcon(new ImageIcon(scaledImage));
        } catch (Exception e) {
            lblLogo.setText("Flower Shop POS");
            lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 18));
        }

        sidebar.add(lblLogo);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20))); // Space before navigation buttons

        // 2. Navigation Buttons
        sidebar.add(createNavButton("Dashboard", "DASHBOARD", true));
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebar.add(createNavButton("Point of Sale", "POS", false));
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebar.add(createNavButton("Inventory", "INVENTORY", false));
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebar.add(createNavButton("Suppliers", "SUPPLIERS", false));
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebar.add(createNavButton("Disputes", "DISPUTES", false));
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebar.add(createNavButton("Returns", "RETURNS", false));
        sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        sidebar.add(createNavButton("Users", "USERS", false));

        sidebar.add(Box.createVerticalGlue());

        // 3. Logout Button
        JButton btnLogout = new JButton("Logout");
        btnLogout.setMaximumSize(new Dimension(200, 38));
        btnLogout.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogout.setBackground(new Color(220, 53, 69));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setFocusPainted(false);
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 13));

        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to log out?",
                    "Logout",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                this.dispose();
                LoginFrame loginFrame = new LoginFrame(userRepository);
                loginFrame.setVisible(true);
            }
        });

        sidebar.add(btnLogout);

        return sidebar;
    }

    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(210, 210, 210)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        statusBar.setBackground(new Color(245, 245, 245));

        // System Status Indicator (Left)
        JLabel lblStatus = new JLabel("● System Ready");
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblStatus.setForeground(new Color(40, 167, 69)); // Green indicator

        // User Details & Live Clock (Right)
        JLabel lblRightInfo = new JLabel();
        lblRightInfo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblRightInfo.setForeground(new Color(60, 60, 60));

        // Timer updating the clock every 1 second
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd | hh:mm:ss a");
        Timer clockTimer = new Timer(1000, e -> {
            String currentTime = LocalDateTime.now().format(formatter);
            lblRightInfo.setText("User: " + currentUser.getFullName() + " (" + currentUser.getRole() + ")   |   " + currentTime);
        });
        clockTimer.start();

        statusBar.add(lblStatus, BorderLayout.WEST);
        statusBar.add(lblRightInfo, BorderLayout.EAST);

        return statusBar;
    }

    private JButton createNavButton(String text, String cardName, boolean isDefault) {
        JButton button = new JButton(text);
        button.setMaximumSize(new Dimension(200, 38));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        if (isDefault) {
            highlightButton(button);
        } else {
            resetButton(button);
        }

        button.addActionListener(e -> {
            cardLayout.show(contentArea, cardName);

            for (JButton btn : navButtons) {
                resetButton(btn);
            }
            highlightButton(button);
        });

        navButtons.add(button);
        return button;
    }

    private void highlightButton(JButton button) {
        button.setBackground(new Color(13, 110, 253));
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
    }

    private void resetButton(JButton button) {
        button.setBackground(UIManager.getColor("Button.background"));
        button.setForeground(Color.BLACK);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 14));
    }
}