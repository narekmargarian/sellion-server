package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


@Entity
@Table(name = "returns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReturnOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("shopName")
    private String shopName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "return_items", joinColumns = @JoinColumn(name = "return_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "quantity")
    private Map<Long, Integer> items = new HashMap<>();

    // ИСПРАВЛЕНО: Добавлена полноценная коллекция для хранения цен на момент возврата
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "return_item_prices", joinColumns = @JoinColumn(name = "return_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "price", precision = 19, scale = 2)
    private Map<Long, BigDecimal> itemPrices = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "return_reason", length = 50)
    private ReasonsReturn returnReason;

    @JsonProperty("returnDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate returnDate;

    @Enumerated(EnumType.STRING)
    private ReturnStatus status = ReturnStatus.DRAFT;

    @JsonProperty("managerId")
    private String managerId;

    @JsonProperty("totalAmount")
    private BigDecimal totalAmount = BigDecimal.ZERO;

    private LocalDateTime createdAt;

    private String comment;
    private String carNumber;

    @Column(precision = 19, scale = 2)
    private BigDecimal purchaseCost = BigDecimal.ZERO;

    private String androidId;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}

