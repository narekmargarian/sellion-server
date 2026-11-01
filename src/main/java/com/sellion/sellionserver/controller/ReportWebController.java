package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.dto.DetailedReportRequest;
import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.entity.Product;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.ProductRepository;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportWebController {

    private final ClientRepository clientRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @GetMapping("/debts")
    public String debtReport(Model model) {
        List<Client> allActiveBalances = clientRepository.findAll().stream()
                .filter(c -> c.getDebt() != null && c.getDebt().compareTo(BigDecimal.ZERO) != 0)
                .collect(Collectors.toList());

        List<Client> clientsWithDebt = allActiveBalances.stream()
                .filter(c -> c.getDebt().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        List<Client> clientsWithAdvance = allActiveBalances.stream()
                .filter(c -> c.getDebt().compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.toList());

        BigDecimal totalDebtSum = allActiveBalances.stream()
                .map(Client::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pureDebtSum = clientsWithDebt.stream()
                .map(Client::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal pureAdvanceSum = clientsWithAdvance.stream()
                .map(Client::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add).abs();

        model.addAttribute("clients", clientsWithDebt);
        model.addAttribute("clientsWithAdvance", clientsWithAdvance);
        model.addAttribute("totalDebtSum", totalDebtSum);
        model.addAttribute("pureDebtSum", pureDebtSum);
        model.addAttribute("pureAdvanceSum", pureAdvanceSum);

        return "debt-report";
    }

    // Вспомогательный метод парсинга дат
    private LocalDateTime parseSafeDateTime(String d, LocalDateTime def, boolean isEnd) {
        if (d == null || d.trim().isEmpty()) return def;
        try {
            LocalDateTime dt;
            if (d.contains("-") && !d.contains(".")) {
                if (d.contains("T") || d.contains(" ")) {
                    dt = LocalDateTime.parse(d.replace(" ", "T"));
                } else {
                    dt = LocalDate.parse(d).atStartOfDay();
                }
            } else if (d.contains(".")) {
                if (d.contains(":")) {
                    dt = LocalDateTime.parse(d, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm[:ss]"));
                } else {
                    dt = LocalDate.parse(d, java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")).atStartOfDay();
                }
            } else {
                dt = LocalDateTime.parse(d.replace(" ", "T"));
            }

            if (isEnd && dt.toLocalTime().equals(LocalTime.MIN)) {
                return dt.with(LocalTime.MAX);
            }
            return dt;
        } catch (Exception e) {
            return def;
        }
    }

    @PostMapping("/excel/orders-detailed-custom")
    public void exportDetailedOrdersExcel(@RequestBody DetailedReportRequest request, HttpServletResponse response) {
        try {
            List<Order> orders = new java.util.ArrayList<>();

            if (request.getIds() != null && !request.getIds().isEmpty()) {
                orders = orderRepository.findAllById(request.getIds());
            } else {
                LocalDateTime start = parseSafeDateTime(request.getStart(), LocalDate.now().atStartOfDay(), false);
                LocalDateTime end = parseSafeDateTime(request.getEnd(), LocalDate.now().atTime(LocalTime.MAX), true);

                if (request.getManagers() != null && !request.getManagers().isEmpty()) {
                    Set<String> cleanManagerIds = request.getManagers().stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toSet());

                    for (String mgrId : cleanManagerIds) {
                        List<Order> mgrOrders = orderRepository.findOrdersByManagerAndDateRange(mgrId, start, end);
                        if (mgrOrders != null) {
                            orders.addAll(mgrOrders);
                        }
                    }
                } else {
                    List<Order> generalOrders = orderRepository.findOrdersBetweenDates(start, end);
                    if (generalOrders != null) {
                        orders.addAll(generalOrders);
                    }
                }
            }

            Map<Long, Order> uniqueOrdersMap = new java.util.LinkedHashMap<>();
            if (orders != null) {
                for (Order o : orders) {
                    if (o != null && o.getId() != null && o.getStatus() != OrderStatus.CANCELLED) {
                        uniqueOrdersMap.putIfAbsent(o.getId(), o);
                    }
                }
            }

            List<Order> filteredOrders = new java.util.ArrayList<>(uniqueOrdersMap.values());

            Map<Long, String> productNamesMap = new HashMap<>();
            try {
                List<Product> allProds = productRepository.findAll();
                if (allProds != null) {
                    productNamesMap = allProds.stream()
                            .filter(p -> p != null && p.getId() != null)
                            .collect(Collectors.toMap(Product::getId, p -> p.getName() != null ? p.getName() : "Без названия", (e, r) -> e));
                }
            } catch (Exception e) {
                log.warn("Не удалось подгрузить справочник товаров: {}", e.getMessage());
            }

            Map<String, Map<String, Map<Long, Integer>>> masterMap = new java.util.LinkedHashMap<>();

            for (Order order : filteredOrders) {
                if (order == null || order.getItems() == null || order.getItems().isEmpty()) continue;

                String mgr = (order.getManagerId() != null && !order.getManagerId().isBlank())
                        ? order.getManagerId().trim().toUpperCase() : "---";
                String shop = (order.getShopName() != null && !order.getShopName().isBlank())
                        ? order.getShopName().trim() : "---";

                for (Map.Entry<Long, Integer> entry : order.getItems().entrySet()) {
                    if (entry == null || entry.getKey() == null) continue;
                    Long pId = entry.getKey();
                    Integer qty = entry.getValue();

                    if (qty == null || qty <= 0) continue;

                    if (request.getProducts() != null && !request.getProducts().isEmpty() && !request.getProducts().contains(pId)) {
                        continue;
                    }

                    masterMap.computeIfAbsent(mgr, k -> new java.util.LinkedHashMap<>())
                            .computeIfAbsent(shop, k -> new java.util.LinkedHashMap<>())
                            .merge(pId, qty, Integer::sum);
                }
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=Detailed_Orders_Report.xlsx");

            try (Workbook workbook = new XSSFWorkbook(); OutputStream out = response.getOutputStream()) {
                Sheet sheet = workbook.createSheet("Отчет");

                // Шрифты Times New Roman
                Font baseFont = workbook.createFont();
                baseFont.setFontName("Times New Roman");
                baseFont.setFontHeightInPoints((short) 10);

                Font boldFont = workbook.createFont();
                boldFont.setFontName("Times New Roman");
                boldFont.setFontHeightInPoints((short) 10);
                boldFont.setBold(true);

                Font titleFont = workbook.createFont();
                titleFont.setFontName("Times New Roman");
                titleFont.setFontHeightInPoints((short) 11);
                titleFont.setItalic(true);
                titleFont.setBold(true);

                // Стили заголовка периода
                CellStyle titleStyle = workbook.createCellStyle();
                titleStyle.setFont(titleFont);
                titleStyle.setAlignment(HorizontalAlignment.CENTER);
                titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

                // Цвет для границ (синеватый тон 1С)
                short borderColor = IndexedColors.PALE_BLUE.getIndex();

                // Точный цвет заливки шапки из вашего RGB (189, 215, 238) для XSSFWorkbook
                XSSFCellStyle headerStyle = (XSSFCellStyle) workbook.createCellStyle();
                headerStyle.setFont(boldFont);
                headerStyle.setFillForegroundColor(new org.apache.poi.xssf.usermodel.XSSFColor(new java.awt.Color(189, 215, 238), null));
                headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                headerStyle.setAlignment(HorizontalAlignment.CENTER);
                headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                headerStyle.setBorderBottom(BorderStyle.THIN);
                headerStyle.setBottomBorderColor(borderColor);
                headerStyle.setBorderTop(BorderStyle.THIN);
                headerStyle.setTopBorderColor(borderColor);
                headerStyle.setBorderLeft(BorderStyle.THIN);
                headerStyle.setLeftBorderColor(borderColor);
                headerStyle.setBorderRight(BorderStyle.THIN);
                headerStyle.setRightBorderColor(borderColor);

                // Основной стиль ячеек с синеватыми тонкими границами
                CellStyle cellStyle = workbook.createCellStyle();
                cellStyle.setFont(baseFont);
                cellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
                cellStyle.setBorderBottom(BorderStyle.THIN);
                cellStyle.setBottomBorderColor(borderColor);
                cellStyle.setBorderTop(BorderStyle.THIN);
                cellStyle.setTopBorderColor(borderColor);
                cellStyle.setBorderLeft(BorderStyle.THIN);
                cellStyle.setLeftBorderColor(borderColor);
                cellStyle.setBorderRight(BorderStyle.THIN);
                cellStyle.setRightBorderColor(borderColor);

                CellStyle centerStyle = workbook.createCellStyle();
                centerStyle.cloneStyleFrom(cellStyle);
                centerStyle.setAlignment(HorizontalAlignment.CENTER);

                CellStyle numberStyle = workbook.createCellStyle();
                numberStyle.cloneStyleFrom(cellStyle);
                numberStyle.setAlignment(HorizontalAlignment.RIGHT);

                // 1. Заголовок периода по центру на строке 0
                Row titleRow = sheet.createRow(0);
                Cell titleCell = titleRow.createCell(2);
                String periodText = (request.getStart() != null && request.getEnd() != null)
                        ? "Период: " + request.getStart() + " — " + request.getEnd()
                        : "Период отчета";
                titleCell.setCellValue(periodText);
                titleCell.setCellStyle(titleStyle);

                // 2. Шапка таблицы сразу на строке 1
                Row headerRow = sheet.createRow(1);
                String[] headers = {"Код менеджера", "Клиент", "Намеклатура", "Количество(шт.)"};
                for (int i = 0; i < headers.length; i++) {
                    Cell cell = headerRow.createCell(i);
                    cell.setCellValue(headers[i]);
                    cell.setCellStyle(headerStyle);
                }

                int rowIdx = 2; // Данные начинаются со строки 2
                int grandTotalQty = 0;

                for (Map.Entry<String, Map<String, Map<Long, Integer>>> mgrEntry : masterMap.entrySet()) {
                    String mgr = mgrEntry.getKey();

                    for (Map.Entry<String, Map<Long, Integer>> shopEntry : mgrEntry.getValue().entrySet()) {
                        String shop = shopEntry.getKey();

                        for (Map.Entry<Long, Integer> productEntry : shopEntry.getValue().entrySet()) {
                            Long pId = productEntry.getKey();
                            int totalQty = productEntry.getValue();
                            String productName = productNamesMap.getOrDefault(pId, "Товар ID " + pId);

                            grandTotalQty += totalQty;

                            Row row = sheet.createRow(rowIdx++);

                            Cell c0 = row.createCell(0);
                            c0.setCellValue(mgr);
                            c0.setCellStyle(centerStyle);

                            Cell c1 = row.createCell(1);
                            c1.setCellValue(shop);
                            c1.setCellStyle(cellStyle);

                            Cell c2 = row.createCell(2);
                            c2.setCellValue(productName);
                            c2.setCellStyle(cellStyle);

                            Cell c3 = row.createCell(3);
                            c3.setCellValue(totalQty);
                            c3.setCellStyle(numberStyle);
                        }
                    }
                }

                // Строка ИТОГО
                Row footerRow = sheet.createRow(rowIdx);
                footerRow.createCell(0).setCellStyle(cellStyle);

                Cell labelCell = footerRow.createCell(1);
                labelCell.setCellValue("ИТОГО:");
                labelCell.setCellStyle(headerStyle);

                footerRow.createCell(2).setCellStyle(cellStyle);

                Cell totalCell = footerRow.createCell(3);
                totalCell.setCellValue(grandTotalQty);

                CellStyle totalStyle = workbook.createCellStyle();
                totalStyle.cloneStyleFrom(numberStyle);
                totalStyle.setFont(boldFont);
                totalCell.setCellStyle(totalStyle);

                // Автоподбор ширины
                for (int i = 0; i < headers.length; i++) {
                    sheet.autoSizeColumn(i);
                    sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1200);
                }

                workbook.write(out);
                out.flush();
            }
        } catch (Exception e) {
            log.error("Критическая ошибка при генерации детального отчета Excel", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}