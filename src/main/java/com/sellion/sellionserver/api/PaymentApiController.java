package com.sellion.sellionserver.api;

import com.sellion.sellionserver.dto.PaymentRequest;
import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Payment;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
import com.sellion.sellionserver.services.FinanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j // Используем Slf4j вместо ручного создания логгера
public class PaymentApiController {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final FinanceService financeService;

    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> registerPayment(@Valid @RequestBody PaymentRequest request) {

        Long invoiceId = request.getInvoiceId();
        BigDecimal paymentAmount = request.getAmount().setScale(2, RoundingMode.HALF_UP);
        String comment = request.getComment() != null ? request.getComment() : "";

        // Используем Optional, чтобы избежать лишних исключений в логах при поиске
        Invoice invoice = invoiceRepository.findById(invoiceId).orElse(null);
        if (invoice == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Счет №" + invoiceId + " не найден в системе."));
        }

        // Проверка остатка долга
        BigDecimal currentDebt = invoice.getTotalAmount().subtract(
                Optional.ofNullable(invoice.getPaidAmount()).orElse(BigDecimal.ZERO)
        );

        // ИСПРАВЛЕНИЕ: Вместо throw возвращаем ResponseEntity с текстом ошибки
        if (paymentAmount.compareTo(currentDebt) > 0) {
            log.warn("Попытка оплаты сверх долга: Счет {}, Долг {}, Оплата {}", invoiceId, currentDebt, paymentAmount);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Сумма платежа (" + paymentAmount + ") превышает остаток долга (" + currentDebt + ")"));
        }

        // 1. Создание записи о платеже
        Payment payment = new Payment();
        payment.setInvoiceId(invoiceId);
        payment.setAmount(paymentAmount);
        payment.setComment(comment);
        payment.setPaymentDate(LocalDateTime.now());
        Payment savedPayment = paymentRepository.save(payment);

        // 2. Обновление статуса счета
        BigDecimal newPaidAmount = Optional.ofNullable(invoice.getPaidAmount()).orElse(BigDecimal.ZERO).add(paymentAmount);
        invoice.setPaidAmount(newPaidAmount);

        if (newPaidAmount.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus("PAID");
        } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            invoice.setStatus("PARTIAL");
        } else {
            invoice.setStatus("UNPAID");
        }
        invoiceRepository.save(invoice);

        // 3. Регистрация финансовой операции
        financeService.registerOperation(
                null,
                "PAYMENT",
                paymentAmount,
                savedPayment.getId(),
                "Оплата по счету " + invoice.getInvoiceNumber() + " (" + comment + ")",
                invoice.getShopName()
        );

        log.info("Успешная оплата: счет {}, сумма {}", invoiceId, paymentAmount);

        // 4. Ответ (совместим с ApiResponse или просто Map)
        Map<String, Object> responseData = Map.of(
                "newInvoiceStatus", invoice.getStatus(),
                "remainingDebt", invoice.getTotalAmount().subtract(newPaidAmount).setScale(2, RoundingMode.HALF_UP),
                "message", "Платеж успешно зарегистрирован"
        );

        return ResponseEntity.ok(responseData);
    }

}

