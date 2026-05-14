package com.example.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.DTO.InvoiceSortingRequestDTO;
import com.example.DTO.VendorAddressDTO;
import com.example.DTO.VendorDTO;
import com.example.client.VendorFeignClient;
import com.example.common.RestAPIResponse;
import com.example.entity.InvoiceItem;
import com.example.entity.ManualInvoice;
import com.example.repository.InvoiceRepository;
import com.example.repository.ManualInvoiceRepository;
import com.example.service.VendorClientService;
import com.example.serviceImpl.ManualInvoiceServiceImpl1;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@RestController
@RequestMapping("/manual-invoice")
public class ManualInvoiceController1 {

	@Autowired
	private ManualInvoiceServiceImpl1 serviceImpl1;

	@Autowired
	private ManualInvoiceRepository manualInvoiceRepository;

	@Autowired
	private VendorClientService vendorClientService;

	@Autowired
	private VendorFeignClient vendorFeignClient;

	@Autowired
	private InvoiceRepository invoiceRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@PostMapping("/save")
	public ResponseEntity<RestAPIResponse> saveInvoice(@RequestBody Map<String, Object> payload) {

		try {
			ManualInvoice invoice = objectMapper.convertValue(payload, ManualInvoice.class);
			
			// FIX frontend bug: id = ""
			if (payload.get("id") == null || payload.get("id").toString().isBlank()) {
				invoice.setId(null);
			}
			
			 // ✅ FIX vendorType mapping
	        if (payload.get("vendorType") != null) {
	            invoice.setVendorType(payload.get("vendorType").toString());
	        }

			// Shipping address
			Object shippingObj = payload.get("shippingAddress");
			if (shippingObj instanceof String) {
				invoice.setShippingAddress(new VendorAddressDTO((String) shippingObj));
			} else if (shippingObj instanceof Map) {
				invoice.setShippingAddress(objectMapper.convertValue(shippingObj, VendorAddressDTO.class));
			}

			// Billing address
			Object billingObj = payload.get("billingAddress");
			if (billingObj instanceof Map) {
				invoice.setBillingAddress(objectMapper.convertValue(billingObj, VendorAddressDTO.class));
			}

			// Items
			List<Map<String, Object>> itemsMap = (List<Map<String, Object>>) payload.get("items");
			List<InvoiceItem> items = new ArrayList<>();

			if (itemsMap != null) {
				for (Map<String, Object> m : itemsMap) {
					InvoiceItem item = new InvoiceItem();
					item.setId(null);
					item.setName((String) m.get("name"));
					item.setDescription((String) m.get("description"));
					item.setHours(Double.valueOf(m.get("hours").toString()));
					item.setRate(Double.valueOf(m.get("rate").toString()));
					items.add(item);
				}
			}

			invoice.setItems(items);

			ManualInvoice saved = serviceImpl1.saveInvoice(invoice);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice saved successfully", saved));

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new RestAPIResponse("Error", e.getMessage(), null));
		}
	}

