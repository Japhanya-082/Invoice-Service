package com.invoice.security;

import com.invoice.tenant.TenantContext;
import com.invoice.tenant.TenantFilter;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial tests for the JWT trust boundary.
 *
 * Context: the platform's HS256 signing secret was published for ~11 months and is
 * shared by six services, so tenant and role authorization was only ever as strong
 * as that key. These tests pin the validation behaviour the remediation added, so a
 * future change cannot quietly weaken it again.
 *
 * Every case asserts a REJECTION except the first.
 */
class JwtSecurityHardeningTest {

    private static final String SECRET =
            "unit-test-signing-key-unit-test-signing-key-unit-test-1234567890";
    private static final String ATTACKER_SECRET =
            "attacker-controlled-key-attacker-controlled-key-attacker-12345678";
    private static final String ISSUER = "invoice-login";
    private static final String AUDIENCE = "invoice-platform";

    private TenantFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TenantFilter();
        ReflectionTestUtils.setField(filter, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(filter, "jwtIssuer", ISSUER);
        ReflectionTestUtils.setField(filter, "jwtAudience", AUDIENCE);
        ReflectionTestUtils.setField(filter, "jwtClockSkewSeconds", 30L);
        ReflectionTestUtils.setField(filter, "internalApiKey", "");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- helpers

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static String token(String secret, String iss, String aud, long ttlMillis) {
        return Jwts.builder()
                .setClaims(Map.of("adminId", 42, "roles", List.of("USER")))
                .setSubject("user@example.com")
                .setIssuer(iss)
                .setAudience(aud)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + ttlMillis))
                .signWith(key(secret), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * TenantFilter clears the SecurityContext in a finally block after the chain
     * runs, so authentication has to be observed from inside the chain.
     */
    private static final class Captor extends MockFilterChain {
        Object authentication;
        boolean reached;
        @Override
        public void doFilter(jakarta.servlet.ServletRequest rq, jakarta.servlet.ServletResponse rs) {
            reached = true;
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }
    }

    private Captor run(String token) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/manual-invoice/getall");
        if (token != null) {
            req.addHeader("Authorization", "Bearer " + token);
        }
        lastResponse = new MockHttpServletResponse();
        Captor captor = new Captor();
        filter.doFilter(req, lastResponse, captor);
        return captor;
    }

    private MockHttpServletResponse lastResponse;

    /**
     * The trust boundary is "no authentication is established". TenantFilter returns
     * 401 directly only for a tampered signature; for every other invalid token it
     * passes through WITHOUT authenticating, and Spring Security's
     * .anyRequest().authenticated() rejects it. Both outcomes are a rejection —
     * what must never happen is an authenticated context.
     */
    private void assertNotAuthenticated(String token) throws Exception {
        Captor c = run(token);
        if (lastResponse.getStatus() == 401) {
            return;                       // rejected outright
        }
        assertTrue(c.reached, "filter neither rejected nor forwarded the request");
        assertNull(c.authentication,
                "an invalid token must not establish an authentication");
    }

    // ------------------------------------------------------------- happy path

    @Test
    @DisplayName("a correctly issued token is accepted")
    void validTokenAccepted() throws Exception {
        Captor c = run(token(SECRET, ISSUER, AUDIENCE, 60_000));
        assertEquals(200, lastResponse.getStatus());
        assertNotNull(c.authentication, "a valid token must establish an authentication");
    }

    // -------------------------------------------------- claim-validation cases

    @Test
    @DisplayName("a token from a different issuer is rejected")
    void wrongIssuerRejected() throws Exception {
        assertNotAuthenticated(token(SECRET, "some-other-issuer", AUDIENCE, 60_000));
    }

    @Test
    @DisplayName("a token for a different audience is rejected")
    void wrongAudienceRejected() throws Exception {
        assertNotAuthenticated(token(SECRET, ISSUER, "some-other-audience", 60_000));
    }

