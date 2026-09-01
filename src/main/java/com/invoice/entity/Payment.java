package com.invoice.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Payment applied to a {@link ManualInvoice}. The DB trigger
 * {@code payments_after_change} keeps {@code manual_invoices.paid_amount},
 * {@code amount_due}, and {@code status} in sync — application code should
 * never write to those columns directly.
 */
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_invoice",    columnList = "invoice_id"),
        @Index(name = "idx_payments_admin",      columnList = "admin_id"),
        @Index(name = "idx_payments_admin_date", columnList = "admin_id,payment_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    public enum Status { POSTED, VOIDED, PENDING, RETURNED }

    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @NotNull
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    /** Tenant boundary. Always written from JWT-derived adminId, never from the request body. */
    @NotNull
    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @NotNull
    @DecimalMin(value = "0.0001", message = "Payment amount must be positive")
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @NotNull
    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Size(max = 120)
    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Size(max = 40)
    @Column(name = "payment_method", length = 40)
    private String paymentMethod;

    @Size(max = 1000)
    @Column(name = "remarks", length = 1000)
    private String remarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.POSTED;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void onInsert() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = Status.POSTED;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}