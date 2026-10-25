package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.ReportDataService;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Inventory report: stock coming in and going out over a date range.
// Filter: All Movements / Stock-In / Stock-Out (plus the From / To dates).
class InventoryReportTab extends ReportTab {

    private static final String[] COLUMNS = {"Date", "Item Type", "Item", "Movement", "Reason", "Quantity",
            "Reference"};
    private static final int[] WIDTHS = {110, 90, 220, 100, 170, 80, 260};
    private static final boolean[] RIGHT = {false, false, false, false, false, true, false};

    private static final String ALL = "All Movements";
    private static final String STOCK_IN = "Stock-In";
    private static final String STOCK_OUT = "Stock-Out";

    private final ReportDataService data;
    private final JComboBox<String> cmbMovement = new JComboBox<>(new String[]{ALL, STOCK_IN, STOCK_OUT});

    InventoryReportTab(User currentUser, AuditService auditService, ReportDataService data) {
        super(currentUser, auditService);
        this.data = data;
        build();
    }

    @Override
    protected void addFilters(JPanel bar) {
        bar.add(boldLabel("Movement"));
        bar.add(cmbMovement);
        cmbMovement.addActionListener(e -> generate());
    }

    @Override
    protected String filterText() {
        return String.valueOf(cmbMovement.getSelectedItem());
    }

    @Override
    protected String pdfTitle() {
        return "INVENTORY REPORT";
    }

    @Override
    protected String refPrefix() {
        return "INV";
    }

    @Override
    protected String[] columns() {
        return COLUMNS;
    }

    @Override
    protected int[] widths() {
        return WIDTHS;
    }

    @Override
    protected boolean[] rightAligned() {
        return RIGHT;
    }

    @Override
    protected String emptyMessage() {
        return "No stock movement in this period.";
    }

    // Movement and quantity are green for stock in, red for stock out
    @Override
    protected Color cellColor(int column, String value) {
        if (column == 3) {
            return STOCK_IN.equals(value) ? GREEN : RED;
        }
        if (column == 5) {
            return value.startsWith("-") ? RED : GREEN;
        }
        return null;
    }

    @Override
    protected Result query(LocalDate from, LocalDate to) {
        String selected = String.valueOf(cmbMovement.getSelectedItem());
        String movement = STOCK_IN.equals(selected) ? ReportDataService.MOVEMENT_IN
                : STOCK_OUT.equals(selected) ? ReportDataService.MOVEMENT_OUT : ReportDataService.MOVEMENT_ALL;

        List<Object[]> raw = data.inventoryMovements(from, to, movement);
        List<String[]> rows = newRows();
        long unitsIn = 0;
        long unitsOut = 0;
        long countIn = 0;
        long countOut = 0;
        for (Object[] r : raw) {
            long quantity = whole(r[5]);
            boolean in = STOCK_IN.equals(String.valueOf(r[3]));
            if (in) {
                unitsIn += quantity;
                countIn++;
            } else {
                unitsOut += quantity;
                countOut++;
            }
            rows.add(new String[]{day(r[0]), text(r[1]), text(r[2]), text(r[3]), text(r[4]),
                    (in ? "+" : "-") + quantity, text(r[6])});
        }
        long net = unitsIn - unitsOut;

        List<String[]> summary = newRows();
        summary.add(new String[]{"Total Stock-In", unitsIn + " units", countIn + (countIn == 1 ? " movement" : " movements")});
        summary.add(new String[]{"Total Stock-Out", unitsOut + " units", countOut + (countOut == 1 ? " movement" : " movements")});
        summary.add(new String[]{"Net Movement", (net > 0 ? "+" : "") + net + " units", "Stock-In minus Stock-Out"});
        summary.add(new String[]{"Movements", String.valueOf(raw.size()), "Lines in this report"});

        String[] totals = {"NET CHANGE", "", "", "", "", (net > 0 ? "+" : "") + net, ""};
        List<String> notes = List.of(
                "Stock-In counts components received from suppliers (deliveries and walk-in purchases).",
                "Stock-Out counts products sold in completed orders and components written off as spoilage.",
                "Components used to arrange a product, and the finished products made, are not listed as movements.",
                "Quantities are units: + is stock in, - is stock out.");
        return new Result(summary, rows, totals, notes);
    }
}