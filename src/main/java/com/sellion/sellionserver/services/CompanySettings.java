package com.sellion.sellionserver.services;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class CompanySettings {
    public Map<String, String> getSellerData() {
        Map<String, String> data = new HashMap<>();
        data.put("name", "Սելլիոն ՍՊԸ");
        data.put("inn", "01234567");
        data.put("address", "ՀՀ, ք. Երևան, Աբովյան 11/11");
        data.put("bank", "Ամերիա Բանկ");
        data.put("iban", "AM00 0000 0000 0000 0000");
        data.put("isVatPayer", "Այո (20%)");
        return data;
    }
}