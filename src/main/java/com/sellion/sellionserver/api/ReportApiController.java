package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.EmailService;
import com.sellion.sellionserver.services.InvoiceExcelService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/reports/excel")
@RequiredArgsConstructor
public class ReportApiController {

    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final InvoiceExcelService invoiceExcelService;
    private final EmailService emailService;

    @GetMapping("/orders-detailed")
    public ResponseEntity<?> exportOrdersDetailed(@RequestParam String start, @RequestParam String end) throws IOException {
        // ИСПРАВЛЕНО: Преобразование String в LocalDateTime для безопасности
        LocalDateTime from = LocalDate.parse(start).atStartOfDay();
        LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

        List<Order> orders = orderRepository.findInvoicedOrdersBetweenDates(from, to);

        if (orders.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Заказы не найдены за указанный период."));
        }

        // ИСПРАВЛЕНО: try-with-resources гарантирует закрытие
        try (Workbook workbook = invoiceExcelService.generateExcel(orders, null, "Մանրամասն հաշվետվություն")) {
            return getResponseEntity(workbook, "Detailed_Report_Orders_" + start + ".xlsx");
        }
    }

    @GetMapping("/returns-detailed")
    public ResponseEntity<?> exportReturnsDetailed(@RequestParam String start, @RequestParam String end) throws IOException {
        LocalDateTime from = LocalDate.parse(start).atStartOfDay();
        LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

        List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(from, to);
        if (returns.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Возвраты не найдены за указанный период."));
        }

        try (Workbook workbook = invoiceExcelService.generateExcel(null, returns, "Մանրամասն վերադարձի հաշվետվություն")) {
            return getResponseEntity(workbook, "Detailed_Report_Returns_" + start + ".xlsx");
        }
    }

    @PostMapping("/send-to-accountant")
    public ResponseEntity<?> sendToAccountant(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam String email,
            @RequestParam(required = false) List<String> types) {
        try {
            LocalDateTime from = LocalDate.parse(start).atStartOfDay();
            LocalDateTime to = LocalDate.parse(end).atTime(LocalTime.MAX);

            List<String> reportTypes = (types != null) ? types : Collections.emptyList();

            List<Order> orders = reportTypes.contains("orders") ?
                    orderRepository.findOrdersBetweenDates(from, to) : Collections.emptyList();

            List<ReturnOrder> returns = reportTypes.contains("returns") ?
                    returnOrderRepository.findReturnsBetweenDates(from, to) : Collections.emptyList();

            if (orders.isEmpty() && returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Нет данных для отправки за указанный период."));
            }

            try (Workbook workbook = invoiceExcelService.generateExcel(orders, returns, "Отчет для бухгалтерии")) {
                byte[] bytes = workbookToBytes(workbook);

                emailService.sendReportWithAttachment(
                        email,
                        "Sellion ERP 2026: Հաշվետվություն " + start + " / " + end,
                        "Добрый день. Во вложении финансовый отчет системы Sellion.",
                        bytes,
                        "Financial_Report_" + start + ".xlsx"
                );
            }

            return ResponseEntity.ok(Map.of("message", "Հաշվետվությունն ուղարկված է " + email));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Ошибка сервера: " + e.getMessage()));
        }
    }

    @PostMapping("/send-selected-corrections")
    @Transactional(readOnly = true)
    public ResponseEntity<?> sendSelectedCorrections(@RequestBody Map<String, Object> payload) {
        try {
            List<Long> ids = ((List<?>) payload.get("ids")).stream()
                    .map(id -> Long.valueOf(id.toString()))
                    .toList();
            String email = (String) payload.get("email");

            if (ids.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error", "Ничего не выбрано"));

            List<ReturnOrder> selected = returnOrderRepository.findAllById(ids);

            try (Workbook workbook = invoiceExcelService.generateExcel(null, selected, "РЕЕСТР КОРРЕКТИРОВОК ФАКТУР")) {
                byte[] bytes = workbookToBytes(workbook);

                emailService.sendReportWithAttachment(
                        email,
                        "Sellion ERP: КОРРЕКТИРОВКИ ФАКТУР (" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) + ")",
                        "Добрый день. Прикрепляем список корректировок для изменения документов.",
                        bytes,
                        "Selected_Corrections_" + LocalDate.now() + ".xlsx"
                );
            }

            return ResponseEntity.ok(Map.of("success", true, "count", ids.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка: " + e.getMessage()));
        }
    }

    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ СТАБИЛЬНОСТИ

    private byte[] workbookToBytes(Workbook workbook) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            workbook.write(bos);
            byte[] bytes = bos.toByteArray();
            if (workbook instanceof SXSSFWorkbook sx) {
                sx.dispose(); // Освобождаем временные файлы на диске
            }
            return bytes;
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
}