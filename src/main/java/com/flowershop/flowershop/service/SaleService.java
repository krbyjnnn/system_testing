package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.*;
import com.flowershop.flowershop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SaleService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductInventoryRepository productInventoryRepository;

    public SaleService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductInventoryRepository productInventoryRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productInventoryRepository = productInventoryRepository;
    }

    // @Transactional: if anything fails, the whole sale is cancelled (nothing is saved).
    @Transactional
    public String completeSale(User user, List<Product> products, List<Integer> quantities,
                               String paymentMethod, String referenceNumber) {

        // 1. Compute the total
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < products.size(); i++) {
            BigDecimal lineTotal = products.get(i).getSellingPrice().multiply(BigDecimal.valueOf(quantities.get(i)));
            total = total.add(lineTotal);
        }

        // 2. Save the order
        LocalDateTime now = LocalDateTime.now();
        String transactionNumber = "TXN" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Order order = orderRepository.save(
                new Order(transactionNumber, user, "WALK-IN", "COMPLETED", now, null, total));

        // 3. Save each item and take it out of stock
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            int qty = quantities.get(i);
            BigDecimal subtotal = product.getSellingPrice().multiply(BigDecimal.valueOf(qty));

            orderItemRepository.save(new OrderItem(qty, product.getSellingPrice(), subtotal, order, product));
            deductStock(product, qty);
        }

        // 4. Save the payment
        String reference = referenceNumber.isEmpty() ? null : referenceNumber;
        paymentRepository.save(new Payment(order, paymentMethod, total, "PAID", LocalDate.now(), reference));

        return transactionNumber;
    }

    // Takes stock from the oldest batch first
    private void deductStock(Product product, int qty) {
        List<ProductInventory> batches = new ArrayList<>(productInventoryRepository.findAll());
        batches.sort(Comparator.comparing(ProductInventory::getDateReceived));

        int remaining = qty;
        for (ProductInventory batch : batches) {
            if (remaining == 0) {
                break;
            }
            if (!batch.getProduct().getProductId().equals(product.getProductId())) {
                continue;
            }
            int take = Math.min(remaining, batch.getQuantityOnHand());
            batch.setQuantityOnHand(batch.getQuantityOnHand() - take);
            productInventoryRepository.save(batch);
            remaining = remaining - take;
        }

        if (remaining > 0) {
            throw new IllegalStateException("Not enough stock for " + product.getProductName());
        }
    }
}