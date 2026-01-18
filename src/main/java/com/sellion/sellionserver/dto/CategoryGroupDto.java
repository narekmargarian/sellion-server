package com.sellion.sellionserver.dto;

import com.sellion.sellionserver.entity.Product;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CategoryGroupDto {
    private String categoryName;
    private List<Product> products;
}