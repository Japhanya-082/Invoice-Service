package com.example.service;

import java.util.List;
import com.example.entity.ManualInvoice;

public interface ManualInvoiceService {
	public ManualInvoice createInvoice(ManualInvoice manualInvoice);
    public ManualInvoice getInvoiceById(Long id);
    public List<ManualInvoice> getAllInvoices();
    public void deleteInvoice(Long id);
}
