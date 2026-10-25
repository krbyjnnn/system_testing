package com.flowershop.flowershop.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Read-only queries for the Inventory, Procurement, Spoilage and Returns report tabs.
// Every method takes an inclusive date range (from, to) and returns raw rows; the report tabs format them.
// Kept in one class so the existing repositories do not need to change.
@Service
@Transactional(readOnly = true)
public class ReportDataService {

    public static final String MOVEMENT_ALL = "ALL";
    public static final String MOVEMENT_IN = "IN";
    public static final String MOVEMENT_OUT = "OUT";

    @PersistenceContext
    private EntityManager em;

    // Stock movement, derived from the records the system already keeps:
    //   Stock-In  = components received from suppliers (deliveries and walk-in purchases)
    //   Stock-Out = products sold in completed orders, and components written off as spoilage
    // Row: {date yyyy-MM-dd, item type, item, movement, reason, quantity (Number), reference}
    public List<Object[]> inventoryMovements(LocalDate from, LocalDate to, String movement) {
        List<Object[]> rows = new ArrayList<>();
        if (!MOVEMENT_OUT.equals(movement)) {
            rows.addAll(stockIn(from, to));
        }
        if (!MOVEMENT_IN.equals(movement)) {
            rows.addAll(stockOutSales(from, to));
            rows.addAll(stockOutSpoilage(from, to));
        }
        // Oldest first; on the same day, stock coming in is listed before stock going out
        rows.sort(Comparator.<Object[], String>comparing(r -> String.valueOf(r[0]))
                .thenComparing(r -> String.valueOf(r[3]))
                .thenComparing(r -> String.valueOf(r[2])));
        return rows;
    }

    private List<Object[]> stockIn(LocalDate from, LocalDate to) {
        return run("""
                SELECT CAST(ci.date_received AS CHAR), 'Component', c.name, 'Stock-In',
                       CASE WHEN p.transaction_type = 'walk_in' THEN 'Walk-in purchase' ELSE 'Supplier delivery' END,
                       CAST(ci.quantity_received AS SIGNED),
                       CONCAT(COALESCE(s.supplier_name, 'No supplier'),
                              CASE WHEN p.receipt_number IS NULL THEN '' ELSE CONCAT(' - Receipt ', p.receipt_number) END)
                FROM component_inventory ci
                JOIN component c ON c.component_id = ci.component_id
                LEFT JOIN procurement p ON p.sdelivery_id = ci.sdelivery_id
                LEFT JOIN supplier s ON s.supplier_id = COALESCE(ci.supplier_id, p.supplier_id)
                WHERE ci.quantity_received > 0
                AND ci.date_received >= :fromDate AND ci.date_received < :toDate
                ORDER BY ci.date_received, ci.cbatch_id
                """, "fromDate", from, "toDate", to.plusDays(1));
    }

    private List<Object[]> stockOutSales(LocalDate from, LocalDate to) {
        return run("""
                SELECT CAST(DATE(o.order_date) AS CHAR) AS saleDay, 'Product', pr.product_name, 'Stock-Out', 'Sale',
                       CAST(SUM(oi.quantity) AS SIGNED),
                       CONCAT(COUNT(DISTINCT o.order_id), IF(COUNT(DISTINCT o.order_id) = 1, ' order', ' orders'))
                FROM order_items oi
                JOIN orders o ON o.order_id = oi.order_id
                JOIN product pr ON pr.product_id = oi.product_id
                WHERE o.order_status = 'COMPLETED'
                AND o.order_date >= :fromTime AND o.order_date < :toTime
                GROUP BY saleDay, pr.product_id, pr.product_name
                ORDER BY saleDay, pr.product_name
                """, "fromTime", from.atStartOfDay(), "toTime", to.plusDays(1).atStartOfDay());
    }

    private List<Object[]> stockOutSpoilage(LocalDate from, LocalDate to) {
        return run("""
                SELECT CAST(DATE(sl.logged_at) AS CHAR), 'Component', c.name, 'Stock-Out',
                       CONCAT('Spoilage - ', sl.reason), CAST(sl.quantity AS SIGNED), CONCAT('Batch #', sl.cbatch_id)
                FROM spoilage_log sl
                JOIN component c ON c.component_id = sl.component_id
                WHERE sl.logged_at >= :fromTime AND sl.logged_at < :toTime
                ORDER BY sl.logged_at
                """, "fromTime", from.atStartOfDay(), "toTime", to.plusDays(1).atStartOfDay());
    }

