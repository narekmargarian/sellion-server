package com.sellion.sellionserver.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long clientId;
    private String clientName;

    // Тип: "ORDER" (увеличивает долг), "PAYMENT" (уменьшает), "RETURN" (уменьшает)
    private String type;

    private Long referenceId; // ID заказа, платежа или возврата

    private Double amount;    // Сумма операции
    private Double balanceAfter; // Остаток долга после этой операции (как в банковской выписке)

    private String comment;
    private LocalDateTime timestamp;
}