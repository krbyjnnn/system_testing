package com.flowershop.flowershop.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Business-style sales report: branded banner, report details, a 2 x 4 grid of key figures, income statement next to
// the payment breakdown, daily breakdown, list of completed orders, notes and sign-off lines.
// Separate from PdfReport so the generic table reports keep their own layout.
public final class SalesReportPdf {

    // ---- EDIT THESE: printed in the banner. A blank value is left out. ----
    private static final String SHOP_NAME = "Junaie's Flower Shop";
    private static final String SHOP_LOCATION = "Davao City, Philippines";
    private static final String SHOP_ADDRESS = "";   // e.g. "Street, Barangay, Davao City"
    private static final String SHOP_CONTACT = "";   // e.g. "Tel/Mobile: 09XX XXX XXXX  |  email@example.com"
    private static final String SHOP_TIN = "";       // e.g. "TIN: 000-000-000-000"

    private static final Color BANNER = new Color(2, 2, 14); // same black as the logo background
    private static final Color BRAND = new Color(25, 135, 84);
    private static final Color BLUE = new Color(13, 110, 253);
    private static final Color RED = new Color(190, 40, 55);
    private static final Color PURPLE = new Color(111, 66, 193);
    private static final Color AMBER = new Color(214, 150, 0);
    private static final Color TEAL = new Color(23, 162, 184);
    private static final Color GRAY = new Color(108, 117, 125);
    private static final Color HEADER_BACKGROUND = new Color(230, 234, 240);
    private static final Color ZEBRA = new Color(248, 249, 251);
    private static final Color LINE = new Color(210, 214, 220);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Color TEXT = new Color(40, 40, 40);

    private static final String[] ORDER_COLUMNS = {"Transaction No.", "Date & Time", "Cashier", "Order Type",
            "Items", "Payment", "Amount"};

    private SalesReportPdf() {
    }

    // Everything the report prints.
    // dailyRows: already formatted, one cell per report column.
    // payments: {method (String), number of payments (Long), total (BigDecimal)}.
    // orderLines: already formatted, one cell per ORDER_COLUMNS entry.
    public record Data(String reportType, String range, String preparedBy,
                       BigDecimal revenue, BigDecimal refunds, BigDecimal cogs, BigDecimal spoilage,
                       long orders, long items, String[] columns, List<Object[]> dailyRows,
                       List<Object[]> payments, List<Object[]> orderLines) {
    }

