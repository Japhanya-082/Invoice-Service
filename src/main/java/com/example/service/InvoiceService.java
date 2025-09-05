package com.example.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.example.entity.Invoice;
import com.example.exception.FileStorageException;

public interface InvoiceService {
   public List<Invoice> uploadAndSaveInvoices(MultipartFile multipartFile) throws FileStorageException;
  public List<Invoice> getAll();
}
