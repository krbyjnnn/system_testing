package com.flowershop.flowershop.repository;

import com.flowershop.flowershop.model.ComponentInventory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComponentInventoryRepository extends JpaRepository<ComponentInventory, Long> {

    List<ComponentInventory> findByComponentComponentId(Long componentId);
    List<ComponentInventory> findBySupplierSupplierId(Long supplierId);

    // Every delivery item from all suppliers (walk-ins included), newest first
    @Query("SELECT ci FROM ComponentInventory ci JOIN ci.sdelivery sd "
            + "ORDER BY ci.dateReceived DESC, ci.cbatchId DESC")
    List<ComponentInventory> findAllDelivered();

    // Everything a supplier delivered (through its deliveries), newest first
    @Query("SELECT ci FROM ComponentInventory ci WHERE ci.sdelivery.supplier.supplierId = :supplierId "
            + "ORDER BY ci.dateReceived DESC, ci.cbatchId DESC")
    List<ComponentInventory> findDeliveredBySupplier(@Param("supplierId") Long supplierId);

    @Query(value = """
            SELECT * FROM (
                SELECT 'Product' AS itemType, p.product_name AS itemName, pi.quantity_on_hand AS quantityOnHand, CAST(pi.expiry_date AS CHAR) AS expiryDate
                FROM product_inventory pi
                JOIN product p ON p.product_id = pi.product_id
                WHERE pi.expiry_date < CURDATE() AND pi.quantity_on_hand > 0
                UNION ALL
                SELECT 'Component', c.name, ci.quantity_on_hand, CAST(ci.expiry_date AS CHAR)
                FROM component_inventory ci
                JOIN component c ON c.component_id = ci.component_id
                WHERE ci.expiry_date < CURDATE() AND ci.quantity_on_hand > 0
            ) s
            ORDER BY s.expiryDate DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT pi.pbatch_id FROM product_inventory pi
                WHERE pi.expiry_date < CURDATE() AND pi.quantity_on_hand > 0
                UNION ALL
                SELECT ci.cbatch_id FROM component_inventory ci
                WHERE ci.expiry_date < CURDATE() AND ci.quantity_on_hand > 0
            ) s
            """,
            nativeQuery = true)
    Page<SpoilageRow> findSpoilage(Pageable pageable);

    // Products and components that are running low, one row per item.
    // The batches of an item are added together and compared with its reorder level.
    // Components with no batches at all are included too, with a quantity of 0.
    @Query(value = """
            SELECT * FROM (
                SELECT 'Product' AS itemType, p.product_name AS itemName, cat.name AS categoryName,
                       CAST(SUM(pi.quantity_on_hand) AS SIGNED) AS quantityOnHand
                FROM product_inventory pi
                JOIN product p ON p.product_id = pi.product_id
                LEFT JOIN category cat ON cat.category_id = p.category_id
                GROUP BY p.product_id, p.product_name, cat.name
                HAVING SUM(pi.quantity_on_hand) <= MAX(pi.reorder_level)
                UNION ALL
                SELECT 'Component', c.name, cat.name,
                       CAST(COALESCE(SUM(ci.quantity_on_hand), 0) AS SIGNED)
                FROM component c
                LEFT JOIN component_inventory ci ON ci.component_id = c.component_id
                LEFT JOIN category cat ON cat.category_id = c.category_id
                GROUP BY c.component_id, c.name, c.reorder_level, cat.name
                HAVING COALESCE(SUM(ci.quantity_on_hand), 0) <= c.reorder_level
            ) s
            ORDER BY s.quantityOnHand ASC, s.itemName ASC
            """,
            countQuery = """
            SELECT COUNT(*) FROM (
                SELECT p.product_id
                FROM product_inventory pi
                JOIN product p ON p.product_id = pi.product_id
                GROUP BY p.product_id
                HAVING SUM(pi.quantity_on_hand) <= MAX(pi.reorder_level)
                UNION ALL
                SELECT c.component_id
                FROM component c
                LEFT JOIN component_inventory ci ON ci.component_id = c.component_id
                GROUP BY c.component_id, c.reorder_level
                HAVING COALESCE(SUM(ci.quantity_on_hand), 0) <= c.reorder_level
            ) s
            """,
            nativeQuery = true)
    Page<LowStockRow> findLowStock(Pageable pageable);
}