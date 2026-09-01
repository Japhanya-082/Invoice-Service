package com.invoice.serviceImpl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.invoice.DTO.VendorDTO;
import com.invoice.client.VendorFeignClient;
import com.invoice.service.VendorClientService;

@Service
public class VendorClientServiceImpl implements VendorClientService {

	@Autowired
	private VendorFeignClient vendorFeignClient;

	@Override
	public List<VendorDTO> fetchVendorByName(String name) {
		return vendorFeignClient.searchVendors(name);
	}
}