//	@GetMapping("/consultant/{consultantId}/exists")
//	public boolean consultantHasInvoice(@PathVariable Long consultantId) {
//		return manualInvoiceRepository.existsByConsultantId(consultantId);
//	}

	@GetMapping("/exists/{poNumber}")
	public ResponseEntity<Map<String, Object>> checkPoNumberDuplicate(@PathVariable String poNumber,
			@RequestParam(required = false) Long invoiceId, @RequestParam Long adminId) {

		boolean exists = serviceImpl1.isPoNumberDuplicate(poNumber, invoiceId, adminId);

		Map<String, Object> response = new HashMap<>();
		response.put("field", "poNumber");
		response.put("value", poNumber);
		response.put("exists", exists);
		response.put("message", exists ? "PO Number already exists" : "PO Number is available");

		return ResponseEntity.ok(response);
	}

	@GetMapping("/invoices/count-by-vendor/{vendorId}")
	public ResponseEntity<Long> countInvoicesByVendor(@PathVariable Long vendorId) {
		long count = manualInvoiceRepository.countByCustomerVendorId(vendorId);
		return ResponseEntity.ok(count);
	}

	// Upload files and attach to invoice
	@PostMapping(value = "/upload/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	@Transactional
	public ResponseEntity<RestAPIResponse> uploadFiles(@PathVariable Long id,
			@RequestParam("files") MultipartFile[] files, HttpServletRequest request) {

		try {
			ManualInvoice invoice = serviceImpl1.getInvoiceById(id);
			if (invoice == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new RestAPIResponse("Error", "Invoice not found", null));
			}

			List<String> uploadedFiles = serviceImpl1.storeMultipleFiles(files);

			// Merge uploaded files
			List<String> currentFiles = invoice.getUploadedFileNames();
			if (currentFiles == null)
				currentFiles = new ArrayList<>();
			currentFiles.addAll(uploadedFiles);
			invoice.setUploadedFileNames(currentFiles);

			// Save files only (no item validation)
			serviceImpl1.updateUploadedFilesOnly(invoice);

			// Generate download URLs
			String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
			List<String> fileUrls = uploadedFiles.stream().map(f -> baseUrl + "/manual-invoice/view/" + f)
					.collect(Collectors.toList());

			Map<String, Object> responseData = new HashMap<>();
			responseData.put("uploadedFiles", uploadedFiles);
			responseData.put("fileDownloadUrls", fileUrls);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Files uploaded successfully", responseData));

		} catch (Exception e) {
			e.printStackTrace();
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to upload files: " + e.getMessage(), null));
		}
	}

	// View single file
	@GetMapping("/view/{filename}")
	public ResponseEntity<Resource> viewFile(@PathVariable String filename) {
		try {
			Resource resource = serviceImpl1.loadFileAsResource(filename);

			// Determine content type based on file extension
			String contentType = "application/octet-stream";
			if (filename.endsWith(".pdf"))
				contentType = "application/pdf";
			else if (filename.endsWith(".csv"))
				contentType = "text/csv";
			else if (filename.endsWith(".docx"))
				contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

			return ResponseEntity.ok()
					.header("Content-Disposition", "inline; filename=\"" + resource.getFilename() + "\"")
					.contentType(MediaType.parseMediaType(contentType)).body(resource);

		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
	}

	// Get invoice by ID + uploaded file URLs
	@GetMapping("/{id}")
	public ResponseEntity<RestAPIResponse> getInvoiceById(@PathVariable Long id, HttpServletRequest request) {
		try {
			ManualInvoice invoice = serviceImpl1.getInvoiceById(id);
			if (invoice == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new RestAPIResponse("Error", "Invoice not found", null));
			}

			String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort();
			List<String> fileUrls = invoice.getUploadedFileNames().stream()
					.map(fileName -> baseUrl + "/manual-invoice/view/" + fileName).collect(Collectors.toList());

			Map<String, Object> responseData = new HashMap<>();
			responseData.put("invoice", invoice);
			responseData.put("fileDownloadUrls", fileUrls);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Retrieved Successfully", responseData));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to retrieve invoice: " + e.getMessage(), null));
		}
	}

	// Get all invoices
	@GetMapping("/getall")
	public ResponseEntity<RestAPIResponse> getAllInvoices(@RequestParam Long adminId) {
		try {
			return ResponseEntity
					.ok(new RestAPIResponse("Success", "All Invoices Retrieved", serviceImpl1.getAllInvoices(adminId)));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to retrieve invoices: " + e.getMessage(), null));
		}
	}

	// Search invoices with pagination
