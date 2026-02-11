package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
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
        // 1. Предварительная загрузка справочников
        Map<String, Client> clients = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getName, Function.identity(), (e, r) -> e));

        Map<Long, Product> products = productRepository.findAll().stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (e, r) -> e));

        // --- 2. ЛОГИКА ПРОДАЖ ---
        if (orders != null && !orders.isEmpty()) {
            Map<String, List<Order>> ordersByShop = orders.stream()
                    .collect(Collectors.groupingBy(Order::getShopName));

            ordersByShop.forEach((shopName, shopOrders) -> {
                List<Order> separate = shopOrders.stream()
                        .filter(o -> Boolean.TRUE.equals(o.getNeedsSeparateInvoice()))
                        .toList();

                List<Order> combined = shopOrders.stream()
                        .filter(o -> !Boolean.TRUE.equals(o.getNeedsSeparateInvoice()))
                        .toList();

                // Рендерим сводную накладную
                if (!combined.isEmpty()) {
                    Map<Long, Integer> mergedItems = new HashMap<>();
                    combined.forEach(o -> o.getItems().forEach((id, qty) -> mergedItems.merge(id, qty, Integer::sum)));

                    Order first = combined.get(0);
                    // Берем процент из первого заказа сводной группы
                    BigDecimal groupPercent = Optional.ofNullable(first.getDiscountPercent()).orElse(BigDecimal.ZERO);

                    String sheetName = createSafeSheetName("Продажа " + shopName);
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), mergedItems, products, seller,
                            "Сводная накладная", first.getManagerId(), first.getCarNumber(), groupPercent);
                }

                // Рендерим раздельные накладные
                for (Order o : separate) {
                    BigDecimal orderPercent = Optional.ofNullable(o.getDiscountPercent()).orElse(BigDecimal.ZERO);

                    String sheetName = createSafeSheetName("Прод " + shopName + " #" + o.getId());
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), o.getItems(), products, seller,
                            "Накладная №" + o.getId(), o.getManagerId(), o.getCarNumber(), orderPercent);
                }
            });
        }

        // --- 3. ЛОГИКА ВОЗВРАТОВ ---
        if (returns != null && !returns.isEmpty()) {
            for (ReturnOrder r : returns) {
                String sheetName = createSafeSheetName("Возврат " + r.getShopName() + " #" + r.getId());
                // Для возвратов передаем BigDecimal.ZERO, если процент на них не распространяется
                renderSheet(workbook, sheetName, r.getShopName(), clients.get(r.getShopName()), r.getItems(), products, seller,
                        "Акт возврата №" + r.getId(), r.getManagerId(), r.getCarNumber(), BigDecimal.ZERO);
            }
        }
    }


//    private void renderSheet(SXSSFWorkbook workbook, String sheetName, String shopName, Client c,
//                             Map<Long, Integer> items, Map<Long, Product> products,
//                             Map<String, String> seller, String docInfo, String managerId, String carNumber,
//                             BigDecimal discountPercent) {
//
//        String finalName = createSafeSheetName(sheetName);
//        Sheet sheet = workbook.createSheet(finalName);
//        int rowIdx = 0;
//
//        // 1. Реквизиты Продавца
//        rowIdx = addSellerHeader(sheet, rowIdx, seller);
//
//        // 2. Реквизиты Покупателя
//        addHeaderRow(sheet, rowIdx++, "ДАННЫЕ ПОКУПАТЕЛЯ", "");
//        addHeaderRow(sheet, rowIdx++, "Наименование:", shopName);
//
//        if (c != null) {
//            addHeaderRow(sheet, rowIdx++, "ИНН (ՀՎՀՀ):", (c.getInn() != null ? c.getInn() : "---"));
//            addHeaderRow(sheet, rowIdx++, "Адрес:", (c.getAddress() != null ? c.getAddress() : "---"));
//
//            // --- НОВОЕ: БАНК И СЧЕТ ПОКУПАТЕЛЯ ---
//            addHeaderRow(sheet, rowIdx++, "Банк:", (c.getBankName() != null ? c.getBankName() : "---"));
//            addHeaderRow(sheet, rowIdx++, "Счет:", (c.getBankAccount() != null ? c.getBankAccount() : "---"));
//            // ------------------------------------
//        } else {
//            addHeaderRow(sheet, rowIdx++, "ИНН / Адрес:", "Данные клиента не найдены");
//        }
//
//        // 3. Информация о скидке
//        String percentLabel = discountPercent.compareTo(BigDecimal.ZERO) >= 0 ? "Скидка магазина:" : "Процент магазина:";
//        addHeaderRow(sheet, rowIdx++, percentLabel, discountPercent.toString() + "%");
//
//        // 4. Служебная информация
//        addHeaderRow(sheet, rowIdx++, "Документ:", docInfo);
//        addHeaderRow(sheet, rowIdx++, "Менеджер / Авто:",
//                (managerId != null ? managerId : "") + " / " + (carNumber != null ? carNumber : ""));
//
//        rowIdx++;
//
//        // 5. Таблица товаров (используем ранее исправленный метод с 7 колонками)
//        rowIdx = fillItemsTable(sheet, rowIdx, items, products, discountPercent);
//    }


