package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Payment;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
import com.sellion.sellionserver.services.FinanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentApiController {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final FinanceService financeService;

    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class) // Гарантирует откат при любой ошибке
    public ResponseEntity<?> registerPayment(@RequestBody Payment payment) {
        // 1. Находим счет
        Invoice invoice = invoiceRepository.findById(payment.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Счет не найден: " + payment.getInvoiceId()));

        // 2. Находим клиента (используем shopName из инвойса)
        Client clientObj = clientRepository.findByName(invoice.getShopName())
                .orElseThrow(() -> new RuntimeException("Клиент не найден: " + invoice.getShopName()));

        // 3. Безопасная подготовка суммы платежа
        // Если пришел null в сумме, считаем платеж нулевым (защита от падения)
        BigDecimal paymentAmount = (payment.getAmount() != null)
                ? BigDecimal.valueOf(payment.getAmount())
                : BigDecimal.ZERO;

        if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Сумма платежа должна быть больше 0"));
        }

        // 4. Сохраняем информацию о платеже
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // 5. Обновляем статус оплаты в инвойсе
        // Используем Optional для защиты от NULL в базе данных
        BigDecimal currentPaid = Optional.ofNullable(invoice.getPaidAmount()).orElse(BigDecimal.ZERO);
        BigDecimal totalAmount = Optional.ofNullable(invoice.getTotalAmount()).orElse(BigDecimal.ZERO);

        BigDecimal newPaidAmount = currentPaid.add(paymentAmount);
        invoice.setPaidAmount(newPaidAmount);

        // ЛОГИКА СТАТУСОВ (2026):
        // compareTo >= 0 означает "больше или равно"
        if (newPaidAmount.compareTo(totalAmount) >= 0) {
            invoice.setStatus("PAID");
        } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus("PARTIAL");
        } else {
            invoice.setStatus("UNPAID");
        }

        invoiceRepository.save(invoice);

        // 6. ПРОВЕДЕНИЕ ПО ФИНАНСОВОМУ УЧЕТУ (Обновление долга клиента)
        financeService.registerOperation(
                clientObj.getId(),
                "PAYMENT",
                paymentAmount,
                payment.getId(),
                "Оплата по счету " + invoice.getInvoiceNumber() + " (" + payment.getComment() + ")",
                invoice.getShopName()
        );

        return ResponseEntity.ok(Map.of(
                "message", "Платеж успешно зарегистрирован и проведен",
                "newInvoiceStatus", invoice.getStatus(),
                "remainingDebt", totalAmount.subtract(newPaidAmount)
        ));
    }
}