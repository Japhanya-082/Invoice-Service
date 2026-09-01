package com.invoice.DTO;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class VendorAddressDTO {
	private String street;
	private String suite;
	private String city;
	private String state;
	private String zipCode;

	// Constructor to convert a string (for shippingAddress)
	public VendorAddressDTO(String street) {
		this.street = street;
		this.suite = "";
		this.city = "";
		this.state = "";
		this.zipCode = "";
	}
}