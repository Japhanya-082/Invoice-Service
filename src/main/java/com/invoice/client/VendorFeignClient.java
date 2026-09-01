package com.invoice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.invoice.DTO.VendorDTO;

@FeignClient(name = "CUSTOMER-SERVICE")
public interface VendorFeignClient {

	
	@GetMapping("/vendor/by-name")
	List<VendorDTO> searchVendors(@RequestParam("name") String name);

	
	@GetMapping("/vendor/{vendorId}")
	VendorDTO getVendorById(@PathVariable("vendorId") Long vendorId);
}
