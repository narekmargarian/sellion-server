package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long invoiceId;
    // ИСПРАВЛЕНО: BigDecimal вместо Double
    @Column(precision = 19, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;
    private LocalDateTime paymentDate = LocalDateTime.now();
    private String comment;


}