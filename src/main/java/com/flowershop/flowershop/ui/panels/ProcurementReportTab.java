package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.ReportDataService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Procurement report: every purchase (supplier delivery or walk-in) received in the date range.
class ProcurementReportTab extends ReportTab {

    private static final String[] COLUMNS = {"Date Received", "Receipt No.", "Supplier", "Type", "Items", "Units",
            "Payment Method", "Payment Status", "Received By", "Total Amount"};
    private static final int[] WIDTHS = {110, 110, 160, 80, 60, 60, 120, 110, 140, 120};
    private static final boolean[] RIGHT = {false, false, false, false, true, true, false, false, false, true};

    private final ReportDataService data;

    ProcurementReportTab(User currentUser, AuditService auditService, ReportDataService data) {
        super(currentUser, auditService);
        this.data = data;
        build();
    }

    @Override
    protected String filterText() {
        return "Date received";
    }

    @Override
    protected String pdfTitle() {
        return "PROCUREMENT REPORT";
    }

    @Override
    protected String refPrefix() {
        return "PRC";
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
        return "No purchases received in this period.";
    }

    @Override
    protected Result query(LocalDate from, LocalDate to) {
        List<Object[]> raw = data.procurement(from, to);
        List<String[]> rows = newRows();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal unpaid = BigDecimal.ZERO;
        long items = 0;
        long units = 0;
        for (Object[] r : raw) {
            BigDecimal amount = big(r[9]);
            total = total.add(amount);
            items += whole(r[4]);
            units += whole(r[5]);
            if (!"PAID".equalsIgnoreCase(String.valueOf(r[7]))) {
                unpaid = unpaid.add(amount);
            }
            rows.add(new String[]{day(r[0]), text(r[1]), text(r[2]), text(r[3]), String.valueOf(whole(r[4])),
                    String.valueOf(whole(r[5])), text(r[6]), text(r[7]), text(r[8]), money(amount)});
        }

        List<String[]> summary = newRows();
        summary.add(new String[]{"Total Purchases", money(total), "Cost of stock received"});
        summary.add(new String[]{"Purchases", String.valueOf(raw.size()), "Deliveries and walk-ins"});
        summary.add(new String[]{"Units Received", String.valueOf(units), items + " item lines"});
        summary.add(new String[]{"Unpaid", money(unpaid), "Not yet marked PAID"});

        String[] totals = {"TOTAL", "", "", "", String.valueOf(items), String.valueOf(units), "", "", "",
                money(total)};
        List<String> notes = List.of(
                "Purchases are listed by the date the stock was received.",
                "Total Amount is the receipt total; when it was not entered, it is quantity received x unit cost.",
                "Walk-in means stock bought in person; Delivery means delivered by the supplier.");
        return new Result(summary, rows, totals, notes);
    }
}