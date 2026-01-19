package com.sellion.sellionserver.services;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CompanySettings {
    public Map<String, String> getSellerData() {
        Map<String, String> data = new HashMap<>();
        data.put("name", "SELLION ERP LLC");
        data.put("inn", "01234567");
        data.put("address", "Армения, г. Ереван, ул. Абовяна 1");
        data.put("bank", "Арарат Банк");
        data.put("iban", "AM00 0000 0000 0000 0000");
        data.put("isVatPayer", "Да (20%)");
        return data;
    }
}