package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "promo_actions")
@Getter @Setter @NoArgsConstructor
public class PromoAction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;      // Имя Акции
    private String managerId;  // Менеджер
    private LocalDate startDate;
    private LocalDate endDate;

    private boolean confirmed = false; // Статус подтверждения
    private String status = "PENDING"; // PENDING, ACTIVE, FINISHED

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "promo_items", joinColumns = @JoinColumn(name = "promo_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "discount_percent")
    private Map<Long, BigDecimal> items = new HashMap<>(); // ID товара -> Процент акции

    private LocalDateTime createdAt = LocalDateTime.now();
}