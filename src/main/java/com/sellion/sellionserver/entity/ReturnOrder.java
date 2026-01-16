package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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

    @ElementCollection
    @CollectionTable(name = "return_items", joinColumns = @JoinColumn(name = "return_id"))
    @MapKeyColumn(name = "product_name")
    @Column(name = "quantity")
    private Map<String, Integer> items;


    private ReasonsReturn returnReason;

    @JsonProperty("returnDate")
    private String returnDate;

    @Enumerated(EnumType.STRING)
    private ReturnStatus status = ReturnStatus.DRAFT;

    @JsonProperty("managerId")
    private String managerId;

    @JsonProperty("totalAmount")
    private Double totalAmount;

    private String createdAt;


    @PrePersist
    public void formatAndSetReturnDate() {
        DateTimeFormatter appFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

        // Форматируем createdAt всегда при создании записи
        if (this.createdAt == null || this.createdAt.isEmpty() || this.createdAt.length() > 19) {
            this.createdAt = LocalDateTime.now().format(appFormatter);
        }

        // Если returnDate пришла с Z или миллисекундами, мы её обрезаем до нужного формата
        if (this.returnDate != null && this.returnDate.length() > 19) {
            this.returnDate = this.returnDate.substring(0, 19);
        } else if (this.returnDate == null || this.returnDate.isEmpty()) {
            this.returnDate = LocalDateTime.now().format(appFormatter);
        }
    }
}