package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("shopName")
    private String shopName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "product_id") // ИСПРАВЛЕНО: теперь храним ID
    @Column(name = "quantity")
    private Map<Long, Integer> items = new HashMap<>(); // ИСПРАВЛЕНО: тип ключа Long

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false) // База будет знать, что это String
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    private boolean needsSeparateInvoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status = OrderStatus.NEW;

    private String managerId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "total_purchase_cost", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPurchaseCost = BigDecimal.ZERO;

    private Long invoiceId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    private String comment;

    @Column(unique = true, name = "android_id") // ИСПРАВЛЕНО: Индекс на уровне БД
    private String androidId;
    private String carNumber;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

}

