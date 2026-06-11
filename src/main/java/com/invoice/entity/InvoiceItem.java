package com.invoice.entity;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "invoice_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@NotBlank(message = "Item name is required")
	private String name;

	private String description;

	@NotNull(message = "Hours is required")
	@Column(precision = 19, scale = 4)
	private BigDecimal hours = BigDecimal.ZERO;

	@NotNull(message = "Rate is required")
	@Column(precision = 19, scale = 4)
	private BigDecimal rate = BigDecimal.ZERO;


	// Calculated in service
	@Column(precision = 19, scale = 4)
	private BigDecimal amount = BigDecimal.ZERO;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invoice_id", nullable = false)
	@JsonBackReference
	private ManualInvoice manualInvoice;
	
}
