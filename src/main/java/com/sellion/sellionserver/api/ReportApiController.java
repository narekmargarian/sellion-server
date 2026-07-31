package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.EmailService;
import com.sellion.sellionserver.services.InvoiceExcelService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reports/excel")
@RequiredArgsConstructor
@Slf4j
public class ReportApiController {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final InvoiceExcelService invoiceExcelService;
    private final EmailService emailService;

    /**
     * Экспорт детального отчета по заказам (с поддержкой менеджера как в оригинале).
     */
    @GetMapping("/orders-detailed")
    public ResponseEntity<?> exportOrdersDetailed(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) String managerId) {
        try {
            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

            // 1. Получаем заказы нужного менеджера за выбранный период
            List<Order> orders;
            if (managerId != null && !managerId.trim().isEmpty() && !managerId.equals("null") && !managerId.equals("undefined") && !managerId.equals("Все менеджеры")) {
                orders = orderRepository.findOrdersByManagerAndDateRange(managerId, from, to);
            } else {
                orders = orderRepository.findOrdersBetweenDates(from, to);
            }

            if (orders.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Заказы за период " + start + " - " + end + " не найдены."));
            }

            // 2. Считаем сводную статистику по каждому товару (ID товара -> [Количество штук, Сумма в деньгах])
            // Подтягиваем названия товаров из базы
            Map<Long, Integer> productQuantities = new HashMap<>();
            Map<Long, BigDecimal> productAmounts = new HashMap<>();

            for (Order order : orders) {
                if (order.getItems() != null) {
                    order.getItems().forEach((productId, qty) -> {
                        productQuantities.put(productId, productQuantities.getOrDefault(productId, 0) + qty);
                    });
                }
            }

            // Получаем актуальные данные товаров (названия, цены)
            List<com.sellion.sellionserver.entity.Product> products = productRepository.findAllById(productQuantities.keySet());
            Map<Long, com.sellion.sellionserver.entity.Product> productMap = products.stream()
                    .collect(Collectors.toMap(com.sellion.sellionserver.entity.Product::getId, java.util.function.Function.identity()));

            // 3. Создаем НОВЫЙ Excel-файл (Apache POI) с нуля именно под эту задачу
            try (org.apache.poi.ss.usermodel.Workbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
                org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Свод по товарам");

                // Заголовок
                int rowIdx = 0;
                org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(rowIdx++);
                headerRow.createCell(0).setCellValue("Наименование товара");
                headerRow.createCell(1).setCellValue("Категория");
                headerRow.createCell(2).setCellValue("Количество (шт)");
                headerRow.createCell(3).setCellValue("Общая сумма (֏)");

                // Заполняем строками
                for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
                    Long productId = entry.getKey();
                    Integer totalQty = entry.getValue();
                    com.sellion.sellionserver.entity.Product p = productMap.get(productId);

                    String productName = (p != null && p.getName() != null) ? p.getName() : "Товар ID: " + productId;
                    String category = (p != null && p.getCategory() != null) ? p.getCategory() : "Без категории";

                    // Примерный расчет суммы по средней цене или базовой цене товара
                    BigDecimal price = (p != null && p.getPrice() != null) ? p.getPrice() : BigDecimal.ZERO;
                    BigDecimal totalSum = price.multiply(BigDecimal.valueOf(totalQty));

                    org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIdx++);
                    row.createCell(0).setCellValue(productName);
                    row.createCell(1).setCellValue(category);
                    row.createCell(2).setCellValue(totalQty);
                    row.createCell(3).setCellValue(totalSum.doubleValue());
                }

                // Автоподгон ширины колонок
                for (int i = 0; i < 4; i++) {
                    sheet.autoSizeColumn(i);
                }

                String fileName = "Manager_Summary_" + (managerId != null && !managerId.isEmpty() ? managerId + "_" : "All_") + start + ".xlsx";
                return getResponseEntity(workbook, fileName);
            }

        } catch (Exception e) {
            log.error("Ошибка генерации сводного отчета: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
    /**
     * Массовая отправка данных бухгалтеру.
     */
    @PostMapping("/send-to-accountant")
    public ResponseEntity<?> sendToAccountant(@RequestBody ReportRequest request) {
        try {
            String start = request.getStart();
            String end = request.getEnd();
            String email = request.getEmail();

            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);
            List<String> reportTypes = (request.getTypes() != null) ? request.getTypes() : Collections.emptyList();

            List<Order> orders = reportTypes.contains("orders") ?
                    orderRepository.findOrdersBetweenDates(from, to) : Collections.emptyList();

            List<ReturnOrder> returns = reportTypes.contains("returns") ?
                    returnOrderRepository.findReturnsBetweenDates(from, to) : Collections.emptyList();

            if (orders.isEmpty() && returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Нет данных для отправки за указанный период."));
            }

            try (Workbook workbook = invoiceExcelService.generateExcel(orders, returns, "Sellion ERP: Финансовый отчет")) {
                byte[] bytes = workbookToBytes(workbook);

                emailService.sendReportWithAttachment(
                        email.trim(),
                        "Sellion ERP 2026: Отчет за " + start + " / " + end,
                        "Добрый день. Во вложении финансовый отчет системы Sellion.",
                        bytes,
                        "Financial_Report_" + start + ".xlsx"
                );
            }

            log.info("Отчет успешно отправлен на {}", email);
            return ResponseEntity.ok(Map.of("message", "Отчет успешно отправлен на " + email));

        } catch (Exception e) {
            log.error("Критическая ошибка отправки отчета: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка сервера при отправке: " + e.getMessage()));
        }
    }

    /**
     * Отправка выбранных корректировок (для изменения фактур).
     */
    @PostMapping("/send-selected-corrections")
    @Transactional(readOnly = true)
    public ResponseEntity<?> sendSelectedCorrections(@RequestBody Map<String, Object> payload) {
        try {
            List<Long> ids = ((List<?>) payload.get("ids")).stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .toList();
            String email = (String) payload.get("email");

            if (ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Список ID пуст"));

            List<ReturnOrder> selectedReturns = returnOrderRepository.findAllById(ids);

            try (Workbook workbook = invoiceExcelService.generateExcel(null, selectedReturns, "РЕЕСТР КОРРЕКТИРОВОК")) {
                byte[] bytes = workbookToBytes(workbook);

                emailService.sendReportWithAttachment(
                        email.trim(),
                        "Sellion ERP: КОРРЕКТИРОВКИ ФАКТУР (" + LocalDate.now() + ")",
                        "Список корректировок для внесения изменений в первичные документы.",
                        bytes,
                        "Corrections_" + LocalDate.now() + ".xlsx"
                );
            }

            return ResponseEntity.ok(Map.of("success", true, "count", ids.size()));
        } catch (Exception e) {
            log.error("Ошибка отправки корректировок: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    private byte[] workbookToBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            if (workbook instanceof SXSSFWorkbook sx) {
                sx.dispose();
            }
            return bos.toByteArray();
        }
    }

    private ResponseEntity<byte[]> getResponseEntity(Workbook workbook, String fileName) throws IOException {
        byte[] bytes = workbookToBytes(workbook);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        headers.setContentLength(bytes.length);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }

    /**
     * Экспорт детального отчета по возвратам.
     */
    @GetMapping("/returns-detailed")
    public ResponseEntity<?> exportReturnsDetailed(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

            List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(from, to);

            if (returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Возвраты за период " + start + " - " + end + " не найдены."));
            }

            try (Workbook workbook = invoiceExcelService.generateExcel(null, returns, "Отчет по возвратам")) {
                return getResponseEntity(workbook, "Returns_Report_" + start + "_" + end + ".xlsx");
            }
        } catch (Exception e) {
            log.error("Ошибка генерации отчета по возвратам: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Возвращает список всех доступных менеджеров из энума ManagerId
     */
    @GetMapping("/managers-list")
    public ResponseEntity<List<String>> getManagersList() {
        return ResponseEntity.ok(com.sellion.sellionserver.entity.ManagerId.getAllDisplayNames());
    }

    @Data
    public static class ReportRequest {
        private String start;
        private String end;
        private String email;
        private List<String> types;
    }
}