package com.invoice.service;

import java.util.List;

import com.invoice.DTO.UserDTO;
import com.invoice.entity.ManualInvoice;

public interface EmailService {

	public void sendOverdueInvoiceEmail(UserDTO user, ManualInvoice invoice, List<String> ccEmails);

	
	public void sendPaymentReminderEmail(UserDTO user, ManualInvoice invoice, int daysUntilDue, List<String> ccEmails);

}
