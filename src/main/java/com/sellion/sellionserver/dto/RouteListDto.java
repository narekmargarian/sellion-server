package com.sellion.sellionserver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RouteListDto {
    private String shopName;
    private String address;
    private String phone;
    private Double totalAmount;
    private String paymentMethod;
    private String comment;
}