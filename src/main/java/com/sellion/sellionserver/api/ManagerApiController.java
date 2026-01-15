package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.ManagerId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
public class ManagerApiController {

    @GetMapping("/managers")
    public List<String> getManagerList() {
        // Используем новый метод, который исключает OFFICE
        return ManagerId.getFieldManagerDisplayNames();
    }
}