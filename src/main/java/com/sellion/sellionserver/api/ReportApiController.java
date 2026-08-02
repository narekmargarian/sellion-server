package com.sellion.sellionserver.api;

import com.sellion.sellionserver.dto.ProductReportDto;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.EmailService;
import com.sellion.sellionserver.services.InvoiceExcelService;
import com.sellion.sellionserver.services.OrderService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/reports/excel")
@RequiredArgsConstructor
@Slf4j
public class ReportApiController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final InvoiceExcelService invoiceExcelService;
    private final EmailService emailService;
    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderService orderService;

    /**
     * Экспорт детального отчета по заказам (с поддержкой опционального менеджера).
     * ИДЕАЛЬНО: Используем try-with-resources для мгновенного освобождения памяти.
     */
    @GetMapping("/orders-detailed")
    public ResponseEntity<?> exportOrdersDetailed(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam(required = false) String managerId) {
        try {
            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

            List<Order> orders;

            // Если указан менеджер и он не пустой — фильтруем по нему
            if (managerId != null && !managerId.isBlank()) {
                orders = orderRepository.findInvoicedOrdersBetweenDatesAndManager(from, to, managerId);
            } else {
                orders = orderRepository.findInvoicedOrdersBetweenDates(from, to);
            }

            if (orders.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Заказы за период " + start + " - " + end + " не найдены."));
            }

            try (Workbook workbook = invoiceExcelService.generateExcel(orders, null, "Отчет по продажам")) {
                return getResponseEntity(workbook, "Orders_Report_" + start + ".xlsx");
            }
        } catch (Exception e) {
            log.error("Ошибка генерации отчета по заказам: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Массовая отправка данных бухгалтеру.
     * ИДЕАЛЬНО: Поддержка нескольких типов данных в одном письме с защитой от пустых отчетов.
     */
    @PostMapping("/send-to-accountant")
    public ResponseEntity<?> sendToAccountant(@RequestBody ReportRequest request) { // Использование DTO и @RequestBody
        try {
            // Извлекаем данные из объекта request
            String start = request.getStart();
            String end = request.getEnd();
            String email = request.getEmail();

            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);
            List<String> reportTypes = (request.getTypes() != null) ? request.getTypes() : Collections.emptyList();



            // Логика выборки данных
            List<Order> orders = reportTypes.contains("orders") ?
                    orderRepository.findOrdersBetweenDates(from, to) : Collections.emptyList();


            List<ReturnOrder> returns = reportTypes.contains("returns") ?
                    returnOrderRepository.findReturnsBetweenDates(from, to) : Collections.emptyList();

            if (orders.isEmpty() && returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Нет данных для отправки за указанный период."));
            }

            // Генерация и отправка
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

    // --- ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (ИДЕАЛЬНАЯ РАБОТА С ПАМЯТЬЮ) ---

    private byte[] workbookToBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            // Если используем SXSSF, обязательно вызываем dispose для очистки временных файлов
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
     * ДОБАВЛЕНО: Эндпоинт, который вызывается при нажатии кнопки "Экспорт Возвратов".
     */
    @GetMapping("/returns-detailed")
    public ResponseEntity<?> exportReturnsDetailed(@RequestParam String start, @RequestParam String end) {
        try {
            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

            // Получаем список возвратов за период
            List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(from, to);

            if (returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Возвраты за период " + start + " - " + end + " не найдены."));
            }

            // Генерируем Excel. Передаем null в заказы и наш список в возвраты.
            try (Workbook workbook = invoiceExcelService.generateExcel(null, returns, "Отчет по возвратам")) {
                return getResponseEntity(workbook, "Returns_Report_" + start + "_" + end + ".xlsx");
            }
        } catch (Exception e) {
            log.error("Ошибка генерации отчета по возвратам: ", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Список менеджеров для выпадающего списка в модалке отчетов.
     */
    @GetMapping("/managers-list")
    public ResponseEntity<?> getManagersList() {
        try {
            // Берем список уникальных менеджеров из заказов или репозитория
            List<String> managers = orderRepository.findDistinctManagers(); // Убедись, что такой метод есть в OrderRepository, либо передавай список из базы
            return ResponseEntity.ok(managers);
        } catch (Exception e) {
            log.error("Ошибка получения списка менеджеров: ", e);
            // Возвращаем пустой список или дефолтные значения, если метода нет
            return ResponseEntity.ok(Collections.emptyList());
        }
    }


    @GetMapping("/api/reports/excel/orders-detailed")
    public void downloadDetailedReport(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(value = "managerId", required = false) String managerId,
            HttpServletResponse response) throws IOException {

        // 1. Получаем заказы через сервис
        List<Order> orders = orderService.getOrdersForPeriod(start, end, managerId);

        if (orders.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Заказы за выбранный период не найдены");
            return;
        }

        // 2. Группируем и суммируем товары
        Map<Long, ProductReportDto> reportMap = new HashMap<>();

        for (Order order : orders) {
            if (order.getItems() == null) continue;

            BigDecimal discountMultiplier = BigDecimal.ONE;
            if (order.getDiscountPercent() != null && order.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                discountMultiplier = BigDecimal.ONE.subtract(order.getDiscountPercent().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
            }

            for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
                Long productId = entry.getKey();
                Integer quantity = entry.getValue();

                Product product = productRepository.findById(productId).orElse(null);
                String productName = (product != null) ? product.getName() : "Товар #" + productId;

                // ИСПРАВЛЕНО: раз category это String, вызываем напрямую без .getName()
                String category = (product != null && product.getCategory() != null) ? product.getCategory() : "Без категории";

                BigDecimal basePrice = (product != null && product.getPrice() != null) ? product.getPrice() : BigDecimal.ZERO;
                BigDecimal itemTotal = basePrice.multiply(new BigDecimal(quantity)).multiply(discountMultiplier);

                reportMap.putIfAbsent(productId, new ProductReportDto(productName, category, 0, BigDecimal.ZERO));
                ProductReportDto dto = reportMap.get(productId);
                dto.addQuantity(quantity);
                dto.addAmount(itemTotal);
            }
        }

        // 3. Генерация Excel через Apache POI
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Детализация товаров");

        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Категория");
        headerRow.createCell(1).setCellValue("Наименование товара");
        headerRow.createCell(2).setCellValue("Кол-во (шт)");
        headerRow.createCell(3).setCellValue("Сумма (֏)");

        int rowNum = 1;
        for (ProductReportDto dto : reportMap.values()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(dto.getCategory());
            row.createCell(1).setCellValue(dto.getProductName());
            row.createCell(2).setCellValue(dto.getQuantity());
            row.createCell(3).setCellValue(dto.getAmount().doubleValue());
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=detailed_report_" + start + "_to_" + end + ".xlsx");

        try (OutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            workbook.close();
        }
    }

    @Data
    public static class ReportRequest {
        private String start;
        private String end;
        private String email;
        private List<String> types;
    }

}