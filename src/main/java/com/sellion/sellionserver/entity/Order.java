package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;


@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
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

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("needsSeparateInvoice")
    private boolean needsSeparateInvoice;

    private String status;

    @JsonProperty("managerId")
    private String managerId;

    // НОВОЕ ПОЛЕ: Итоговая сумма заказа
    @JsonProperty("totalAmount")
    private Double totalAmount;
}