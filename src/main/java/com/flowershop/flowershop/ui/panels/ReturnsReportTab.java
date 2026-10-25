package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.ReportDataService;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

// Returns report: every product return logged in the date range, with the refund given.
// All statuses are included, like the Total Refunds in the Sales report.
class ReturnsReportTab extends ReportTab {

    private static final String[] COLUMNS = {"Date", "Return No.", "Transaction No.", "Product", "Qty", "Reason",
            "Status", "Refund Amount"};
    private static final int[] WIDTHS = {110, 100, 130, 200, 50, 220, 100, 120};
    private static final boolean[] RIGHT = {false, false, false, false, true, false, false, true};

    private final ReportDataService data;

    ReturnsReportTab(User currentUser, AuditService auditService, ReportDataService data) {
        super(currentUser, auditService);
        this.data = data;
        build();
    }

    @Override
    protected String filterText() {
        return "Date returned";
    }

    @Override
    protected String pdfTitle() {
        return "RETURNS REPORT";
    }

    @Override
    protected String refPrefix() {
        return "RTN";
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
        return "No returns in this period.";
    }

    @Override
    protected Color cellColor(int column, String value) {
        return column == 7 ? RED : null;
    }

    @Override
    protected Result query(LocalDate from, LocalDate to) {
        List<Object[]> raw = data.returns(from, to);
        List<String[]> rows = newRows();
        BigDecimal refunds = BigDecimal.ZERO;
        long units = 0;
        for (Object[] r : raw) {
            BigDecimal refund = big(r[7]);
            refunds = refunds.add(refund);
            units += whole(r[4]);
            rows.add(new String[]{day(r[0]), text(r[1]), text(r[2]), text(r[3]), String.valueOf(whole(r[4])),
                    text(r[5]), text(r[6]), money(refund)});
        }
        BigDecimal average = raw.isEmpty() ? BigDecimal.ZERO
                : refunds.divide(BigDecimal.valueOf(raw.size()), 2, RoundingMode.HALF_UP);

        List<String[]> summary = newRows();
        summary.add(new String[]{"Total Refunds", money(refunds), "Returned to customers"});
        summary.add(new String[]{"Returns", String.valueOf(raw.size()), "Logged in this period"});
        summary.add(new String[]{"Units Returned", String.valueOf(units), "Items sent back"});
        summary.add(new String[]{"Average Refund", money(average), "Per return"});

        String[] totals = {"TOTAL", "", "", "", String.valueOf(units), "", "", money(refunds)};
        List<String> notes = List.of(
                "Returns are listed by the date they were logged.",
                "All return statuses are included; the total equals the Total Refunds in the Sales report "
                        + "for the same dates.");
        return new Result(summary, rows, totals, notes);
    }
}