package com.invoice.DTO;

import lombok.Data;

@Data
public class InvoiceSortingRequestDTO {

	private String search;
	private String sortField;
	private String sortOrder;
	private Integer pageNumber;
	private Integer pageSize;
	private Long adminId;
	private String vendorType;
	private String status;
	private Long invoiceId;
}