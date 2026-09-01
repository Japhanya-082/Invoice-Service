package com.invoice.service;

public interface InvoiceEmailService {

	public void sendOverdueInvoiceEmail(String authHeader, String invoiceNumber);

}