//    private int fillItemsTable(Sheet sheet, int rowIdx, Map<Long, Integer> items, Map<Long, Product> products, BigDecimal discountPercent) {
//        Row header = sheet.createRow(rowIdx++);
//        // 7 колонок согласно вашему запросу
//        String[] cols = {"Товар", "Код(ԱՏԳ)", "Ед.", "Кол-во", "Прайс", "Цена (со скидкой)", "Сумма"};
//
//        for (int i = 0; i < cols.length; i++) {
//            header.createCell(i).setCellValue(cols[i]);
//        }
//
//        BigDecimal totalAmount = BigDecimal.ZERO;
//
//        // Расчет коэффициента скидки: (1 - percent/100)
//        BigDecimal modifier = BigDecimal.ONE.subtract(
//                Optional.ofNullable(discountPercent).orElse(BigDecimal.ZERO)
//                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
//        );
//
//        if (items != null) {
//            for (Map.Entry<Long, Integer> item : items.entrySet()) {
//                Product p = products.get(item.getKey());
//                if (p == null) continue;
//
//                BigDecimal qty = BigDecimal.valueOf(item.getValue());
//
//                // 1. Базовая цена (Прайс)
//                BigDecimal basePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);
//
//                // 2. Итоговая цена (Цена со скидкой), округляем до целых для ֏
//                BigDecimal finalPrice = basePrice.multiply(modifier).setScale(0, RoundingMode.HALF_UP);
//
//                // 3. Сумма по строке
//                BigDecimal itemTotal = finalPrice.multiply(qty);
//                totalAmount = totalAmount.add(itemTotal);
//
//                Row row = sheet.createRow(rowIdx++);
//                row.createCell(0).setCellValue(p.getName()); // Товар
//                row.createCell(1).setCellValue(p.getHsnCode() != null ? p.getHsnCode() : ""); // Код(ԱՏԳ)
//                row.createCell(2).setCellValue(p.getUnit() != null ? p.getUnit() : "шт"); // Ед.
//                row.createCell(3).setCellValue(item.getValue()); // Кол-во
//                row.createCell(4).setCellValue(basePrice.doubleValue()); // Прайс (базовый)
//                row.createCell(5).setCellValue(finalPrice.doubleValue()); // Цена (со скидкой)
//                row.createCell(6).setCellValue(itemTotal.doubleValue()); // Сумма (индекс 6)
//            }
//        }
//
//        rowIdx++;
//        Row totalRow = sheet.createRow(rowIdx++);
//        // Сдвигаем надпись ИТОГО к последним колонкам
//        totalRow.createCell(5).setCellValue("ИТОГО К ОПЛАТЕ:");
//        totalRow.createCell(6).setCellValue(totalAmount.doubleValue());
//
//        // Автоматическая подстройка ширины колонок (опционально, для красоты)
//        for (int i = 0; i < cols.length; i++) {
//            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 512);
//        }
//
//        return rowIdx;
//    }


    private void renderSheet(SXSSFWorkbook workbook, String sheetName, String shopName, Client c,
                             Map<Long, Integer> items, Map<Long, Product> products,
                             Map<String, String> seller, String docInfo, String managerId, String carNumber,
                             BigDecimal discountPercent) {

        String finalName = createSafeSheetName(sheetName);
        Sheet sheet = workbook.createSheet(finalName);

        // Включаем отслеживание для автоподбора ширины
        ((SXSSFSheet) sheet).trackAllColumnsForAutoSizing();

        // 1. Создание стилей
        CellStyle borderStyle = workbook.createCellStyle();
        borderStyle.setBorderTop(BorderStyle.THIN);
        borderStyle.setBorderBottom(BorderStyle.THIN);
        borderStyle.setBorderLeft(BorderStyle.THIN);
        borderStyle.setBorderRight(BorderStyle.THIN);

        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.cloneStyleFrom(borderStyle);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); // Легкий фон для заголовков
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        int rowIdx = 0;

        // --- ДАННЫЕ ПРОДАВЦА ---
        Row rowHeaderS = sheet.createRow(rowIdx++);
        Cell cellS = rowHeaderS.createCell(0);
        cellS.setCellValue("ДАННЫЕ ПРОДАВЦА");
        cellS.setCellStyle(headerStyle);
        rowHeaderS.createCell(1).setCellStyle(headerStyle); // Нужно для отрисовки правой границы
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1));

        addHeaderRowWithBorder(sheet, rowIdx++, "Компания:", seller.getOrDefault("name", ""), borderStyle);
        addHeaderRowWithBorder(sheet, rowIdx++, "ИНН:", seller.getOrDefault("inn", ""), borderStyle);
        addHeaderRowWithBorder(sheet, rowIdx++, "Банк/Счет:", seller.getOrDefault("bank", "") + " " + seller.getOrDefault("iban", ""), borderStyle);

        rowIdx++; // Маленький отступ

        // --- ДАННЫЕ ПОКУПАТЕЛЯ ---
        Row rowHeaderP = sheet.createRow(rowIdx++);
        Cell cellP = rowHeaderP.createCell(0);
        cellP.setCellValue("ДАННЫЕ ПОКУПАТЕЛЯ");
        cellP.setCellStyle(headerStyle);
        rowHeaderP.createCell(1).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1));

        if (c != null) {
            addHeaderRowWithBorder(sheet, rowIdx++, "Наименование:", shopName, borderStyle);
            addHeaderRowWithBorder(sheet, rowIdx++, "ИНН (ՀՎՀՀ):", c.getInn(), borderStyle);
            addHeaderRowWithBorder(sheet, rowIdx++, "Адрес:", c.getAddress(), borderStyle);
            addHeaderRowWithBorder(sheet, rowIdx++, "Банк:", c.getBankName(), borderStyle);
            addHeaderRowWithBorder(sheet, rowIdx++, "Счет:", c.getBankAccount(), borderStyle);
        } else {
            addHeaderRowWithBorder(sheet, rowIdx++, "Наименование:", shopName, borderStyle);
            addHeaderRowWithBorder(sheet, rowIdx++, "Данные:", "Реквизиты клиента не заполнены", borderStyle);
        }

        if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {
            addHeaderRowWithBorder(sheet, rowIdx++, "Скидка магазина:", discountPercent.toString() + "%", borderStyle);
        } else {
            // Если это возврат или скидки нет, можно просто пропустить эту строку или написать "Без скидки"
            // Для чистоты документа согласно вашему фото — просто не выводим эту строку
        }

