package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/reports")
@RequiredArgsConstructor
public class ReportWebController {

    private final ClientRepository clientRepository;

    @GetMapping("/debts")
    public String debtReport(Model model) {
        // Получаем вообще всех клиентов, у которых баланс не равен нулю
        List<Client> allActiveBalances = clientRepository.findAll().stream()
                .filter(c -> c.getDebt() != null && c.getDebt().compareTo(BigDecimal.ZERO) != 0)
                .collect(Collectors.toList());

        // 1. Клиенты с реальным долгом перед нами (> 0)
        List<Client> clientsWithDebt = allActiveBalances.stream()
                .filter(c -> c.getDebt().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        // 2. Клиенты с предоплатой / авансом (< 0) — это наш долг перед ними
        List<Client> clientsWithAdvance = allActiveBalances.stream()
                .filter(c -> c.getDebt().compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.toList());

        // 3. ИСТИННЫЙ общий долг системы (Сальдо): Долги минус Авансы
        BigDecimal totalDebtSum = allActiveBalances.stream()
                .map(Client::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Суммируем отдельно чистые долги для аналитики
        BigDecimal pureDebtSum = clientsWithDebt.stream()
                .map(Client::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Суммируем отдельно авансы для аналитики
        BigDecimal pureAdvanceSum = clientsWithAdvance.stream()
                .map(Client::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add).abs(); // Берем по модулю для красоты вывода

        model.addAttribute("clients", clientsWithDebt); // Сохраняем совместимость со старой таблицей
        model.addAttribute("clientsWithAdvance", clientsWithAdvance); // Добавляем список авансов
        model.addAttribute("totalDebtSum", totalDebtSum);
        model.addAttribute("pureDebtSum", pureDebtSum);
        model.addAttribute("pureAdvanceSum", pureAdvanceSum);

        return "debt-report";
    }
}
