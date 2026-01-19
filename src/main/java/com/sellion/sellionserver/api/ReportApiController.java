package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.ReturnOrder;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ReturnOrderRepository;
import com.sellion.sellionserver.services.CompanySettings;
import com.sellion.sellionserver.services.EmailService;
import com.sellion.sellionserver.services.InvoiceExcelService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/reports/excel")
@RequiredArgsConstructor
//@PreAuthorize("hasAnyRole('ADMIN', 'ACCOUNTANT')")
public class ReportApiController {


    private final OrderRepository orderRepository;
    private final ReturnOrderRepository returnOrderRepository;
    private final InvoiceExcelService invoiceExcelService;
    private final EmailService emailService;
    private final CompanySettings companySettings;
    private static final Logger log = LoggerFactory.getLogger(ReportApiController.class);


    // Метод для детального отчета по заказам (возвращает файл или ошибку для toast)
    @GetMapping("/orders-detailed")
    public ResponseEntity<?> exportOrdersDetailed(@RequestParam String start, @RequestParam String end) throws IOException {
        List<Order> orders = orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59");

        if (orders.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Заказы не найдены за указанный период."));
        }

        Workbook workbook = invoiceExcelService.generateExcel(orders, null, "Մանրամասն հաշվետվություն");
        return getResponseEntity(workbook, "Detailed_Report_Orders_" + start + ".xlsx");
    }

    // Метод для детального отчета по возвратам (возвращает файл или ошибку для toast)
    @GetMapping("/returns-detailed")
    public ResponseEntity<?> exportReturnsDetailed(@RequestParam String start, @RequestParam String end) throws IOException {
        List<ReturnOrder> returns = returnOrderRepository.findReturnsBetweenDates(start + "T00:00:00", end + "T23:59:59");

        if (returns.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Возвраты не найдены за указанный период."));
        }

        Workbook workbook = invoiceExcelService.generateExcel(null, returns, "Մանրամասն վերադարձի հաշվետվություն");
        return getResponseEntity(workbook, "Detailed_Report_Returns_" + start + ".xlsx");
    }

    // <<< МЕТОД ДЛЯ ОТПРАВКИ НА EMAIL (Теперь с выбором типа отчета) >>>
    @PostMapping("/send-to-accountant")
    public ResponseEntity<?> sendToAccountant(
            @RequestParam String start,
            @RequestParam String end,
            @RequestParam String email,
            @RequestParam(required = false) List<String> types) { // Принимаем список типов: "orders", "returns"
        try {
            // Убеждаемся, что список типов не null, если не передан, делаем пустым
            List<String> reportTypes = (types != null) ? types : Collections.emptyList();

            List<Order> orders = Collections.emptyList();
            List<ReturnOrder> returns = Collections.emptyList();

            // Загружаем только те данные, которые выбрал пользователь
            if (reportTypes.contains("orders")) {
                orders = orderRepository.findOrdersBetweenDates(start + "T00:00:00", end + "T23:59:59");
            }
            if (reportTypes.contains("returns")) {
                returns = returnOrderRepository.findReturnsBetweenDates(start + "T00:00:00", end + "T23:59:59");
            }

            if (orders.isEmpty() && returns.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Нет данных для отправки за указанный период."));
            }

            Workbook workbook = invoiceExcelService.generateExcel(orders, returns, "Отчет для бухгалтерии");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            workbook.write(bos);
            byte[] bytes = bos.toByteArray();

            if (workbook instanceof SXSSFWorkbook sx) {
                sx.dispose();
            }
            workbook.close();

            emailService.sendReportWithAttachment(
                    email,
                    "Sellion ERP: Հաշվետվություն " + start + " / " + end,
                    "Добрый день. Во вложении финансовый отчет (армянская версия).",
                    bytes,
                    "Financial_Report_" + start + ".xlsx"
            );

            return ResponseEntity.ok(Map.of("message", "Հաշվետվությունն ուղարկված է " + email));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Ошибка: " + e.getMessage()));
        }
    }
    // >>> КОНЕЦ ИЗМЕНЕННОГО МЕТОДА EMAIL

    // Вспомогательный метод для формирования HTTP-ответа с файлом
    private ResponseEntity<byte[]> getResponseEntity(Workbook workbook, String fileName) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        workbook.write(bos);
        byte[] bytes = bos.toByteArray();

        if (workbook instanceof SXSSFWorkbook sx) {
            sx.dispose();
        }
        workbook.close();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", fileName);
        headers.setContentLength(bytes.length);

        return new ResponseEntity<>(bytes, headers, HttpStatus.OK);
    }
}