    // One row per purchase (delivery or walk-in), by the date the stock was received.
    // Row: {date, receipt no., supplier, type, items, units (Number), payment method, payment status,
    //       received by, total amount (BigDecimal-like)}
    public List<Object[]> procurement(LocalDate from, LocalDate to) {
        return run("""
                SELECT CAST(p.date_received AS CHAR), COALESCE(p.receipt_number, '-'), s.supplier_name,
                       CASE WHEN p.transaction_type = 'walk_in' THEN 'Walk-in' ELSE 'Delivery' END,
                       CAST((SELECT COUNT(*) FROM component_inventory ci
                             WHERE ci.sdelivery_id = p.sdelivery_id) AS SIGNED),
                       CAST((SELECT COALESCE(SUM(ci.quantity_received), 0) FROM component_inventory ci
                             WHERE ci.sdelivery_id = p.sdelivery_id) AS SIGNED),
                       COALESCE(p.payment_method, '-'), COALESCE(p.payment_status, '-'), u.full_name,
                       COALESCE(NULLIF(p.total_amount, 0),
                                (SELECT COALESCE(SUM(ci.quantity_received * ci.unit_cost), 0)
                                 FROM component_inventory ci WHERE ci.sdelivery_id = p.sdelivery_id))
                FROM procurement p
                JOIN supplier s ON s.supplier_id = p.supplier_id
                JOIN users u ON u.user_id = p.received_by
                WHERE p.date_received >= :fromDate AND p.date_received < :toDate
                ORDER BY p.date_received, p.sdelivery_id
                """, "fromDate", from, "toDate", to.plusDays(1));
    }

    // Spoilage logged by staff, plus expired stock still on the shelf (by its expiry date), the same two sources
    // the Sales report uses for its Spoilage Cost, so the totals agree.
    // Row: {date, item type, item, quantity (Number), unit cost, reason, logged by}
    public List<Object[]> spoilage(LocalDate from, LocalDate to) {
        return run("""
                SELECT s.d, s.itemType, s.itemName, s.qty, s.unitCost, s.reason, s.loggedBy FROM (
                    SELECT CAST(DATE(sl.logged_at) AS CHAR) AS d, 'Component' AS itemType, c.name AS itemName,
                           CAST(sl.quantity AS SIGNED) AS qty, sl.unit_cost AS unitCost, sl.reason AS reason,
                           COALESCE(u.full_name, '-') AS loggedBy
                    FROM spoilage_log sl
                    JOIN component c ON c.component_id = sl.component_id
                    LEFT JOIN users u ON u.user_id = sl.logged_by
                    WHERE sl.logged_at >= :fromTime AND sl.logged_at < :toTime
                    UNION ALL
                    SELECT CAST(pi.expiry_date AS CHAR), 'Product', p.product_name,
                           CAST(pi.quantity_on_hand AS SIGNED), pi.unit_cost, 'Expired (still on shelf)', '-'
                    FROM product_inventory pi
                    JOIN product p ON p.product_id = pi.product_id
                    WHERE pi.expiry_date >= :fromDate AND pi.expiry_date < :toDate
                    AND pi.expiry_date < CURDATE() AND pi.quantity_on_hand > 0
                    UNION ALL
                    SELECT CAST(ci.expiry_date AS CHAR), 'Component', c.name,
                           CAST(ci.quantity_on_hand AS SIGNED), ci.unit_cost, 'Expired (still on shelf)', '-'
                    FROM component_inventory ci
                    JOIN component c ON c.component_id = ci.component_id
                    WHERE ci.expiry_date >= :fromDate AND ci.expiry_date < :toDate
                    AND ci.expiry_date < CURDATE() AND ci.quantity_on_hand > 0
                ) s
                ORDER BY s.d, s.itemName
                """, "fromTime", from.atStartOfDay(), "toTime", to.plusDays(1).atStartOfDay(),
                "fromDate", from, "toDate", to.plusDays(1));
    }

    // Every return logged in the range (all statuses, like the refunds in the Sales report).
    // Row: {date, return no., transaction no., product, quantity (Number), reason, status, refund amount}
    public List<Object[]> returns(LocalDate from, LocalDate to) {
        return run("""
                SELECT CAST(DATE(r.return_date) AS CHAR), CONCAT('RET-', r.return_id),
                       COALESCE(o.transaction_number, '-'), COALESCE(p.product_name, '-'),
                       CAST(r.quantity_returned AS SIGNED), r.reason, r.status, r.refund_amount
                FROM returns r
                JOIN orders o ON o.order_id = r.order_id
                LEFT JOIN order_items oi ON oi.order_item_id = r.order_item_id
                LEFT JOIN product p ON p.product_id = oi.product_id
                WHERE r.return_date >= :fromTime AND r.return_date < :toTime
                ORDER BY r.return_date, r.return_id
                """, "fromTime", from.atStartOfDay(), "toTime", to.plusDays(1).atStartOfDay());
    }

    // Runs a native query with name/value parameter pairs and returns every row as an Object[]
    @SuppressWarnings("unchecked")
    private List<Object[]> run(String sql, Object... nameValuePairs) {
        Query query = em.createNativeQuery(sql);
        for (int i = 0; i < nameValuePairs.length; i += 2) {
            query.setParameter((String) nameValuePairs[i], nameValuePairs[i + 1]);
        }
        List<Object[]> rows = new ArrayList<>();
        for (Object row : query.getResultList()) {
            rows.add((Object[]) row);
        }
        return rows;
    }
}