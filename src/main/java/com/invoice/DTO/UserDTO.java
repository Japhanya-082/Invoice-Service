package com.invoice.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserDTO {
    private String email;
    private String fullName;
    private String mobileNumber;
    private String companyName;
    private String organizationName;
    private String roleName;   
}
