package com.flowershop.flowershop.service;

import com.flowershop.flowershop.model.*;
import com.flowershop.flowershop.repository.*;
import com.flowershop.flowershop.util.Permissions;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class SaleService {

    public static final BigDecimal DOWN_PAYMENT_RATE = new BigDecimal("0.30");

    /** Contact and schedule info for PICK-UP and DELIVERY orders (fee is 0 for pick-up). */
    public record DeliveryDetails(String recipientName, String recipientPhone, String address,
                                  LocalDate date, BigDecimal fee, String notes) {
    }

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final ProductInventoryRepository productInventoryRepository;
    private final DeliveryRepository deliveryRepository;
    private final AuditService auditService;

    public SaleService(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            ProductInventoryRepository productInventoryRepository,
            DeliveryRepository deliveryRepository,
            AuditService auditService
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.productInventoryRepository = productInventoryRepository;
        this.deliveryRepository = deliveryRepository;
        this.auditService = auditService;
    }

    // 30% of the total, rounded to centavos
    public static BigDecimal downPaymentOf(BigDecimal total) {
        return total.multiply(DOWN_PAYMENT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    // What is still unpaid on an order (total minus every PAID payment so far)
    public BigDecimal balanceDue(Order order) {
        BigDecimal paid = paymentRepository.sumPaidByOrder(order.getOrderId());
        BigDecimal balance = order.getTotalAmount().subtract(paid != null ? paid : BigDecimal.ZERO);
        return balance.signum() < 0 ? BigDecimal.ZERO : balance;
    }

    /**
     * orderType: WALK-IN, PICK-UP or DELIVERY.
     * WALK-IN  -> full payment now, order is COMPLETED.
     * Others   -> 30% down payment now, order stays PENDING until the balance is paid.
     * details: required for PICK-UP and DELIVERY, null for WALK-IN.
     */
    // @Transactional: if anything fails, the whole sale is cancelled (nothing is saved).
    @Transactional
    public String completeSale(User user, List<Product> products, List<Integer> quantities,
                               String orderType, String paymentMethod, String referenceNumber,
                               DeliveryDetails details) {

        // Access is limited by the shared POS permission.
        Permissions.require(user, Permissions.Feature.POS);

        boolean walkIn = "WALK-IN".equals(orderType);
        if (!walkIn && details == null) {
            throw new IllegalArgumentException("Pick-up and delivery orders need contact details.");
        }

        // 1. Compute the total (items + delivery fee, so it matches what the customer pays)
        BigDecimal fee = details != null ? details.fee() : BigDecimal.ZERO;
        BigDecimal total = fee;
        for (int i = 0; i < products.size(); i++) {
            BigDecimal lineTotal = products.get(i).getSellingPrice().multiply(BigDecimal.valueOf(quantities.get(i)));
            total = total.add(lineTotal);
        }

        // 2. Save the order (COMPLETED = fully paid, PENDING = waiting for the balance)
        LocalDateTime now = LocalDateTime.now();
        String transactionNumber = "TXN" + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String orderStatus = walkIn ? "COMPLETED" : "PENDING";
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

        // 4. Save the payment collected now: full amount for walk-in, 30% down payment otherwise
        BigDecimal amountNow = walkIn ? total : downPaymentOf(total);
        String reference = referenceNumber == null || referenceNumber.isEmpty() ? null : referenceNumber;
        paymentRepository.save(new Payment(order, paymentMethod, amountNow, "PAID", LocalDate.now(), reference));

        // 5. Save pick-up / delivery details (fulfilment starts as PENDING)
        if (details != null) {
            deliveryRepository.save(new Delivery(order, details.recipientName(), details.recipientPhone(),
                    details.address(), details.date(), "PENDING", fee, details.notes()));
        }

        if (walkIn) {
            auditService.log(user, "Walk-in sale", "POS",
                    transactionNumber + ", total " + peso(total) + ", paid via " + paymentMethod);
        } else {
            auditService.log(user, "Reservation created", "POS",
                    transactionNumber + " (" + orderType + "), total " + peso(total)
                            + ", down payment " + peso(amountNow) + " via " + paymentMethod
                            + ", customer " + details.recipientName());
        }

        return transactionNumber;
    }

    // Collects the remaining balance, then marks the order and its delivery COMPLETED
    @Transactional
    public void completePendingOrder(User user, Long orderId, String paymentMethod, String referenceNumber) {
        Permissions.require(user, Permissions.Feature.POS);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found."));
        if (!"PENDING".equals(order.getOrderStatus())) {
            throw new IllegalStateException("Only pending orders can be completed.");
        }

        BigDecimal balance = balanceDue(order);

        order.setOrderStatus("COMPLETED");
        orderRepository.save(order);

        for (Delivery delivery : deliveryRepository.findByOrderOrderId(orderId)) {
            delivery.setDeliveryStatus("COMPLETED");
            deliveryRepository.save(delivery);
        }

        if (balance.signum() > 0) {
            String reference = referenceNumber == null || referenceNumber.isEmpty() ? null : referenceNumber;
            paymentRepository.save(new Payment(order, paymentMethod, balance, "PAID", LocalDate.now(), reference));
        }

        auditService.log(user, "Balance collected", "POS",
                order.getTransactionNumber() + " completed, balance " + peso(balance) + " via " + paymentMethod);
    }

    private String peso(BigDecimal value) {
        return "₱" + String.format("%,.2f", value);
    }

    // Takes stock from the batch that expires first (no expiry goes last), then the oldest received.
    // Same order of use as the components in ProductionService.
    private void deductStock(Product product, int qty) {
        List<ProductInventory> batches = new ArrayList<>(productInventoryRepository.findAll());
        batches.sort(Comparator
                .comparing(ProductInventory::getExpiryDate, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(ProductInventory::getDateReceived)
                .thenComparing(ProductInventory::getPbatchId));

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