package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;


@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonProperty("shopName")
    private String shopName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "product_name")
    @Column(name = "quantity")
    private Map<String, Integer> items;

    @JsonProperty("deliveryDate")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate deliveryDate;


    private PaymentMethod paymentMethod;

    @JsonProperty("needsSeparateInvoice")
    private boolean needsSeparateInvoice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.NEW;

    @JsonProperty("managerId")
    private String managerId;

    @JsonProperty("totalAmount")
    private Double totalAmount;
    private Double totalPurchaseCost;

    // НОВОЕ ПОЛЕ: Ссылка на инвойс (счёт), если он создан
    private Long invoiceId;

    // НОВОЕ ПОЛЕ: Дата создания заказа (нужно для фильтров оператора)

    private String createdAt;

    private String comment;

    @Column(unique = true) // База данных сама не даст создать дубликат
    private String androidId;
    private String carNumber;


    @PrePersist
    public void formatAndSetDate() {
        // Логика форматирования остается только для createdAt,
        // чтобы сохранить полную дату и время создания заказа.
        if (this.createdAt == null || this.createdAt.isEmpty() || this.createdAt.length() > 19) {
            DateTimeFormatter appFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            if (this.createdAt != null && this.createdAt.length() > 19) {
                this.createdAt = this.createdAt.substring(0, 19);
            } else {
                this.createdAt = now.format(appFormatter);
            }
        }
    }

}

