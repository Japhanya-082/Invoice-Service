//package com.example.serviceImpl;
//
//import java.util.List;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import com.example.entity.InvoiceItem;
//import com.example.entity.ManualInvoice;
//import com.example.repository.ManualInvoiceRepository;
//import com.example.service.ManualInvoiceService;
//
//import jakarta.transaction.Transactional;
//
//@Service
//public class ManualInvoiceServiceImpl implements ManualInvoiceService {
//
//    @Autowired
//    private ManualInvoiceRepository manualInvoiceRepository;
//
//    @Override
//    @Transactional
//    public ManualInvoice createInvoice(ManualInvoice manualInvoice) {
//        double subtotal = 0.0;
//        double taxTotal = 0.0;
//
//        if (manualInvoice.getItems() != null) {
//            for (InvoiceItem item : manualInvoice.getItems()) {
//                double itemTotal = item.getQuantity() * item.getPrice();
//                item.setTotal(itemTotal);
//                item.setManualInvoice(manualInvoice); // ✅ only set reference here
//                subtotal += itemTotal;
//                taxTotal += (item.getTax() != null ? item.getTax() : 0.0);
//            }
//        }
//
//        manualInvoice.setSubtotal(subtotal);
//        manualInvoice.setTax(taxTotal);
//        manualInvoice.setTotal(subtotal + taxTotal);
//        manualInvoice.setCredit(manualInvoice.getCredit() != null ? manualInvoice.getCredit() : 0.0);
//        manualInvoice.setAmountDue(manualInvoice.getTotal() - manualInvoice.getCredit());
//
//        return manualInvoiceRepository.save(manualInvoice);
//    }
//
//    @Override
//    public ManualInvoice getInvoiceById(Long id) {
//        return manualInvoiceRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Invoice not found with id " + id));
//    }
//
//    @Override
//    public List<ManualInvoice> getAllInvoices() {
//        return manualInvoiceRepository.findAll();
//    }
//
//    @Override
//    public void deleteInvoice(Long id) {
//        if (!manualInvoiceRepository.existsById(id)) {
//            throw new RuntimeException("Invoice not found with id " + id);
//        }
//        manualInvoiceRepository.deleteById(id);
//    }
//}
