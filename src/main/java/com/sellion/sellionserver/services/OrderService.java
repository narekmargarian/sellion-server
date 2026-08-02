package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository; // Убедитесь, что репозиторий подключен

    public boolean canOperatorEdit(Order order) {
        if (order == null) return false;
        return (order.getStatus() == OrderStatus.NEW || order.getStatus() == OrderStatus.ACCEPTED)
                && order.getInvoiceId() == null;
    }

    public List<Order> getOrdersForPeriod(LocalDate start, LocalDate end, String managerId) {
        LocalDateTime startDateTime = start.atStartOfDay();
        LocalDateTime endDateTime = end.atTime(23, 59, 59);

        if (managerId != null && !managerId.isEmpty() && !managerId.equals("Все менеджеры")) {
            // Используем уже имеющийся в вашем репозитории метод для менеджера
            return orderRepository.findOrdersByManagerAndDateRange(managerId, startDateTime, endDateTime);
        } else {
            // Используем уже имеющийся метод для всех заказов за период
            return orderRepository.findOrdersBetweenDates(startDateTime, endDateTime);
        }
    }
}