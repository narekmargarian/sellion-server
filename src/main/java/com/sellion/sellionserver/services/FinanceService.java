package com.sellion.sellionserver.services;


import com.sellion.sellionserver.controller.AdminManagementController;
import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    /**
     * Регистрация финансовой операции с гарантированной точностью BigDecimal.
     * Идеально подходит для заказов, оплат и возвратов.
     */
    @Transactional(rollbackFor = Exception.class)
    public void registerOperation(Long clientId, String type, BigDecimal amount, Long refId, String comment, String shopName) {
        // 1. Поиск клиента с двойной защитой
        // Если clientId не передан, ищем по имени магазина.
        // Использование .trim() защищает от ошибок ввода.
        Client client = (clientId != null)
                ? clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Критическая ошибка: Клиент с ID " + clientId + " не найден"))
                : clientRepository.findByName(shopName != null ? shopName.trim() : "")
                .orElseThrow(() -> new RuntimeException("Критическая ошибка: Клиент с названием '" + shopName + "' не найден"));

        // 2. Инициализация текущего долга (защита от null)
        BigDecimal currentDebt = Optional.ofNullable(client.getDebt()).orElse(BigDecimal.ZERO);

        // 3. Логика изменения баланса (2026 стандарт)
        // ORDER (Заказ) — увеличивает долг клиента (+)
        // PAYMENT (Оплата) и RETURN (Возврат) — уменьшают долг клиента (-)
        BigDecimal delta = amount.setScale(2, RoundingMode.HALF_UP);

        if (!"ORDER".equals(type)) {
            delta = delta.negate(); // Делаем сумму отрицательной для вычитания из долга
        }

        BigDecimal newDebt = currentDebt.add(delta);

        // 4. Атомарное обновление баланса клиента
        client.setDebt(newDebt);
        clientRepository.save(client);

        // 5. Создание записи в истории транзакций (Аудит)
        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount.setScale(2, RoundingMode.HALF_UP))
                .balanceAfter(newDebt.setScale(2, RoundingMode.HALF_UP))
                .comment(comment != null ? comment : "Без комментария")
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);

        log.info("Финансовая операция [{}]: Клиент [{}], Сумма [{}], Баланс после: [{}]",
                type, client.getName(), amount, newDebt);
    }
}
