package com.flowershop.flowershop.ui;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.*;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.BackupService;
import com.flowershop.flowershop.service.ProductionService;
import com.flowershop.flowershop.service.SaleService;
import com.flowershop.flowershop.service.StockReceivingService;
import com.flowershop.flowershop.ui.panels.*;
import com.flowershop.flowershop.util.Permissions;
import com.flowershop.flowershop.util.Permissions.Feature;

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

    // Sidebar palette: black panel (matches the logo background), gray buttons
    private static final Color SIDEBAR_BG = new Color(2, 2, 14);
    private static final Color NAV_BG = new Color(72, 72, 78);
    private static final Color NAV_FG = Color.WHITE;
    private static final Color NAV_ACTIVE_BG = new Color(190, 190, 196);
    private static final Color NAV_ACTIVE_FG = Color.BLACK;

    public MainFrame(User currentUser, UserRepository userRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        initUI();
    }

    private void initUI() {
        setTitle("Junaie's Flower Shop Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());

        // 1. Sidebar Menu (West)
        JPanel sidebar = createSidebar();
        add(sidebar, BorderLayout.WEST);

        // 2. Card Content Container (Center)
        cardLayout = new CardLayout();
        contentArea = new JPanel(cardLayout);

        // Only the screens this role may use are built at all, so nothing else can be opened.
        // The first one added is the screen shown after login.
        if (allowed(Feature.REPORTS)) {
            contentArea.add(
                    new DashboardPanel(
                            SpringContext.getBean(ProductRepository.class),
                            SpringContext.getBean(ProductInventoryRepository.class),
                            SpringContext.getBean(OrderRepository.class)
                    ),
                    "DASHBOARD"
            );
        }
        if (allowed(Feature.POS)) {
            contentArea.add(
                    new POSPanel(
                            currentUser,
                            SpringContext.getBean(ProductRepository.class),
                            SpringContext.getBean(ProductInventoryRepository.class),
                            SpringContext.getBean(SaleService.class)
                    ),
                    "POS"
            );
        }
        if (allowed(Feature.INVENTORY_VIEW)) {
            contentArea.add(
                    new InventoryPanel(
                            SpringContext.getBean(ProductRepository.class),
                            SpringContext.getBean(ProductInventoryRepository.class),
                            SpringContext.getBean(ComponentRepository.class),
                            SpringContext.getBean(ComponentInventoryRepository.class),
                            SpringContext.getBean(CategoryRepository.class),
                            SpringContext.getBean(SupplierRepository.class),
                            SpringContext.getBean(RecipeRepository.class),
                            SpringContext.getBean(ProductionService.class),
                            SpringContext.getBean(StockReceivingService.class),
                            currentUser,
                            !allowed(Feature.INVENTORY_EDIT) // view only for Staff
                    ),
                    "INVENTORY"
            );
        }
        if (allowed(Feature.SUPPLIERS)) {
            contentArea.add(
                    new SupplierPanel(
                            SpringContext.getBean(SupplierRepository.class),
                            SpringContext.getBean(ComponentInventoryRepository.class),
                            SpringContext.getBean(ProcurementRepository.class),
                            SpringContext.getBean(ComponentRepository.class),
                            currentUser
                    ),
                    "SUPPLIERS"
            );
        }
        if (allowed(Feature.DISPUTES)) {
            contentArea.add(
                    new DisputePanel(
                            currentUser,
                            SpringContext.getBean(DisputeRepository.class),
                            SpringContext.getBean(OrderRepository.class)
                    ),
                    "DISPUTES"
            );
        }
        if (allowed(Feature.RETURNS)) {
            contentArea.add(
                    new ReturnsPanel(
                            currentUser,
                            SpringContext.getBean(ReturnsRepository.class),
                            SpringContext.getBean(DisputeRepository.class),
                            SpringContext.getBean(OrderItemRepository.class)
                    ),
                    "RETURNS"
            );
        }
        if (allowed(Feature.USERS)) {
            contentArea.add(new UserPanel(currentUser, userRepository), "USERS");
        }
        if (allowed(Feature.AUDIT)) {
            contentArea.add(new AuditPanel(SpringContext.getBean(AuditLogRepository.class), userRepository), "AUDIT");
        }
        if (allowed(Feature.BACKUPS)) {
            contentArea.add(new BackupPanel(currentUser, SpringContext.getBean(BackupService.class)), "BACKUPS");
        }

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
        sidebar.setBackground(SIDEBAR_BG);

        // 1. Logo Header Section
        JLabel lblLogo = new JLabel();
        lblLogo.setAlignmentX(Component.CENTER_ALIGNMENT);

        try {
            ImageIcon originalIcon = new ImageIcon(getClass().getResource("/static/logo.jpg"));
            Image scaledImage = originalIcon.getImage().getScaledInstance(120, 120, Image.SCALE_SMOOTH);
            lblLogo.setIcon(new ImageIcon(scaledImage));
        } catch (Exception e) {
            lblLogo.setText("Flower Shop POS");
            lblLogo.setFont(new Font("Segoe UI", Font.BOLD, 20));
            lblLogo.setForeground(Color.WHITE);
        }

        sidebar.add(lblLogo);
        sidebar.add(Box.createRigidArea(new Dimension(0, 20))); // Space before navigation buttons

        // 2. Navigation Buttons: a button only exists if the user's role may use that screen.
        // Same order as the cards above, so the first button matches the screen shown after login.
        addNav(sidebar, Feature.REPORTS, "Dashboard", "DASHBOARD");
        addNav(sidebar, Feature.POS, "Point of Sale", "POS");
        addNav(sidebar, Feature.INVENTORY_VIEW,
                allowed(Feature.INVENTORY_EDIT) ? "Inventory" : "Inventory (View Only)", "INVENTORY");
        addNav(sidebar, Feature.SUPPLIERS, "Suppliers", "SUPPLIERS");
        addNav(sidebar, Feature.DISPUTES, "Disputes", "DISPUTES");
        addNav(sidebar, Feature.RETURNS, "Returns", "RETURNS");
        addNav(sidebar, Feature.USERS, "Users", "USERS");
        addNav(sidebar, Feature.AUDIT, "Audit Trail", "AUDIT");
        addNav(sidebar, Feature.BACKUPS, "Backups", "BACKUPS");

        sidebar.add(Box.createVerticalGlue());

        // 3. Logout Button
        JButton btnLogout = new JButton("Logout");
        btnLogout.setMaximumSize(new Dimension(200, 38));
        btnLogout.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnLogout.setBackground(new Color(220, 53, 69));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setFont(new Font("Segoe UI", Font.BOLD, 15));
        styleFlat(btnLogout);

        btnLogout.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Are you sure you want to log out?",
                    "Logout",
                    JOptionPane.YES_NO_OPTION
            );
            if (confirm == JOptionPane.YES_OPTION) {
                SpringContext.getBean(AuditService.class).log(currentUser, "Logout", "AUTH",
                        currentUser.getUsername() + " logged out");
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
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblStatus.setForeground(new Color(40, 167, 69)); // Green indicator

        // User Details & Live Clock (Right)
        JLabel lblRightInfo = new JLabel();
        lblRightInfo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
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

    private boolean allowed(Feature feature) {
        return Permissions.can(currentUser, feature);
    }

    private void addNav(JPanel sidebar, Feature feature, String text, String cardName) {
        if (!allowed(feature)) {
            return;
        }
        if (!navButtons.isEmpty()) {
            sidebar.add(Box.createRigidArea(new Dimension(0, 8)));
        }
        // The first allowed screen is the one shown after login, so it starts highlighted
        sidebar.add(createNavButton(text, cardName, navButtons.isEmpty()));
    }

    private JButton createNavButton(String text, String cardName, boolean isDefault) {
        JButton button = new JButton(text);
        button.setMaximumSize(new Dimension(200, 38));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        styleFlat(button);

        if (isDefault) {
            highlightButton(button);
        } else {
            resetButton(button);
        }

        button.addActionListener(e -> {
            cardLayout.show(contentArea, cardName);
            refreshScreen();

            for (JButton btn : navButtons) {
                resetButton(btn);
            }
            highlightButton(button);
        });

        navButtons.add(button);
        return button;
    }

    // Reloads the screen that was just opened, so its data is always up to date
    private void refreshScreen() {
        for (Component c : contentArea.getComponents()) {
            if (!c.isVisible()) {
                continue;
            }
            if (c instanceof DashboardPanel) {
                ((DashboardPanel) c).refreshDashboardData();
            } else if (c instanceof POSPanel) {
                ((POSPanel) c).loadProducts();
            } else if (c instanceof InventoryPanel) {
                ((InventoryPanel) c).refresh();
            } else if (c instanceof SupplierPanel) {
                ((SupplierPanel) c).loadData();
            } else if (c instanceof DisputePanel) {
                ((DisputePanel) c).loadData();
            } else if (c instanceof ReturnsPanel) {
                ((ReturnsPanel) c).loadData();
            } else if (c instanceof UserPanel) {
                ((UserPanel) c).loadData();
            } else if (c instanceof AuditPanel) {
                ((AuditPanel) c).loadData();
            } else if (c instanceof BackupPanel) {
                ((BackupPanel) c).loadData();
            }
        }
    }

    // Paint the colour ourselves so it shows the same on every look-and-feel
    private void styleFlat(JButton button) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
    }

    private void highlightButton(JButton button) {
        button.setBackground(NAV_ACTIVE_BG);
        button.setForeground(NAV_ACTIVE_FG);
        button.setFont(new Font("Segoe UI", Font.BOLD, 15));
    }

    private void resetButton(JButton button) {
        button.setBackground(NAV_BG);
        button.setForeground(NAV_FG);
        button.setFont(new Font("Segoe UI", Font.PLAIN, 15));
    }

}