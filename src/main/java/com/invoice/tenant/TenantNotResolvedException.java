package com.invoice.tenant;

/**
 * Thrown when a request needs a tenant schema and none could be established.
 *
 * <p>Its whole purpose is to be louder than the alternative. The previous
 * behaviour (G-57) was to fall back to the default datasource silently, which is
 * how unrelated tenants could come to share one schema — and this service's
 * {@code invoice} table has no {@code admin_id} column, so the router is its
 * entire boundary.
 */
public class TenantNotResolvedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TenantNotResolvedException(String message) {
		super(message);
	}
}
