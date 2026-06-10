package com.invoice.security;

import com.invoice.tenant.SecurityUtils;
import com.invoice.tenant.TenantContext;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SecurityUtilsTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void throwsWhenNoAuthOnThread() {
        assertThrows(SecurityUtils.SecurityIntegrityException.class,
                SecurityUtils::getCurrentAdminId);
    }

    @Test
    void readsFromTenantContext() {
        TenantContext.setCurrentAdminId(42L);
        assertEquals(42L, SecurityUtils.getCurrentAdminId());
    }

    @Test
    void readsFromSecurityContextDetailsWhenTenantContextEmpty() {
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken("user@example.com", null, List.of());
        auth.setDetails(99L);
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertEquals(99L, SecurityUtils.getCurrentAdminId());
    }

    @Test
    void rejectsCrossTenantResource() {
        TenantContext.setCurrentAdminId(7L);
        assertThrows(SecurityUtils.SecurityIntegrityException.class,
                () -> SecurityUtils.assertOwnedByCurrentTenant(8L));
    }

    @Test
    void acceptsSameTenantResource() {
        TenantContext.setCurrentAdminId(7L);
        assertDoesNotThrow(() -> SecurityUtils.assertOwnedByCurrentTenant(7L));
    }
}
