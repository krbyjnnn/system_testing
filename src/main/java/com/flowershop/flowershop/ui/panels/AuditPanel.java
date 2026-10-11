package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.AuditLog;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.AuditLogRepository;
import com.flowershop.flowershop.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;

public class AuditPanel extends JPanel {

    private static final int PAGE_SIZE = 20;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String[] MODULES = {"All", "AUTH", "POS", "ORDERS", "RETURNS", "DISPUTES",
            "INVENTORY", "PRODUCTS", "SUPPLIERS", "USERS"};

    // One entry of the User dropdown: an actual account (userId null = everyone)
    private record UserOption(Long userId, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final DefaultTableModel model;
    private final JComboBox<String> cmbModule = new JComboBox<>(MODULES);
    private final JComboBox<UserOption> cmbUser = new JComboBox<>();
    private boolean loadingUsers = false; // true while the dropdown is being refilled, so it doesn't trigger a search
    private final JButton btnPrevious = new JButton("Previous");
    private final JButton btnNext = new JButton("Next");
    private final JLabel lblPage = new JLabel("Page 1 of 1", SwingConstants.CENTER);
    private int page = 0;

    public AuditPanel(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;

        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("Audit Trail");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));

        JPanel filter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filter.setOpaque(false);
        filter.add(new JLabel("User:"));
        filter.add(cmbUser);
        filter.add(new JLabel("Module:"));
        filter.add(cmbModule);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(lblTitle, BorderLayout.WEST);
        top.add(filter, BorderLayout.EAST);

        model = new DefaultTableModel(new String[]{"Date & Time", "User", "Module", "Action", "Details"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setRowHeight(28);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        table.getColumnModel().getColumn(0).setPreferredWidth(150);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(90);
        table.getColumnModel().getColumn(3).setPreferredWidth(180);
        table.getColumnModel().getColumn(4).setPreferredWidth(500);

        btnPrevious.addActionListener(e -> {
            page--;
            search();
        });
        btnNext.addActionListener(e -> {
            page++;
            search();
        });
        cmbModule.addActionListener(e -> {
            page = 0;
            search();
        });
        cmbUser.addActionListener(e -> {
            if (!loadingUsers) {
                page = 0;
                search();
            }
        });

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        controls.setOpaque(false);
        controls.add(btnPrevious);
        controls.add(lblPage);
        controls.add(btnNext);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);

        loadData();
    }

    // Called when the screen is opened: refreshes the user list (new accounts may have been added), then the table
    public void loadData() {
        loadUsers();
        search();
    }

    // Fills the User dropdown with every account, keeping the one that was selected
    private void loadUsers() {
        UserOption selected = (UserOption) cmbUser.getSelectedItem();
        Long selectedId = selected == null ? null : selected.userId();

        loadingUsers = true;
        try {
            cmbUser.removeAllItems();
            cmbUser.addItem(new UserOption(null, "All users"));
            UserOption toSelect = cmbUser.getItemAt(0);
            for (User user : userRepository.findAll()) {
                UserOption option = new UserOption(user.getUserId(),
                        user.getFullName() + " (" + user.getRole() + ")");
                cmbUser.addItem(option);
                if (user.getUserId().equals(selectedId)) {
                    toSelect = option;
                }
            }
            cmbUser.setSelectedItem(toSelect);
        } finally {
            loadingUsers = false;
        }
    }

    // Shows the page of log entries that match the Module and User filters
    private void search() {
        String module = (String) cmbModule.getSelectedItem();
        UserOption selectedUser = (UserOption) cmbUser.getSelectedItem();
        Long userId = selectedUser == null ? null : selectedUser.userId();
        boolean allModules = module == null || "All".equals(module);

        PageRequest request = PageRequest.of(page, PAGE_SIZE);
        Page<AuditLog> result;
        if (userId == null) {
            result = allModules
                    ? auditLogRepository.findAllByOrderByTimestampDesc(request)
                    : auditLogRepository.findByModuleOrderByTimestampDesc(module, request);
        } else {
            result = allModules
                    ? auditLogRepository.findByUserUserIdOrderByTimestampDesc(userId, request)
                    : auditLogRepository.findByModuleAndUserUserIdOrderByTimestampDesc(module, userId, request);
        }

        int totalPages = Math.max(1, result.getTotalPages());
        if (page >= totalPages) {
            page = totalPages - 1;
            search();
            return;
        }

        model.setRowCount(0);
        for (AuditLog log : result.getContent()) {
            model.addRow(new Object[]{
                    log.getTimestamp() != null ? log.getTimestamp().format(DATE_TIME) : "",
                    log.getUser() != null ? log.getUser().getFullName() : "System",
                    log.getModule(),
                    log.getAction(),
                    log.getDetails()
            });
        }

        lblPage.setText("Page " + (page + 1) + " of " + totalPages);
        btnPrevious.setEnabled(page > 0);
        btnNext.setEnabled(page + 1 < totalPages);
    }
}