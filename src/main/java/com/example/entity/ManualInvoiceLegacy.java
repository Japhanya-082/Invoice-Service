package com.example.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "manual_invoice")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManualInvoiceLegacy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", unique = true)
    private String invoiceNumber;

    private String customer;
    private String customerEmail;
    private String customerPhone;

    private String status;
    private String currency;
    private String paymentTerms;
    private String notes;
    private String template;

    @Column(name = "invoice_date")
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "po_number")
    private String poNumber;

    private Double subtotal = 0.0;
    private Double tax = 0.0;
    private Double total = 0.0;
    private Double credit = 0.0;

    @Column(name = "amount_due")
    private Double amountDue = 0.0;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "consultant_id")
    private Long consultantId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
