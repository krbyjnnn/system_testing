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

    /** Contact and schedule info for PICK-UP and DELIVERY orders (fee is 0 for pick-up). */
    public record DeliveryDetails(String recipientName, String recipientPhone, String address,
                                  LocalDate date, BigDecimal fee, String notes) {
    }

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final DeliveryRepository deliveryRepository;

    public SaleService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductInventoryRepository productInventoryRepository,
            DeliveryRepository deliveryRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.deliveryRepository = deliveryRepository;
    }

    /**
     * orderType: WALK-IN, PICK-UP or DELIVERY.
     * payNow: true  -> order is COMPLETED and a payment is saved now.
     *         false -> order stays PENDING with no payment until it is handed over.
     * details: required for PICK-UP and DELIVERY, null for WALK-IN.
     */
    // @Transactional: if anything fails, the whole sale is cancelled (nothing is saved).
    @Transactional
    public String completeSale(User user, List<Product> products, List<Integer> quantities,
                               String orderType, boolean payNow,
                               String paymentMethod, String referenceNumber,
                               DeliveryDetails details) {

        boolean walkIn = "WALK-IN".equals(orderType);
        if (!walkIn && details == null) {
            throw new IllegalArgumentException("Pick-up and delivery orders need contact details.");
        }
        if (walkIn && !payNow) {
            throw new IllegalArgumentException("Walk-in orders must be paid right away.");
        }

        // 1. Compute the total (items + delivery fee, so it matches what the customer pays)
        BigDecimal fee = details != null ? details.fee() : BigDecimal.ZERO;
        BigDecimal total = fee;
        for (int i = 0; i < products.size(); i++) {
            BigDecimal lineTotal = products.get(i).getSellingPrice().multiply(BigDecimal.valueOf(quantities.get(i)));
            total = total.add(lineTotal);
        }

        // 2. Save the order (COMPLETED = paid, PENDING = waiting for payment)
        LocalDateTime now = LocalDateTime.now();
        String transactionNumber = "TXN" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String orderStatus = payNow ? "COMPLETED" : "PENDING";
        Order order = orderRepository.save(
                new Order(transactionNumber, user, orderType, orderStatus, now, null, total));

        // 3. Save each item and take it out of stock (stock is reserved even for unpaid orders)
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            int qty = quantities.get(i);
            BigDecimal subtotal = product.getSellingPrice().multiply(BigDecimal.valueOf(qty));

            orderItemRepository.save(new OrderItem(qty, product.getSellingPrice(), subtotal, order, product));
            deductStock(product, qty);
        }

        // 4. Save the payment only when money is collected now
        if (payNow) {
            String reference = referenceNumber.isEmpty() ? null : referenceNumber;
            paymentRepository.save(new Payment(order, paymentMethod, total, "PAID", LocalDate.now(), reference));
        }

        // 5. Save pick-up / delivery details (fulfilment starts as PENDING)
        if (details != null) {
            deliveryRepository.save(new Delivery(order, details.recipientName(), details.recipientPhone(),
                    details.address(), details.date(), "PENDING", fee, details.notes()));
        }

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