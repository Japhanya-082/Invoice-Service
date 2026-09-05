package com.invoice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.invoice.DTO.VendorDTO;
import com.invoice.DTO.VendorEnvelope;

@FeignClient(name = "customer-service", url = "${CUSTOMER_SERVICE_URL:http://customer:5679}")
public interface VendorFeignClient {

	
	@GetMapping("/vendor/by-name")
	List<VendorDTO> searchVendors(@RequestParam("name") String name);

	
	/**
	 * Tenant-scoped in Customer-Service (`findByVendorIdAndAdminId`), answering
	 * 404 for another tenant's id exactly as for one that does not exist. The
	 * envelope is not optional — see {@link VendorEnvelope}.
	 */
	@GetMapping("/vendor/{vendorId}")
	VendorEnvelope getVendorById(@PathVariable("vendorId") Long vendorId);
}
