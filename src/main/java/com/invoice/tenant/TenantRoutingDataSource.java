package com.invoice.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class TenantRoutingDataSource extends AbstractRoutingDataSource {

	/** Maximum number of tenant DataSources kept in memory. */
	private static final int MAX_TENANT_DATASOURCES = 100;

	private final String baseJdbcUrl;
	private final String username;
	private final String password;
	private final String entityPackage;
	/** When true (the default), a request whose tenant cannot be resolved is refused rather than served from the default pool. */
	private final boolean requireTenant;
	private final ConcurrentHashMap<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

	public TenantRoutingDataSource(String baseJdbcUrl, String username, String password, String entityPackage) {
		this(baseJdbcUrl, username, password, entityPackage, true);
	}

	public TenantRoutingDataSource(String baseJdbcUrl, String username, String password, String entityPackage,
			boolean requireTenant) {
		this.baseJdbcUrl = baseJdbcUrl;
		this.username = username;
		this.password = password;
		this.entityPackage = entityPackage;
		this.requireTenant = requireTenant;
	}

	
	@Override
	protected Object determineCurrentLookupKey() {
		return TenantContext.getCurrentTenant();
	}

	
	/**
	 * Resolves the datasource for the current request, refusing rather than
	 * guessing.
	 *
	 * <p>This method used to read {@code if (tenant == null) return
	 * getResolvedDefaultDataSource();} — failing <em>open</em> (G-57). A token
	 * with no {@code companyDomain} claim left the tenant unresolved, so every
	 * such request was served from the shared {@code invoice} pool; and because
	 * the {@code invoice} table has no {@code admin_id} column, {@code findAll()}
	 * then returned every tenant's rows with nothing in the logs.
	 *
	 * <p>The distinction that makes failing closed possible is
	 * {@link TenantContext#isTenantRequiredButUnresolved()}, set only by the
	 * filter and only for a request that authenticated as a tenant user without a
	 * resolvable schema. Everything else — startup dialect detection, health
	 * probes, internal service calls with no tenant — is unmarked and still
	 * reaches the default datasource.
	 */
	@Override
	protected DataSource determineTargetDataSource() {
		String tenant = TenantContext.getCurrentTenant();

		if (tenant == null) {
			if (requireTenant && TenantContext.isTenantRequiredButUnresolved()) {
				log.error("Refusing a request with no resolvable tenant (adminId={}). The token "
						+ "carries no companyDomain claim, so no schema can be selected; serving it "
						+ "from the default datasource would expose other tenants' invoices.",
						TenantContext.getCurrentAdminId());
				throw new TenantNotResolvedException("No tenant is associated with this request.");
			}
			if (TenantContext.isTenantRequiredButUnresolved()) {
				log.warn("Serving a request with no resolvable tenant (adminId={}) from the DEFAULT "
						+ "datasource because tenant.require-tenant=false. Other tenants' invoices are "
						+ "reachable in this mode.", TenantContext.getCurrentAdminId());
			}
			return getResolvedDefaultDataSource();
		}
		if (!tenantDataSources.containsKey(tenant) && tenantDataSources.size() >= MAX_TENANT_DATASOURCES) {
			log.warn(
					"Tenant DataSource cache has reached the maximum size of {}. "
							+ "Tenant '{}' will not be cached — consider eviction or increasing the limit.",
					MAX_TENANT_DATASOURCES, tenant);
			return buildTenantDataSource(tenant);
		}
		return tenantDataSources.computeIfAbsent(tenant, this::buildTenantDataSource);
	}

	
	private DataSource buildTenantDataSource(String schemaName) {
		log.info("Initialising DataSource for tenant schema: {}", schemaName);
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(buildUrl(schemaName));
		config.setUsername(username);
		config.setPassword(password);
		config.setMaximumPoolSize(5);
		config.setMinimumIdle(1);
		config.setPoolName("TenantPool-invoice-" + schemaName);
		HikariDataSource ds = new HikariDataSource(config);
		initSchemaEntities(ds, schemaName);
		return ds;
	}

	private void initSchemaEntities(DataSource ds, String schemaName) {
		try {
			LocalContainerEntityManagerFactoryBean emfBean = new LocalContainerEntityManagerFactoryBean();
			emfBean.setDataSource(ds);
			emfBean.setPackagesToScan(entityPackage);

			HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
			adapter.setGenerateDdl(true);
			emfBean.setJpaVendorAdapter(adapter);

			Properties props = new Properties();
			props.setProperty("hibernate.hbm2ddl.auto", "update");
			props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
			props.setProperty("hibernate.show_sql", "false");
			emfBean.setJpaProperties(props);

			emfBean.afterPropertiesSet();
			if (emfBean.getObject() != null)
				emfBean.getObject().close();

			log.info("Invoice-Service tables initialised in schema: {}", schemaName);
		} catch (Exception e) {
			log.error("Failed to initialise invoice tables in schema {}: {}", schemaName, e.getMessage());
		}
	}

	private String buildUrl(String schemaName) {
		// Same driver-awareness as TenantDataSourceConfig.withSchema -- appending
		// a PostgreSQL parameter to a non-PostgreSQL URL corrupts it.
		return com.invoice.config.TenantDataSourceConfig.withSchema(baseJdbcUrl, schemaName);
	}
}
