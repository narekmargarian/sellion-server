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
                    Map<Long, BigDecimal> mergedPromos = new HashMap<>(); // Собираем акции для сводной

                    combined.forEach(o -> {
                        // Объединяем количество товаров
                        o.getItems().forEach((id, qty) -> mergedItems.merge(id, qty, Integer::sum));
                        // Объединяем акции (если у одного товара в разных заказах разные акции, затрется последней)
                        if (o.getAppliedPromoItems() != null) {
                            mergedPromos.putAll(o.getAppliedPromoItems());
                        }
                    });

                    Order first = combined.get(0);
                    BigDecimal groupPercent = Optional.ofNullable(first.getDiscountPercent()).orElse(BigDecimal.ZERO);

                    String sheetName = createSafeSheetName("Продажа " + shopName);
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), mergedItems, products, seller,
                            "Сводная накладная", first.getManagerId(), first.getCarNumber(), groupPercent, mergedPromos);
                }

                // Рендерим раздельные накладные
                for (Order o : separate) {
                    BigDecimal orderPercent = Optional.ofNullable(o.getDiscountPercent()).orElse(BigDecimal.ZERO);

                    String sheetName = createSafeSheetName("Прод " + shopName + " #" + o.getId());
                    renderSheet(workbook, sheetName, shopName, clients.get(shopName), o.getItems(), products, seller,
                            "Накладная №" + o.getId(), o.getManagerId(), o.getCarNumber(), orderPercent, o.getAppliedPromoItems());
                }
            });
        }

        // --- 3. ЛОГИКА ВОЗВРАТОВ ---
        if (returns != null && !returns.isEmpty()) {
            for (ReturnOrder r : returns) {
                String sheetName = createSafeSheetName("Возврат " + r.getShopName() + " #" + r.getId());
                // Для возвратов передаем пустую карту акций
                renderSheet(workbook, sheetName, r.getShopName(), clients.get(r.getShopName()), r.getItems(), products, seller,
                        "Акт возврата №" + r.getId(), r.getManagerId(), r.getCarNumber(), BigDecimal.ZERO, new HashMap<>());
            }
        }
    }




    private void renderSheet(SXSSFWorkbook workbook, String sheetName, String shopName, Client c,
                             Map<Long, Integer> items, Map<Long, Product> products,
                             Map<String, String> seller, String docInfo, String managerId, String carNumber,
                             BigDecimal discountPercent, Map<Long, BigDecimal> appliedPromos) {

        String finalName = createSafeSheetName(sheetName);
        Sheet sheet = workbook.createSheet(finalName);
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
        headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
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
        rowHeaderS.createCell(1).setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1));

        addHeaderRowWithBorder(sheet, rowIdx++, "Компания:", seller.getOrDefault("name", ""), borderStyle);
        addHeaderRowWithBorder(sheet, rowIdx++, "ИНН:", seller.getOrDefault("inn", ""), borderStyle);
        addHeaderRowWithBorder(sheet, rowIdx++, "Банк/Счет:", seller.getOrDefault("bank", "") + " " + seller.getOrDefault("iban", ""), borderStyle);

        rowIdx++;

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

        // Находим этот блок в методе renderSheet
        if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) > 0) {
            // ИСПРАВЛЕНО: используем stripTrailingZeros().toPlainString() для красоты (12.0% -> 12%)
            String discountStr = discountPercent.stripTrailingZeros().toPlainString() + "%";
            addHeaderRowWithBorder(sheet, rowIdx++, "Скидка магазина:", discountStr, borderStyle);
        }


        addHeaderRowWithBorder(sheet, rowIdx++, "Документ:", docInfo, borderStyle);
        addHeaderRowWithBorder(sheet, rowIdx++, "Менеджер / Авто:",
                (managerId != null ? managerId : "") + " / " + (carNumber != null ? carNumber : ""), borderStyle);

        rowIdx += 2;

        // ВЫЗОВ ИСПРАВЛЕННОГО МЕТОДА (с передачей акций)
        rowIdx = fillItemsTable(sheet, rowIdx, items, products, discountPercent, appliedPromos);

        // Автоподбор для 8 колонок
        for (int i = 0; i < 8; i++) {
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


    private int fillItemsTable(Sheet sheet, int rowIdx, Map<Long, Integer> items, Map<Long, Product> products,
                               BigDecimal discountPercent, Map<Long, BigDecimal> appliedPromos) {
        Workbook wb = sheet.getWorkbook();
        DataFormat format = wb.createDataFormat();

        // Стили с поддержкой одного знака после запятой
        CellStyle borderStyle = wb.createCellStyle();
        borderStyle.setBorderBottom(BorderStyle.THIN);
        borderStyle.setBorderTop(BorderStyle.THIN);
        borderStyle.setBorderRight(BorderStyle.THIN);
        borderStyle.setBorderLeft(BorderStyle.THIN);
        borderStyle.setVerticalAlignment(VerticalAlignment.CENTER);
        borderStyle.setDataFormat(format.getFormat("#,##0.0")); // Формат: 1 751.2

        CellStyle centerStyle = wb.createCellStyle();
        centerStyle.cloneStyleFrom(borderStyle);
        centerStyle.setAlignment(HorizontalAlignment.CENTER);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.cloneStyleFrom(centerStyle);
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        headerStyle.setFont(boldFont);
        headerStyle.setDataFormat(format.getFormat("@")); // Для заголовков текст

        // 1. Заголовки
        Row header = sheet.createRow(rowIdx++);
        String[] colNames = {"Товар", "Код(ԱՏԳ)", "Ед.", "Кол-во", "Прайс", "%", "Цена (со скидкой)", "Сумма"};

        for (int i = 0; i < colNames.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(colNames[i]);
            cell.setCellStyle(headerStyle);
        }

        BigDecimal totalAmount = BigDecimal.ZERO;

        // 2. Заполнение строками
        if (items != null) {
            for (Map.Entry<Long, Integer> entry : items.entrySet()) {
                Long productId = entry.getKey();
                Product p = products.get(productId);
                if (p == null) continue;

                BigDecimal itemPromo = (appliedPromos != null) ? appliedPromos.getOrDefault(productId, BigDecimal.ZERO) : BigDecimal.ZERO;
                BigDecimal finalPercent = (itemPromo.compareTo(BigDecimal.ZERO) > 0) ? itemPromo : (discountPercent != null ? discountPercent : BigDecimal.ZERO);

                BigDecimal qty = BigDecimal.valueOf(entry.getValue());
                BigDecimal basePrice = Optional.ofNullable(p.getPrice()).orElse(BigDecimal.ZERO);

                // ИСПРАВЛЕНО: modifier и finalPrice теперь сохраняют точность
                BigDecimal modifier = BigDecimal.ONE.subtract(finalPercent.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));

                // Расчет: Цена за 1 шт с округлением до 0.1
                BigDecimal finalPrice = basePrice.multiply(modifier).setScale(1, RoundingMode.HALF_UP);

                // Сумма по строке (Цена * Кол-во)
                BigDecimal itemTotal = finalPrice.multiply(qty).setScale(1, RoundingMode.HALF_UP);
                totalAmount = totalAmount.add(itemTotal);

                Row row = sheet.createRow(rowIdx++);
                int c = 0;
                createStyledCell(row, c++, p.getName(), borderStyle);
                createStyledCell(row, c++, p.getHsnCode() != null ? p.getHsnCode() : "", borderStyle);
                createStyledCell(row, c++, p.getUnit() != null ? p.getUnit() : "шт", centerStyle);
                createStyledCell(row, c++, entry.getValue().doubleValue(), centerStyle);

                // ИСПРАВЛЕНО: Используем doubleValue() вместо intValue()
                createStyledCell(row, c++, basePrice.doubleValue(), borderStyle);

                // Колонка %
                String percentValue = (finalPercent.compareTo(BigDecimal.ZERO) > 0) ? finalPercent.stripTrailingZeros().toPlainString() : "";
                createStyledCell(row, c++, percentValue, centerStyle);

                createStyledCell(row, c++, finalPrice.doubleValue(), borderStyle);
                createStyledCell(row, c++, itemTotal.doubleValue(), borderStyle);
            }
        }

        // 3. Итоговая строка
        rowIdx++;
        Row totalRow = sheet.createRow(rowIdx++);

        Cell totalLabel = totalRow.createCell(6);
        totalLabel.setCellValue("ИТОГО К ОПЛАТЕ:");
        totalLabel.setCellStyle(headerStyle);

        Cell sumCell = totalRow.createCell(7);
        // ИСПРАВЛЕНО: Итоговая сумма с точностью 0.1
        sumCell.setCellValue(totalAmount.setScale(1, RoundingMode.HALF_UP).doubleValue());
        sumCell.setCellStyle(headerStyle);

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





