//    @GetMapping("/search")
//    public ResponseEntity<RestAPIResponse> searchInvoices(
//            @RequestParam(name = "search", required = false) String keyword,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "10") int size) {
//        try {
//            Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
//            Page<ManualInvoice> invoices = serviceImpl1.searchInvoices(keyword, pageable);
//            return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Retrieved", invoices));
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(new RestAPIResponse("Error", "Failed to search Invoices: " + e.getMessage(), null));
//        }
//    }

	// vasim/03/03
	@GetMapping("/searchAndSort")
	public ResponseEntity<RestAPIResponse> getManualInvoices(@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size,
			@RequestParam(defaultValue = "id") String sortField, @RequestParam(defaultValue = "asc") String sortDir,
			@RequestParam Long adminId) {

		try {

			Page<ManualInvoice> invoicePage = serviceImpl1.getAllInvoicesWithPaginationAndSearch(page, size, sortField,
					sortDir, keyword, adminId);

			Map<String, Object> response = new HashMap<>();
			response.put("invoices", invoicePage.getContent());
			response.put("currentPage", invoicePage.getNumber());
			response.put("totalItems", invoicePage.getTotalElements());
			response.put("totalPages", invoicePage.getTotalPages());
			response.put("sortField", sortField);
			response.put("sortDir", sortDir);
			response.put("keyword", keyword);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoices retrieved successfully", response));

		} catch (Exception e) {

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to fetch Invoices: " + e.getMessage(), null));
		}
	}

	@GetMapping("/count")
	public ResponseEntity<RestAPIResponse> getInvoiceCounts() {
		Map<String, Long> counts = serviceImpl1.getInvoiceCounts();
		return ResponseEntity.ok(new RestAPIResponse("success", "Invoice counts fetched", counts));
	}

	// ---------------- Today's overdue count ----------------
	@GetMapping("/today-overdue-count")
	public ResponseEntity<RestAPIResponse> getTodayOverdueCount() {
		Long count = serviceImpl1.getTodayOverdueCount();
		return ResponseEntity.ok(new RestAPIResponse("Success", "Today's overdue count fetched", count));
	}

	// ---------------- Today's overdue invoices for popup ----------------
	@GetMapping("/today-overdue-invoices")
	public ResponseEntity<RestAPIResponse> getTodayOverdueInvoices() {
		List<ManualInvoice> invoices = serviceImpl1.getTodayOverdueInvoices();
		return ResponseEntity.ok(new RestAPIResponse("Success", "Today's overdue invoices fetched", invoices));
	}

	@PutMapping("/update-status/{invoiceNumber}")
	public ResponseEntity<String> updateInvoiceStatus(@PathVariable String invoiceNumber,
			@RequestBody Map<String, String> payload) {

		String status = payload.get("status");
		ManualInvoice invoice = manualInvoiceRepository.findByInvoiceNumber(invoiceNumber)
				.orElseThrow(() -> new RuntimeException("Invoice not found: " + invoiceNumber));

		invoice.setStatus(status);
		invoice.setUpdatedAt(LocalDateTime.now());
		manualInvoiceRepository.save(invoice);

		return ResponseEntity.ok("Invoice " + invoiceNumber + " status updated to " + status);
	}

	// Update invoice
	@PutMapping("/{id}")
	public ResponseEntity<RestAPIResponse> updateInvoice(@PathVariable Long id, @RequestBody ManualInvoice invoice) {
		try {
			ManualInvoice updatedInvoice = serviceImpl1.updateInvoice(id, invoice);
			if (updatedInvoice == null) {
				return ResponseEntity.status(HttpStatus.NOT_FOUND)
						.body(new RestAPIResponse("Error", "Invoice not found", null));
			}
			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Updated Successfully", updatedInvoice));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to update invoice: " + e.getMessage(), null));
		}
	}

	@PutMapping("/update/{id}")
	public ResponseEntity<RestAPIResponse> updateManualInvoice(@PathVariable Long id,
			@RequestBody ManualInvoice invoice) {
		try {
			ManualInvoice updatedInvoice = serviceImpl1.updateManualInvoice(id, invoice);
			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice updated successfully", updatedInvoice));
		} catch (RuntimeException e) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new RestAPIResponse("Error", e.getMessage(), null));
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to update invoice: " + e.getMessage(), null));
		}
	}

	@PutMapping("/invoices/update-vendor")
	public ResponseEntity<Void> updateInvoicesByVendor(@RequestBody VendorDTO vendorDTO) {

		List<ManualInvoice> invoices = manualInvoiceRepository.findByCustomerVendorIdAndAdminId(vendorDTO.getVendorId(),
				vendorDTO.getAdminId());

		for (ManualInvoice invoice : invoices) {

			// Vendor snapshot update
			invoice.setCustomer(vendorDTO.getVendorName());
			invoice.setCustomerEmail(vendorDTO.getEmail());

			// Address snapshot update
			invoice.setBillingAddress(vendorDTO.getVendorAddress());
			invoice.setShippingAddress(vendorDTO.getVendorAddress());
		}

		manualInvoiceRepository.saveAll(invoices);

		return ResponseEntity.ok().build();
	}

	// Delete invoice
	@DeleteMapping("/{id}")
	public ResponseEntity<RestAPIResponse> deleteInvoice(@PathVariable Long id, @RequestParam Long adminId) {

		try {

			serviceImpl1.deleteInvoice(id, adminId);

			return ResponseEntity.ok(new RestAPIResponse("Success", "Invoice Deleted Successfully", null));

		} catch (Exception e) {

			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(new RestAPIResponse("Error", "Failed to delete invoice: " + e.getMessage(), null));
		}
	}

	@GetMapping("/consultant/{consultantId}/exists")
	public boolean hasInvoices(@PathVariable("consultantId") Long consultantId) {
		return manualInvoiceRepository.existsByConsultantId(consultantId);
	}

