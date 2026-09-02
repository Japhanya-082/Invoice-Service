package com.invoice.security;

import com.invoice.controller.SchemaProvisioningController;
import com.invoice.tenant.TenantContext;
import com.invoice.tenant.TenantFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * N-07 regression tests for Invoice-Service.
 *
 * Mirrors the Customer-Service suite. POST /internal/provision-schema/{schemaName}
 * concatenates its path segment into DDL that cannot be parameterised, and was
 * reachable anonymously: permitAll() plus a TenantFilter exemption that returned
 * before the internal-API-key check could run.
 */
class SchemaProvisioningSecurityTest {

    private static final String PROVISION_PATH = "/internal/provision-schema/acme_corp";
    private static final String INTERNAL_KEY = "unit-test-internal-key-unit-test-internal-key-123456";

    private TenantFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret",
                "unit-test-signing-key-unit-test-signing-key-unit-test-1234567890");
        ReflectionTestUtils.setField(filter, "jwtIssuer", "invoice-login");
        ReflectionTestUtils.setField(filter, "jwtAudience", "invoice-platform");
        ReflectionTestUtils.setField(filter, "jwtClockSkewSeconds", 30L);
        ReflectionTestUtils.setField(filter, "internalApiKey", INTERNAL_KEY);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static final class Captor extends MockFilterChain {
        Authentication authentication;
        @Override
        public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
    }

    private Captor call(String apiKey) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", PROVISION_PATH);
        if (apiKey != null) req.addHeader("X-Internal-Api-Key", apiKey);
        Captor c = new Captor();
        filter.doFilter(req, new MockHttpServletResponse(), c);
        return c;
    }

    // ------------------------------------------------------------ authentication

    @Test
    @DisplayName("the provisioning path is NOT exempt from the tenant filter")
    void pathNotExempt() {
        assertEquals(Boolean.FALSE,
                ReflectionTestUtils.invokeMethod(filter, "isExempt", PROVISION_PATH));
    }

    @Test
    @DisplayName("anonymous call establishes no authentication")
    void anonymousNotAuthenticated() throws Exception {
        assertNull(call(null).authentication);
    }

    @Test
    @DisplayName("wrong internal key establishes no authentication")
    void wrongKeyNotAuthenticated() throws Exception {
        assertNull(call("wrong-key").authentication);
    }

    @Test
    @DisplayName("correct internal key authenticates as ROLE_INTERNAL without X-Admin-Id")
    void correctKeyAuthenticates() throws Exception {
        Authentication a = call(INTERNAL_KEY).authentication;
        assertNotNull(a);
        assertTrue(a.getAuthorities().stream()
                .anyMatch(x -> "ROLE_INTERNAL".equals(x.getAuthority())));
    }

    // ------------------------------------------------------- identifier validation

    @Test
    @DisplayName("a valid identifier passes validation and reaches the datasource")
    void validIdentifierAccepted() throws Exception {
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new java.sql.SQLException("no database in this test"));
        ResponseEntity<String> res = new SchemaProvisioningController(ds).provisionSchema("acme_corp");
        assertEquals(500, res.getStatusCode().value(), "validation passed; the stub then failed");
        verify(ds).getConnection();
    }

    @ParameterizedTest
    @DisplayName("uppercase, bad characters, over-length and injection payloads are rejected before JDBC")
    @ValueSource(strings = {
            "ACME",                                   // uppercase
            "Acme_Corp",                              // mixed case
            "acme-corp",                              // hyphen
            "acme corp",                              // whitespace
            "1acme",                                  // leading digit
            "_acme",                                  // leading underscore
            "acme$",                                  // dollar
            "acmé",                                   // non-ASCII
            "a\"b",                                   // quote — the injection primitive
            "evil\"; DROP SCHEMA invoice CASCADE; --",
            "acme; DROP TABLE x",
            "acme'--",
            "",                                       // empty
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", // 65 > NAMEDATALEN-1
    })
    void invalidIdentifierRejectedWithoutTouchingDatabase(String payload) {
        DataSource ds = mock(DataSource.class);
        ResponseEntity<String> res = new SchemaProvisioningController(ds).provisionSchema(payload);
        assertEquals(400, res.getStatusCode().value(), "should be rejected: " + payload);
        verifyNoInteractions(ds);
    }

    @Test
    @DisplayName("exactly 63 characters is accepted — PostgreSQL NAMEDATALEN-1")
    void maxLengthIdentifierAccepted() throws Exception {
        String name = "a".repeat(63);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new java.sql.SQLException("no database in this test"));
        assertEquals(500, new SchemaProvisioningController(ds).provisionSchema(name)
                .getStatusCode().value());
        verify(ds).getConnection();
    }

    @ParameterizedTest
    @DisplayName("reserved and pg_-prefixed names are refused before JDBC")
    @ValueSource(strings = {
            "invoice", "public", "information_schema", "pg_catalog", "pg_toast",
            "pg_secret", "pg_", "pg_anything",
    })
    void reservedNamesRefused(String reserved) {
        DataSource ds = mock(DataSource.class);
        ResponseEntity<String> res = new SchemaProvisioningController(ds).provisionSchema(reserved);
        assertEquals(400, res.getStatusCode().value(), "should be refused: " + reserved);
        verifyNoInteractions(ds);
    }
}
