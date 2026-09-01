package com.invoice.service;

import java.util.List;

import com.invoice.DTO.VendorDTO;

public interface VendorClientService {
	
	public List<VendorDTO> fetchVendorByName(String name);

}