    @Test
    @DisplayName("a token carrying no issuer claim is rejected")
    void missingIssuerRejected() throws Exception {
        String t = Jwts.builder()
                .setClaims(Map.of("adminId", 42))
                .setSubject("user@example.com")
                .setAudience(AUDIENCE)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(SECRET), SignatureAlgorithm.HS256)
                .compact();
        assertNotAuthenticated(t);
    }

    @Test
    @DisplayName("a token carrying no audience claim is rejected")
    void missingAudienceRejected() throws Exception {
        String t = Jwts.builder()
                .setClaims(Map.of("adminId", 42))
                .setSubject("user@example.com")
                .setIssuer(ISSUER)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key(SECRET), SignatureAlgorithm.HS256)
                .compact();
        assertNotAuthenticated(t);
    }

    // ------------------------------------------------------- signature cases

    @Test
    @DisplayName("a token signed with a different key is rejected (this is what rotation buys)")
    void foreignKeyRejected() throws Exception {
        assertNotAuthenticated(token(ATTACKER_SECRET, ISSUER, AUDIENCE, 60_000));
    }

    @Test
    @DisplayName("a tampered payload is rejected")
    void tamperedTokenRejected() throws Exception {
        String good = token(SECRET, ISSUER, AUDIENCE, 60_000);
        String[] parts = good.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String tampered = payload.replace("\"adminId\":42", "\"adminId\":99");
        parts[1] = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(tampered.getBytes(StandardCharsets.UTF_8));
        assertNotAuthenticated(String.join(".", parts));
    }

    @Test
    @DisplayName("an unsigned alg=none token is rejected")
    void algNoneRejected() throws Exception {
        String unsigned = Jwts.builder()
                .setClaims(Map.of("adminId", 42, "roles", List.of("ADMIN")))
                .setSubject("attacker@example.com")
                .setIssuer(ISSUER)
                .setAudience(AUDIENCE)
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .compact();                      // no signWith -> alg=none
        assertNotAuthenticated(unsigned);
    }

    @Test
    @DisplayName("an expired token is rejected beyond the allowed clock skew")
    void expiredTokenRejected() throws Exception {
        assertNotAuthenticated(token(SECRET, ISSUER, AUDIENCE, -120_000));
    }

    @Test
    @DisplayName("a garbage bearer value is rejected")
    void malformedTokenRejected() throws Exception {
        assertNotAuthenticated("not-a-jwt");
    }

    // --------------------------------------------------- fail-closed startup

    @Test
    @DisplayName("startup fails when jwt.issuer is blank — a blank value silently disables validation")
    void blankIssuerRefusesToStart() {
        TenantFilter f = new TenantFilter();
        ReflectionTestUtils.setField(f, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(f, "jwtIssuer", "");
        ReflectionTestUtils.setField(f, "jwtAudience", AUDIENCE);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(f, "assertClaimValidationIsConfigured"));
        assertTrue(e.getMessage().contains("jwt.issuer"));
    }

    @Test
    @DisplayName("startup fails when jwt.audience is blank")
    void blankAudienceRefusesToStart() {
        TenantFilter f = new TenantFilter();
        ReflectionTestUtils.setField(f, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(f, "jwtIssuer", ISSUER);
        ReflectionTestUtils.setField(f, "jwtAudience", "  ");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(f, "assertClaimValidationIsConfigured"));
        assertTrue(e.getMessage().contains("jwt.audience"));
    }

    @Test
    @DisplayName("startup fails on a signing key shorter than 256 bits")
    void weakSigningKeyRefusesToStart() {
        TenantFilter f = new TenantFilter();
        ReflectionTestUtils.setField(f, "jwtSecret", "too-short");
        ReflectionTestUtils.setField(f, "jwtIssuer", ISSUER);
        ReflectionTestUtils.setField(f, "jwtAudience", AUDIENCE);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ReflectionTestUtils.invokeMethod(f, "assertClaimValidationIsConfigured"));
        assertTrue(e.getMessage().contains("32 bytes"));
    }
}
