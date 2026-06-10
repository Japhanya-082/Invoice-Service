package com.invoice.repository;

import com.invoice.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findByAdminIdAndDeletedAtIsNull(Long adminId, Pageable pageable);

    List<Payment> findByInvoiceIdAndAdminIdAndDeletedAtIsNullOrderByPaymentDateAsc(Long invoiceId, Long adminId);

    Optional<Payment> findByPaymentIdAndAdminId(Long paymentId, Long adminId);

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p " +
           "WHERE p.invoiceId = :invoiceId AND p.adminId = :adminId " +
           "AND p.status = com.invoice.entity.Payment.Status.POSTED " +
           "AND p.deletedAt IS NULL")
    BigDecimal sumPostedAmount(@Param("invoiceId") Long invoiceId, @Param("adminId") Long adminId);

    @Query("SELECT p FROM Payment p WHERE p.adminId = :adminId AND p.deletedAt IS NULL " +
           "AND p.paymentDate BETWEEN :from AND :to ORDER BY p.paymentDate DESC, p.paymentId DESC")
    Page<Payment> findByAdminIdAndDateRange(@Param("adminId") Long adminId,
                                            @Param("from") LocalDate from,
                                            @Param("to") LocalDate to,
                                            Pageable pageable);

    boolean existsByAdminIdAndPaymentReferenceIgnoreCase(Long adminId, String paymentReference);
}
