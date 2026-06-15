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

    // ─── Dashboard: Monthly totals ────────────────────────────────────────────

    @Query("SELECT SUM(p.amount) FROM Payment p " +
           "WHERE p.adminId = :adminId " +
           "AND p.status = com.invoice.entity.Payment.Status.POSTED " +
           "AND p.deletedAt IS NULL " +
           "AND p.invoiceId IN (" +
           "  SELECT i.id FROM ManualInvoice i " +
           "  WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'receivable' AND i.deletedAt IS NULL" +
           ") " +
           "AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) = :month")
    BigDecimal sumCollectedThisMonth(
            @Param("adminId") Long adminId,
            @Param("year")    int year,
            @Param("month")   int month);

    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.adminId = :adminId " +
           "AND p.status = com.invoice.entity.Payment.Status.POSTED " +
           "AND p.deletedAt IS NULL " +
           "AND p.invoiceId IN (" +
           "  SELECT i.id FROM ManualInvoice i " +
           "  WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'receivable' AND i.deletedAt IS NULL" +
           ") " +
           "AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) = :month")
    Long countCollectedThisMonth(
            @Param("adminId") Long adminId,
            @Param("year")    int year,
            @Param("month")   int month);

    @Query("SELECT SUM(p.amount) FROM Payment p " +
           "WHERE p.adminId = :adminId " +
           "AND p.status = com.invoice.entity.Payment.Status.POSTED " +
           "AND p.deletedAt IS NULL " +
           "AND p.invoiceId IN (" +
           "  SELECT i.id FROM ManualInvoice i " +
           "  WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'payable' AND i.deletedAt IS NULL" +
           ") " +
           "AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) = :month")
    BigDecimal sumPaidThisMonth(
            @Param("adminId") Long adminId,
            @Param("year")    int year,
            @Param("month")   int month);

    @Query("SELECT COUNT(p) FROM Payment p " +
           "WHERE p.adminId = :adminId " +
           "AND p.status = com.invoice.entity.Payment.Status.POSTED " +
           "AND p.deletedAt IS NULL " +
           "AND p.invoiceId IN (" +
           "  SELECT i.id FROM ManualInvoice i " +
           "  WHERE i.adminId = :adminId AND LOWER(i.vendorType) = 'payable' AND i.deletedAt IS NULL" +
           ") " +
           "AND YEAR(p.paymentDate) = :year " +
           "AND MONTH(p.paymentDate) = :month")
    Long countPaidThisMonth(
            @Param("adminId") Long adminId,
            @Param("year")    int year,
            @Param("month")   int month);

    @Query("SELECT p FROM Payment p " +
           "WHERE p.adminId = :adminId AND p.deletedAt IS NULL " +
           "ORDER BY p.createdAt DESC")
    List<Payment> findRecentPayments(
            @Param("adminId") Long adminId,
            org.springframework.data.domain.Pageable pageable);
}
