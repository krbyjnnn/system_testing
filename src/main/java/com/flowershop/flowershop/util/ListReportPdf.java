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
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Business-style PDF for the list reports (Inventory, Procurement, Spoilage, Returns): same branded banner,
// details row and footer as the Sales report, then a row of key figures and one full-width table with a totals row.
// SalesReportPdf keeps its own layout, because the sales report has a more specialised structure.
public final class ListReportPdf {

    // ---- EDIT THESE: printed in the banner. A blank value is left out. ----
    private static final String SHOP_NAME = "Junaie's Flower Shop";
    private static final String SHOP_LOCATION = "Davao City, Philippines";
    private static final String SHOP_ADDRESS = "";
    private static final String SHOP_CONTACT = "";
    private static final String SHOP_TIN = "";

    private static final Color BANNER = new Color(2, 2, 14);
    private static final Color BRAND = new Color(25, 135, 84);
    private static final Color[] ACCENTS = {
            new Color(13, 110, 253), new Color(25, 135, 84), new Color(214, 150, 0), new Color(111, 66, 193)};
    private static final Color HEADER_BACKGROUND = new Color(230, 234, 240);
    private static final Color ZEBRA = new Color(248, 249, 251);
    private static final Color LINE = new Color(210, 214, 220);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Color TEXT = new Color(40, 40, 40);

    private ListReportPdf() {
    }

    // reportName: "INVENTORY REPORT" etc. (banner, top right). refPrefix: start of the reference number, e.g. "INV".
    // range / filter: shown in the details row. summary: {label, value, caption} boxes (at most 4 per row).
    // columns, widths, rightAligned: one entry per column. rows: already formatted, one cell per column.
    // totals: optional last row (one cell per column), null for none. notes: optional lines printed after the table.
    public record Data(String reportName, String refPrefix, String range, String filter, String preparedBy,
                       List<String[]> summary, String[] columns, float[] widths, boolean[] rightAligned,
                       List<String[]> rows, String[] totals, String emptyMessage, List<String> notes) {
    }

