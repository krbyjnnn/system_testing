package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.OrderItemRepository;
import com.flowershop.flowershop.repository.OrderRepository;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.ReportDataService;
import com.flowershop.flowershop.ui.SpringContext;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;

// Reports page: one tab per report module.
//   Sales       - Daily / Weekly / Monthly / Period-specific (SalesReportPanel, unchanged)
//   Inventory   - stock movement with an All / Stock-In / Stock-Out filter
//   Procurement - purchases received in a date range
//   Spoilage    - spoilage and expired stock in a date range
//   Returns     - returns and refunds in a date range
// MainFrame builds this panel exactly as before and calls loadData() when the page is opened.
public class ReportsPanel extends JPanel {

    private final SalesReportPanel sales;
    private final InventoryReportTab inventory;
    private final ProcurementReportTab procurement;
    private final SpoilageReportTab spoilage;
    private final ReturnsReportTab returns;
    private final JTabbedPane tabs = new JTabbedPane();

    public ReportsPanel(User currentUser, OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository, AuditService auditService) {
        // Taken from the Spring context so MainFrame keeps building this panel the same way
        ReportDataService data = SpringContext.getBean(ReportDataService.class);

        sales = new SalesReportPanel(currentUser, orderRepository, orderItemRepository, auditService);
        inventory = new InventoryReportTab(currentUser, auditService, data);
        procurement = new ProcurementReportTab(currentUser, auditService, data);
        spoilage = new SpoilageReportTab(currentUser, auditService, data);
        returns = new ReturnsReportTab(currentUser, auditService, data);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("Reports");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        JLabel lblSubtitle = new JLabel("View, filter and export the shop's reports.");
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSubtitle.setForeground(Color.GRAY);
        JPanel titles = new JPanel(new GridLayout(2, 1));
        titles.setOpaque(false);
        titles.add(lblTitle);
        titles.add(lblSubtitle);

        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.setBackground(new Color(245, 247, 250));
        tabs.addTab("Sales", sales);
        tabs.addTab("Inventory", inventory);
        tabs.addTab("Procurement", procurement);
        tabs.addTab("Spoilage", spoilage);
        tabs.addTab("Returns", returns);
        // Each tab refreshes when it is opened, so its figures are never out of date
        tabs.addChangeListener(e -> loadSelectedTab());

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(titles, BorderLayout.NORTH);

        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    // Called by MainFrame whenever the Reports page is opened
    public void loadData() {
        loadSelectedTab();
    }

    private void loadSelectedTab() {
        java.awt.Component selected = tabs.getSelectedComponent();
        if (selected == sales) {
            sales.loadData();
        } else if (selected instanceof ReportTab) {
            ((ReportTab) selected).loadData();
        }
    }
}