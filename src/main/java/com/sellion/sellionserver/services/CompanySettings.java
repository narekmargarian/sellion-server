package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.CompanySetting;
import com.sellion.sellionserver.repository.CompanySettingRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanySettings {
    private final CompanySettingRepository companySettingRepository;
    private static final Logger log = LoggerFactory.getLogger(CompanySettings.class);

    // Метод получения настройки с дефолтным значением
    public String getSetting(String key, String defaultValue) {
        return companySettingRepository.findById(key)
                .map(CompanySetting::getSettingValue)
                .orElse(defaultValue);
    }

    public String getAccountantEmail() {
        return getSetting("ACCOUNTANT_EMAIL", "accountant@company.am");
    }

    public Map<String, String> getSellerData() {
        Map<String, String> data = new HashMap<>();
        data.put("name", getSetting("COMPANY_NAME", "Սելլիոն ՍՊԸ"));
        data.put("inn", getSetting("COMPANY_INN", "01234567"));
        data.put("address", getSetting("COMPANY_ADDRESS", "ՀՀ, ք. Երևան"));
        data.put("bank", getSetting("COMPANY_BANK", "Ամերիա Բանկ"));
        data.put("iban", getSetting("COMPANY_IBAN", "AM0000..."));
        return data;
    }

    @Transactional
    public void updateSetting(String key, String value) {
        companySettingRepository.save(new CompanySetting(key, value));
        log.info("Настройка {} обновлена на: {}", key, value);
    }
}
