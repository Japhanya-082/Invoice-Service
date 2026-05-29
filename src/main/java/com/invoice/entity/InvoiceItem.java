package com.invoice.entity;

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
	private Double hours = 0.0;

	@NotNull(message = "Rate is required")
	private Double rate = 0.0;

	
	// Calculated in service
	private Double amount = 0.0;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "invoice_id", nullable = false)
	@JsonBackReference
	private ManualInvoice manualInvoice;
	

}
