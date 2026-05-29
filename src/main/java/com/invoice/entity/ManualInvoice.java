package com.invoice.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
	private Double totalHours = 0.0;
	private Double subtotal = 0.0;
	private Double tax = 0.0;
	private Double total = 0.0;
	private Double amountDue = 0.0;
	private Double credit = 0.0;
	private String currency;
	private String issuedBy;
	private Double discount = 0.0;

	// Timestamps
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	@Column(name = "adminId")
	private Long adminId;

	@Column(name = "paymentAmount")
	private String paymentAmount;

	@Column(name = "paymentDate")
	private LocalDate paymentDate;

	@Column(name = "dueAmount")
	private String dueAmount;

	@Column(name = "remarks")
	private String remarks;

	@Column(name = "periodStart")
	private LocalDate periodStart;

	@Column(name = "periodend")
	private LocalDate periodend;

	@Column(name = "paidAmount")
	private String paidAmount;

	@Column(name = "paidDate")
	private LocalDate paidDate;

	@Column(name = "vendorType")
	private String vendorType;

	@Column(name = "period")
	private String period;

	@Column(name = "employment_id")
	private Long employmentId;

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
	@NotEmpty(message = "Invoice must contain at least one item")
	@Valid
	private List<InvoiceItem> items = new ArrayList<>();

	public void addItem(InvoiceItem item) {
		items.add(item);
		item.setManualInvoice(this);
	}

	public void clearItems() {
		items.clear();
	}
}
