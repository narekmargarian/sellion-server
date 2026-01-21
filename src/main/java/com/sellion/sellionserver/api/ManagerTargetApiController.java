package com.sellion.sellionserver.api;

import com.sellion.sellionserver.entity.ManagerTarget;
import com.sellion.sellionserver.repository.ManagerTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.time.Year;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/targets")
@RequiredArgsConstructor
public class ManagerTargetApiController {

    private final ManagerTargetRepository targetRepository;

    @PostMapping("/save")
    public void saveTarget(@RequestBody Map<String, Object> payload) {
        String managerId = (String) payload.get("managerId");
        BigDecimal amount = new BigDecimal(payload.get("targetAmount").toString());
        Month currentMonth = LocalDate.now().getMonth();
        Year currentYear = Year.now();

        ManagerTarget existingTarget = targetRepository.findByManagerIdAndMonthAndYear(managerId, currentMonth, currentYear);
        if (existingTarget == null) {
            existingTarget = new ManagerTarget();
            existingTarget.setManagerId(managerId);
            existingTarget.setMonth(currentMonth);
            existingTarget.setYear(currentYear);
        }
        existingTarget.setTargetAmount(amount);
        targetRepository.save(existingTarget);
    }
}