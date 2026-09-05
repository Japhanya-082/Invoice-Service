package com.invoice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * RUNTIME authorization verification for N-07 — the full Spring Security chain,
 * not source inspection.
 *
 * Proves `@PreAuthorize("hasRole('INTERNAL')")` is actually enforced: without
 * `@EnableMethodSecurity` the annotation is a silent no-op and every assertion
 * below would fail.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SchemaProvisioningRuntimeAuthzTest {

    private static final String PATH = "/internal/provision-schema/acme_corp";
    private static final String SECRET = "test-only-do-not-use-in-prod-test-only-do-not-use-in-prod-1234";
    private static final String INTERNAL_KEY_PROPERTY_ABSENT = "any-value";

    @Autowired
    private MockMvc mockMvc;

    // companyDomain is present so the token is a realistic, resolvable tenant
    // token: without it the G-57 fail-closed filter refuses with 503 before
    // authorization runs, and these tests are about the 403 authz refusal.
    private static String userToken(List<String> roles) {
        return Jwts.builder()
                .setClaims(Map.of("adminId", 42, "companyDomain", "acme", "roles", roles))
                .setSubject("user@example.com")
                .setIssuer("invoice-login")
                .setAudience("invoice-platform")
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    @DisplayName("unauthenticated POST is rejected")
    void anonymousRejected() throws Exception {
        int s = mockMvc.perform(post(PATH)).andReturn().getResponse().getStatus();
        // Spring answers 403 rather than 401 for an anonymous principal when no
        // AuthenticationEntryPoint is configured. Both are rejections; what matters
        // is that the DDL endpoint is not reachable without credentials.
        assertTrue(s == 401 || s == 403, "expected 401/403 but got " + s);
    }

    @Test
    @DisplayName("an ordinary authenticated user is rejected (403) — a tenant JWT is not INTERNAL")
    void ordinaryUserForbidden() throws Exception {
        int s = mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer " + userToken(List.of("USER"))))
                .andReturn().getResponse().getStatus();
        assertEquals(403, s,
                "authenticated is not authorized: a user JWT must not substitute for ROLE_INTERNAL");
    }

    @Test
    @DisplayName("even an ADMIN tenant JWT cannot provision schemas")
    void adminUserForbidden() throws Exception {
        int s = mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer " + userToken(List.of("ADMIN"))))
                .andReturn().getResponse().getStatus();
        assertEquals(403, s, "ROLE_ADMIN is a tenant role, not an internal-service role");
    }

    @Test
    @DisplayName("a wrong internal API key is rejected")
    void wrongInternalKeyRejected() throws Exception {
        int s = mockMvc.perform(post(PATH)
                        .header("X-Internal-Api-Key", "definitely-not-the-key"))
                .andReturn().getResponse().getStatus();
        assertTrue(s == 401 || s == 403, "expected 401/403 but got " + s);
    }

    @Test
    @DisplayName("an unsigned alg=none token cannot reach the endpoint")
    void algNoneRejected() throws Exception {
        String unsigned = Jwts.builder()
                .setClaims(Map.of("adminId", 1, "roles", List.of("INTERNAL")))
                .setSubject("attacker")
                .setIssuer("invoice-login")
                .setAudience("invoice-platform")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();
        int s = mockMvc.perform(post(PATH).header("Authorization", "Bearer " + unsigned))
                .andReturn().getResponse().getStatus();
        assertTrue(s == 401 || s == 403, "expected 401/403 but got " + s);
    }

    @Test
    @DisplayName("a forged ROLE_INTERNAL claim in a tenant JWT does not grant internal access")
    void forgedInternalRoleClaimRejected() throws Exception {
        // Signed with the correct key, but claiming INTERNAL. The internal role is
        // granted only by the X-Internal-Api-Key path, never from a token claim.
        int s = mockMvc.perform(post(PATH)
                        .header("Authorization", "Bearer " + userToken(List.of("INTERNAL"))))
                .andReturn().getResponse().getStatus();
        assertEquals(403, s,
                "a role claim in a tenant token must not confer internal-service authority");
    }
}
