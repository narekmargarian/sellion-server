package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoiceExcelService {
    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final CompanySettings companySettings;


    // Метод для одного заказа (решает ошибку "Provided: Order")
    public Workbook generateExcel(Order order) {
        return generateExcel(List.of(order), null, "Հաշիվ №" + order.getId());
    }

    // Главный метод для списков заказов и возвратов
    public Workbook generateExcel(List<Order> orders, List<ReturnOrder> returns, String title) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        Map<String, String> seller = companySettings.getSellerData();

        if (orders != null && !orders.isEmpty()) {
            fillSheetData(workbook, "Վաճառք", orders, null, seller);
        }
        if (returns != null && !returns.isEmpty()) {
            fillSheetData(workbook, "Վերադարձ", null, returns, seller);
        }
        return workbook;
    }

    // Этот метод генерирует ОДИН ЛИСТ НА ОДИН ЗАКАЗ/КЛИЕНТА
    private void fillSheetData(Workbook workbook, String sheetBaseName, List<Order> orders, List<ReturnOrder> returns, Map<String, String> seller) {
        Map<String, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, Function.identity(), (existing, replacement) -> existing));
        Map<String, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getName, Function.identity(), (existing, replacement) -> existing));

        if (orders != null) {
            for (Order o : orders) {
                Client c = clients.getOrDefault(o.getShopName(), new Client());
                Sheet sheet = workbook.createSheet(sheetBaseName + " " + c.getName());
                int rowIdx = 0;

                // === БЛОК ПРОДАВЦА (Многострочный, как на фото) ===
                addRow(sheet, rowIdx++, "ՎԱՃԱՌՈՂԻ ՏՎՅԱԼՆԵՐ", ""); // Заголовок
                addRow(sheet, rowIdx++, "Անվանում:", seller.get("name"));
                addRow(sheet, rowIdx++, "ՀՎՀՀ:", seller.get("inn"));
                addRow(sheet, rowIdx++, "Իրավաբանական հասցե:", seller.get("address"));
                addRow(sheet, rowIdx++, "Բանկի անվանում:", seller.get("bank"));
                addRow(sheet, rowIdx++, "Հաշվեհամար (IBAN):", seller.get("iban"));
                addRow(sheet, rowIdx++, "ԱԱՀ վճարող է:", seller.get("isVatPayer"));
                rowIdx++; // Пустая строка

                // === БЛОК ПОКУПАТЕЛЯ (Многострочный, как на фото) ===
                addRow(sheet, rowIdx++, "ԳՆՈՐԴԻ ՏՎՅԱԼՆԵՐ", "");
                addRow(sheet, rowIdx++, "Անվանում:", c.getName());
                addRow(sheet, rowIdx++, "ՀՎՀՀ:", c.getInn());
                addRow(sheet, rowIdx++, "Հաշվեհամար (բանկ):", c.getBankAccount());
                addRow(sheet, rowIdx++, "Մենեջեր:", o.getManagerId() != null ? o.getManagerId().toString() : "");
                rowIdx++;

                // === ТАБЛИЦА ТОВАРОВ ===
                Row header = sheet.createRow(rowIdx++);
                String[] cols = {"Ապրանք", "Կոդ (SKU)", "Միավոր", "Քանակ", "Գին (առանց ԱԱՀ)", "ԱԱՀ գումար", "Ընդհանուր գումար"};
                for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

                double orderTotalAmount = 0.0;
                for (Map.Entry<String, Integer> item : o.getItems().entrySet()) {
                    Product p = products.get(item.getKey());
                    if (p == null) continue;
                    orderTotalAmount += p.getPrice() * item.getValue();
                    writeProductRow(sheet.createRow(rowIdx++), p, item.getValue());
                }

                // === ИТОГОВАЯ СУММА ===
                rowIdx++;
                Row totalRow = sheet.createRow(rowIdx++);
                totalRow.createCell(5).setCellValue("Ընդհանուր վճարվող գումար:");
                totalRow.createCell(6).setCellValue(round(orderTotalAmount));
            }
        }

        // Логика для возвратов
        if (returns != null) {
            for (ReturnOrder r : returns) {
                Client c = clients.getOrDefault(r.getShopName(), new Client());
                Sheet sheet = workbook.createSheet(sheetBaseName + " " + c.getName());
                int rowIdx = 0;

                // === БЛОК ПРОДАВЦА (Полная информация, многострочно) ===
                addRow(sheet, rowIdx++, "ՎԱՃԱՌՈՂԻ ՏՎՅԱԼՆԵՐ", "");
                addRow(sheet, rowIdx++, "Անվանում:", seller.get("name"));
                addRow(sheet, rowIdx++, "ՀՎՀՀ:", seller.get("inn"));
                addRow(sheet, rowIdx++, "Իրավաբանական հասցե:", seller.get("address"));
                addRow(sheet, rowIdx++, "Բանկի անվանում:", seller.get("bank"));
                addRow(sheet, rowIdx++, "Հաշվեհամար (IBAN):", seller.get("iban"));
                addRow(sheet, rowIdx++, "ԱԱՀ վճարող է:", seller.get("isVatPayer"));
                rowIdx++; // Пустая строка

                // === БЛОК ПОКУПАТЕЛЯ (Полная информация, многострочно) ===
                addRow(sheet, rowIdx++, "ԳՆՈՐԴԻ ՏՎՅԱԼՆԵՐ", "");
                addRow(sheet, rowIdx++, "Անվանում:", c.getName());
                addRow(sheet, rowIdx++, "ՀՎՀՀ:", c.getInn());
                addRow(sheet, rowIdx++, "Հաշվեհամար (բանկ):", c.getBankAccount());
                addRow(sheet, rowIdx++, "Մենեջեր:", r.getManagerId() != null ? r.getManagerId().toString() : "");
                rowIdx++;

                // ... (логика таблицы и итогов для возврата)
                Row header = sheet.createRow(rowIdx++);
                String[] cols = {"Ապրանք", "Կոդ (SKU)", "Միավոր", "Քանակ", "Գին (առանց ԱԱՀ)", "ԱԱՀ գումար", "Ընդհանուր գումար"};
                for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);

                double returnTotalAmount = 0.0;
                for (Map.Entry<String, Integer> item : r.getItems().entrySet()) {
                    Product p = products.get(item.getKey());
                    if (p == null) continue;
                    returnTotalAmount += p.getPrice() * item.getValue();
                    writeProductRow(sheet.createRow(rowIdx++), p, item.getValue());
                }

                rowIdx++;
                Row totalRow = sheet.createRow(rowIdx++);
                totalRow.createCell(5).setCellValue("Ընդհանուր վճարվող գումար:");
                totalRow.createCell(6).setCellValue(round(returnTotalAmount));
            }
        }
    }

    private void writeProductRow(Row row, Product p, int qty) {
        double priceWithVat = p.getPrice();
        double priceNoVat = priceWithVat / 1.2;
        double vatAmount = (priceWithVat - priceNoVat) * qty;
        double total = priceWithVat * qty;

        int cellIdx = 0;
        row.createCell(cellIdx++).setCellValue(p.getName());
        row.createCell(cellIdx++).setCellValue(p.getHsnCode());
        row.createCell(cellIdx++).setCellValue(p.getUnit() != null ? p.getUnit() : "հատ");
        row.createCell(cellIdx++).setCellValue(qty);
        row.createCell(cellIdx++).setCellValue(round(priceNoVat));
        row.createCell(cellIdx++).setCellValue(round(vatAmount));
        row.createCell(cellIdx++).setCellValue(round(total));
    }

    private void addRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}