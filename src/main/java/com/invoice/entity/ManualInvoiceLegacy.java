package com.invoice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
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

	@Column(precision = 19, scale = 4)
	private BigDecimal subtotal = BigDecimal.ZERO;

	@Column(precision = 19, scale = 4)
	private BigDecimal tax = BigDecimal.ZERO;

	@Column(precision = 19, scale = 4)
	private BigDecimal total = BigDecimal.ZERO;

	@Column(precision = 19, scale = 4)
	private BigDecimal credit = BigDecimal.ZERO;

	@Column(name = "amount_due", precision = 19, scale = 4)
	private BigDecimal amountDue = BigDecimal.ZERO;

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

