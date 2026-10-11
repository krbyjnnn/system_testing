package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.Procurement;
import com.flowershop.flowershop.model.Supplier;
import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.ComponentRepository;
import com.flowershop.flowershop.repository.ProcurementRepository;
import com.flowershop.flowershop.repository.SupplierRepository;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class SupplierPanel extends JPanel {

    private final SupplierRepository supplierRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final ProcurementRepository procurementRepository;
    private final ComponentRepository componentRepository;

    // The logged-in user, saved as the one who received the procurement
    private final User currentUser;

    private JTabbedPane tabs;
    private JTextField txtSearch;
    private JTable supplierTable;
    private DefaultTableModel tableModel;
    private JLabel lblSummary;

    // Placeholder supplier for market purchases. It is not a real supplier, so it is hidden from the table.
    private static final String WALK_IN = "Walk-in / Market";

    // Column positions in the Purchased Items table
    private static final int COL_NO = 0;
    private static final int COL_COMPONENT = 1;
    private static final int COL_QTY = 2;
    private static final int COL_COST = 3;
    private static final int COL_EXPIRY = 4;
    private static final int COL_TOTAL = 5;
    private static final int COL_ACTION = 6;

    private JLabel lblFormTitle;
    private JTextField txtName;
    private JTextField txtContact;
    private JTextField txtPhone;
    private JTextField txtEmail;
    private JTextField txtAddress;
    private JButton btnAdd;
    private JButton btnUpdate;
    private JButton btnClear;
    private JButton btnToggleStatus;

    private JRadioButton rdoWalkIn;
    private JRadioButton rdoDelivery;
    private JComboBox<String> cboSource;
    private JTextField txtReceiptNo;
    private JTextField txtDateReceived;
    private JComboBox<String> cboPaymentStatus;
    private JComboBox<String> cboPaymentMethod;

    private JTable itemsTable;
    private DefaultTableModel itemsModel;
    private JComboBox<String> cboComponent;
    private JLabel lblGrandTotal;
    private JButton btnAddItem;
    private JButton btnSave;

    private DefaultTableModel historyModel;
    private JLabel lblHistorySummary;

    // Same order as the history table rows, so a clicked row maps directly to a procurement
    private List<Procurement> shownProcurements = new ArrayList<>();

    // The date chosen in the calendar popup. Starts on today.
    private LocalDate receivedDate = LocalDate.now();

    // Same order as the Supplier / Source dropdown, so the chosen index maps to a supplier
    private List<Supplier> activeSuppliers = new ArrayList<>();

    // Every component by name, so an item line can be matched back to the component it buys.
    // The full name is used for Component because it would clash with java.awt.Component.
    private final Map<String, com.flowershop.flowershop.model.Component> componentsByName = new TreeMap<>();

    // Same order as the table rows, so a row index maps directly to a supplier
    private List<Supplier> shownSuppliers = new ArrayList<>();

    // The supplier loaded in the form. null means the form is in add mode.
    private Supplier selectedSupplier = null;

    public SupplierPanel(SupplierRepository supplierRepository, ComponentInventoryRepository componentInventoryRepository,
                         ProcurementRepository procurementRepository, ComponentRepository componentRepository,
                         User currentUser) {
        this.supplierRepository = supplierRepository;
        this.componentInventoryRepository = componentInventoryRepository;
        this.procurementRepository = procurementRepository;
        this.componentRepository = componentRepository;
        this.currentUser = currentUser;

        initUI();
        loadData();
    }

    private void initUI() {
        setLayout(new BorderLayout(15, 15));
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        setBackground(new Color(245, 247, 250));

        JLabel lblTitle = new JLabel("Suppliers");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 22));
        add(lblTitle, BorderLayout.NORTH);

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        // Form and search box stacked at the top of the card
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);
        JPanel formPanel = buildFormPanel();
        JPanel searchPanel = buildSearchPanel();
        formPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        topPanel.add(formPanel);
        topPanel.add(Box.createVerticalStrut(12));
        topPanel.add(searchPanel);

        String[] columns = {"ID", "Supplier", "Contact Person", "Phone", "Email", "Address", "Status"};
        // Cells are read-only. Suppliers are edited through the form instead.
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        supplierTable = new JTable(tableModel);
        supplierTable.setRowHeight(28);
        supplierTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        supplierTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        supplierTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Status is the last column. ACTIVE gets a light green cell with dark green text,
        // INACTIVE gets a light red cell with dark red text.
        supplierTable.getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component cell = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                cell.setFont(cell.getFont().deriveFont(Font.BOLD));
                // Keep the normal selection colors on the selected row so the text stays readable
                if (!isSelected) {
                    if ("ACTIVE".equals(value)) {
                        cell.setBackground(new Color(212, 237, 222));
                        cell.setForeground(new Color(21, 87, 36));
                    } else if ("INACTIVE".equals(value)) {
                        cell.setBackground(new Color(248, 215, 218));
                        cell.setForeground(new Color(114, 28, 36));
                    }
                }
                return cell;
            }
        });

        // Clicking a row loads that supplier into the form (edit mode)
        supplierTable.getSelectionModel().addListSelectionListener(e -> {
            // Ignore the in-between events fired while the selection is still changing
            if (e.getValueIsAdjusting()) {
                return;
            }
            int row = supplierTable.getSelectedRow();
            if (row < 0 || row >= shownSuppliers.size()) {
                return;
            }
            fillForm(shownSuppliers.get(row));
        });

        lblSummary = new JLabel(" ");
        lblSummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // Disabled until a supplier is selected
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

        card.add(topPanel, BorderLayout.NORTH);
        card.add(new JScrollPane(supplierTable), BorderLayout.CENTER);
        card.add(bottomPanel, BorderLayout.SOUTH);

        tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.addTab("Suppliers", card);
        tabs.addTab("Procurement Details", buildProcurementTab());
        tabs.addTab("Procurement History", buildProcurementHistoryTab());
        // Refresh each tab when it opens: the dropdowns may be out of date after supplier changes,
        // and the history may have new procurements
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                loadActiveSuppliers();
                loadComponentChoices();
            } else if (tabs.getSelectedIndex() == 2) {
                loadHistory();
            }
        });
        add(tabs, BorderLayout.CENTER);
    }

    // Same card style as the Suppliers tab: header on top, purchased items in the middle, totals and save at the bottom
    private JPanel buildProcurementTab() {
        JPanel tab = new JPanel(new BorderLayout(0, 10));
        tab.setBackground(Color.WHITE);
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        // The footer is built before the items table because the table updates the grand total label
        JPanel footer = buildProcurementFooter();
        tab.add(buildProcurementHeader(), BorderLayout.NORTH);
        tab.add(buildItemsPanel(), BorderLayout.CENTER);
        tab.add(footer, BorderLayout.SOUTH);
        return tab;
    }

    private JPanel buildProcurementHeader() {
        JLabel lblTitle = new JLabel("Procurement Details");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JLabel lblMode = new JLabel("Transaction Mode:");
        lblMode.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rdoWalkIn = new JRadioButton("Walk-in / Direct Purchase", true);
        rdoDelivery = new JRadioButton("Supplier Delivery");
        rdoWalkIn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rdoDelivery.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        rdoWalkIn.setOpaque(false);
        rdoDelivery.setOpaque(false);
        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(rdoWalkIn);
        modeGroup.add(rdoDelivery);
        rdoWalkIn.addActionListener(e -> applyTransactionMode());
        rdoDelivery.addActionListener(e -> applyTransactionMode());

        JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        modePanel.setOpaque(false);
        modePanel.add(lblMode);
        modePanel.add(rdoWalkIn);
        modePanel.add(rdoDelivery);

        cboSource = new JComboBox<>();
        txtReceiptNo = new JTextField();
        // Read-only on purpose: the date is picked from the calendar popup, never typed
        txtDateReceived = new JTextField(receivedDate.toString());
        txtDateReceived.setEditable(false);
        txtDateReceived.setBackground(Color.WHITE);
        txtDateReceived.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        txtDateReceived.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                showReceivedCalendar();
            }
        });
        JButton btnPickDate = new JButton("▼");
        btnPickDate.addActionListener(e -> showReceivedCalendar());
        JPanel datePicker = new JPanel(new BorderLayout(4, 0));
        datePicker.setOpaque(false);
        datePicker.add(txtDateReceived, BorderLayout.CENTER);
        datePicker.add(btnPickDate, BorderLayout.EAST);
        cboPaymentStatus = new JComboBox<>(new String[]{"UNPAID", "PAID"});
        cboPaymentMethod = new JComboBox<>(new String[]{"Cash Drawer", "Online Payment - GCash/Bank"});

        // Row 1 = labels, row 2 = inputs, same layout as the supplier form
        JPanel grid = new JPanel(new GridLayout(2, 5, 10, 3));
        grid.setOpaque(false);
        String[] labels = {"Supplier / Source", "Receipt / Invoice No.", "Date Received", "Payment Status", "Payment Method"};
        for (String label : labels) {
            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            grid.add(l);
        }
        grid.add(cboSource);
        grid.add(txtReceiptNo);
        grid.add(datePicker);
        grid.add(cboPaymentStatus);
        grid.add(cboPaymentMethod);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(modePanel, BorderLayout.NORTH);
        center.add(grid, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.add(lblTitle, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);

        loadActiveSuppliers();
        applyTransactionMode();
        return panel;
    }

    // Both modes buy from the registered suppliers, so the dropdown lists only the active ones
    private void loadActiveSuppliers() {
        Object previous = cboSource.getSelectedItem();

        cboSource.removeAllItems();
        activeSuppliers.clear();
        for (Supplier s : supplierRepository.findAll()) {
            if ("ACTIVE".equals(s.getStatus()) && !WALK_IN.equalsIgnoreCase(s.getSupplierName())) {
                activeSuppliers.add(s);
                cboSource.addItem(s.getSupplierName());
            }
        }
        // Keep the earlier choice when the list is refreshed
        if (previous != null) {
            cboSource.setSelectedItem(previous);
        }
    }

    // Walk-in: payment is locked to PAID because cash is handed over on the spot.
    // Delivery: payment status can be chosen.
    private void applyTransactionMode() {
        boolean walkIn = rdoWalkIn.isSelected();
        if (walkIn) {
            cboPaymentStatus.setSelectedItem("PAID");
        }
        cboPaymentStatus.setEnabled(!walkIn);
    }

    private void showReceivedCalendar() {
        showCalendar(txtDateReceived, 0, txtDateReceived.getHeight(), receivedDate, date -> {
            receivedDate = date;
            txtDateReceived.setText(date.toString());
        }, null);
    }

    // Shared by the Date Received box and the Expiry Date cells.
    // onClear is optional: when given, the popup shows a Clear button for fields that may be left empty.
    private void showCalendar(Component invoker, int x, int y, LocalDate current,
                              Consumer<LocalDate> onPicked, Runnable onClear) {
        JPopupMenu popup = new JPopupMenu();
        showMonth(popup, YearMonth.from(current), current, onPicked, onClear);
        popup.show(invoker, x, y);
    }

    // Fills the popup with one month. The prev/next buttons call this again with the neighbouring month.
    private void showMonth(JPopupMenu popup, YearMonth month, LocalDate current,
                           Consumer<LocalDate> onPicked, Runnable onClear) {
        JButton btnPrev = new JButton("<");
        btnPrev.addActionListener(e -> showMonth(popup, month.minusMonths(1), current, onPicked, onClear));
        JButton btnNext = new JButton(">");
        btnNext.addActionListener(e -> showMonth(popup, month.plusMonths(1), current, onPicked, onClear));
        JLabel lblMonth = new JLabel(
                month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear(),
                SwingConstants.CENTER);
        lblMonth.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel header = new JPanel(new BorderLayout());
        header.add(btnPrev, BorderLayout.WEST);
        header.add(lblMonth, BorderLayout.CENTER);
        header.add(btnNext, BorderLayout.EAST);

        JPanel days = new JPanel(new GridLayout(0, 7, 2, 2));
        for (String name : new String[]{"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"}) {
            JLabel l = new JLabel(name, SwingConstants.CENTER);
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            days.add(l);
        }

        // getValue() is 1 (Monday) to 7 (Sunday), so % 7 gives how many blank cells come before day 1 on a Sunday-first grid
        int blanks = month.atDay(1).getDayOfWeek().getValue() % 7;
        for (int i = 0; i < blanks; i++) {
            days.add(new JLabel());
        }
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate date = month.atDay(day);
            JButton btnDay = new JButton(String.valueOf(day));
            btnDay.setMargin(new Insets(2, 2, 2, 2));
            btnDay.setFocusPainted(false);
            btnDay.setPreferredSize(new Dimension(36, 28));
            if (date.equals(current)) {
                btnDay.setBackground(new Color(184, 207, 229));
            }
            if (date.equals(LocalDate.now())) {
                btnDay.setFont(btnDay.getFont().deriveFont(Font.BOLD));
            }
            btnDay.addActionListener(e -> {
                onPicked.accept(date);
                popup.setVisible(false);
            });
            days.add(btnDay);
        }
        // Pad to 6 full weeks so the popup keeps the same size in every month
        for (int i = 7 + blanks + month.lengthOfMonth(); i < 7 * 7; i++) {
            days.add(new JLabel());
        }

        JPanel calendar = new JPanel(new BorderLayout(0, 6));
        calendar.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        calendar.add(header, BorderLayout.NORTH);
        calendar.add(days, BorderLayout.CENTER);

        if (onClear != null) {
            JButton btnClearDate = new JButton("Clear");
            btnClearDate.addActionListener(e -> {
                onClear.run();
                popup.setVisible(false);
            });
            calendar.add(btnClearDate, BorderLayout.SOUTH);
        }

        popup.removeAll();
        popup.add(calendar);
        popup.revalidate();
        popup.repaint();
    }

    private JPanel buildItemsPanel() {
        JLabel lblItems = new JLabel("Purchased Items");
        lblItems.setFont(new Font("Segoe UI", Font.BOLD, 14));

        String[] columns = {"#", "Component", "Qty Received", "Unit Cost (₱)", "Expiry Date", "Total (₱)", "Action"};
        // Only component, quantity and unit cost are typed or chosen in the cell.
        // Expiry is picked from the calendar, total is calculated, and Action removes the line.
        itemsModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == COL_COMPONENT || column == COL_QTY || column == COL_COST;
            }
        };
        itemsTable = new JTable(itemsModel);
        itemsTable.setRowHeight(28);
        itemsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        itemsTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        // Saves what is being typed when the user clicks elsewhere, so the totals stay up to date
        itemsTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        itemsTable.getColumnModel().getColumn(COL_NO).setMaxWidth(50);

        cboComponent = new JComboBox<>();
        itemsTable.getColumnModel().getColumn(COL_COMPONENT).setCellEditor(new DefaultCellEditor(cboComponent));

        // One click is enough to start typing in the number cells
        DefaultCellEditor numberEditor = new DefaultCellEditor(new JTextField());
        numberEditor.setClickCountToStart(1);
        itemsTable.getColumnModel().getColumn(COL_QTY).setCellEditor(numberEditor);
        itemsTable.getColumnModel().getColumn(COL_COST).setCellEditor(numberEditor);

        DefaultTableCellRenderer removeRenderer = new DefaultTableCellRenderer();
        removeRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        removeRenderer.setForeground(new Color(200, 30, 30));
        itemsTable.getColumnModel().getColumn(COL_ACTION).setCellRenderer(removeRenderer);

        // Expiry and Action cells are not editors, so clicks on them are handled here
        itemsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = itemsTable.rowAtPoint(e.getPoint());
                int col = itemsTable.columnAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                if (col == COL_ACTION) {
                    removeItemLine(row);
                } else if (col == COL_EXPIRY) {
                    pickExpiry(row);
                }
            }
        });

        // Changing quantity or unit cost recalculates that row's total, and any change updates the grand total
        itemsModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && (e.getColumn() == COL_QTY || e.getColumn() == COL_COST)) {
                itemsModel.setValueAt(formatMoney(rowTotal(e.getFirstRow())), e.getFirstRow(), COL_TOTAL);
            }
            updateGrandTotal();
        });

        btnAddItem = new JButton("+ Add Item Line");
        btnAddItem.addActionListener(e -> addItemLine());
        JPanel addPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        addPanel.setOpaque(false);
        addPanel.add(btnAddItem);

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        panel.add(lblItems, BorderLayout.NORTH);
        panel.add(new JScrollPane(itemsTable), BorderLayout.CENTER);
        panel.add(addPanel, BorderLayout.SOUTH);

        loadComponentChoices();
        addItemLine();
        return panel;
    }

    private JPanel buildProcurementFooter() {
        lblGrandTotal = new JLabel("Grand Total: " + formatMoney(BigDecimal.ZERO));
        lblGrandTotal.setFont(new Font("Segoe UI", Font.BOLD, 14));

        btnSave = new JButton("Save & Log Procurement");
        btnSave.addActionListener(e -> saveProcurement());

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(lblGrandTotal, BorderLayout.WEST);
        footer.add(btnSave, BorderLayout.EAST);
        return footer;
    }

    // Every registered component can be bought here, including ones that have no stock yet
    private void loadComponentChoices() {
        componentsByName.clear();
        for (com.flowershop.flowershop.model.Component component : componentRepository.findAll()) {
            componentsByName.put(component.getName(), component);
        }

        cboComponent.removeAllItems();
        // Blank choice so a new line starts empty instead of copying the last selection
        cboComponent.addItem("");
        for (String name : componentsByName.keySet()) {
            cboComponent.addItem(name);
        }
    }

    private void addItemLine() {
        stopEditing();
        itemsModel.addRow(new Object[]{
                itemsModel.getRowCount() + 1, "", "", "", "-", formatMoney(BigDecimal.ZERO), "Remove"
        });
    }

    private void removeItemLine(int row) {
        stopEditing();
        itemsModel.removeRow(row);
        // Renumber so the # column stays 1, 2, 3...
        for (int i = 0; i < itemsModel.getRowCount(); i++) {
            itemsModel.setValueAt(i + 1, i, COL_NO);
        }
    }

    // Expiry is optional, so the calendar has a Clear button that puts back the "-"
    private void pickExpiry(int row) {
        stopEditing();
        Rectangle cell = itemsTable.getCellRect(row, COL_EXPIRY, false);
        LocalDate current = readExpiry(row);
        if (current == null) {
            current = LocalDate.now();
        }
        showCalendar(itemsTable, cell.x, cell.y + cell.height, current,
                date -> itemsModel.setValueAt(date.toString(), row, COL_EXPIRY),
                () -> itemsModel.setValueAt("-", row, COL_EXPIRY));
    }

    // The expiry cell holds a date, or "-" when there is none
    private LocalDate readExpiry(int row) {
        try {
            return LocalDate.parse(String.valueOf(itemsModel.getValueAt(row, COL_EXPIRY)));
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    // Commits a half-typed cell before the rows change
    private void stopEditing() {
        if (itemsTable.isEditing()) {
            itemsTable.getCellEditor().stopCellEditing();
        }
    }

    // Total = quantity x unit cost. Anything that is not a number counts as 0.
    private BigDecimal rowTotal(int row) {
        BigDecimal qty = parseNumber(itemsModel.getValueAt(row, COL_QTY));
        BigDecimal cost = parseNumber(itemsModel.getValueAt(row, COL_COST));
        return qty.multiply(cost);
    }

    private BigDecimal parseNumber(Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private void updateGrandTotal() {
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (int row = 0; row < itemsModel.getRowCount(); row++) {
            grandTotal = grandTotal.add(rowTotal(row));
        }
        lblGrandTotal.setText("Grand Total: " + formatMoney(grandTotal));
    }

    // Logs one procurement and adds each item line to the inventory as a new batch,
    // so the purchased components show up in the component stock.
    private void saveProcurement() {
        stopEditing();

        int sourceIndex = cboSource.getSelectedIndex();
        if (sourceIndex < 0) {
            JOptionPane.showMessageDialog(this, "Select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (itemsModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Add at least one item line.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Supplier supplier = activeSuppliers.get(sourceIndex);
        BigDecimal grandTotal = BigDecimal.ZERO;
        List<ComponentInventory> items = new ArrayList<>();
        for (int row = 0; row < itemsModel.getRowCount(); row++) {
            String componentName = String.valueOf(itemsModel.getValueAt(row, COL_COMPONENT)).trim();
            BigDecimal qty = parseNumber(itemsModel.getValueAt(row, COL_QTY));
            BigDecimal cost = parseNumber(itemsModel.getValueAt(row, COL_COST));
            boolean wholeQty = qty.signum() > 0 && qty.remainder(BigDecimal.ONE).signum() == 0;
            if (componentName.isEmpty() || !wholeQty || cost.signum() <= 0) {
                JOptionPane.showMessageDialog(this,
                        "Item line " + (row + 1) + " needs a component, a whole-number quantity and a unit cost.",
                        "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            com.flowershop.flowershop.model.Component component = componentsByName.get(componentName);
            if (component == null) {
                JOptionPane.showMessageDialog(this,
                        "Item line " + (row + 1) + ": component not found. Reopen this tab and choose it again.",
                        "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Perishable components need an expiry date so spoilage can be tracked
            LocalDate expiry = readExpiry(row);
            boolean perishable = component.getCategory() != null && component.getCategory().isPerishable();
            if (perishable && expiry == null) {
                JOptionPane.showMessageDialog(this,
                        "Item line " + (row + 1) + ": " + componentName + " is perishable, so it needs an expiry date.",
                        "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (expiry != null && expiry.isBefore(receivedDate)) {
                JOptionPane.showMessageDialog(this,
                        "Item line " + (row + 1) + ": the expiry date cannot be before the date received.",
                        "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            grandTotal = grandTotal.add(rowTotal(row));

            // A new batch: everything received is still on hand
            ComponentInventory item = new ComponentInventory(supplier, component, qty.intValueExact(),
                    component.getReorderLevel(), cost.setScale(2, RoundingMode.HALF_UP), receivedDate, expiry);
            item.setQuantityReceived(qty.intValueExact());
            items.add(item);
        }

        // A blank receipt number is saved as null, not as an empty text
        String receiptNo = txtReceiptNo.getText().trim();
        Procurement procurement = new Procurement(
                supplier,
                receiptNo.isEmpty() ? null : receiptNo,
                receivedDate,
                (String) cboPaymentMethod.getSelectedItem(),
                currentUser);
        procurement.setTransactionType(rdoWalkIn.isSelected() ? "walk_in" : "delivery");
        procurement.setPaymentStatus((String) cboPaymentStatus.getSelectedItem());
        procurement.setTotalAmount(grandTotal.setScale(2, RoundingMode.HALF_UP));

        Procurement saved = null;
        try {
            saved = procurementRepository.save(procurement);
            // The items can only be linked once the procurement has its id
            for (ComponentInventory item : items) {
                item.setSdelivery(saved);
            }
            componentInventoryRepository.saveAll(items);

            resetProcurementForm();
            // Opening the history tab reloads it, so the new procurement shows at the top
            tabs.setSelectedIndex(2);
        } catch (Exception ex) {
            // Undo the procurement so it does not exist without its items
            if (saved != null) {
                try {
                    procurementRepository.delete(saved);
                } catch (Exception ignored) {
                }
            }
            JOptionPane.showMessageDialog(this, "Procurement was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Back to a blank form, ready for the next receipt
    private void resetProcurementForm() {
        rdoWalkIn.setSelected(true);
        txtReceiptNo.setText("");
        receivedDate = LocalDate.now();
        txtDateReceived.setText(receivedDate.toString());
        cboPaymentMethod.setSelectedIndex(0);
        if (cboSource.getItemCount() > 0) {
            cboSource.setSelectedIndex(0);
        }
        applyTransactionMode();

        itemsModel.setRowCount(0);
        addItemLine();
    }

    private String formatMoney(BigDecimal amount) {
        return "₱" + String.format("%,.2f", amount);
    }

    private JPanel buildProcurementHistoryTab() {
        JLabel lblTitle = new JLabel("Procurement History");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        String[] columns = {"Date Received", "Transaction Mode", "Supplier", "Receipt No.", "Payment Status",
                "Payment Method", "Total", "Received By"};
        historyModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable historyTable = new JTable(historyModel);
        historyTable.setRowHeight(28);
        historyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        historyTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Clicking a row opens a window with the items that were purchased
        historyTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = historyTable.rowAtPoint(e.getPoint());
                if (row >= 0 && row < shownProcurements.size()) {
                    showProcurementItems(shownProcurements.get(row));
                }
            }
        });

        lblHistorySummary = new JLabel(" ");
        lblHistorySummary.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JPanel tab = new JPanel(new BorderLayout(0, 10));
        tab.setBackground(Color.WHITE);
        tab.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        tab.add(lblTitle, BorderLayout.NORTH);
        tab.add(new JScrollPane(historyTable), BorderLayout.CENTER);
        tab.add(lblHistorySummary, BorderLayout.SOUTH);
        return tab;
    }

    // One row per procurement, newest first
    private void loadHistory() {
        historyModel.setRowCount(0);
        shownProcurements.clear();

        List<Procurement> procurements = new ArrayList<>(procurementRepository.findAll());
        procurements.sort(Comparator.comparing(Procurement::getDateReceived, Comparator.reverseOrder())
                .thenComparing(Procurement::getSdeliveryId, Comparator.reverseOrder()));

        BigDecimal grandTotal = BigDecimal.ZERO;
        for (Procurement p : procurements) {
            shownProcurements.add(p);
            grandTotal = grandTotal.add(p.getTotalAmount());
            historyModel.addRow(new Object[]{
                    p.getDateReceived().toString(),
                    modeLabel(p.getTransactionType()),
                    p.getSupplier().getSupplierName(),
                    text(p.getReceiptNumber()),
                    text(p.getPaymentStatus()),
                    text(p.getPaymentMethod()),
                    formatMoney(p.getTotalAmount()),
                    p.getReceivedBy().getFullName()
            });
        }

        lblHistorySummary.setText(procurements.size() + " procurements  |  Total: " + formatMoney(grandTotal));
    }

    // Read-only receipt view: the procurement details on top, the purchased items below.
    // The items are the inventory batches that were linked to this procurement when it was saved.
    private void showProcurementItems(Procurement procurement) {
        JPanel details = new JPanel(new GridLayout(0, 2, 20, 4));
        details.setOpaque(false);
        addDetail(details, "Date Received", procurement.getDateReceived().toString());
        addDetail(details, "Transaction Mode", modeLabel(procurement.getTransactionType()));
        addDetail(details, "Supplier", procurement.getSupplier().getSupplierName());
        addDetail(details, "Receipt No.", text(procurement.getReceiptNumber()).isEmpty() ? "-" : procurement.getReceiptNumber());
        addDetail(details, "Payment", text(procurement.getPaymentStatus()) + " (" + text(procurement.getPaymentMethod()) + ")");
        addDetail(details, "Received By", procurement.getReceivedBy().getFullName());

        String[] columns = {"Component", "Qty Received", "Unit Cost", "Total", "Expiry Date"};
        DefaultTableModel itemModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // findAllDelivered() returns every delivered batch, so keep only the ones of this procurement, in the order they were saved
        List<ComponentInventory> items = new ArrayList<>();
        for (ComponentInventory batch : componentInventoryRepository.findAllDelivered()) {
            if (batch.getSdelivery().getSdeliveryId().equals(procurement.getSdeliveryId())) {
                items.add(batch);
            }
        }
        items.sort(Comparator.comparing(ComponentInventory::getCbatchId));

        for (ComponentInventory item : items) {
            BigDecimal total = item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantityReceived()));
            itemModel.addRow(new Object[]{
                    item.getComponent().getName(),
                    item.getQuantityReceived(),
                    formatMoney(item.getUnitCost()),
                    formatMoney(total),
                    item.getExpiryDate() == null ? "-" : item.getExpiryDate().toString()
            });
        }

        JTable itemTable = new JTable(itemModel);
        itemTable.setRowHeight(28);
        itemTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 13));
        itemTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Procurements saved before items were linked to them have no items to show
        JLabel lblTotal = new JLabel(items.isEmpty()
                ? "No items were recorded for this procurement."
                : "Grand Total: " + formatMoney(procurement.getTotalAmount()));
        lblTotal.setFont(new Font("Segoe UI", Font.BOLD, 14));

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Procurement Details",
                Dialog.ModalityType.APPLICATION_MODAL);
        JButton btnClose = new JButton("Close");
        btnClose.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(lblTotal, BorderLayout.WEST);
        footer.add(btnClose, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        content.add(details, BorderLayout.NORTH);
        content.add(new JScrollPane(itemTable), BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setSize(720, 460);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // One "label: value" pair in the details grid
    private void addDetail(JPanel panel, String label, String value) {
        JLabel lblName = new JLabel(label + ":");
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        JLabel lblValue = new JLabel(value);
        lblValue.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(lblName);
        panel.add(lblValue);
    }

    // The database stores a short code, the screen shows the same wording as the toggle
    private String modeLabel(String transactionType) {
        if ("walk_in".equalsIgnoreCase(transactionType)) {
            return "Walk-in / Direct Purchase";
        }
        if ("delivery".equalsIgnoreCase(transactionType)) {
            return "Supplier Delivery";
        }
        return text(transactionType);
    }

    private JPanel buildFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setOpaque(false);

        lblFormTitle = new JLabel("Add Supplier");
        lblFormTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));

        txtName = new JTextField();
        txtContact = new JTextField();
        txtPhone = new JTextField();
        txtEmail = new JTextField();
        txtAddress = new JTextField();

        // Row 1 = labels, row 2 = inputs, so each label sits right above its field
        JPanel grid = new JPanel(new GridLayout(2, 5, 10, 3));
        grid.setOpaque(false);
        String[] labels = {"Supplier Name", "Contact Person", "Phone", "Email", "Address"};
        for (String label : labels) {
            JLabel l = new JLabel(label);
            l.setFont(new Font("Segoe UI", Font.BOLD, 12));
            grid.add(l);
        }
        grid.add(txtName);
        grid.add(txtContact);
        grid.add(txtPhone);
        grid.add(txtEmail);
        grid.add(txtAddress);

        // Both buttons share saveSupplier(). The flag says whether to create or update.
        btnAdd = new JButton("Add Supplier");
        btnAdd.addActionListener(e -> saveSupplier(true));
        btnUpdate = new JButton("Update Supplier");
        btnUpdate.addActionListener(e -> saveSupplier(false));
        btnUpdate.setEnabled(false);
        btnClear = new JButton("Clear");
        btnClear.addActionListener(e -> clearForm());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(btnAdd);
        buttons.add(btnUpdate);
        buttons.add(btnClear);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setOpaque(false);
        center.add(grid, BorderLayout.NORTH);
        center.add(buttons, BorderLayout.SOUTH);

        panel.add(lblFormTitle, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSearchPanel() {
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setOpaque(false);
        txtSearch = new JTextField();
        // Pressing Enter or clicking Search both reload the table using the typed keyword
        txtSearch.addActionListener(e -> loadData());
        JButton btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> loadData());
        JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchButtons.setOpaque(false);
        searchButtons.add(btnSearch);
        searchPanel.add(new JLabel("Search: "), BorderLayout.WEST);
        searchPanel.add(txtSearch, BorderLayout.CENTER);
        searchPanel.add(searchButtons, BorderLayout.EAST);
        return searchPanel;
    }

    // Edit mode: Add is disabled so a loaded supplier can't be duplicated by accident
    private void fillForm(Supplier supplier) {
        selectedSupplier = supplier;
        txtName.setText(text(supplier.getSupplierName()));
        txtContact.setText(text(supplier.getContactPerson()));
        txtPhone.setText(text(supplier.getPhone()));
        txtEmail.setText(text(supplier.getEmail()));
        txtAddress.setText(text(supplier.getAddress()));

        lblFormTitle.setText("Edit Supplier: " + supplier.getSupplierName());
        btnAdd.setEnabled(false);
        btnUpdate.setEnabled(true);

        // The button label shows the action it will do, the opposite of the current status
        boolean active = "ACTIVE".equals(supplier.getStatus());
        btnToggleStatus.setText(active ? "Set Inactive" : "Set Active");
        btnToggleStatus.setEnabled(true);
    }

    // Back to add mode
    private void clearForm() {
        selectedSupplier = null;
        supplierTable.clearSelection();
        txtName.setText("");
        txtContact.setText("");
        txtPhone.setText("");
        txtEmail.setText("");
        txtAddress.setText("");

        lblFormTitle.setText("Add Supplier");
        btnAdd.setEnabled(true);
        btnUpdate.setEnabled(false);

        btnToggleStatus.setText("Set Inactive");
        btnToggleStatus.setEnabled(false);
    }

    // Rebuilds the table from the database, filtered by the search keyword
    public void loadData() {
        String keyword = txtSearch.getText().trim().toLowerCase();
        shownSuppliers.clear();
        tableModel.setRowCount(0);

        // Inactive suppliers are still listed so they can be reactivated
        for (Supplier s : supplierRepository.findAll()) {
            if (WALK_IN.equalsIgnoreCase(s.getSupplierName())) {
                continue;
            }
            // The keyword can match either the supplier name or the contact person
            boolean matches = s.getSupplierName().toLowerCase().contains(keyword)
                    || text(s.getContactPerson()).toLowerCase().contains(keyword);
            if (!matches) {
                continue;
            }

            shownSuppliers.add(s);
            tableModel.addRow(new Object[]{
                    s.getSupplierId(),
                    s.getSupplierName(),
                    text(s.getContactPerson()),
                    text(s.getPhone()),
                    text(s.getEmail()),
                    text(s.getAddress()),
                    s.getStatus()
            });
        }

        lblSummary.setText(shownSuppliers.size() + " suppliers");

        // Rows were rebuilt, so any selection or edit state is stale
        clearForm();
    }

    // Suppliers are never deleted, so old inventory batches keep their supplier history
    private void toggleStatus() {
        if (selectedSupplier == null) {
            JOptionPane.showMessageDialog(this, "Select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean active = "ACTIVE".equals(selectedSupplier.getStatus());
        String action = active ? "deactivate" : "activate";
        int confirm = JOptionPane.showConfirmDialog(this,
                "Do you want to " + action + " " + selectedSupplier.getSupplierName() + "?",
                "Change Status", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            selectedSupplier.setStatus(active ? "INACTIVE" : "ACTIVE");
            supplierRepository.save(selectedSupplier);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Status was not changed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            // Reload to discard the unsaved status change on the in-memory object
            loadData();
        }
    }

    // isNew = true creates a supplier, false updates the one loaded in the form
    private void saveSupplier(boolean isNew) {
        if (txtName.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Supplier name is required.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Supplier supplier = isNew ? new Supplier() : selectedSupplier;
        if (supplier == null) {
            JOptionPane.showMessageDialog(this, "Select a supplier first.", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        supplier.setSupplierName(txtName.getText().trim());
        supplier.setContactPerson(txtContact.getText().trim());
        supplier.setPhone(txtPhone.getText().trim());
        supplier.setEmail(txtEmail.getText().trim());
        supplier.setAddress(txtAddress.getText().trim());

        try {
            supplierRepository.save(supplier);
            loadData();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Supplier was not saved: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Avoids showing "null" in the UI
    private String text(String value) {
        return value == null ? "" : value;
    }
}