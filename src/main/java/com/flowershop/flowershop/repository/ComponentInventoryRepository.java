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
}