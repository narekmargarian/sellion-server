package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.services.CompanySettings;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor

public class AdminSettingsApiController {
    private final CompanySettings companySettings;

    @PostMapping("/update-all")
    public ResponseEntity<?> updateSettings(@RequestBody Map<String, String> settings) {
        settings.forEach(companySettings::updateSetting);
        return ResponseEntity.ok(Map.of("message", "Настройки успешно сохранены"));
    }
}
