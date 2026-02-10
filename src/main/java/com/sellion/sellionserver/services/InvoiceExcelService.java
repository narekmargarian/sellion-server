package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceExcelService {

    private final ProductRepository productRepository;
    private final ClientRepository clientRepository;
    private final CompanySettings companySettings;

    /**
     * Основной метод генерации Excel.
     * ИДЕАЛЬНО: Использует SXSSFWorkbook для экономии памяти (сброс строк на диск).
     */
    public Workbook generateExcel(List<Order> orders, List<ReturnOrder> returns, String title) {
        // Окно в 100 строк в памяти, остальное — во временных файлах
        SXSSFWorkbook workbook = new SXSSFWorkbook(100);
        Map<String, String> seller = companySettings.getSellerData();

        try {
            fillSheetData(workbook, seller, orders, returns);
        } catch (Exception e) {
            log.error("Критическая ошибка при формировании Excel: ", e);
            // Важно закрыть книгу даже при ошибке, чтобы удалить временные файлы
            workbook.dispose();
            throw e;
        }
        return workbook;
    }

    private void fillSheetData(SXSSFWorkbook workbook, Map<String, String> seller, List<Order> orders, List<ReturnOrder> returns) {
        // 1. Предварительная загрузка справочников (Batch load)
        Map<String, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, Function.identity(), (e, r) -> e));

        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (e, r) -> e));

        // --- 2. ЛОГИКА ПРОДАЖ ---
        if (orders != null && !orders.isEmpty()) {
            Map<String, List<Order>> ordersByShop = orders.stream()
                    .collect(Collectors.groupingBy(Order::getShopName));

            ordersByShop.forEach((shopName, shopOrders) -> {
                // ИСПРАВЛЕНО: Используем getNeedsSeparateInvoice() и добавляем проверку на null (Boolean.TRUE.equals)
                List<Order> separate = shopOrders.stream()
                        .filter(o -> Boolean.TRUE.equals(o.getNeedsSeparateInvoice()))
                        .toList();

                List<Order> combined = shopOrders.stream()
                        .filter(o -> !Boolean.TRUE.equals(o.getNeedsSeparateInvoice()))
                        .toList();

                // Рендерим сводную накладную (если есть заказы для объединения)
                if (!combined.isEmpty()) {
                    Map<Long, Integer> mergedItems = new HashMap<>();
                    combined.forEach(o -> o.getItems().forEach((id, qty) -> mergedItems.merge(id, qty, Integer::sum)));

                    Order first = combined.get(0);
                    String sheetName = createSafeSheetName("Продажа " + shopName);
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), mergedItems, products, seller,
                            "Сводная накладная", first.getManagerId(), first.getCarNumber());
                }

                // Рендерим раздельные накладные
                for (Order o : separate) {
                    String sheetName = createSafeSheetName("Прод " + shopName + " #" + o.getId());
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), o.getItems(), products, seller,
                            "Накладная №" + o.getId(), o.getManagerId(), o.getCarNumber());
                }
            });
        }

        // --- 3. ЛОГИКА ВОЗВРАТОВ ---
        if (returns != null && !returns.isEmpty()) {
            for (ReturnOrder r : returns) {
                String sheetName = createSafeSheetName("Возврат " + r.getShopName() + " #" + r.getId());
                renderSheet(workbook, sheetName, r.getShopName(), clients.get(r.getShopName()), r.getItems(), products, seller,
                        "Акт возврата №" + r.getId(), r.getManagerId(), r.getCarNumber());
            }
        }
    }


    private void renderSheet(SXSSFWorkbook workbook, String sheetName, String shopName, Client c,
                             Map<Long, Integer> items, Map<Long, Product> products,
                             Map<String, String> seller, String docInfo, String managerId, String carNumber) {

        // Защита от дублирования имен листов (Apache POI упадет при дубле)
        String finalName = sheetName;
        int count = 1;
        while (workbook.getSheet(finalName) != null) {
            finalName = sheetName + "_" + (count++);
        }

        Sheet sheet = workbook.createSheet(finalName);
        int rowIdx = 0;

        // Реквизиты Продавца
        rowIdx = addSellerHeader(sheet, rowIdx, seller);

        // Реквизиты Покупателя
        addHeaderRow(sheet, rowIdx++, "ДАННЫЕ ПОКУПАТЕЛЯ", "");
        addHeaderRow(sheet, rowIdx++, "Наименование:", shopName);
        if (c != null) {
            addHeaderRow(sheet, rowIdx++, "ИНН (ՀՎՀՀ):", c.getInn());
            addHeaderRow(sheet, rowIdx++, "Адрес:", c.getAddress());
            String bankInfo = (c.getBankName() != null ? c.getBankName() : "") + " " + (c.getBankAccount() != null ? c.getBankAccount() : "");
            addHeaderRow(sheet, rowIdx++, "Банк/Счет:", bankInfo.trim());
        }
        addHeaderRow(sheet, rowIdx++, "Документ:", docInfo);
        addHeaderRow(sheet, rowIdx++, "Менеджер / Авто:", (managerId != null ? managerId : "") + " / " + (carNumber != null ? carNumber : ""));

        rowIdx++;
        // Таблица товаров
        rowIdx = fillItemsTable(sheet, rowIdx, items, products);
    }

    private int fillItemsTable(Sheet sheet, int rowIdx, Map<Long, Integer> items, Map<Long, Product> products) {
        Row header = sheet.createRow(rowIdx++);
        String[] cols = {"Товар", "Код (ԱՏԳ)", "Ед.", "Кол-во", "Цена", "Сумма"};
        for (int i = 0; i < cols.length; i++) {
            header.createCell(i).setCellValue(cols[i]);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        if (items != null) {
            for (Map.Entry<Long, Integer> item : items.entrySet()) {
                Product p = products.get(item.getKey());
                if (p == null) continue;

                BigDecimal qty = BigDecimal.valueOf(item.getValue());
                BigDecimal price = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);
                BigDecimal itemTotal = price.multiply(qty);
                totalAmount = totalAmount.add(itemTotal);

                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(p.getName());
                row.createCell(1).setCellValue(p.getHsnCode() != null ? p.getHsnCode() : "");
                row.createCell(2).setCellValue(p.getUnit() != null ? p.getUnit() : "шт");
                row.createCell(3).setCellValue(item.getValue());
                row.createCell(4).setCellValue(price.setScale(2, RoundingMode.HALF_UP).doubleValue());
                row.createCell(5).setCellValue(itemTotal.setScale(2, RoundingMode.HALF_UP).doubleValue());
            }
        }

        rowIdx++;
        Row totalRow = sheet.createRow(rowIdx++);
        totalRow.createCell(4).setCellValue("ИТОГО:");
        totalRow.createCell(5).setCellValue(totalAmount.setScale(2, RoundingMode.HALF_UP).doubleValue());

        return rowIdx;
    }

    private int addSellerHeader(Sheet sheet, int rowIdx, Map<String, String> seller) {
        addHeaderRow(sheet, rowIdx++, "ДАННЫЕ ПРОДАВЦА", "");
        addHeaderRow(sheet, rowIdx++, "Компания:", seller.getOrDefault("name", "Sellion ERP"));
        addHeaderRow(sheet, rowIdx++, "ИНН:", seller.getOrDefault("inn", "---"));
        addHeaderRow(sheet, rowIdx++, "Банк/Счет:", seller.getOrDefault("bank", "") + " " + seller.getOrDefault("iban", ""));
        return ++rowIdx;
    }

    private void addHeaderRow(Sheet sheet, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }

    private String createSafeSheetName(String name) {
        // Ограничение Excel: имя листа до 31 символа и без запрещенных знаков
        String safeName = name.replaceAll("[\\\\*?/\\[\\]]", "-");
        return safeName.length() > 30 ? safeName.substring(0, 27) + "..." : safeName;
    }
}





















