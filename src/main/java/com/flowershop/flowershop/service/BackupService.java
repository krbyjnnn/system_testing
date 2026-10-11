package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.User;
import com.flowershop.flowershop.util.Permissions;
import com.flowershop.flowershop.util.Permissions.Feature;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;
import java.util.stream.Stream;

@Service
public class BackupService {

    public record BackupInfo(String type, String period, LocalDateTime created, long sizeBytes, Path file) {
    }

    private record TableSpec(String name, String weeklyWhere, boolean tracked) {
    }

    private static final int ROWS_PER_INSERT = 200;
    private static final int WEEKS_CATCH_UP = 4;
    private static final Preferences PREFS = Preferences.userNodeForPackage(BackupService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final DateTimeFormatter HEADER_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<TableSpec> TABLES = List.of(
            new TableSpec("users", null, false),
            new TableSpec("category", null, false),
            new TableSpec("supplier", null, false),
            new TableSpec("component", null, false),
            new TableSpec("product", null, false),
            new TableSpec("recipe", null, false),
            new TableSpec("orders", "order_date >= ? AND order_date < ?", true),
            new TableSpec("order_items",
                    "order_id IN (SELECT order_id FROM orders WHERE order_date >= ? AND order_date < ?)", true),
            new TableSpec("payment", "payment_date >= ? AND payment_date < ?", true),
            new TableSpec("delivery", "delivery_date >= ? AND delivery_date < ?", true),
            new TableSpec("dispute",
                    "(date_filed >= ? AND date_filed < ?) OR (date_updated >= ? AND date_updated < ?)", true),
            new TableSpec("returns", "return_date >= ? AND return_date < ?", true),
            new TableSpec("procurement", "date_created >= ? AND date_created < ?", true),
            new TableSpec("component_inventory", "date_received >= ? AND date_received < ?", true),
            new TableSpec("product_inventory", "date_received >= ? AND date_received < ?", true),
            new TableSpec("audit_log", "timestamp >= ? AND timestamp < ?", false)
    );

    private final DataSource dataSource;
    private final AuditService auditService;

    public BackupService(DataSource dataSource, AuditService auditService) {
        this.dataSource = dataSource;
        this.auditService = auditService;
    }

    public static LocalDate weekStartOf(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public Path createFullBackup(User user, Path folder) throws IOException, SQLException {
        Permissions.require(user, Feature.BACKUPS);
        Path file = folder.resolve("full_" + LocalDateTime.now().format(STAMP) + ".sql");
        int rows = writeDump(file, "Full backup (all data)", null, null);
        auditService.log(user, "Full backup created", "BACKUPS", file.getFileName() + ", " + rows + " row(s)");
        return file;
    }

    public Path createWeeklyBackup(User user, LocalDate weekStart, Path folder) throws IOException, SQLException {
        Permissions.require(user, Feature.BACKUPS);
        return writeWeekly(user, weekStart, folder, "Weekly backup created");
    }

    public Path getFolder() {
        return Paths.get(PREFS.get("folder",
                Paths.get(System.getProperty("user.home"), "FlowerShopBackups").toString()));
    }

    public void setFolder(Path folder) {
        PREFS.put("folder", folder.toString());
    }

    public synchronized List<Path> runDueWeeklyBackups() throws IOException, SQLException {
        Path folder = getFolder();
        LocalDate lastComplete = weekStartOf(LocalDate.now()).minusWeeks(1);
        LocalDate earliest = lastComplete.minusWeeks(WEEKS_CATCH_UP - 1);

        List<BackupInfo> weekly = listBackups(folder).stream()
                .filter(b -> "Weekly".equals(b.type()))
                .toList();

        LocalDate newest = null;
        for (BackupInfo info : weekly) {
            LocalDate start = periodStart(info);
            if (start != null && (newest == null || start.isAfter(newest))) {
                newest = start;
            }
        }

        LocalDate first = lastComplete;
        if (newest != null) {
            LocalDate candidate = newest.isBefore(lastComplete) ? newest : lastComplete;
            first = candidate.isBefore(earliest) ? earliest : candidate;
        }

        List<Path> created = new ArrayList<>();
        for (LocalDate week = first; !week.isAfter(lastComplete); week = week.plusWeeks(1)) {
            if (!isCovered(weekly, week)) {
                created.add(writeWeekly(null, week, folder, "Automatic weekly backup created"));
            }
        }
        return created;
    }

    private boolean isCovered(List<BackupInfo> weekly, LocalDate weekStart) {
        LocalDateTime weekEnded = weekStart.plusDays(7).atStartOfDay();
        for (BackupInfo info : weekly) {
            if (weekStart.equals(periodStart(info)) && !info.created().isBefore(weekEnded)) {
                return true;
            }
        }
        return false;
    }

    private LocalDate periodStart(BackupInfo info) {
        try {
            return LocalDate.parse(info.period().substring(0, 10));
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private Path writeWeekly(User user, LocalDate weekStart, Path folder, String action)
            throws IOException, SQLException {
        LocalDate monday = weekStartOf(weekStart);
        LocalDate sunday = monday.plusDays(6);
        Path file = folder.resolve("weekly_" + monday + "_to_" + sunday + "_"
                + LocalDateTime.now().format(STAMP) + ".sql");
        int rows = writeDump(file, "Weekly differential backup (" + monday + " to " + sunday + ")",
                monday.atStartOfDay(), monday.plusDays(7).atStartOfDay());
        auditService.log(user, action, "BACKUPS",
                monday + " to " + sunday + ", " + file.getFileName() + ", " + rows + " row(s)");
        return file;
    }

    public List<BackupInfo> listBackups(Path folder) {
        if (folder == null || !Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .map(this::describe)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BackupInfo::created).reversed())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private BackupInfo describe(Path file) {
        String name = file.getFileName().toString();
        String type;
        String period;
        if (name.startsWith("full_")) {
            type = "Full";
            period = "All data";
        } else if (name.startsWith("weekly_")) {
            String[] parts = name.split("_");
            if (parts.length < 4) {
                return null;
            }
            type = "Weekly";
            period = parts[1] + " to " + parts[3];
        } else {
            return null;
        }
        try {
            LocalDateTime created = LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(),
                    ZoneId.systemDefault());
            return new BackupInfo(type, period, created, Files.size(file), file);
        } catch (IOException e) {
            return null;
        }
    }

    private synchronized int writeDump(Path file, String title, LocalDateTime from, LocalDateTime to)
            throws IOException, SQLException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        boolean weekly = from != null;
        int total = 0;
        try (Connection con = dataSource.getConnection();
             BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            con.setAutoCommit(false);
            con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

            out.write("-- Junaie's Flower Shop backup\n");
            out.write("-- " + title + "\n");
            out.write("-- Created: " + LocalDateTime.now().format(HEADER_TIME) + "\n\n");
            out.write("SET NAMES utf8mb4;\n");
            out.write("SET FOREIGN_KEY_CHECKS=0;\n");
            out.write("SET SQL_MODE='NO_AUTO_VALUE_ON_ZERO';\n\n");

            for (TableSpec table : TABLES) {
                total += dumpTable(con, out, table, weekly, from, to);
            }

            out.write("SET FOREIGN_KEY_CHECKS=1;\n");
            con.commit();
        } catch (IOException | SQLException | RuntimeException e) {
            Files.deleteIfExists(file);
            throw e;
        }
        return total;
    }

    private int dumpTable(Connection con, BufferedWriter out, TableSpec table, boolean weekly,
                          LocalDateTime from, LocalDateTime to) throws IOException, SQLException {
        String name = table.name();
        boolean filtered = weekly && table.weeklyWhere() != null;
        String condition = filtered ? weeklyCondition(con, table) : null;

        out.write("-- Table " + name + (filtered ? " (this week only)" : "") + "\n");

        if (!weekly) {
            out.write("DROP TABLE IF EXISTS `" + name + "`;\n");
            try (Statement st = con.createStatement();
                 ResultSet rs = st.executeQuery("SHOW CREATE TABLE `" + name + "`")) {
                if (rs.next()) {
                    out.write(rs.getString(2) + ";\n\n");
                }
            }
        }

        String sql = "SELECT * FROM `" + name + "`" + (filtered ? " WHERE " + condition : "");
        int rowCount = 0;

        try (PreparedStatement ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(Integer.MIN_VALUE);
            if (filtered) {
                int params = (int) condition.chars().filter(c -> c == '?').count();
                for (int i = 1; i <= params; i++) {
                    ps.setTimestamp(i, Timestamp.valueOf(i % 2 == 1 ? from : to));
                }
            }

            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();

                StringBuilder head = new StringBuilder(weekly ? "REPLACE INTO `" : "INSERT INTO `");
                head.append(name).append("` (");
                for (int i = 1; i <= cols; i++) {
                    if (i > 1) {
                        head.append(", ");
                    }
                    head.append('`').append(md.getColumnName(i)).append('`');
                }
                head.append(") VALUES\n");

                int inBatch = 0;
                while (rs.next()) {
                    out.write(inBatch == 0 ? head.toString() : ",\n");
                    out.write("(");
                    for (int i = 1; i <= cols; i++) {
                        if (i > 1) {
                            out.write(", ");
                        }
                        out.write(literal(rs, md, i));
                    }
                    out.write(")");
                    inBatch++;
                    rowCount++;
                    if (inBatch == ROWS_PER_INSERT) {
                        out.write(";\n");
                        inBatch = 0;
                    }
                }
                if (inBatch > 0) {
                    out.write(";\n");
                }
            }
        }
        out.write("\n");
        return rowCount;
    }

    private String weeklyCondition(Connection con, TableSpec table) throws SQLException {
        if (!table.tracked() || !hasUpdatedAt(con, table.name())) {
            return table.weeklyWhere();
        }
        return "(" + table.weeklyWhere() + ") OR (updated_at >= ? AND updated_at < ?)";
    }

    private boolean hasUpdatedAt(Connection con, String table) throws SQLException {
        try (ResultSet rs = con.getMetaData().getColumns(con.getCatalog(), null, table, "updated_at")) {
            return rs.next();
        }
    }

    private String literal(ResultSet rs, ResultSetMetaData md, int index) throws SQLException {
        switch (md.getColumnType(index)) {
            case Types.BIT:
            case Types.BOOLEAN: {
                boolean value = rs.getBoolean(index);
                return rs.wasNull() ? "NULL" : (value ? "1" : "0");
            }
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
            case Types.DECIMAL:
            case Types.NUMERIC:
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE: {
                String value = rs.getString(index);
                return value == null ? "NULL" : value;
            }
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB: {
                byte[] value = rs.getBytes(index);
                if (value == null) {
                    return "NULL";
                }
                return value.length == 0 ? "''" : "0x" + HexFormat.of().formatHex(value);
            }
            default: {
                String value = rs.getString(index);
                return value == null ? "NULL" : "'" + escape(value) + "'";
            }
        }
    }

    private String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '\'': sb.append("\\'"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\0': sb.append("\\0"); break;
                case '\u001a': sb.append("\\Z"); break;
                default: sb.append(c);
            }
        }
        return sb.toString();
    }
}