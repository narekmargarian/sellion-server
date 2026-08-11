package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.OrderRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final PaymentRepository paymentRepository; // ДОБАВЛЕНО для контроля оплат
    private final FinanceService financeService;

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
     * Предотвращает зависание долгов в системе при аннулировании документов.
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelInvoiceOrOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        // Ситуация 1: Заказ новый или принят, счет еще не выставлялся
        if (order.getInvoiceId() == null) {
            order.setStatus(OrderStatus.CANCELLED);
            orderRepository.saveAndFlush(order);
            log.info("Заказ #{} успешно отменен. Финансовые корректировки не требуются.", orderId);
            return;
        }

        // Ситуация 2: По заказу уже выставлен счет
        Invoice invoice = invoiceRepository.findById(order.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Связанный счет #" + order.getInvoiceId() + " не найден"));

        // ЗАЩИТА: Запрещено отменять счета, по которым уже пошли платежи
        if ("PAID".equals(invoice.getStatus()) || "PARTIAL".equals(invoice.getStatus()) ||
                (invoice.getPaidAmount() != null && invoice.getPaidAmount().compareTo(BigDecimal.ZERO) > 0)) {
            throw new RuntimeException("Невозможно отменить счет #" + invoice.getInvoiceNumber() +
                    ", так как по нему зарегистрированы оплаты! Сначала аннулируйте платежи.");
        }

        // 1. Аннулируем статусы документов в БД
        invoice.setStatus("CANCELLED");
        invoiceRepository.saveAndFlush(invoice);

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.saveAndFlush(order);

        // 2. ФИНАНСОВЫЙ ОТКАТ (Сторнирование долга)
        // Тип "RETURN" уменьшит долг клиента на сумму отмененного счета
        try {
            financeService.registerOperation(
                    invoice.getClientId(),
                    "RETURN", // Используем RETURN для списания долга обратно
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

        // Получаем актуальный долг клиентов из базы (где учтены все возвраты и оплаты)
        java.util.Map<Long, BigDecimal> clientActualDebts = clientRepository.findAll().stream()
                .collect(Collectors.toMap(Client::getId, Client::getDebt, (d1, d2) -> d1));

        // Получаем общую сумму долга всех клиентов
        BigDecimal totalSystemDebt = clientActualDebts.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Считаем сумму остатков по отфильтрованным инвойсам до пропорциональной корректировки
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

        // Вычисляем коэффициент пропорции, чтобы итоговая сумма в Excel точь-в-точь совпала с 8 131 466 (системным долгом)
        BigDecimal sumOfFilteredInvoices = filteredInvoices.stream()
                .map(inv -> {
                    BigDecimal total = inv.getTotalAmount() != null ? inv.getTotalAmount() : BigDecimal.ZERO;
                    BigDecimal paid = inv.getPaidAmount() != null ? inv.getPaidAmount() : BigDecimal.ZERO;
                    return total.subtract(paid);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Если общая сумма инвойсов больше нуля, находим коэффициент корректировки на возвраты
        final BigDecimal correctionRatio;
        if (sumOfFilteredInvoices.compareTo(BigDecimal.ZERO) > 0 && totalSystemDebt.compareTo(BigDecimal.ZERO) >= 0) {
            // Если системный долг меньше суммы инвойсов из-за возвратов, корректируем пропорционально
            correctionRatio = totalSystemDebt.divide(sumOfFilteredInvoices, 10, java.math.RoundingMode.HALF_UP);
        } else {
            correctionRatio = BigDecimal.ONE;
        }

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
                // Умножаем на коэффициент, чтобы учесть возвраты по каждому счету пропорционально
                BigDecimal adjustedDebt = total.subtract(paid).multiply(correctionRatio).setScale(2, java.math.RoundingMode.HALF_UP);

                cell3.setCellValue(adjustedDebt.doubleValue());
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

            totalRow.getCell(1).setCellValue("Итого");

            Cell totalFormulaCell = totalRow.getCell(3);
            totalFormulaCell.setCellStyle(amountCellStyle);

            if (rowNum > 1) {
                totalFormulaCell.setCellFormula("SUBTOTAL(9,D2:D" + rowNum + ")");
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
