package com.sellion.sellionserver.controller;

import com.sellion.sellionserver.entity.Client;
import com.sellion.sellionserver.repository.ClientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping("/admin/clients")
@RequiredArgsConstructor
public class ClientWebController {
    private final ClientRepository clientRepository;

    @PostMapping("/create")
    public String createClient(@ModelAttribute Client client) {
        clientRepository.save(client);
        // Редиректим обратно на /admin и указываем открыть вкладку клиентов
        return "redirect:/admin?activeTab=tab-clients";
    }


}