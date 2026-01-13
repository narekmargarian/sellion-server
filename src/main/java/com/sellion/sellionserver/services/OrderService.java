package com.sellion.sellionserver.services;

import com.sellion.sellionserver.entity.Order;
import com.sellion.sellionserver.entity.OrderStatus;
import com.sellion.sellionserver.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {
    public boolean canOperatorEdit(Order order) {
        if (order == null) return false;
        // Оператор может менять только НОВЫЕ или ПРИНЯТЫЕ заказы, если нет счета
        return (order.getStatus() == OrderStatus.NEW || order.getStatus() == OrderStatus.ACCEPTED)
                && order.getInvoiceId() == null;
    }
}