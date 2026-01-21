package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
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
    private static final Logger log = LoggerFactory.getLogger(InvoiceExcelService.class);

    public Workbook generateExcel(Order order) {
        return generateExcel(List.of(order), null, "Հաշիվ №" + order.getId());
    }

    public Workbook generateExcel(List<Order> orders, List<ReturnOrder> returns, String title) {
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        Map<String, String> seller = companySettings.getSellerData();
        fillSheetData(workbook, seller, orders, returns);
        return workbook;
    }

    private void fillSheetData(SXSSFWorkbook workbook, Map<String, String> seller, List<Order> orders, List<ReturnOrder> returns) {
        Map<String, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, Function.identity(), (existing, replacement) -> existing));

        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (existing, replacement) -> existing));

        // --- ЛОГИКА ПРОДАЖ ---
        if (orders != null && !orders.isEmpty()) {
            Map<String, List<Order>> ordersByShop = orders.stream()
                    .collect(Collectors.groupingBy(Order::getShopName));

            ordersByShop.forEach((shopName, shopOrders) -> {
                List<Order> separate = shopOrders.stream().filter(Order::isNeedsSeparateInvoice).toList();
                List<Order> combined = shopOrders.stream().filter(o -> !o.isNeedsSeparateInvoice()).toList();

                if (!combined.isEmpty()) {
                    Map<Long, Integer> mergedItems = new HashMap<>();
                    combined.forEach(o -> o.getItems().forEach((id, qty) -> mergedItems.merge(id, qty, Integer::sum)));

                    Order first = combined.get(0);
                    String sheetName = createSafeSheetName("Վաճառք " + shopName);
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), mergedItems, products, seller,
                            "Объединенный", first.getManagerId(), first.getCarNumber());
                }

                for (Order o : separate) {
                    String sheetName = createSafeSheetName("Վաճառք " + shopName + " №" + o.getId());
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), o.getItems(), products, seller,
                            "Заказ №" + o.getId(), o.getManagerId(), o.getCarNumber());
                }
            });
        }

        // --- ЛОГИКА ВОЗВРАТОВ ---
        if (returns != null && !returns.isEmpty()) {
            for (ReturnOrder r : returns) {
                String sheetName = createSafeSheetName("Վերադարձ " + r.getShopName() + " №" + r.getId());
                renderSheet(workbook, sheetName, r.getShopName(), clients.get(r.getShopName()), r.getItems(), products, seller,
                        "Возврат №" + r.getId(), r.getManagerId(), r.getCarNumber());
            }
        }
    }

    private void renderSheet(Workbook workbook, String sheetName, String shopName, Client c,
                             Map<Long, Integer> items, Map<Long, Product> products,
                             Map<String, String> seller, String docInfo, String managerId, String carNumber) {
        Sheet sheet = workbook.createSheet(sheetName);
        int rowIdx = 0;

        rowIdx = addSellerHeader(sheet, rowIdx, seller);

        addRow(sheet, rowIdx++, "ԳՆՈՐԴԻ ՏՎՅԱԼՆԵՐ", "");
        addRow(sheet, rowIdx++, "Անվանում:", shopName);
        if (c != null) {
            addRow(sheet, rowIdx++, "ՀՎՀՀ:", c.getInn());
            addRow(sheet, rowIdx++, "Հասցե:", c.getAddress());
        }
        addRow(sheet, rowIdx++, "Փաստաթուղթ:", docInfo);

        // Поля из ордера
        addRow(sheet, rowIdx++, "Մենեջեր:", managerId != null ? managerId : "");
        addRow(sheet, rowIdx++, "Ավտոմեքենայի համար:", carNumber != null ? carNumber : "");

        rowIdx++;
        fillItemsTable(sheet, rowIdx, items, products);
    }

    private int fillItemsTable(Sheet sheet, int rowIdx, Map<Long, Integer> items, Map<Long, Product> products) {
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"Ապրանք", "Կոդ (ԱՏԳ)", "Միավոր", "Քանակ", "Գին", "Գումար"};

        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (Map.Entry<Long, Integer> item : items.entrySet()) {
                Product p = products.get(item.getKey());
                if (p == null) continue;

                BigDecimal qty = BigDecimal.valueOf(item.getValue());
                BigDecimal price = (p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO);
                BigDecimal itemTotal = price.multiply(qty);
                totalAmount = totalAmount.add(itemTotal);

                writeProductRow(sheet.createRow(rowIdx++), p, item.getValue());
            }
        }

        rowIdx++;
        Row totalRow = sheet.createRow(rowIdx++);
        totalRow.createCell(4).setCellValue("Ընդհանուր:");
        totalRow.createCell(5).setCellValue(totalAmount.setScale(2, RoundingMode.HALF_UP).doubleValue());

        return rowIdx;
    }

    private void writeProductRow(Row row, Product p, int qty) {
        BigDecimal price = (p.getPrice() != null) ? p.getPrice() : BigDecimal.ZERO;
        BigDecimal totalLine = price.multiply(BigDecimal.valueOf(qty));

        int cellIdx = 0;
        row.createCell(cellIdx++).setCellValue(p.getName());
        row.createCell(cellIdx++).setCellValue(p.getHsnCode() != null ? p.getHsnCode() : "");
        row.createCell(cellIdx++).setCellValue(p.getUnit() != null ? p.getUnit() : "հատ");
        row.createCell(cellIdx++).setCellValue(qty);
        row.createCell(cellIdx++).setCellValue(price.doubleValue());
        row.createCell(cellIdx++).setCellValue(totalLine.doubleValue());
    }

    private void addRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value != null ? value : "");
    }

    private String createSafeSheetName(String name) {
        if (name.length() > 31) return name.substring(0, 28) + "...";
        return name.replaceAll("[\\\\*?/\\[\\]]", "-");
    }

    private int addSellerHeader(Sheet sheet, int rowIdx, Map<String, String> seller) {
        addRow(sheet, rowIdx++, "ՎԱՃԱՌՈՂԻ ՏՎՅԱԼՆԵՐ", "");
        addRow(sheet, rowIdx++, "Անվանում:", seller.get("name"));
        addRow(sheet, rowIdx++, "ՀՎՀՀ:", seller.get("inn"));
        addRow(sheet, rowIdx++, "Իրավաբանական հասցե:", seller.get("address"));
        addRow(sheet, rowIdx++, "Բանկի անվանում:", seller.get("bank"));
        addRow(sheet, rowIdx++, "Հաշվեհամար:", seller.get("iban"));
        return ++rowIdx;
    }
}























