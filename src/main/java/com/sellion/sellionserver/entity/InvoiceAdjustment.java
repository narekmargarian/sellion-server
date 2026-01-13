package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_adjustments")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceAdjustment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long invoiceId;
    private String productName;
    private Integer oldQuantity;
    private Integer newQuantity;
    private String reason; // Причина изменения
    private LocalDateTime createdAt = LocalDateTime.now();
    private String adjustedBy; // Кто внес изменения
}