//	@GetMapping("/consultant/{consultantId}/exists")
//	public boolean hasInvoices(@PathVariable("consultantId") Long consultantId, @RequestParam Long adminId) {
//
//		return manualInvoiceRepository.existsByConsultantIdAndAdminId(consultantId, adminId);
//	}

	@PostMapping("/send-mail/{invoiceNumber}")
	public ResponseEntity<RestAPIResponse> sendInvoiceMail(@PathVariable String invoiceNumber,
			@RequestParam Long adminId) {

		serviceImpl1.sendInvoiceMail(invoiceNumber, adminId);

		return ResponseEntity.ok(new RestAPIResponse("success", "Invoice mail sent successfully", null));
	}
	//Bhargav 17-03-26
	@GetMapping("/consultant/{consultantId}")
	public ResponseEntity<?> getInvoicesByConsultant(@PathVariable Long consultantId) {

	    List<ManualInvoice> invoices = serviceImpl1.getInvoicesByConsultantId(consultantId);

	    return ResponseEntity.ok(
	            new RestAPIResponse("Success", "Invoices fetched successfully", invoices)
	    );
	}
	//Bhargav 17-03-26
	
	
	// Bhargav 20-03-26 
	@GetMapping("/pending-invoices/{adminId}")
	public ResponseEntity<RestAPIResponse> getPendingInvoices(@PathVariable Long adminId) {

	    List<ManualInvoice> invoices = serviceImpl1.getPendingInvoicesByAdmin(adminId);

	    return ResponseEntity.ok(
	            new RestAPIResponse("Success", "Pending & Partially Paid invoices fetched successfully", invoices)
	    );
	}
	
	
	@PostMapping("/pending-invoices/searchAndsorting")
	public ResponseEntity<RestAPIResponse> getPendingInvoices(
	        @RequestBody InvoiceSortingRequestDTO requestDTO) {

	    Page<ManualInvoice> invoices =
	            serviceImpl1.getPendingInvoicesByAdmin(requestDTO);

	    return ResponseEntity.ok(
	            new RestAPIResponse(
	                    "Success",
	                    "Pending & Partially received invoices fetched successfully",
	                    invoices.getContent()
	            )
	    );
	}
	// Bhargav 20-03-26 
	
	@PostMapping("/invoices/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoicesByAdminAndVendorType(
	        @RequestBody InvoiceSortingRequestDTO requestDTO) {

	    Page<ManualInvoice> invoices =
	            serviceImpl1.getInvoicesByAdminAndVendorType(requestDTO);

	    return ResponseEntity.ok(
	            new RestAPIResponse(
	                    "Success",
	                    "Invoices fetched successfully",
	                    invoices.getContent()
	            )
	    );
	}
	
	@PostMapping("/send-mails/{invoiceNumber}")
	public ResponseEntity<RestAPIResponse> sendInvoiceMails(
	        @PathVariable String invoiceNumber,
	        @RequestParam Long adminId) {

	    try {
	        serviceImpl1.sendInvoiceMails(invoiceNumber, adminId);

	        return ResponseEntity.ok(
	                new RestAPIResponse("success", "Invoice mail sent successfully", null)
	        );

	    } catch (RuntimeException e) {

	        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
	                .body(new RestAPIResponse("fail", e.getMessage(), null));
	    }
	}
	
	

	@PostMapping("/vendortype-receivable/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoiceByAdminAndVendorType(
	        @RequestBody InvoiceSortingRequestDTO requestDTO) {

	    Page<ManualInvoice> invoices =
	            serviceImpl1.getInvoiceByAdminAndVendorType(requestDTO);

	    return ResponseEntity.ok(
	            new RestAPIResponse(
	                    "Success",
	                    "Invoices fetched successfully",
	                    invoices.getContent()
	            )
	    );
	}
	
	
	@PostMapping("/vendortype-receivablestatus/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoiceByAdminAndVendorTypestatus(
	        @RequestBody InvoiceSortingRequestDTO requestDTO) {

	    Page<ManualInvoice> invoices =
	            serviceImpl1.getInvoiceByAdminAndVendorTypereceivablestatus(requestDTO);

	    return ResponseEntity.ok(
	            new RestAPIResponse(
	                    "Success",
	                    "Invoices fetched successfully",
	                    invoices.getContent()
	            )
	    );
	}
	
	
	
	@PostMapping("/invoicestatus/searchAndSorting")
	public ResponseEntity<RestAPIResponse> getInvoicesByAdminAndVendorTypestatus(
	        @RequestBody InvoiceSortingRequestDTO requestDTO) {

	    Page<ManualInvoice> invoices =
	            serviceImpl1.getInvoicesByAdminAndVendorTypestatusinvoicestatus(requestDTO);

	    return ResponseEntity.ok(
	            new RestAPIResponse(
	                    "Success",
	                    "Invoices fetched successfully",
	                    invoices.getContent()
	            )
	    );
	}
	
	
	@GetMapping("/status-count/{adminId}")
	public ResponseEntity<?> getInvoiceStatusCounts(@PathVariable Long adminId) {

		Map<String, Object> data = serviceImpl1.getInvoiceStatusCounts(adminId);

		Map<String, Object> response = new LinkedHashMap<>();

		response.put("message", "Invoice status counts fetched successfully");
		response.put("status", "success");
		response.put("data", data);

		return ResponseEntity.ok(response);
	}
	
	
}
