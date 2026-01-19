package com.sellion.sellionserver.services;


import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public void registerOperation(Long clientId, String type, Double amount, Long refId, String comment) {
        Client client = clientRepository.findById(clientId).orElseThrow();

        // --- ИСПОЛЬЗУЕМ BIGDECIMAL ДЛЯ ТОЧНЫХ ФИНАНСОВЫХ РАСЧЕТОВ ---
        BigDecimal currentDebt = BigDecimal.valueOf(client.getDebt());
        BigDecimal opAmount = BigDecimal.valueOf(amount);

        BigDecimal delta = type.equals("ORDER") ? opAmount : opAmount.negate();
        // Округляем до двух знаков после запятой
        BigDecimal newDebt = currentDebt.add(delta).setScale(2, RoundingMode.HALF_UP);

        client.setDebt(newDebt.doubleValue());
        clientRepository.save(client);

        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount)
                .balanceAfter(newDebt.doubleValue())
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }
}