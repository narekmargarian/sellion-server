package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import lombok.Data;
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
        List<Client> clientsWithDebt = clientRepository.findAll().stream()
                .filter(c -> c.getDebt() != null && c.getDebt().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toList());

        // 2. Суммируем долги через reduce (так как mapToDouble больше не подходит)
        BigDecimal totalDebtSum = clientsWithDebt.stream()
                .map(Client::getDebt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("clients", clientsWithDebt);
        model.addAttribute("totalDebtSum", totalDebtSum);

        return "debt-report";
    }



}