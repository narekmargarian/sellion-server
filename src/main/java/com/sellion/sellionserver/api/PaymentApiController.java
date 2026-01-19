package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Payment;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
import com.sellion.sellionserver.services.FinanceService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final FinanceService financeService;

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<?> registerPayment(@RequestBody Payment payment) {
        // 1. Находим счет
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Счет не найден: " + payment.getInvoiceId()));

        // 2. Находим клиента
        Client clientObj = clientRepository.findByName(invoice.getShopName())
                .orElseThrow(() -> new RuntimeException("Клиент не найден: " + invoice.getShopName()));

        // 3. Подготовка суммы платежа (конвертируем из Double платежа в BigDecimal для расчетов)
        BigDecimal paymentAmount = BigDecimal.valueOf(payment.getAmount());

        // 4. Сохраняем информацию о платеже
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // 5. Обновляем статус оплаты в инвойсе через BigDecimal
        BigDecimal currentPaid = (invoice.getPaidAmount() != null) ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaidAmount = currentPaid.add(paymentAmount);
        invoice.setPaidAmount(newPaidAmount);

        // Сравнение BigDecimal через compareTo:
        // a.compareTo(b) >= 0 означает a >= b
        // a.subtract(b).abs().doubleValue() < 0.01 — аналог Math.abs для точности

        BigDecimal total = invoice.getTotalAmount();

        if (newPaidAmount.compareTo(total) >= 0) {
            // Если оплачено больше или равно сумме счета
            invoice.setStatus("PAID");
        } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus("PARTIAL");
        } else {
            invoice.setStatus("UNPAID");
        }

        invoiceRepository.save(invoice);

        // 6. ВЫЗЫВАЕМ ФИНАНСОВЫЙ СЕРВИС
        // Теперь передаем paymentAmount как BigDecimal
        financeService.registerOperation(
                clientObj.getId(),
                "PAYMENT",
                paymentAmount,
                payment.getId(),
                "Оплата по счету " + invoice.getInvoiceNumber(),
                invoice.getShopName()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Платеж успешно зарегистрирован и проведен по учету",
                "newInvoiceStatus", invoice.getStatus()
        ));
    }
}