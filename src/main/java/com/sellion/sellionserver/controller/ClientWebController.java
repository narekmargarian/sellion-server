package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;


@Controller
@RequestMapping("/admin/clients")
@RequiredArgsConstructor
public class ClientWebController {
    private final ClientRepository clientRepository;

    @PostMapping("/create")
    public String createClient(@ModelAttribute Client client) {
        // Если БД требует BigDecimal для долга, инициализируем
        if (client.getDebt() == null) {
            client.setDebt(BigDecimal.ZERO);
        }
        clientRepository.save(client);
        // Возвращаемся на вкладку клиентов
        return "redirect:/admin?activeTab=tab-clients";
    }



}