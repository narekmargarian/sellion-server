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

    @ElementCollection
    @CollectionTable(name = "order_items", joinColumns = @JoinColumn(name = "order_id"))
    @MapKeyColumn(name = "product_name")
    @Column(name = "quantity")
    private Map<String, Integer> items;

    @JsonProperty("deliveryDate")
    private String deliveryDate;


    private PaymentMethod paymentMethod;

    @JsonProperty("needsSeparateInvoice")
    private boolean needsSeparateInvoice;

    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.NEW;

    @JsonProperty("managerId")
    private String managerId;

    @JsonProperty("totalAmount")
    private Double totalAmount;

    // НОВОЕ ПОЛЕ: Ссылка на инвойс (счёт), если он создан
    private Long invoiceId;

    // НОВОЕ ПОЛЕ: Дата создания заказа (нужно для фильтров оператора)

    private String createdAt;

    private String comment;



    @PrePersist
    public void formatAndSetDate() {
        // Если дата уже установлена (например, пришла из Android), мы её переформатируем
        // Если дата пустая, мы ставим текущее время
        if (this.createdAt == null || this.createdAt.isEmpty() || this.createdAt.length() > 19) {
            DateTimeFormatter appFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            LocalDateTime now = LocalDateTime.now();

            // Если дата пришла с Z или миллисекундами, мы её обрезаем до нужного формата
            if (this.createdAt != null && this.createdAt.length() > 19) {
                this.createdAt = this.createdAt.substring(0, 19);
            } else {
                this.createdAt = now.format(appFormatter);
            }
        }
    }
}

