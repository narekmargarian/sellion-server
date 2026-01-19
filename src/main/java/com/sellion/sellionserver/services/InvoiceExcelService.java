package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class InvoiceExcelService {


    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final CompanySettings companySettings;

    public Workbook generateInvoiceExcel(Order order) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Invoice_" + order.getId());

        Map<String, String> seller = companySettings.getSellerData();
        Client client = clientRepository.findByName(order.getShopName()).orElse(new Client());

        int rowIdx = 0;

        // 1. ДАННЫЕ ПРОДАВЦА
        addRow(sheet, rowIdx++, "ПРОДАВЕЦ:", seller.get("name"));
        addRow(sheet, rowIdx++, "ИНН продавца:", seller.get("inn"));
        addRow(sheet, rowIdx++, "Адрес продавца:", seller.get("address"));
        addRow(sheet, rowIdx++, "Банк / Счет продавца:", seller.get("bank") + " / " + seller.get("iban"));
        rowIdx++;

        // 2. ДАННЫЕ ПОКУПАТЕЛЯ
        addRow(sheet, rowIdx++, "ПОКУПАТЕЛЬ:", client.getName());
        addRow(sheet, rowIdx++, "ИНН покупателя:", client.getInn());
        addRow(sheet, rowIdx++, "Адрес доставки:", client.getAddress());
        addRow(sheet, rowIdx++, "Р/С Покупателя:", client.getBankAccount() != null ? client.getBankAccount() : "---");
        // Берем метод оплаты напрямую из заказа (как вы и просили)
        addRow(sheet, rowIdx++, "Форма оплаты:", order.getPaymentMethod() != null ? order.getPaymentMethod().getDisplayName() : "TRANSFER");
        rowIdx++;

        // 3. ДАННЫЕ ДОКУМЕНТА И ТРАНСПОРТА
        addRow(sheet, rowIdx++, "Дата отгрузки:", order.getDeliveryDate().toString());
        addRow(sheet, rowIdx++, "Менеджер (Код):", order.getManagerId());
        addRow(sheet, rowIdx++, "Номер автомобиля:", order.getCarNumber() != null ? order.getCarNumber() : "---");
        addRow(sheet, rowIdx++, "Валюта:", "AMD");
        rowIdx++;

        // 4. ТАБЛИЦА ТОВАРОВ
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"Наименование", "Код товара (SKU)", "Ед. изм.", "Кол-во", "Цена (без НДС)", "Сумма (без НДС)", "НДС (20%)", "Сумма с НДС"};
        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }

        double totalInvoiceSum = 0;
        for (Map.Entry<String, Integer> entry : order.getItems().entrySet()) {
            Product p = productRepository.findByName(entry.getKey()).orElse(null);
            if (p == null) continue;

            Row row = sheet.createRow(rowIdx++);
            double qty = entry.getValue();
            double priceWithVat = p.getPrice();
            double priceNoVat = priceWithVat / 1.2;
            double sumNoVat = priceNoVat * qty;
            double vatAmount = sumNoVat * 0.2;
            double totalLine = sumNoVat + vatAmount;
            totalInvoiceSum += totalLine;

            row.createCell(0).setCellValue(p.getName());

            // ИСПРАВЛЕНО: Берем только hsnCode (SKU). Если пусто — пишем "НЕ ЗАПОЛНЕНО"
            row.createCell(1).setCellValue(p.getHsnCode() != null && !p.getHsnCode().isEmpty() ? p.getHsnCode() : "НЕТ КОДА");

            // ИСПРАВЛЕНО: Берем Ед. изм. напрямую из базы товара
            row.createCell(2).setCellValue(p.getUnit() != null ? p.getUnit() : "шт");

            row.createCell(3).setCellValue(qty);
            row.createCell(4).setCellValue(round(priceNoVat));
            row.createCell(5).setCellValue(round(sumNoVat));
            row.createCell(6).setCellValue(round(vatAmount));
            row.createCell(7).setCellValue(round(totalLine));
        }

        rowIdx++;
        addRow(sheet, rowIdx++, "ИТОГО К ОПЛАТЕ:", round(totalInvoiceSum));

        return workbook;
    }

    private void addRow(Sheet sheet, int rowIdx, String label, Object value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(String.valueOf(value));
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}

