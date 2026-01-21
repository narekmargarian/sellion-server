package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Invoice;
import com.sellion.sellionserver.entity.Payment;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.InvoiceRepository;
import com.sellion.sellionserver.repository.PaymentRepository;
import com.sellion.sellionserver.services.FinanceService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class PaymentApiController {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final ClientRepository clientRepository;
    private final FinanceService financeService;
    private static final Logger log = LoggerFactory.getLogger(PaymentApiController.class);

    @PostMapping("/register")
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<?> registerPayment(@RequestBody Map<String, Object> payload) {
        try {
            Long invoiceId = Long.valueOf(payload.get("invoiceId").toString());
            BigDecimal paymentAmount = new BigDecimal(payload.get("amount").toString());
            String comment = payload.get("comment") != null ? payload.get("comment").toString() : "";

            Invoice invoice = invoiceRepository.findById(invoiceId)
                    .orElseThrow(() -> new RuntimeException("Счет не найден: " + invoiceId));

            Client clientObj = clientRepository.findByName(invoice.getShopName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден: " + invoice.getShopName()));

            if (paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Сумма платежа должна быть больше 0"));
            }

            Payment payment = new Payment();
            payment.setInvoiceId(invoiceId);
            payment.setAmount(paymentAmount.doubleValue());
            payment.setComment(comment);
            payment.setPaymentDate(LocalDateTime.now());
            Payment savedPayment = paymentRepository.save(payment);

            BigDecimal currentPaid = Optional.ofNullable(invoice.getPaidAmount()).orElse(BigDecimal.ZERO);
            BigDecimal totalAmount = Optional.ofNullable(invoice.getTotalAmount()).orElse(BigDecimal.ZERO);

            BigDecimal newPaidAmount = currentPaid.add(paymentAmount);
            invoice.setPaidAmount(newPaidAmount);

            if (newPaidAmount.compareTo(totalAmount) >= 0) {
                invoice.setStatus("PAID");
            } else if (newPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
                invoice.setStatus("PARTIAL");
            } else {
                invoice.setStatus("UNPAID");
            }

            invoiceRepository.save(invoice);

            financeService.registerOperation(
                    clientObj.getId(),
                    "PAYMENT",
                    paymentAmount,
                    savedPayment.getId(),
                    "Оплата по счету " + invoice.getInvoiceNumber() + " (" + comment + ")",
                    invoice.getShopName()
            );

            log.info("Успешная оплата: счет {}, сумма {}", invoiceId, paymentAmount);

            return ResponseEntity.ok(Map.of(
                    "message", "Платеж успешно зарегистрирован",
                    "newInvoiceStatus", invoice.getStatus(),
                    "remainingDebt", totalAmount.subtract(newPaidAmount).setScale(2, RoundingMode.HALF_UP)
            ));

        } catch (Exception e) {
            log.error("Критическая ошибка при регистрации платежа: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "Ошибка сервера: " + e.getMessage()));
        }
    }
}