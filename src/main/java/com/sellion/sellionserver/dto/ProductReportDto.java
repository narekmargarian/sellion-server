package com.sellion.sellionserver.dto;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class ProductReportDto {

    // Геттеры
    private String productName;
    private String category;
    private int quantity;
    private BigDecimal amount;

    public ProductReportDto(String productName, String category, int quantity, BigDecimal amount) {
        this.productName = productName;
        this.category = category;
        this.quantity = quantity;
        this.amount = amount;
    }

    public void addQuantity(int q) { this.quantity += q; }
    public void addAmount(BigDecimal a) { this.amount = this.amount.add(a); }

}