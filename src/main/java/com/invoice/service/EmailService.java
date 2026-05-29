package com.invoice.service;

import com.invoice.DTO.UserDTO;
import com.invoice.entity.ManualInvoice;

public interface EmailService {

	public void sendOverdueInvoiceEmail(UserDTO user, ManualInvoice invoice);
	

}
