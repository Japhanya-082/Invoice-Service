package com.invoice.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.invoice.entity.Invoice;
import com.invoice.exception.FileStorageException;

public interface InvoiceService {
	public List<Invoice> uploadAndSaveInvoices(MultipartFile multipartFile) throws FileStorageException;

	public List<Invoice> getAll();

	public void deleteByInvoiceNumber(Long invoiceId);

}
