package com.invoice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import com.invoice.DTO.ConsultantDTO;

@FeignClient(name = "consultant-service", url = "http://localhost:5679")
public interface ConsultantFeignClient {

	@GetMapping("/con/{id}")
	ConsultantDTO getConsultant(@PathVariable("id") Long id);
}
