package com.sellion.sellionserver.dto;

import lombok.Data;

import java.util.List;


@Data
public class DetailedReportRequest {
    private List<Long> ids;
    private String start;
    private String end;
    private List<String> managers;
    private List<String> clients;
    private List<Long> products;
}

