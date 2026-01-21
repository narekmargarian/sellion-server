package com.sellion.sellionserver.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Month;
import java.time.Year;

@Entity
@Getter
@Setter
@Table(name = "managers_target")
@AllArgsConstructor
@NoArgsConstructor
public class ManagerTarget {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String managerId;
    private BigDecimal targetAmount;
    private Month month;
    private Year year;
}

