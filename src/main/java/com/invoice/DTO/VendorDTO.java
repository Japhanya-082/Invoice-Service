package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VendorDTO {
	private Long vendorId;
	private String vendorName;
	private String email;
	private String phoneNumber;
	private VendorAddressDTO vendorAddress;
	private Long adminId;
	private String vendorType;
	
}