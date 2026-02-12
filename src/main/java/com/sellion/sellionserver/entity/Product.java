package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private BigDecimal price = BigDecimal.ZERO;
    private Integer itemsPerBox;
    private String barcode;
    private String category;
    private BigDecimal purchasePrice = BigDecimal.ZERO;
    private Integer stockQuantity;
    private Boolean isDeleted = false;  // Флаг мягкого удаления
    private String unit;
    private String hsnCode;
    private LocalDate expiryDate;
    private BigDecimal promoPercent = BigDecimal.ZERO;


}