    public static void write(File file, Data data) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageFooter());
            document.open();

            document.add(banner(data));
            document.add(details(data));
            document.add(keyFigures(data));
            document.add(statementAndPayments(data));
            document.add(dailyBreakdown(data));
            document.add(completedOrders(data));
            document.add(notes());
            document.add(signOff(data));
            // Closing writes the end of the PDF, so it has to happen before the file stream is closed
            document.close();
        }
    }

    private static PdfPTable banner(Data data) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 4, 3});

        Image logo = loadLogo();
        PdfPCell logoCell = logo == null ? new PdfPCell(new Phrase("")) : new PdfPCell(logo, false);
        styleBanner(logoCell);
        table.addCell(logoCell);

        PdfPCell name = new PdfPCell();
        styleBanner(name);
        name.addElement(new Paragraph(SHOP_NAME, font(18, true, Color.WHITE)));
        name.addElement(new Paragraph(SHOP_LOCATION, font(10, false, Color.WHITE)));
        if (!SHOP_ADDRESS.isBlank()) {
            name.addElement(new Paragraph(SHOP_ADDRESS, font(9, false, Color.WHITE)));
        }
        if (!SHOP_CONTACT.isBlank()) {
            name.addElement(new Paragraph(SHOP_CONTACT, font(9, false, Color.WHITE)));
        }
        if (!SHOP_TIN.isBlank()) {
            name.addElement(new Paragraph(SHOP_TIN, font(9, false, Color.WHITE)));
        }
        table.addCell(name);

        PdfPCell right = new PdfPCell();
        styleBanner(right);
        Paragraph title = new Paragraph("SALES REPORT", font(18, true, Color.WHITE));
        title.setAlignment(Element.ALIGN_RIGHT);
        Paragraph type = new Paragraph(data.reportType(), font(10, false, Color.WHITE));
        type.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
        right.addElement(type);
        table.addCell(right);
        return table;
    }

    private static void styleBanner(PdfPCell cell) {
        cell.setBackgroundColor(BANNER);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BRAND);
        cell.setBorderWidthBottom(3);
        cell.setPadding(10);
    }

    // The logo is the same file the app shows in its sidebar; the report still prints without it
    private static Image loadLogo() {
        try {
            URL url = SalesReportPdf.class.getResource("/static/logo.jpg");
            if (url == null) {
                return null;
            }
            Image logo = Image.getInstance(url);
            logo.scaleToFit(60, 56);
            return logo;
        } catch (Exception ex) {
            return null;
        }
    }

    private static PdfPTable details(Data data) throws DocumentException {
        LocalDateTime now = LocalDateTime.now();
        String[][] pairs = {
                {"REPORT PERIOD", data.range()},
                {"DATE & TIME PRINTED", now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"))},
                {"PREPARED BY", data.preparedBy()},
                {"REFERENCE NO.", "SR-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}
        };
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        for (String[] pair : pairs) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(LINE);
            cell.setPaddingTop(4);
            cell.setPaddingBottom(8);
            cell.setPaddingLeft(2);
            cell.addElement(new Paragraph(pair[0], font(8, true, MUTED)));
            cell.addElement(new Paragraph(pair[1], font(11, true, TEXT)));
            table.addCell(cell);
        }
        return table;
    }

    // 2 rows x 4 boxes, each with a coloured top edge; narrow empty columns and a short empty row act as gaps
    private static PdfPTable keyFigures(Data data) throws DocumentException {
        BigDecimal net = net(data);
        BigDecimal averageOrder = data.orders() == 0 ? BigDecimal.ZERO
                : data.revenue().divide(BigDecimal.valueOf(data.orders()), 2, RoundingMode.HALF_UP);

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 0.06f, 1, 0.06f, 1, 0.06f, 1});
        table.setSpacingBefore(14);
        table.setKeepTogether(true);

        metricBox(table, "GROSS REVENUE", money(data.revenue()), "Completed, paid orders", BLUE, TEXT, true);
        metricBox(table, "TOTAL REFUNDS", money(data.refunds()),
                percent(data.refunds(), data.revenue()) + " of gross revenue", RED, TEXT, true);
        metricBox(table, "COST OF GOODS SOLD", money(data.cogs()), "Estimated from batch cost", AMBER, TEXT, true);
        metricBox(table, "SPOILAGE COST", money(data.spoilage()), "Expired and wilted stock", PURPLE, TEXT, false);

        PdfPCell gap = blank();
        gap.setColspan(7);
        gap.setFixedHeight(8);
        table.addCell(gap);

        metricBox(table, "NET CASH FLOW", money(net), percent(net, data.revenue()) + " net margin", BRAND,
                net.signum() < 0 ? RED : TEXT, true);
        metricBox(table, "ORDERS", String.valueOf(data.orders()), "Completed orders", TEAL, TEXT, true);
        metricBox(table, "ITEMS SOLD", String.valueOf(data.items()), "Units sold", TEAL, TEXT, true);
        metricBox(table, "AVERAGE ORDER VALUE", money(averageOrder), "Gross revenue per order", GRAY, TEXT, false);
        return table;
    }

    private static void metricBox(PdfPTable table, String label, String value, String caption, Color accent,
                                  Color valueColor, boolean gapAfter) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(LINE);
        cell.setBorderWidthTop(3);
        cell.setBorderColorTop(accent);
        cell.setPadding(10);
        cell.addElement(new Paragraph(label, font(8, true, MUTED)));
        cell.addElement(new Paragraph(value, font(16, true, valueColor)));
        cell.addElement(new Paragraph(caption, font(8, false, MUTED)));
        table.addCell(cell);
        if (gapAfter) {
            table.addCell(blank());
        }
    }

    // Income statement on the left, payment breakdown on the right (so the register can be balanced against
    // digital receipts). Both are laid out as rows of one table so they line up.
    private static PdfPTable statementAndPayments(Data data) throws DocumentException {
        BigDecimal netSales = data.revenue().subtract(data.refunds());
        BigDecimal grossProfit = netSales.subtract(data.cogs());
        BigDecimal net = net(data);

        String[][] statement = {
                {"Gross Revenue", money(data.revenue()), "plain"},
                {"Less: Refunds", money(data.refunds()), "plain"},
                {"Net Sales", money(netSales), "subtotal"},
                {"Less: Cost of Goods Sold", money(data.cogs()), "plain"},
                {"Gross Profit", money(grossProfit), "subtotal"},
                {"Less: Spoilage Loss", money(data.spoilage()), "plain"},
                {"Net Cash Flow", money(net), "total"}
        };

        List<String[]> payments = new java.util.ArrayList<>();
        payments.add(new String[]{"Method", "Payments", "Amount", "header"});
        BigDecimal paidTotal = BigDecimal.ZERO;
        long paidCount = 0;
        for (Object[] row : data.payments()) {
            BigDecimal amount = (BigDecimal) row[2];
            paidTotal = paidTotal.add(amount);
            paidCount += (Long) row[1];
            payments.add(new String[]{String.valueOf(row[0]), String.valueOf(row[1]), money(amount), "plain"});
        }
        if (data.payments().isEmpty()) {
            payments.add(new String[]{"No payments received", "", "", "plain"});
        } else {
            payments.add(new String[]{"Total Received", String.valueOf(paidCount), money(paidTotal), "total"});
        }

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 2, 0.4f, 2.4f, 1.1f, 2});
        table.setSpacingBefore(16);
        table.setKeepTogether(true);

        table.addCell(sectionHeading("Income Statement", 2));
        table.addCell(blank());
        table.addCell(sectionHeading("Payment Breakdown", 3));

        int rows = Math.max(statement.length, payments.size());
        for (int i = 0; i < rows; i++) {
            if (i < statement.length) {
                table.addCell(statementCell(statement[i][0], statement[i][2], false, net));
                table.addCell(statementCell(statement[i][1], statement[i][2], true, net));
            } else {
                table.addCell(blank());
                table.addCell(blank());
            }
            table.addCell(blank());
            if (i < payments.size()) {
                String[] p = payments.get(i);
                table.addCell(paymentCell(p[0], p[3], Element.ALIGN_LEFT));
                table.addCell(paymentCell(p[1], p[3], Element.ALIGN_RIGHT));
                table.addCell(paymentCell(p[2], p[3], Element.ALIGN_RIGHT));
            } else {
                table.addCell(blank());
                table.addCell(blank());
                table.addCell(blank());
            }
        }
        return table;
    }

    private static PdfPCell emptyMessage(String text, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(9, false, MUTED)));
        cell.setColspan(colspan);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private static PdfPTable dailyBreakdown(Data data) throws DocumentException {
        String[] columns = data.columns();
        PdfPTable table = new PdfPTable(columns.length);
        table.setWidthPercentage(100);
        if (columns.length == 8) {
            table.setWidths(new float[]{2.2f, 0.8f, 1f, 1.5f, 1.3f, 1.8f, 1.3f, 1.5f});
        }
        table.setSpacingBefore(20);
        // The heading and the column titles repeat on every page when the period is long
        table.setHeaderRows(2);

        table.addCell(sectionHeading("Daily Breakdown", columns.length));
        for (int i = 0; i < columns.length; i++) {
            table.addCell(headerCell(columns[i], i == 0));
        }

        if (data.dailyRows().isEmpty()) {
            table.addCell(emptyMessage("No sales activity in this period.", columns.length));
        }
        int rowNumber = 0;
        for (Object[] row : data.dailyRows()) {
            Color background = rowNumber++ % 2 == 1 ? ZEBRA : null;
            for (int i = 0; i < row.length; i++) {
                String text = String.valueOf(row[i]);
                Color color = TEXT;
                boolean bold = false;
                if (i == row.length - 1) {
                    color = text.startsWith("-") ? RED : BRAND;
                    bold = true;
                }
                table.addCell(bodyCell(text, i == 0, bold, color, background, false));
            }
        }

        // Totals row, so the table adds up to the figures at the top of the report
        BigDecimal net = net(data);
        String[] totals = {"TOTAL", String.valueOf(data.orders()), String.valueOf(data.items()),
                money(data.revenue()), money(data.refunds()), money(data.cogs()), money(data.spoilage()), money(net)};
        for (int i = 0; i < totals.length; i++) {
            Color color = i == totals.length - 1 ? (net.signum() < 0 ? RED : BRAND) : TEXT;
            table.addCell(bodyCell(totals[i], i == 0, true, color, HEADER_BACKGROUND, true));
        }
        return table;
    }

    // One line per completed order, so the printed report can be checked against the receipts
    private static PdfPTable completedOrders(Data data) throws DocumentException {
        PdfPTable table = new PdfPTable(ORDER_COLUMNS.length);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.2f, 2.2f, 2f, 1.3f, 0.7f, 1.5f, 1.5f});
        table.setSpacingBefore(20);
        table.setHeaderRows(2);

        table.addCell(sectionHeading("Completed Orders (" + data.orders() + ")", ORDER_COLUMNS.length));
        for (int i = 0; i < ORDER_COLUMNS.length; i++) {
            boolean leftAligned = i < 4 || i == 5;
            table.addCell(headerCell(ORDER_COLUMNS[i], leftAligned));
        }

        if (data.orderLines().isEmpty()) {
            table.addCell(emptyMessage("No completed orders in this period.", ORDER_COLUMNS.length));
        }
        int rowNumber = 0;
        for (Object[] row : data.orderLines()) {
            Color background = rowNumber++ % 2 == 1 ? ZEBRA : null;
            for (int i = 0; i < row.length; i++) {
                boolean leftAligned = i < 4 || i == 5;
                table.addCell(bodyCell(String.valueOf(row[i]), leftAligned, i == row.length - 1, TEXT, background,
                        false));
            }
        }

        if (!data.orderLines().isEmpty()) {
            String[] totals = {"TOTAL", "", "", "", String.valueOf(data.items()), "", money(data.revenue())};
            for (int i = 0; i < totals.length; i++) {
                boolean leftAligned = i < 4 || i == 5;
                table.addCell(bodyCell(totals[i], leftAligned, true, TEXT, HEADER_BACKGROUND, true));
            }
        }
        return table;
    }

    private static PdfPTable notes() throws DocumentException {
        String[] lines = {
                "Gross revenue counts completed (paid) orders only.",
                "Refunds are counted on the date the return was logged.",
                "Cost of goods sold is estimated from the average batch cost of each product sold.",
                "Spoilage includes expired stock still on the shelf and spoilage logged by staff.",
                "Payments are counted on the date they were received, so they can differ from revenue "
                        + "when an order is paid on a different day.",
                "The Payment Breakdown shows cash and digital payments, so the register and the GCash / Maya records "
                        + "can be checked against it.",
                "Net Cash Flow = Gross Revenue - Refunds - Cost of Goods Sold - Spoilage Loss."
        };
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(18);
        // Keeps the notes from being split across two pages
        table.setKeepTogether(true);

        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(LINE);
        cell.setPaddingTop(8);
        cell.addElement(new Paragraph("NOTES", font(8, true, MUTED)));
        for (String line : lines) {
            cell.addElement(new Paragraph("-  " + line, font(8, false, MUTED)));
        }
        table.addCell(cell);
        return table;
    }

    private static PdfPTable signOff(Data data) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3, 1, 3, 1, 3});
        table.setSpacingBefore(40);
        // Keeps the three signature lines on the same page
        table.setKeepTogether(true);

        String[][] signers = {{"Prepared by", data.preparedBy()}, {"Reviewed by", ""}, {"Approved by", "Owner"}};
        for (int i = 0; i < signers.length; i++) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(Rectangle.TOP);
            cell.setBorderColor(TEXT);
            cell.setPaddingTop(4);
            cell.addElement(new Paragraph(signers[i][0], font(8, true, MUTED)));
            if (!signers[i][1].isEmpty()) {
                cell.addElement(new Paragraph(signers[i][1], font(10, false, TEXT)));
            }
            table.addCell(cell);
            if (i < signers.length - 1) {
                table.addCell(blank());
            }
        }
        return table;
    }

    private static PdfPCell sectionHeading(String text, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text.toUpperCase(), font(10, true, BRAND)));
        cell.setColspan(colspan);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(BRAND);
        cell.setBorderWidthBottom(1.5f);
        cell.setPaddingLeft(0);
        cell.setPaddingBottom(5);
        return cell;
    }

    private static PdfPCell headerCell(String text, boolean leftAligned) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font(9, true, TEXT)));
        cell.setBackgroundColor(HEADER_BACKGROUND);
        cell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setPadding(6);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(leftAligned ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
        return cell;
    }

    private static PdfPCell statementCell(String text, String kind, boolean amount, BigDecimal net) {
        boolean strong = !"plain".equals(kind);
        Color color = TEXT;
        if ("total".equals(kind) && amount) {
            color = net.signum() < 0 ? RED : BRAND;
        }
        PdfPCell cell = new PdfPCell(new Phrase(text, font(10, strong, color)));
        cell.setPadding(5);
        cell.setPaddingLeft(amount ? 5 : ("plain".equals(kind) ? 14 : 5));
        cell.setHorizontalAlignment(amount ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        if ("subtotal".equals(kind)) {
            cell.setBackgroundColor(ZEBRA);
        } else if ("total".equals(kind)) {
            cell.setBackgroundColor(HEADER_BACKGROUND);
        }
        return cell;
    }

    private static PdfPCell paymentCell(String text, String kind, int alignment) {
        boolean strong = !"plain".equals(kind);
        PdfPCell cell = new PdfPCell(new Phrase(text, font(10, strong, TEXT)));
        cell.setPadding(5);
        cell.setHorizontalAlignment(alignment);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        if (strong) {
            cell.setBackgroundColor(HEADER_BACKGROUND);
        }
        return cell;
    }

    private static PdfPCell bodyCell(String text, boolean leftAligned, boolean bold, Color color, Color background,
                                     boolean total) {
        // The standard PDF fonts have no peso sign, so it is printed as PHP
        PdfPCell cell = new PdfPCell(new Phrase(text.replace("₱", "PHP "), font(9, bold, color)));
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setHorizontalAlignment(leftAligned ? Element.ALIGN_LEFT : Element.ALIGN_RIGHT);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        if (total) {
            cell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
            cell.setBorderWidthTop(1.5f);
        }
        return cell;
    }

    private static PdfPCell blank() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    private static BigDecimal net(Data data) {
        return data.revenue().subtract(data.refunds()).subtract(data.cogs()).subtract(data.spoilage());
    }

    private static String money(BigDecimal value) {
        return (value.signum() < 0 ? "-" : "") + "PHP " + String.format("%,.2f", value.abs());
    }

    private static String percent(BigDecimal part, BigDecimal whole) {
        if (whole.signum() == 0) {
            return "0.0%";
        }
        return String.format("%.1f%%", part.multiply(BigDecimal.valueOf(100))
                .divide(whole, 1, RoundingMode.HALF_UP));
    }

    private static com.lowagie.text.Font font(int size, boolean bold, Color color) {
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size,
                com.lowagie.text.Font.NORMAL, color);
    }

    // Footer on every page: shop name on the left, "Page X of Y" on the right.
    // The total page count is only known at the end, so it is written into a template when the document closes.
    private static class PageFooter extends PdfPageEventHelper {
        private PdfTemplate totalPages;
        private BaseFont baseFont;

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            totalPages = writer.getDirectContent().createTemplate(30, 12);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            float y = document.bottom() - 22;

            canvas.setColorStroke(LINE);
            canvas.moveTo(document.left(), y + 12);
            canvas.lineTo(document.right(), y + 12);
            canvas.stroke();

            canvas.beginText();
            canvas.setFontAndSize(baseFont, 8);
            canvas.setColorFill(MUTED);
            canvas.setTextMatrix(document.left(), y);
            canvas.showText(SHOP_NAME + " - Sales Report  |  Printed "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"))
                    + "  |  Confidential: for internal use only");
            canvas.endText();

            String pageText = "Page " + writer.getPageNumber() + " of ";
            float textWidth = baseFont.getWidthPoint(pageText, 8);
            float x = document.right() - textWidth - 14;
            canvas.beginText();
            canvas.setFontAndSize(baseFont, 8);
            canvas.setColorFill(MUTED);
            canvas.setTextMatrix(x, y);
            canvas.showText(pageText);
            canvas.endText();
            canvas.addTemplate(totalPages, x + textWidth, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            totalPages.beginText();
            totalPages.setFontAndSize(baseFont, 8);
            totalPages.setColorFill(MUTED);
            totalPages.setTextMatrix(0, 0);
            totalPages.showText(String.valueOf(writer.getPageNumber() - 1));
            totalPages.endText();
        }
    }
}