package com.invoice.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.invoice.DTO.InvoiceSortingRequestDTO;
import com.invoice.entity.ManualInvoice;

public interface ManualInvoiceService1 {
	ManualInvoice saveInvoice(ManualInvoice paramManualInvoice);

	public boolean isPoNumberDuplicate(String poNumber, Long invoiceId, Long adminId);

	ManualInvoice getInvoiceById(Long paramLong);

	ManualInvoice updateInvoice(Long paramLong, ManualInvoice paramManualInvoice);

	public List<ManualInvoice> getAllInvoices(Long adminId);

	public void deleteInvoice(Long id, Long adminId);

	ManualInvoice updateManualInvoice(Long id, ManualInvoice invoice);

	String storeFile(MultipartFile paramMultipartFile) throws IOException;

	List<String> storeMultipleFiles(MultipartFile[] files) throws IOException;

	public ManualInvoice updateUploadedFilesOnly(ManualInvoice invoice);

	List<String> getAllTemplates();

	Resource loadFileAsResource(String paramString) throws Exception;

	Page<ManualInvoice> searchInvoices(String paramString, Pageable paramPageable);

	public Map<String, Long> getInvoiceCounts();

	public void sendInvoiceMail(String invoiceNumber, Long adminId);

	public Long getTodayOverdueCount();

	public List<ManualInvoice> getTodayOverdueInvoices();

	public Page<ManualInvoice> getAllInvoicesWithPaginationAndSearch(int page, int size, String sortField,
			String sortDir, String keyword, Long adminId);

	public ManualInvoice getInvoiceByNumber(String invoiceNumber);

	public List<ManualInvoice> getInvoicesByConsultantId(Long consultantId);

	public List<ManualInvoice> getPendingInvoicesByAdmin(Long adminId);

	public Page<ManualInvoice> getPendingInvoicesByAdmin(InvoiceSortingRequestDTO requestDTO);

	public Page<ManualInvoice> getInvoicesByAdminAndVendorType(InvoiceSortingRequestDTO requestDTO);

	public void sendInvoiceMails(String invoiceNumber, Long adminId);

	public Page<ManualInvoice> getInvoiceByAdminAndVendorType(InvoiceSortingRequestDTO requestDTO);

	public Page<ManualInvoice> getInvoicesByAdminAndVendorTypestatus(InvoiceSortingRequestDTO requestDTO);

	public Page<ManualInvoice> getInvoiceByAdminAndVendorTypereceivablestatus(InvoiceSortingRequestDTO requestDTO);

	public Page<ManualInvoice> getInvoicesByAdminAndVendorTypestatusinvoicestatus(InvoiceSortingRequestDTO requestDTO);

	public Map<String, Object> getInvoiceStatusCounts(Long adminId);

	public ResponseEntity<Boolean> checkEmploymentInvoices(Long employmentId);
	
}
