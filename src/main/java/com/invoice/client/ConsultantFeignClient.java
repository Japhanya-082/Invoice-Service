package com.invoice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.invoice.DTO.ConsultantDTO;

@FeignClient(name = "consultant-service", url = "${CUSTOMER_SERVICE_URL:http://customer:5679}")
public interface ConsultantFeignClient {

	/**
	 * The summary, not the detail. This service reads id, names, email and
	 * adminId; the detail also carried the SSN, date of birth and visa dates
	 * across the network on every invoice save. Needs Customer-Service with
	 * {@code GET /con/{id}/summary} (PR #58) deployed first.
	 */
	@GetMapping("/con/{id}/summary")
	ConsultantDTO getConsultant(@PathVariable("id") Long id);
}
