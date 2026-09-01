package com.invoice.tenant;

/**
 * Per-request tenant boundary state. Two distinct things live here:
 * <p>
 * 1) the database schema name routed by {@code TenantRoutingDataSource},
 * and<br>
 * 2) the authoritative {@code adminId} that scopes every business query.
 * <p>
 * Both are populated by {@code TenantFilter} from the validated JWT. Code paths
 * that need the tenant must read these — never trust an admin id pulled from a
 * request body, path variable, query string, or HTTP header.
 */
public class TenantContext {

	private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
	private static final ThreadLocal<Long> CURRENT_ADMIN_ID = new ThreadLocal<>();

	public static void setCurrentTenant(String schema) {
		CURRENT_TENANT.set(schema);
	}

	public static String getCurrentTenant() {
		return CURRENT_TENANT.get();
	}

	public static void setCurrentAdminId(Long adminId) {
		CURRENT_ADMIN_ID.set(adminId);
	}

	public static Long getCurrentAdminId() {
		return CURRENT_ADMIN_ID.get();
	}

	public static void clear() {
		CURRENT_TENANT.remove();
		CURRENT_ADMIN_ID.remove();
	}

	public static String toSchemaName(String companyDomain) {
		if (companyDomain == null)
			return null;
		return companyDomain.toLowerCase().replaceAll("[^a-z0-9]", "_");
	}
}
