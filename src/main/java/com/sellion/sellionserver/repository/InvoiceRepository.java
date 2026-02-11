package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findAllByShopName(String shopName);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    @Query("SELECT SUM(i.totalAmount - i.paidAmount) FROM Invoice i")
    BigDecimal calculateTotalDebt();

    @Query("SELECT SUM(i.paidAmount) FROM Invoice i")
    BigDecimal calculateTotalPaid();

    // Поиск по менеджеру, статусу (НЕ равен PAID) и промежутку дат
    @Query("SELECT i FROM Invoice i WHERE i.managerId = :managerId " +
            "AND i.status <> 'PAID' " +
            "AND i.createdAt >= :start AND i.createdAt <= :end " +
            "ORDER BY i.createdAt DESC")
    List<Invoice> findUnpaidByManagerAndPeriod(
            @Param("managerId") String managerId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // Пагинация по датам
    Page<Invoice> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);

    // Для расчетов статистики без пагинации
    List<Invoice> findAllByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT i FROM Invoice i WHERE i.createdAt >= :start AND i.createdAt <= :end " +
            "AND (:manager IS NULL OR i.managerId = :manager) " +
            "AND (:status IS NULL OR " + // Если статус не выбран, условие ниже игнорируется
            "(:status = 'Оплачен' AND i.paidAmount >= i.totalAmount) OR " +
            "(:status = 'Частично' AND i.paidAmount > 0 AND i.paidAmount < i.totalAmount) OR " +
            "(:status = 'Не Оплачен' AND i.paidAmount <= 0))")
    Page<Invoice> findFilteredInvoices(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("manager") String manager,
            @Param("status") String status,
            Pageable pageable);

}