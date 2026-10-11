package com.flowershop.flowershop.util;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Builds the PDF reports of the dashboard: a title, an optional list of key figures, then one table per section
public final class PdfReport {

    private static final String SHOP_NAME = "Junaie's Flower Shop";
    private static final Color HEADER_BACKGROUND = new Color(230, 234, 240);
    private static final Color LINE = new Color(210, 214, 220);

    private PdfReport() {
    }

    // heading is optional: a report with a single table already has the report title as its heading
    public record Section(String heading, String[] columns, List<Object[]> rows) {
    }

    // summary holds {label, value} pairs shown above the tables
    public static void write(File file, String title, List<String[]> summary, List<Section> sections) throws Exception {
        // Wide tables (6 or more columns) get a landscape page so the columns are not squeezed
        boolean wide = sections.stream().anyMatch(s -> s.columns().length >= 6);
        Document document = new Document(wide ? PageSize.A4.rotate() : PageSize.A4, 36, 36, 36, 36);

        try (FileOutputStream out = new FileOutputStream(file)) {
            PdfWriter.getInstance(document, out);

            HeaderFooter footer = new HeaderFooter(new Phrase("Page ", font(9, false)), true);
            footer.setBorder(Rectangle.NO_BORDER);
            footer.setAlignment(Element.ALIGN_CENTER);
            document.setFooter(footer);
            document.open();

            document.add(new Paragraph(SHOP_NAME, font(11, false)));
            document.add(new Paragraph(title, font(20, true)));
            Paragraph generated = new Paragraph("Generated "
                    + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a")), font(9, false));
            generated.setSpacingAfter(12);
            document.add(generated);

            if (!summary.isEmpty()) {
                document.add(summaryTable(summary));
            }
            for (Section section : sections) {
                if (section.heading() != null) {
                    Paragraph heading = new Paragraph(section.heading(), font(14, true));
                    heading.setSpacingBefore(16);
                    heading.setSpacingAfter(6);
                    document.add(heading);
                }
                document.add(dataTable(section));
            }
            // Closing writes the end of the PDF, so it has to happen before the file stream is closed
            document.close();
        }
    }

    private static PdfPTable summaryTable(List<String[]> summary) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        for (String[] pair : summary) {
            table.addCell(cell(pair[0], font(11, false), null));
            table.addCell(cell(pair[1], font(11, true), null));
        }
        return table;
    }

    private static PdfPTable dataTable(Section section) {
        PdfPTable table = new PdfPTable(section.columns().length);
        table.setWidthPercentage(100);
        // The header row repeats on every page when the table is long
        table.setHeaderRows(1);

        for (String column : section.columns()) {
            table.addCell(cell(column, font(10, true), HEADER_BACKGROUND));
        }

        if (section.rows().isEmpty()) {
            PdfPCell empty = cell("No records.", font(10, false), null);
            empty.setColspan(section.columns().length);
            table.addCell(empty);
            return table;
        }
        for (Object[] row : section.rows()) {
            for (Object value : row) {
                table.addCell(cell(value == null ? "" : String.valueOf(value), font(10, false), null));
            }
        }
        return table;
    }

    private static PdfPCell cell(String text, com.lowagie.text.Font font, Color background) {
        // The standard PDF fonts have no peso sign, so it is printed as PHP
        PdfPCell cell = new PdfPCell(new Phrase(text.replace("₱", "PHP "), font));
        cell.setPadding(5);
        cell.setBorderColor(LINE);
        if (background != null) {
            cell.setBackgroundColor(background);
        }
        return cell;
    }

    private static com.lowagie.text.Font font(int size, boolean bold) {
        return FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, size);
    }
}