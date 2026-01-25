package com.sellion.sellionserver.repository;

import com.sellion.sellionserver.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findAllByShopName(String shopName);

    List<Invoice> findAllByOrderByCreatedAtDesc();

    @Query("SELECT SUM(i.totalAmount - i.paidAmount) FROM Invoice i")
    BigDecimal calculateTotalDebt();

    @Query("SELECT SUM(i.paidAmount) FROM Invoice i")
    BigDecimal calculateTotalPaid();

}