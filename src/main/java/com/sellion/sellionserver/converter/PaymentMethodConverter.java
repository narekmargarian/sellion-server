package com.sellion.sellionserver.converter;

import com.sellion.sellionserver.entity.PaymentMethod;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class PaymentMethodConverter
        implements AttributeConverter<PaymentMethod, String> {

    @Override
    public String convertToDatabaseColumn(PaymentMethod attribute) {
        return attribute == null ? null : attribute.getDisplayName();
    }

    @Override
    public PaymentMethod convertToEntityAttribute(String dbData) {
        return PaymentMethod.fromString(dbData);
    }
}