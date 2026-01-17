package com.sellion.sellionserver.services;


import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.entity.Transaction;
import com.sellion.sellionserver.repository.ClientRepository;
import com.sellion.sellionserver.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FinanceService {
    private final TransactionRepository transactionRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public void registerOperation(Long clientId, String type, Double amount, Long refId, String comment) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new RuntimeException("Клиент не найден"));

        // Вычисляем дельту. Заказ увеличивает долг (+), оплата и возврат уменьшают (-)
        double delta = type.equals("ORDER") ? amount : -amount;

        // Обновляем долг в базе
        client.setDebt(client.getDebt() + delta);
        clientRepository.save(client);

        // Пишем транзакцию (Акт сверки)
        Transaction tx = Transaction.builder()
                .clientId(client.getId())
                .clientName(client.getName())
                .type(type)
                .referenceId(refId)
                .amount(amount)
                .balanceAfter(client.getDebt())
                .comment(comment)
                .timestamp(LocalDateTime.now())
                .build();

        transactionRepository.save(tx);
    }
}