//    private final ProductRepository productRepository;
//    private final ClientRepository clientRepository;
//    private final CompanySettings companySettings;
//    private static final Logger log = LoggerFactory.getLogger(InvoiceExcelService.class);
//
//
//    public Workbook generateExcel(Order order) {
//        return generateExcel(List.of(order), null, "Հաշիվ №" + order.getId());
//    }
//
//    public Workbook generateExcel(List<Order> orders, List<ReturnOrder> returns, String title) {
//        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
//        Map<String, String> seller = companySettings.getSellerData();
//
//        // 2026: Группировка и обработка данных
//        fillSheetData(workbook, seller, orders, returns);
//
//        return workbook;
//    }
//
//    private void fillSheetData(SXSSFWorkbook workbook, Map<String, String> seller, List<Order> orders, List<ReturnOrder> returns) {
//        Map<String, Client> clients = clientRepository.findAll().stream()
//                .collect(Collectors.toMap(Client::getName, Function.identity(), (existing, replacement) -> existing));
//
//        Map<Long, Product> products = productRepository.findAll().stream()
//                .collect(Collectors.toMap(Product::getId, Function.identity(), (existing, replacement) -> existing));
//
//        // --- ЛОГИКА ЗАКАЗОВ (Վաճառք) ---
//        if (orders != null && !orders.isEmpty()) {
//            // Группируем заказы по магазину
//            Map<String, List<Order>> ordersByShop = orders.stream()
//                    .collect(Collectors.groupingBy(Order::getShopName));
//
//            ordersByShop.forEach((shopName, shopOrders) -> {
//                // Разделяем: те, кому нужна отдельная фактура, и те, кого объединяем
//                List<Order> separate = shopOrders.stream().filter(Order::isNeedsSeparateInvoice).toList();
//                List<Order> combined = shopOrders.stream().filter(o -> !o.isNeedsSeparateInvoice()).toList();
//
//                // 1. Объединенные (Отдельная фактура: НЕТ)
//                if (!combined.isEmpty()) {
//                    Map<Long, Integer> mergedItems = new HashMap<>();
//                    combined.forEach(o -> o.getItems().forEach((id, qty) -> mergedItems.merge(id, qty, Integer::sum)));
//
//                    String sheetName = createSafeSheetName("Վաճառք " + shopName);
//                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), mergedItems, products, seller, "Объединенный");
//                }
//
//                // 2. Отдельные (Отдельная фактура: ДА)
//                for (Order o : separate) {
//                    String sheetName = createSafeSheetName("Վաճառք " + shopName + " №" + o.getId());
//                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), o.getItems(), products, seller, "Заказ №" + o.getId());
//                }
//            });
//        }
//
//        // --- ЛОГИКА ВОЗВРАТОВ (Վերադարձ) ---
//        if (returns != null && !returns.isEmpty()) {
//            for (ReturnOrder r : returns) {
//                // Возвраты всегда печатаем отдельно, чтобы не путать бухгалтерию
//                String sheetName = createSafeSheetName("Վերադարձ " + r.getShopName() + " №" + r.getId());
//                renderSheet(workbook, sheetName, r.getShopName(), clients.get(r.getShopName()), r.getItems(), products, seller, "Возврат №" + r.getId());
//            }
//        }
//    }
//
//    private void renderSheet(Workbook workbook, String sheetName, String shopName, Client c,
//                             Map<Long, Integer> items, Map<Long, Product> products,
//                             Map<String, String> seller, String docInfo) {
//        Sheet sheet = workbook.createSheet(sheetName);
//        int rowIdx = 0;
//
//        rowIdx = addSellerHeader(sheet, rowIdx, seller);
//
//        addRow(sheet, rowIdx++, "ԳՆՈՐԴԻ ՏՎՅԱԼՆԵՐ (Данные покупателя)", "");
//        addRow(sheet, rowIdx++, "Անվանում (Магазин):", shopName);
//        if (c != null) {
//            addRow(sheet, rowIdx++, "ՀՎՀՀ (ИНН):", c.getInn());
//            addRow(sheet, rowIdx++, "Հասցե (Адрес):", c.getAddress());
//        }
//        addRow(sheet, rowIdx++, "Փաստաթուղթ (Документ):", docInfo);
//        rowIdx++;
//
//        fillItemsTable(sheet, rowIdx, items, products);
//    }
//
//    private String createSafeSheetName(String name) {
//        // Excel лимит 31 символ на имя листа
//        if (name.length() > 31) {
//            return name.substring(0, 28) + "...";
//        }
//        return name;
//    }
//
//    private int addSellerHeader(Sheet sheet, int rowIdx, Map<String, String> seller) {
//        addRow(sheet, rowIdx++, "ՎԱՃԱՌՈՂԻ ՏՎՅԱԼՆԵՐ", "");
//        addRow(sheet, rowIdx++, "Անվանում:", seller.get("name"));
//        addRow(sheet, rowIdx++, "ՀՎՀՀ:", seller.get("inn"));
//        addRow(sheet, rowIdx++, "Իրավաբանական հասցե:", seller.get("address"));
//        addRow(sheet, rowIdx++, "Բանկի անվանում:", seller.get("bank"));
//        addRow(sheet, rowIdx++, "Հաշվեհամար (IBAN):", seller.get("iban"));
//        addRow(sheet, rowIdx++, "ԱԱՀ վճարող է:", seller.get("isVatPayer"));
//        return ++rowIdx;
//    }
//
//    private int fillItemsTable(Sheet sheet, int rowIdx, Map<Long, Integer> items, Map<Long, Product> products) {
//        Row header = sheet.createRow(rowIdx++);
//        String[] cols = {"Ապրանք", "Կոդ (ԱՏԳ)", "Միավոր", "Քանակ", "Գին (առանց ԱԱՀ)", "ԱԱՀ գումար", "Ընդհանուր գումար"};
//        for (int i = 0; i < cols.length; i++) header.createCell(i).setCellValue(cols[i]);
//
//        BigDecimal totalAmount = BigDecimal.ZERO;
//        if (items != null) {
//            for (Map.Entry<Long, Integer> item : items.entrySet()) {
//                Product p = products.get(item.getKey());
//                if (p == null) continue;
//
//                BigDecimal qty = BigDecimal.valueOf(item.getValue());
//                BigDecimal itemTotal = (p.getPrice() != null ? p.getPrice() : BigDecimal.ZERO).multiply(qty);
//                totalAmount = totalAmount.add(itemTotal);
//
//                writeProductRow(sheet.createRow(rowIdx++), p, item.getValue());
//            }
//        }
//
//        rowIdx++;
//        Row totalRow = sheet.createRow(rowIdx++);
//        totalRow.createCell(5).setCellValue("Ընդհանուր (Итого):");
//        totalRow.createCell(6).setCellValue(totalAmount.setScale(2, RoundingMode.HALF_UP).doubleValue());
//        return rowIdx;
//    }
//
//    private void writeProductRow(Row row, Product p, int qty) {
//        BigDecimal priceWithVat = (p.getPrice() != null) ? p.getPrice() : BigDecimal.ZERO;
//        BigDecimal divisor = new BigDecimal("1.2");
//        BigDecimal priceNoVat = priceWithVat.divide(divisor, 2, RoundingMode.HALF_UP);
//        BigDecimal totalLine = priceWithVat.multiply(BigDecimal.valueOf(qty));
//        BigDecimal totalVatLine = totalLine.subtract(priceNoVat.multiply(BigDecimal.valueOf(qty)));
//
//        int cellIdx = 0;
//        row.createCell(cellIdx++).setCellValue(p.getName());
//        row.createCell(cellIdx++).setCellValue(p.getHsnCode() != null ? p.getHsnCode() : "");
//        row.createCell(cellIdx++).setCellValue(p.getUnit() != null ? p.getUnit() : "հատ");
//        row.createCell(cellIdx++).setCellValue(qty);
//        row.createCell(cellIdx++).setCellValue(priceNoVat.doubleValue());
//        row.createCell(cellIdx++).setCellValue(totalVatLine.doubleValue());
//        row.createCell(cellIdx++).setCellValue(totalLine.doubleValue());
//    }
//
//    private void addRow(Sheet sheet, int rowIdx, String label, String value) {
//        Row row = sheet.createRow(rowIdx);
//        row.createCell(0).setCellValue(label);
//        row.createCell(1).setCellValue(value != null ? value : "");
//    }
