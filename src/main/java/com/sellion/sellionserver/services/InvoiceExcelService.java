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
            fillSheetData(workbook.createSheet("Վաճառք"), orders, null, seller);
        }
        if (returns != null && !returns.isEmpty()) {
            fillSheetData(workbook.createSheet("Վերադարձ"), null, returns, seller);
        }
        return workbook;
    }

    private void fillSheetData(Sheet sheet, List<Order> orders, List<ReturnOrder> returns, Map<String, String> seller) {
        int rowIdx = 0;

        // 1. ՎԱՃԱՌՈՂԻ ՏՎՅԱԼՆԵՐ
        addRow(sheet, rowIdx++, "ՎԱՃԱՌՈՂԻ ՏՎՅԱԼՆԵՐ", "");
        addRow(sheet, rowIdx++, "Անվանում:", seller.get("name"));
        addRow(sheet, rowIdx++, "ՀՎՀՀ:", seller.get("inn"));
        addRow(sheet, rowIdx++, "Իրավաբանական հասցե:", seller.get("address"));
        addRow(sheet, rowIdx++, "Բանկի անվանում:", seller.get("bank"));
        addRow(sheet, rowIdx++, "Հաշվեհամար (IBAN):", seller.get("iban"));
        addRow(sheet, rowIdx++, "ԱԱՀ վճարող է:", seller.get("isVatPayer"));
        rowIdx++;

        // 2. ԱՂՅՈՒՍԱԿԻ ԳԼԽԱՄԱՍ (Заголовки таблицы)
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {
                "Առաքման ամսաթիվ", "Գնորդի անվանում", "Գնորդի ՀՎՀՀ", "Մենեջեր",
                "Ապրանքի անվանում", "Կոդ (SKU)", "Միավոր", "Քանակ",
                "Գին (առանց ԱԱՀ)", "ԱԱՀ գումար", "Ընդհանուր գումար"
        };
        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }

        // Оптимизация: кэш клиентов и продуктов
        Map<String, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, c -> c, (a, b) -> a));
        Map<String, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getName, p -> p, (a, b) -> a));

        // Заполнение данными и добавление итогов по каждому заказу
        if (orders != null) {
            for (Order o : orders) {
                Client c = clients.getOrDefault(o.getShopName(), new Client());
                double orderTotalAmount = 0.0;

                for (Map.Entry<String, Integer> item : o.getItems().entrySet()) {
                    Product p = products.get(item.getKey());
                    if (p == null) continue;
                    orderTotalAmount += p.getPrice() * item.getValue();
                    writeProductRow(sheet.createRow(rowIdx++), o, c, p, item.getValue());
                }

                // *** ИТОГОВЫЙ БЛОК ДЛЯ ТЕКУЩЕГО ЗАКАЗА ***
                Row totalRow = sheet.createRow(rowIdx++);
                totalRow.createCell(9).setCellValue("Ընդհանուր պատվերի համար:"); // Подпись
                totalRow.createCell(10).setCellValue(round(orderTotalAmount)); // Сумма

                rowIdx++; // Пустая строка между заказами
            }
        }
    }

    private void writeProductRow(Row row, Order o, Client c, Product p, int qty) {
        double priceWithVat = p.getPrice();
        double priceNoVat = priceWithVat / 1.2;
        double vatAmount = (priceWithVat - priceNoVat) * qty;
        double total = priceWithVat * qty;

        // *** ИСПРАВЛЕНИЕ ОШИБКИ ЗДЕСЬ ***
        // Мы берем только первую часть строки даты до символа 'T'
        String dateString = o.getCreatedAt() != null && o.getCreatedAt().contains("T") ? o.getCreatedAt().split("T")[0] : o.getCreatedAt();

        row.createCell(0).setCellValue(dateString);
        row.createCell(1).setCellValue(o.getShopName());
        row.createCell(2).setCellValue(c.getInn() != null ? c.getInn() : "---");
        row.createCell(3).setCellValue(o.getManagerId() != null ? o.getManagerId().toString() : "");
        row.createCell(4).setCellValue(p.getName());
        row.createCell(5).setCellValue(p.getHsnCode());
        row.createCell(6).setCellValue(p.getUnit() != null ? p.getUnit() : "հատ");
        row.createCell(7).setCellValue(qty);
        row.createCell(8).setCellValue(round(priceNoVat));
        row.createCell(9).setCellValue(round(vatAmount));
        row.createCell(10).setCellValue(round(total));
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