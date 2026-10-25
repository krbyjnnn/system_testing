package com.flowershop.flowershop.ui.panels;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.service.AuditService;
import com.flowershop.flowershop.service.ReportDataService;

import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Spoilage report: stock that was written off or expired in the date range, with what it cost.
// Uses the same two sources as the Spoilage Cost in the Sales report, so the totals agree.
class SpoilageReportTab extends ReportTab {

    private static final String[] COLUMNS = {"Date", "Item Type", "Item", "Quantity", "Unit Cost", "Reason",
            "Total Loss", "Logged By"};
    private static final int[] WIDTHS = {110, 90, 220, 80, 100, 190, 110, 140};
    private static final boolean[] RIGHT = {false, false, false, true, true, false, true, false};
    private static final String EXPIRED = "Expired (still on shelf)";

    private final ReportDataService data;

    SpoilageReportTab(User currentUser, AuditService auditService, ReportDataService data) {
        super(currentUser, auditService);
        this.data = data;
        build();
    }

    @Override
    protected String filterText() {
        return "Logged spoilage and expired stock";
    }

    @Override
    protected String pdfTitle() {
        return "SPOILAGE REPORT";
    }

    @Override
    protected String refPrefix() {
        return "SPL";
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
        return "No spoilage in this period.";
    }

    // The loss is the figure to watch, so it stands out
    @Override
    protected Color cellColor(int column, String value) {
        return column == 6 ? RED : null;
    }

    @Override
    protected Result query(LocalDate from, LocalDate to) {
        List<Object[]> raw = data.spoilage(from, to);
        List<String[]> rows = newRows();
        BigDecimal totalLoss = BigDecimal.ZERO;
        BigDecimal loggedLoss = BigDecimal.ZERO;
        BigDecimal expiredLoss = BigDecimal.ZERO;
        long units = 0;
        for (Object[] r : raw) {
            long quantity = whole(r[3]);
            BigDecimal unitCost = big(r[4]);
            BigDecimal loss = unitCost.multiply(BigDecimal.valueOf(quantity));
            totalLoss = totalLoss.add(loss);
            units += quantity;
            if (EXPIRED.equals(String.valueOf(r[5]))) {
                expiredLoss = expiredLoss.add(loss);
            } else {
                loggedLoss = loggedLoss.add(loss);
            }
            rows.add(new String[]{day(r[0]), text(r[1]), text(r[2]), String.valueOf(quantity), money(unitCost),
                    text(r[5]), money(loss), text(r[6])});
        }

        List<String[]> summary = newRows();
        summary.add(new String[]{"Total Spoilage Loss", money(totalLoss), "Cost of stock lost"});
        summary.add(new String[]{"Units Lost", String.valueOf(units), raw.size() + (raw.size() == 1 ? " line" : " lines")});
        summary.add(new String[]{"Logged by Staff", money(loggedLoss), "Written off by staff"});
        summary.add(new String[]{"Expired on Shelf", money(expiredLoss), "Expired, not yet written off"});

        String[] totals = {"TOTAL", "", "", String.valueOf(units), "", "", money(totalLoss), ""};
        List<String> notes = List.of(
                "Total Loss = quantity x unit cost of the batch.",
                "Expired (still on shelf) is stock past its expiry date that has not been written off yet; "
                        + "it is dated by its expiry date.",
                "The total equals the Spoilage Cost shown in the Sales report for the same dates.");
        return new Result(summary, rows, totals, notes);
    }
}