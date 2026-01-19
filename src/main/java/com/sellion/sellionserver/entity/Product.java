package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Entity
@Table(name = "products")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double price;
    private Integer itemsPerBox;
    private String barcode;
    private String category;
    private Double purchasePrice;
    private Integer stockQuantity;
    private boolean isDeleted = false; // Флаг мягкого удаления
    private String unit;
    private String hsnCode;


}