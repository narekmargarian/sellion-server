package com.sellion.sellionserver.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Entity
@Table(name = "system_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SystemSetting {
    @Id
    private String settingKey; // Например, "COMPANY_NAME"
    private String settingValue; // Например, "Սելլիոն ՍՊԸ"
}
