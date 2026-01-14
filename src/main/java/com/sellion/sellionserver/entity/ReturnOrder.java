package com.sellion.sellionserver.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "returns")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
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

    @JsonProperty("returnReason")
    private String returnReason;

    @JsonProperty("returnDate")
    private String returnDate;

    @Enumerated(EnumType.STRING)
    private ReturnStatus status = ReturnStatus.DRAFT;

    @JsonProperty("managerId")
    private String managerId;

    @JsonProperty("totalAmount")
    private Double totalAmount;

    private String createdAt;
}