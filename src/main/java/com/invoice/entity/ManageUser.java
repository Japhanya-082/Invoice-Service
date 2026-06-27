package com.invoice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "manage_users", schema = "invoice")
public class ManageUser {

    @Id
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "primary_email")
    private String primaryEmail;

    @Column(name = "role_name")
    private String roleName;

    @Column(name = "active")
    private Boolean active;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "address")
    private String address;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public String getPrimaryEmail() { return primaryEmail; }
    public String getRoleName() { return roleName; }
    public Boolean getActive() { return active; }
    public String getFullName() { return fullName; }
    public String getCompanyName() { return companyName; }
    public String getAddress() { return address; }
    public String getCity() { return city; }
    public String getState() { return state; }

    public String getFormattedAddress() {
        StringBuilder sb = new StringBuilder();
        if (address != null && !address.isBlank()) sb.append(address);
        if (city != null && !city.isBlank()) { if (sb.length() > 0) sb.append(", "); sb.append(city); }
        if (state != null && !state.isBlank()) { if (sb.length() > 0) sb.append(", "); sb.append(state); }
        return sb.toString();
    }
}
