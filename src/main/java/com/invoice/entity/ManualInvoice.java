package com.invoice.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.invoice.DTO.VendorAddressDTO;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "manual_invoices", uniqueConstraints = { @UniqueConstraint(columnNames = "po_number") }, indexes = {
		@Index(name = "idx_manual_invoices_admin_id", columnList = "adminId"),
		@Index(name = "idx_manual_invoices_consultant_id", columnList = "consultant_id"),
		@Index(name = "idx_manual_invoices_status", columnList = "status"),
		@Index(name = "idx_manual_invoices_vendor_type", columnList = "vendorType"),
		@Index(name = "idx_manual_invoices_invoice_number", columnList = "invoiceNumber") })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ManualInvoice {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "manual_invoice_seq")
	@SequenceGenerator(name = "manual_invoice_seq", sequenceName = "manual_invoice_seq", allocationSize = 1)
	private Long id;

	private Long customerVendorId;

	@Column(name = "consultant_id")
	private Long consultantId;

	@Column(name = "consultant_name")
	private String consultantName;

	// Customer info
	private String customer;
	private String customerEmail;
	private String customerPhone;

	// Invoice info
	private String template;
	private String invoiceNumber;
	private LocalDate invoiceDate;
	private LocalDate dueDate;
	private String paymentTerms;

	@Column(name = "po_number")
	private String poNumber;

	private String salesRep;
	private String status;
	private String termsAndConditions;
	private String notes;

	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "manual_invoice_files", joinColumns = @JoinColumn(name = "invoice_id"))
	@Column(name = "file_name")
	private List<String> uploadedFileNames = new ArrayList<>();

	// Financial info
	@Column(name = "total_hours", precision = 19, scale = 4)
	private BigDecimal totalHours = BigDecimal.ZERO;

	@Column(precision = 19, scale = 4)
	private BigDecimal subtotal = BigDecimal.ZERO;

	@Column(precision = 19, scale = 4)
	private BigDecimal tax = BigDecimal.ZERO;

	@Column(precision = 19, scale = 4)
	private BigDecimal total = BigDecimal.ZERO;

	@Column(name = "amount_due", precision = 19, scale = 4)
	private BigDecimal amountDue = BigDecimal.ZERO;

	@Column(precision = 19, scale = 4)
	private BigDecimal credit = BigDecimal.ZERO;

	private String currency;
	private String issuedBy;

	@Column(precision = 19, scale = 4)
	private BigDecimal discount = BigDecimal.ZERO;

	// Timestamps
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	@Column(name = "adminId")
	private Long adminId;

	@Column(name = "paymentAmount", precision = 19, scale = 4)
	private BigDecimal paymentAmount;

	@Column(name = "paymentDate")
	private LocalDate paymentDate;

	@Column(name = "dueAmount", precision = 19, scale = 4)
	private BigDecimal dueAmount;

	@Column(name = "remarks")
	private String remarks;

	@Column(name = "periodStart")
	private LocalDate periodStart;

	@Column(name = "periodend")
	private LocalDate periodend;

	@Column(name = "paidAmount", precision = 19, scale = 4)
	private BigDecimal paidAmount;

	@Column(name = "paidDate")
	private LocalDate paidDate;

	@Column(name = "vendorType")
	private String vendorType;

	@Column(name = "period")
	private String period;

	@Column(name = "employment_id")
	private Long employmentId;

	@jakarta.persistence.Version
	@Column(name = "version", nullable = false)
	private Long version = 0L;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;

	@Column(name = "reminder_snoozed_until")
	private LocalDate reminderSnoozedUntil;

	// Billing Address
	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "street", column = @Column(name = "billing_street")),
			@AttributeOverride(name = "suite", column = @Column(name = "billing_suite")),
			@AttributeOverride(name = "city", column = @Column(name = "billing_city")),
			@AttributeOverride(name = "state", column = @Column(name = "billing_state")),
			@AttributeOverride(name = "zipCode", column = @Column(name = "billing_zip_code")) })
	private VendorAddressDTO billingAddress;

	// Shipping Address
	@Embedded
	@AttributeOverrides({ @AttributeOverride(name = "street", column = @Column(name = "shipping_street")),
			@AttributeOverride(name = "suite", column = @Column(name = "shipping_suite")),
			@AttributeOverride(name = "city", column = @Column(name = "shipping_city")),
			@AttributeOverride(name = "state", column = @Column(name = "shipping_state")),
			@AttributeOverride(name = "zipCode", column = @Column(name = "shipping_zip_code")) })
	private VendorAddressDTO shippingAddress;

	@OneToMany(mappedBy = "manualInvoice", cascade = CascadeType.ALL, orphanRemoval = true)
	@Valid
	private List<InvoiceItem> items = new ArrayList<>();

	public void addItem(InvoiceItem item) {
	    items.add(item);
	    item.setManualInvoice(this);
	}

	public void clearItems() {
		items.clear();
	}

