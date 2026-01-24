package com.sellion.sellionserver.entity;

import lombok.Data;

import java.util.Map;

@Data
public class WriteOffRequest {
    private String comment;
    private Map<Long, Integer> items;
}