package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/admin/clients")
@RequiredArgsConstructor
public class ClientWebController {
    private final ClientRepository clientRepository;
    private final TransactionRepository transactionRepository; // ДОБАВЛЕНО для записи стартового аудита

    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class) // ДОБАВЛЕНО для атомарности создания клиента и транзакции
    public String createClient(@ModelAttribute Client client) {
        // Если БД требует BigDecimal для долга, инициализируем
        if (client.getDebt() == null) {
            client.setDebt(BigDecimal.ZERO);
        }

        BigDecimal initialDebt = client.getDebt().setScale(2, RoundingMode.HALF_UP);
        client.setDebt(initialDebt);

        // 1. Сохраняем клиента в базу, чтобы получить его сгенерированный ID
        Client savedClient = clientRepository.save(client);

        // 2. ИСПРАВЛЕНИЕ: Если у клиента есть стартовый долг, фиксируем его в аудите транзакций
        if (initialDebt.compareTo(BigDecimal.ZERO) > 0) {
            Transaction initialTx = Transaction.builder()
                    .clientId(savedClient.getId())
                    .clientName(savedClient.getName())
                    .type("ORDER") // Тип ORDER увеличивает долг, что логично для стартового долга
                    .referenceId(savedClient.getId()) // В качестве refId используем ID самого клиента
                    .amount(initialDebt)
                    .balanceAfter(initialDebt)
                    .comment("Инициализация начального долга при создании карточки")
                    .timestamp(LocalDateTime.now())
                    .build();

            transactionRepository.save(initialTx);
        }

        // Возвращаемся на вкладку клиентов
        return "redirect:/admin?activeTab=tab-clients";
    }
}