//	/** Statuses permitted by the DB check constraint ck_manual_invoices_status. */
//	private static final java.util.Set<String> ALLOWED_STATUSES = java.util.Set.of(
//			"DRAFT", "PENDING", "RECEIVED", "PARTIALLY_PAID", "PAID", "OVERDUE", "CANCELLED");
//
//	/**
//	 * Single choke point that guarantees {@code status} conforms to
//	 * ck_manual_invoices_status (UPPERCASE_UNDERSCORE, fixed set) on EVERY insert and
//	 * update — regardless of what casing or synonym a caller passed. This prevents
//	 * title-case values like "Pending" from ever reaching the database again, no matter
//	 * which service writes the entity.
//	 */
//	@jakarta.persistence.PrePersist
//	@jakarta.persistence.PreUpdate
//	private void normalizeStatus() {
//		if (status == null || status.isBlank()) {
//			return; // NULL is allowed by the constraint
//		}
//		String s = status.trim().toUpperCase().replaceAll("\\s+", "_");
//		switch (s) {
//			case "PARTIALLY_RECEIVED":
//				s = "PARTIALLY_PAID";
//				break;
//			case "EXCESS_RECEIVED":
//			case "EXCESS_PAID":
//				s = "PAID";
//				break;
//			case "SENT":
//				s = "PENDING";
//				break;
//			default:
//				break;
//		}
//		// Last-resort guard: an unrecognized status falls back to PENDING so a save can
//		// never crash on the constraint. (In practice the cases above cover the UI values.)
//		if (!ALLOWED_STATUSES.contains(s)) {
//			s = "PENDING";
//		}
//		status = s;
//	}
	
	/** Statuses permitted by the DB check constraint. */
	private static final Set<String> ALLOWED_STATUSES = Set.of(
	        "DRAFT",
	        "PENDING",
	        "RECEIVED",
	        "PARTIALLY_RECEIVED",
	        "PARTIALLY_PAID",
	        "PAID",
	        "OVERDUE",
	        "CANCELLED",
	        "EXCESS_RECEIVED",
	        "EXCESS_PAID"
	);

	@PrePersist
	@PreUpdate
	private void normalizeStatus() {
	    if (status == null || status.isBlank()) {
	        return;
	    }

	    String s = status.trim()
	                     .toUpperCase()
	                     .replaceAll("\\s+", "_");

	    // Normalize frontend aliases
	    if ("SENT".equals(s)) {
	        s = "PENDING";
	    }

	    // Enforce AR/AP status separation so dumped or mis-labelled data is always corrected.
	    // receivable invoices must use RECEIVED-family; payable must use PAID-family.
	    if ("receivable".equalsIgnoreCase(vendorType)) {
	        switch (s) {
	            case "PAID":           s = "RECEIVED";           break;
	            case "PARTIALLY_PAID": s = "PARTIALLY_RECEIVED"; break;
	            case "EXCESS_PAID":    s = "EXCESS_RECEIVED";    break;
	            default: break;
	        }
	    } else if ("payable".equalsIgnoreCase(vendorType)) {
	        switch (s) {
	            case "RECEIVED":           s = "PAID";           break;
	            case "PARTIALLY_RECEIVED": s = "PARTIALLY_PAID"; break;
	            case "EXCESS_RECEIVED":    s = "EXCESS_PAID";    break;
	            default: break;
	        }
	    }

	    // Prevent invalid values
	    if (!ALLOWED_STATUSES.contains(s)) {
	        s = "PENDING";
	    }

	    status = s;
	}
	
	
}