    public static void write(File file, Data data) throws Exception {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 48);
        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PageFooter(data.reportName()));
            document.open();

            document.add(banner(data));
            document.add(details(data));
            if (!data.summary().isEmpty()) {
                document.add(keyFigures(data));
            }
            document.add(dataTable(data));
            if (!data.notes().isEmpty()) {
                document.add(notes(data));
            }
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
        Paragraph title = new Paragraph(data.reportName(), font(18, true, Color.WHITE));
        title.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(title);
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
            URL url = ListReportPdf.class.getResource("/static/logo.jpg");
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
                {"FILTER", data.filter()},
                {"DATE & TIME PRINTED", now.format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a"))},
                {"PREPARED BY", data.preparedBy()},
                {"REFERENCE NO.", data.refPrefix() + "-" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}
        };
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.4f, 2f, 2.2f, 2f, 2.2f});
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

    // Boxes in rows of up to 4: coloured top edge, label, big value, small caption.
    // Narrow empty columns between the boxes act as gaps; unused slots in the last row stay empty.
    private static PdfPTable keyFigures(Data data) throws DocumentException {
        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 0.06f, 1, 0.06f, 1, 0.06f, 1});
        table.setSpacingBefore(14);
        table.setKeepTogether(true);

        List<String[]> boxes = data.summary();
        for (int start = 0; start < boxes.size(); start += 4) {
            if (start > 0) {
                PdfPCell gap = blank();
                gap.setColspan(7);
                gap.setFixedHeight(8);
                table.addCell(gap);
            }
            for (int slot = 0; slot < 4; slot++) {
                int index = start + slot;
                if (index < boxes.size()) {
                    table.addCell(metricBox(boxes.get(index), ACCENTS[slot]));
                } else {
                    table.addCell(blank());
                }
                if (slot < 3) {
                    table.addCell(blank());
                }
            }
        }
        return table;
    }

    private static PdfPCell metricBox(String[] box, Color accent) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(LINE);
        cell.setBorderWidthTop(3);
        cell.setBorderColorTop(accent);
        cell.setPadding(10);
        cell.addElement(new Paragraph(box[0].toUpperCase(), font(8, true, MUTED)));
        cell.addElement(new Paragraph(pdfText(box[1]), font(16, true, TEXT)));
        cell.addElement(new Paragraph(box.length > 2 ? box[2] : "", font(8, false, MUTED)));
        return cell;
    }

    private static PdfPTable dataTable(Data data) throws DocumentException {
        String[] columns = data.columns();
        PdfPTable table = new PdfPTable(columns.length);
        table.setWidthPercentage(100);
        table.setWidths(data.widths());
        table.setSpacingBefore(18);
        // The heading and the column titles repeat on every page when the report is long
        table.setHeaderRows(2);

        PdfPCell heading = new PdfPCell(new Phrase(data.reportName().toUpperCase() + " DETAILS", font(10, true, BRAND)));
        heading.setColspan(columns.length);
        heading.setBorder(Rectangle.BOTTOM);
        heading.setBorderColor(BRAND);
        heading.setBorderWidthBottom(1.5f);
        heading.setPaddingLeft(0);
        heading.setPaddingBottom(5);
        table.addCell(heading);

        for (int i = 0; i < columns.length; i++) {
            PdfPCell cell = new PdfPCell(new Phrase(columns[i], font(9, true, TEXT)));
            cell.setBackgroundColor(HEADER_BACKGROUND);
            cell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
            cell.setBorderColor(LINE);
            cell.setPadding(6);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setHorizontalAlignment(data.rightAligned()[i] ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(cell);
        }

        if (data.rows().isEmpty()) {
            PdfPCell cell = new PdfPCell(new Phrase(data.emptyMessage(), font(9, false, MUTED)));
            cell.setColspan(columns.length);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(LINE);
            cell.setPadding(10);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
            return table;
        }

        int rowNumber = 0;
        for (String[] row : data.rows()) {
            Color background = rowNumber++ % 2 == 1 ? ZEBRA : null;
            for (int i = 0; i < columns.length; i++) {
                table.addCell(bodyCell(i < row.length ? row[i] : "", data.rightAligned()[i], false, background));
            }
        }
        if (data.totals() != null) {
            for (int i = 0; i < columns.length; i++) {
                table.addCell(bodyCell(i < data.totals().length ? data.totals()[i] : "", data.rightAligned()[i], true,
                        HEADER_BACKGROUND));
            }
        }
        return table;
    }

    private static PdfPCell bodyCell(String text, boolean rightAligned, boolean total, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(pdfText(text), font(9, total, TEXT)));
        cell.setPadding(5);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(LINE);
        cell.setHorizontalAlignment(rightAligned ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        if (total) {
            cell.setBorder(Rectangle.TOP | Rectangle.BOTTOM);
            cell.setBorderWidthTop(1.5f);
        }
        return cell;
    }

    private static PdfPTable notes(Data data) throws DocumentException {
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
        for (String line : data.notes()) {
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

    private static PdfPCell blank() {
        PdfPCell cell = new PdfPCell(new Phrase(""));
        cell.setBorder(Rectangle.NO_BORDER);
        return cell;
    }

    // The standard PDF fonts have no peso sign, so it is printed as PHP
    private static String pdfText(String text) {
        return text == null ? "" : text.replace("₱", "PHP ");
    }

    private static com.lowagie.text.Font font(int size, boolean bold, Color color) {
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size,
                com.lowagie.text.Font.NORMAL, color);
    }

    // Footer on every page: report name and print time on the left, "Page X of Y" on the right.
    // The total page count is only known at the end, so it is written into a template when the document closes.
    private static class PageFooter extends PdfPageEventHelper {
        private final String reportName;
        private PdfTemplate totalPages;
        private BaseFont baseFont;

        PageFooter(String reportName) {
            this.reportName = reportName;
        }

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
            canvas.showText(SHOP_NAME + " - " + reportName + "  |  Printed "
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