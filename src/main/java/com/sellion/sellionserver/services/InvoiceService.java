package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.*;
import com.sellion.sellionserver.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final PaymentRepository paymentRepository;
    private final FinanceService financeService;
    private final ReturnOrderRepository returnOrderRepository;

    /**
     * ЭТАП 2: Создание инвойса из зарезервированного заказа.
     */
    @Transactional(rollbackFor = Exception.class)
    public Invoice createInvoiceFromOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        if (order.getInvoiceId() != null) {
            log.warn("Для заказа #{} уже существует инвойс ID: {}", orderId, order.getInvoiceId());
            return null;
        }

        if (order.getStatus() != OrderStatus.RESERVED) {
            throw new RuntimeException("Ошибка: Нельзя выставить счет. Заказ должен иметь статус RESERVED. Текущий статус: " + order.getStatus());
        }

        String cleanShopName = (order.getShopName() != null) ? order.getShopName().trim() : "";
        Client client = clientRepository.findByNameIgnoreCase(cleanShopName)
                .orElseThrow(() -> new RuntimeException("Критическая ошибка: Магазин '" + cleanShopName + "' не найден в справочнике!"));

        String invoiceNumber = "INV-" + LocalDate.now().getYear() + "-" + order.getId();

        Invoice invoice = new Invoice();
        invoice.setOrder(order);
        invoice.setClientId(client.getId());
        invoice.setShopName(cleanShopName);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setTotalAmount(order.getTotalAmount());
        invoice.setStatus("UNPAID");

        Invoice savedInvoice = invoiceRepository.saveAndFlush(invoice);

        order.setInvoiceId(savedInvoice.getId());
        order.setStatus(OrderStatus.PROCESSED);
        orderRepository.saveAndFlush(order);

        try {
            financeService.registerOperation(
                    client.getId(),
                    "ORDER",
                    order.getTotalAmount(),
                    savedInvoice.getId(),
                    "Счет " + invoiceNumber,
                    order.getShopName()
            );
        } catch (Exception e) {
            log.error("Критическая ошибка при регистрации финансов для инвойса {}", invoiceNumber);
            throw new RuntimeException("Ошибка финансов: " + e.getMessage());
        }

        log.info("Инвойс {} успешно создан для магазина {}. Статус заказа изменен на PROCESSED", invoiceNumber, order.getShopName());
        return savedInvoice;
    }

    /**
     * АТОМАРНАЯ ОТМЕНА ЗАКАЗА И СЧЕТА С КОРРЕКТИРОВКОЙ БАЛАНСА.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelInvoiceOrOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        if (order.getInvoiceId() == null) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.saveAndFlush(order);
            log.info("Заказ #{} успешно отменен. Финансовые корректировки не требуются.", orderId);
            return;
        }

        Invoice invoice = invoiceRepository.findById(order.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Связанный счет #" + order.getInvoiceId() + " не найден"));

        if ("PAID".equals(invoice.getStatus()) || "PARTIAL".equals(invoice.getStatus()) ||
                (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)) {
            throw new RuntimeException("Невозможно отменить счет #" + invoice.getInvoiceNumber() +
                    ", так как по нему зарегистрированы оплаты! Сначала аннулируйте платежи.");
        }

        invoice.setStatus("CANCELLED");
        invoiceRepository.saveAndFlush(invoice);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(order);

        try {
            financeService.registerOperation(
                    invoice.getClientId(),
                    "RETURN",
                    invoice.getTotalAmount(),
                    invoice.getId(),
                    "Аннулирование счета " + invoice.getInvoiceNumber() + " (Отмена заказа)",
                    invoice.getShopName()
            );
        } catch (Exception e) {
            log.error("Критическая ошибка при финансовом откате отмены счета {}", invoice.getInvoiceNumber());
            throw new RuntimeException("Ошибка финансового отката: " + e.getMessage());
        }

        log.info("Счет {} и заказ #{} успешно аннулированы. Проведена корректировка долга на сумму {}",
                invoice.getInvoiceNumber(), orderId, invoice.getTotalAmount());
    }



    public void exportDebtsToExcel(String start, String end, OutputStream outputStream) throws IOException {
        List<Invoice> allInvoices = invoiceRepository.findAll();

        // Считаем общую сумму возвратов за выбранный период (или за все время, если даты не заданы)
        BigDecimal totalReturnsSum = BigDecimal.ZERO;
        try {
            if (start != null && end != null && !start.isEmpty() && !end.isEmpty()) {
                LocalDateTime startDateTime = LocalDate.parse(start).atStartOfDay();
                LocalDateTime endDateTime = LocalDate.parse(end).atTime(23, 59, 59);
                BigDecimal periodReturns = returnOrderRepository.sumConfirmedReturns(startDateTime, endDateTime);
                if (periodReturns != null) {
                    totalReturnsSum = periodReturns;
                }
            } else {
                // Если диапазон дат не задан, берем сумму всех подтвержденных возвратов
                List<ReturnOrder> allConfirmedReturns = returnOrderRepository.findAllByStatus(ReturnStatus.CONFIRMED);
                totalReturnsSum = allConfirmedReturns.stream()
                        .map(r -> r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        } catch (Exception e) {
            log.warn("Не удалось автоматически рассчитать сумму возвратов для Excel: {}", e.getMessage());
        }

        // Отбираем инвойсы по статусу и диапазону дат
        List<Invoice> filteredInvoices = allInvoices.stream()
                .filter(inv -> {
                    String status = inv.getStatus();
                    if (status != null && (status.equalsIgnoreCase("PAID") || status.equalsIgnoreCase("Оплачен") || status.equalsIgnoreCase("CANCELLED"))) {
                        return false;
                    }

                    BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
                    BigDecimal remaining = total.subtract(paid);

                    if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                        return false;
                    }

                    if (start == null || end == null || inv.getCreatedAt() == null) return true;
                    LocalDate invDate = inv.getCreatedAt().toLocalDate();
                    LocalDate startDate = LocalDate.parse(start);
                    LocalDate endDate = LocalDate.parse(end);
                    return !invDate.isBefore(startDate) && !invDate.isAfter(endDate);
                })
                .collect(Collectors.toList());

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Список долгов");

            CellStyle cellStyle = workbook.createCellStyle();
            cellStyle.setBorderTop(BorderStyle.THIN);
            cellStyle.setBorderBottom(BorderStyle.THIN);
            cellStyle.setBorderLeft(BorderStyle.THIN);
            cellStyle.setBorderRight(BorderStyle.THIN);

            CellStyle amountCellStyle = workbook.createCellStyle();
            amountCellStyle.cloneStyleFrom(cellStyle);
            DataFormat format = workbook.createDataFormat();
            amountCellStyle.setDataFormat(format.getFormat("0.00"));

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.cloneStyleFrom(cellStyle);
            headerStyle.setFont(boldFont);

            Row headerRow = sheet.createRow(0);
            String[] headers = {"ID Счета", "Дата", "Магазин", "Сумма", "Статус"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            for (Invoice inv : filteredInvoices) {
                Row row = sheet.createRow(rowNum++);

                Cell cell0 = row.createCell(0);
                cell0.setCellValue(inv.getId() != null ? inv.getId().toString() : "N/A");
                cell0.setCellStyle(cellStyle);

                Cell cell1 = row.createCell(1);
                cell1.setCellValue(inv.getCreatedAt() != null ? inv.getCreatedAt().toLocalDate().toString() : "");
                cell1.setCellStyle(cellStyle);

                Cell cell2 = row.createCell(2);
                cell2.setCellValue(inv.getShopName() != null ? inv.getShopName() : "");
                cell2.setCellStyle(cellStyle);

                Cell cell3 = row.createCell(3);
                BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;

                BigDecimal exactRemaining = total.subtract(paid).setScale(2, RoundingMode.HALF_UP);

                cell3.setCellValue(exactRemaining.doubleValue());
                cell3.setCellStyle(amountCellStyle);

                Cell cell4 = row.createCell(4);
                String rawStatus = inv.getStatus() != null ? inv.getStatus() : "";
                String russianStatus = rawStatus;

                if (rawStatus.equalsIgnoreCase("UNPAID")) {
                    russianStatus = "Не оплачен";
                } else if (rawStatus.equalsIgnoreCase("PARTIAL")) {
                    russianStatus = "Частично";
                }

                cell4.setCellValue(russianStatus);
                cell4.setCellStyle(cellStyle);
            }

            Row totalRow = sheet.createRow(rowNum);

            for (int i = 0; i < 5; i++) {
                totalRow.createCell(i).setCellStyle(headerStyle);
            }

            totalRow.getCell(1).setCellValue("Итого (с учетом возвратов)");

            Cell totalFormulaCell = totalRow.getCell(3);
            totalFormulaCell.setCellStyle(amountCellStyle);

            if (rowNum > 1) {
                // Динамически вычитаем реальную сумму возвратов из формулы SUBTOTAL
                totalFormulaCell.setCellFormula("SUBTOTAL(9,D2:D" + rowNum + ") - " + totalReturnsSum.toPlainString());
            } else {
                totalFormulaCell.setCellValue(0.0);
            }

            if (rowNum > 1) {
                sheet.setAutoFilter(new CellRangeAddress(0, rowNum - 1, 0, 4));
            }

            for (int i = 0; i < 5; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
        }
    }
}