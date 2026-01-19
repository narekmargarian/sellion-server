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
    @MapKeyColumn(name = "product_name")
    @Column(name = "quantity")
    private Map<String, Integer> items;

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

    private String createdAt;


    @PrePersist
    public void formatAndSetReturnDate() {
        // Форматтер для createdAt (с полным временем)
        DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        // Логика форматирования остается только для createdAt, с полным временем
        if (this.createdAt == null || this.createdAt.isEmpty() || this.createdAt.length() > 19) {
            this.createdAt = now.format(fullFormatter);
        }

    }

}