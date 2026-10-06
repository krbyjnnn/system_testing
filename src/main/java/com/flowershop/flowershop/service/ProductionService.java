package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.ComponentInventory;
import com.flowershop.flowershop.model.Product;
import com.flowershop.flowershop.model.ProductInventory;
import com.flowershop.flowershop.model.Recipe;
import com.flowershop.flowershop.repository.ComponentInventoryRepository;
import com.flowershop.flowershop.repository.ProductInventoryRepository;
import com.flowershop.flowershop.repository.RecipeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ProductionService {

    private static final int DEFAULT_REORDER_LEVEL = 5;

    private final RecipeRepository recipeRepository;
    private final ComponentInventoryRepository componentInventoryRepository;
    private final ProductInventoryRepository productInventoryRepository;

    public ProductionService(
            RecipeRepository recipeRepository,
            ComponentInventoryRepository componentInventoryRepository,
            ProductInventoryRepository productInventoryRepository
    ) {
        this.recipeRepository = recipeRepository;
        this.componentInventoryRepository = componentInventoryRepository;
        this.productInventoryRepository = productInventoryRepository;
    }

    // Arranges "quantity" pieces of a product: takes the components out of stock
    // and adds the finished pieces to the product's stock.
    // Everything happens in one transaction, so if anything fails, nothing is deducted.
    @Transactional
    public ProductInventory makeProduct(Product product, int quantity) {
        if (!Boolean.TRUE.equals(product.getIsComposite())) {
            throw new IllegalStateException("This product is not made from components.");
        }
        if (quantity < 1) {
            throw new IllegalStateException("Quantity must be at least 1.");
        }

        List<Recipe> recipe = recipeRepository.findByProductProductId(product.getProductId());
        if (recipe.isEmpty()) {
            throw new IllegalStateException("This product has no recipe yet. Add its components first.");
        }

        LocalDate today = LocalDate.now();

        // Order of use: earliest expiry first (no expiry goes last), then oldest received, then lowest batch id
        Comparator<ComponentInventory> order = Comparator
                .comparing(ComponentInventory::getExpiryDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(ComponentInventory::getDateReceived)
                .thenComparing(ComponentInventory::getCbatchId);

        // Step 1: for every recipe line, find the usable batches and check there is enough
        List<List<ComponentInventory>> usable = new ArrayList<>();
        List<String> shortages = new ArrayList<>();

        for (Recipe line : recipe) {
            int needed = line.getQuantityNeeded() * quantity;
            int available = 0;
            List<ComponentInventory> batches = new ArrayList<>();

            for (ComponentInventory batch : componentInventoryRepository
                    .findByComponentComponentId(line.getComponent().getComponentId())) {
                // Expired stock is not used, and empty batches are skipped
                boolean expired = batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today);
                if (!expired && batch.getQuantityOnHand() > 0) {
                    batches.add(batch);
                    available += batch.getQuantityOnHand();
                }
            }
            batches.sort(order);
            usable.add(batches);

            if (available < needed) {
                shortages.add(line.getComponent().getName() + " (need " + needed + ", have " + available + ")");
            }
        }

        if (!shortages.isEmpty()) {
            throw new IllegalStateException("Not enough stock for: " + String.join(", ", shortages));
        }

        // Step 2: take the components out of the batches, in order
        BigDecimal totalCost = BigDecimal.ZERO;
        LocalDate earliestExpiry = null;

        for (int i = 0; i < recipe.size(); i++) {
            int remaining = recipe.get(i).getQuantityNeeded() * quantity;

            for (ComponentInventory batch : usable.get(i)) {
                if (remaining == 0) {
                    break;
                }
                int take = Math.min(remaining, batch.getQuantityOnHand());

                batch.setQuantityOnHand(batch.getQuantityOnHand() - take);
                componentInventoryRepository.save(batch);

                totalCost = totalCost.add(batch.getUnitCost().multiply(BigDecimal.valueOf(take)));
                if (batch.getExpiryDate() != null
                        && (earliestExpiry == null || batch.getExpiryDate().isBefore(earliestExpiry))) {
                    earliestExpiry = batch.getExpiryDate();
                }
                remaining -= take;
            }
        }

        // Step 3: the finished pieces become a batch of the product.
        // Cost = what the components really cost, expiry = the earliest expiry among the components used.
        BigDecimal unitCost = totalCost.divide(BigDecimal.valueOf(quantity), 2, RoundingMode.HALF_UP);

        return productInventoryRepository.save(new ProductInventory(
                null, product, quantity, DEFAULT_REORDER_LEVEL, unitCost, today, earliestExpiry));
    }
}