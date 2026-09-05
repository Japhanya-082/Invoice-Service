package com.invoice.security;

import com.invoice.tenant.TenantContext;
import com.invoice.tenant.TenantNotResolvedException;
import com.invoice.tenant.TenantRoutingDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * G-57: the router must fail closed. A request that authenticated as a tenant
 * user but carries no resolvable schema must be refused, not served from the
 * shared default pool — this service's {@code invoice} table has no
 * {@code admin_id} column, so the router is its entire tenant boundary.
 *
 * <p>Unit-level on purpose: {@code determineTargetDataSource} is the exact point
 * that used to read {@code if (tenant == null) return default}. Driving it
 * directly needs no database, and the marker is set the same way the filter sets
 * it.
 */
class TenantRoutingFailClosedTest {

	private final DataSource defaultDs = new org.springframework.jdbc.datasource.SimpleDriverDataSource();

	private TenantRoutingDataSource router(boolean requireTenant) {
		TenantRoutingDataSource r = new TenantRoutingDataSource(
				"jdbc:postgresql://localhost:5432/db", "u", "p", "com.invoice.entity", requireTenant);
		r.setDefaultTargetDataSource(defaultDs);
		r.setTargetDataSources(new java.util.HashMap<>());
		r.afterPropertiesSet();
		return r;
	}

	private DataSource resolve(TenantRoutingDataSource r) {
		return (DataSource) ReflectionTestUtils.invokeMethod(r, "determineTargetDataSource");
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	@DisplayName("a tenant user with no resolvable schema is refused, not served the default")
	void unresolvedTenantIsRefused() {
		TenantContext.setCurrentAdminId(42L);
		TenantContext.markTenantRequiredButUnresolved();
		assertThrows(TenantNotResolvedException.class, () -> resolve(router(true)));
	}

	@Test
	@DisplayName("the refusal does not disclose how tenancy is resolved")
	void refusalDoesNotLeakMechanism() {
		TenantContext.setCurrentAdminId(42L);
		TenantContext.markTenantRequiredButUnresolved();
		TenantNotResolvedException ex = assertThrows(TenantNotResolvedException.class, () -> resolve(router(true)));
		String m = ex.getMessage().toLowerCase();
		assertFalse(m.contains("schema"));
		assertFalse(m.contains("companydomain"));
	}

	@Test
	@DisplayName("startup / health / internal calls (no tenant, unmarked) still reach the default")
	void unmarkedNoTenantReachesDefault() {
		// Nothing marked — the case of dialect detection at startup, a health
		// probe, or an internal call with no tenant. Must NOT be refused.
		assertSame(defaultDs, resolve(router(true)));
	}

	@Test
	@DisplayName("a resolved tenant takes the per-tenant pool path, never the default")
	void resolvedTenantIsRouted() {
		// A set tenant must go down buildTenantDataSource, not fall back to the
		// default. Building the pool eagerly connects, and there is no database
		// here, so the tell that the tenant branch was taken is that it throws a
		// pool-initialisation error rather than quietly returning defaultDs.
		TenantContext.setCurrentTenant("acme");
		TenantRoutingDataSource r = router(true);
		try {
			DataSource ds = resolve(r);
			// If a pool ever initialises without a DB, at least prove it is not the default.
			assertNotSame(defaultDs, ds);
		} catch (Exception e) {
			// Expected without a DB: it attempted a tenant pool, i.e. did not fail open.
			assertFalse(e instanceof TenantNotResolvedException,
					"a resolved tenant must not be treated as unresolved");
		}
	}

	@Test
	@DisplayName("fail-open mode serves the unresolved request from the default, with a warning")
	void failOpenModeServesDefault() {
		TenantContext.setCurrentAdminId(42L);
		TenantContext.markTenantRequiredButUnresolved();
		assertSame(defaultDs, resolve(router(false)));
	}
}
