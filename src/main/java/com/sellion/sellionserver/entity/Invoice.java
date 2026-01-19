package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Номер счета (можно сделать красивый, например 2026-0001)
    private String invoiceNumber;

    @OneToOne
    @JoinColumn(name = "order_id")
    private Order order; // Связь с заказом из твоего Order.java


    private BigDecimal totalAmount = BigDecimal.ZERO;


    private BigDecimal paidAmount = BigDecimal.ZERO;


    private String status = "UNPAID"; // UNPAID, PARTIAL, PAID

    private LocalDateTime createdAt = LocalDateTime.now();

    // Менеджер, чей это заказ (берем из заказа)
    private String managerId;

    private String shopName;
}