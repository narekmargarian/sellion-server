package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
                .filter(c -> c.getDebt() > 0)
                .collect(Collectors.toList());

        double totalDebtSum = clientsWithDebt.stream()
                .mapToDouble(Client::getDebt)
                .sum();

        model.addAttribute("clients", clientsWithDebt);
        model.addAttribute("totalDebtSum", totalDebtSum);
        return "debt-report";
    }
}