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

    // ИСПРАВЛЕНО: Переход на ID товаров (Long) вместо имен (String)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "return_items", joinColumns = @JoinColumn(name = "return_id"))
    @MapKeyColumn(name = "product_id") // В БД колонка теперь будет хранить ID
    @Column(name = "quantity")
    private Map<Long, Integer> items = new HashMap<>(); // Инициализация обязательна

    @Enumerated(EnumType.STRING) // Это ОБЯЗАТЕЛЬНО, чтобы в БД хранилось "EXPIRED", а не число 0
    @Column(name = "return_reason",length = 50)
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

    private String comment;
    private String carNumber;


    @PrePersist
    public void formatAndSetReturnDate() {
        // Форматтер для 2026 года в формате ISO
        DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();

        if (this.createdAt == null || this.createdAt.isEmpty()) {
            this.createdAt = now.format(fullFormatter);
        } else if (this.createdAt.length() > 19) {
            this.createdAt = this.createdAt.substring(0, 19);
        }
    }
}
