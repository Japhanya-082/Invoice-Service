package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultantDTO {

	private Long id;
	private String firstName;
	private String middleName;
	private String lastName;
	private String email;
	private String invoiceMail;
	private Long adminId;

	public String getFullName() {
		return (firstName != null ? firstName : "") + " " + (middleName != null ? middleName : "") + " "
				+ (lastName != null ? lastName : "");
	}
}