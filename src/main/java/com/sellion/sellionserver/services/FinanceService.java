package com.sellion.sellionserver.services;


import com.sellion.sellionserver.controller.AdminManagementController;
import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;
    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);


    @Transactional(rollbackFor = Exception.class)
    public void registerOperation(Long clientId, String type, BigDecimal amount, Long refId, String comment, String shopName) {
        // 1. Поиск клиента (приоритет по ID, затем по имени)
        Client client = (clientId != null)
                ? clientRepository.findById(clientId).orElseThrow(() -> new RuntimeException("Клиент ID " + clientId + " не найден"))
                : clientRepository.findByName(shopName).orElseThrow(() -> new RuntimeException("Клиент '" + shopName + "' не найден"));

        // 2. Инициализация баланса, если он null
        BigDecimal currentDebt = Optional.ofNullable(client.getDebt()).orElse(BigDecimal.ZERO);

        // 3. Расчет новой суммы (ORDER увеличивает долг, PAYMENT/RETURN уменьшают)
        // Используем чистый BigDecimal без double
        BigDecimal delta = amount.setScale(2, RoundingMode.HALF_UP);
        if (!"ORDER".equals(type)) {
            delta = delta.negate();
        }

        BigDecimal newDebt = currentDebt.add(delta);

        // 4. Обновление клиента
        client.setDebt(newDebt);
        clientRepository.save(client);

        // 5. Создание транзакции (Immutable style)
        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .balanceAfter(newDebt.setScale(2, RoundingMode.HALF_UP))
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);

        log.info("Финансы: Клиент {}, Операция {}, Сумма {}, Новый долг {}",
                client.getName(), type, amount, newDebt);
    }

}
