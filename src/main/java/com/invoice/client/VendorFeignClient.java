package com.invoice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.invoice.DTO.VendorDTO;

@FeignClient(name = "customer-service", url = "${CUSTOMER_SERVICE_URL:http://customer:5679}")
public interface VendorFeignClient {

	
	@GetMapping("/vendor/by-name")
	List<VendorDTO> searchVendors(@RequestParam("name") String name);

	
	@GetMapping("/vendor/{vendorId}")
	VendorDTO getVendorById(@PathVariable("vendorId") Long vendorId);
}