//        addHeaderRowWithBorder(sheet, rowIdx++, "Скидка магазина:", discountPercent.toString() + "%", borderStyle);
        addHeaderRowWithBorder(sheet, rowIdx++, "Документ:", docInfo, borderStyle);
        addHeaderRowWithBorder(sheet, rowIdx++, "Менеджер / Авто:",
                (managerId != null ? managerId : "") + " / " + (carNumber != null ? carNumber : ""), borderStyle);

        rowIdx += 2; // Отступ перед таблицей

        // 5. Таблица товаров (используем исправленный метод с 7 колонками)
        // ВАЖНО: Передаем созданные стили в метод таблицы, чтобы границы были одинаковыми
        rowIdx = fillItemsTable(sheet, rowIdx, items, products, discountPercent);

        // Автоподбор ширины для всех колонок
        for (int i = 0; i < 7; i++) {
            sheet.autoSizeColumn(i);
        }
    }


    // Вспомогательный метод для строк с границами
    private void addHeaderRowWithBorder(Sheet sheet, int rowIdx, String label, String value, CellStyle style) {
        // 1. Создаем строку или получаем существующую (безопасный метод)
        Row row = sheet.getRow(rowIdx);
        if (row == null) {
            row = sheet.createRow(rowIdx);
        }

        // 2. Ячейка А (Заголовок: Компания, ИНН и т.д.)
        Cell cellLabel = row.createCell(0);
        cellLabel.setCellValue(label != null ? label : "");
        cellLabel.setCellStyle(style); // Применяем стиль с границами

        // 3. Ячейка B (Значение: Название компании, Номер счета и т.д.)
        Cell cellValue = row.createCell(1);
        cellValue.setCellValue(value != null ? value : "");
        cellValue.setCellStyle(style); // Применяем тот же стиль границ
    }


    private int fillItemsTable(Sheet sheet, int rowIdx, Map<Long, Integer> items, Map<Long, Product> products, BigDecimal discountPercent) {
        Workbook wb = sheet.getWorkbook();

        // Определение режима: если скидка 0 или null — это Возврат
        boolean isReturn = discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0;

        // 1. Создание стилей (те же, что в вашем коде)
        CellStyle borderStyle = wb.createCellStyle();
        borderStyle.setBorderBottom(BorderStyle.THIN);
        borderStyle.setBorderTop(BorderStyle.THIN);
        borderStyle.setBorderRight(BorderStyle.THIN);
        borderStyle.setBorderLeft(BorderStyle.THIN);
        borderStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        CellStyle centerStyle = wb.createCellStyle();
        centerStyle.cloneStyleFrom(borderStyle);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.cloneStyleFrom(centerStyle);
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);

        // 2. Отрисовка динамических заголовков
        Row header = sheet.createRow(rowIdx++);
        List<String> colNames = new ArrayList<>(Arrays.asList("Товар", "Код(ԱՏԳ)", "Ед.", "Кол-во"));

        if (isReturn) {
            colNames.add("Цена"); // Для возврата только одна цена
        } else {
            colNames.add("Прайс");
            colNames.add("Цена (со скидкой)");
        }
        colNames.add("Сумма");

        for (int i = 0; i < colNames.size(); i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(colNames.get(i));
            cell.setCellStyle(headerStyle);
        }

        // 3. Расчет модификатора скидки
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal modifier = isReturn ? BigDecimal.ONE : BigDecimal.ONE.subtract(
                Optional.ofNullable(discountPercent).orElse(BigDecimal.ZERO)
                        .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)
        );

        // 4. Заполнение строк товарами
        if (items != null) {
            for (Map.Entry<Long, Integer> entry : items.entrySet()) {
                Product p = products.get(entry.getKey());
                if (p == null) continue;

                BigDecimal qty = BigDecimal.valueOf(entry.getValue());
                BigDecimal basePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);
                BigDecimal finalPrice = basePrice.multiply(modifier).setScale(0, RoundingMode.HALF_UP);
                BigDecimal itemTotal = finalPrice.multiply(qty);
                totalAmount = totalAmount.add(itemTotal);

                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                createStyledCell(row, c++, p.getName(), borderStyle);
                createStyledCell(row, c++, p.getHsnCode() != null ? p.getHsnCode() : "", borderStyle);
                createStyledCell(row, c++, p.getUnit() != null ? p.getUnit() : "шт", centerStyle);
                createStyledCell(row, c++, entry.getValue(), centerStyle);

                if (!isReturn) {
                    createStyledCell(row, c++, basePrice.intValue(), borderStyle); // Только для Продаж
                }
                createStyledCell(row, c++, finalPrice.intValue(), borderStyle);
                createStyledCell(row, c++, itemTotal.intValue(), borderStyle);
            }
        }

        // 5. Итоговая строка (автоматический расчет колонок)
        rowIdx++;
        Row totalRow = sheet.createRow(rowIdx++);

        // Если колонок меньше, сдвигаем "Итого" влево
        int labelCol = isReturn ? 4 : 5;
        int sumCol = isReturn ? 5 : 6;

        Cell totalLabel = totalRow.createCell(labelCol);
        totalLabel.setCellValue("ИТОГО К ОПЛАТЕ:");
        totalLabel.setCellStyle(headerStyle);

        Cell sumCell = totalRow.createCell(sumCol);
        sumCell.setCellValue(totalAmount.setScale(0, RoundingMode.HALF_UP).doubleValue());
        sumCell.setCellStyle(headerStyle);

        // 6. Настройка ширины колонок (адаптивно)
        sheet.setColumnWidth(0, 12000);
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 2000);
        sheet.setColumnWidth(3, 2500);
        if (!isReturn) {
            sheet.setColumnWidth(4, 3500);
            sheet.setColumnWidth(5, 4500);
            sheet.setColumnWidth(6, 4500);
        } else {
            sheet.setColumnWidth(4, 4500);
            sheet.setColumnWidth(5, 4500);
        }

        return rowIdx;
    }


    // Вспомогательный метод для создания ячеек со стилем
    private void createStyledCell(Row row, int col, Object value, CellStyle style) {
        Cell cell = row.createCell(col);
        if (value instanceof String) cell.setCellValue((String) value);
        else if (value instanceof Number) cell.setCellValue(((Number) value).doubleValue());
        cell.setCellStyle(style);
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





















