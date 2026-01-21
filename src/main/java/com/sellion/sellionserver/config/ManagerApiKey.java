package com.sellion.sellionserver.config;


import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "manager_api_keys")
@Getter
@Setter
@NoArgsConstructor
public class ManagerApiKey {
    @Id
    private String managerId; // Например, "1011" или "Менеджер Офис"
    private String apiKeyHash; // Зашифрованный ключ
}