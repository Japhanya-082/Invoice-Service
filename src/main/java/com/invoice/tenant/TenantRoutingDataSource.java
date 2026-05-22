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
    private final ConcurrentHashMap<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

    public TenantRoutingDataSource(String baseJdbcUrl, String username, String password, String entityPackage) {
        this.baseJdbcUrl = baseJdbcUrl;
        this.username = username;
        this.password = password;
        this.entityPackage = entityPackage;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            return getResolvedDefaultDataSource();
        }
        if (!tenantDataSources.containsKey(tenant) && tenantDataSources.size() >= MAX_TENANT_DATASOURCES) {
            log.warn("Tenant DataSource cache has reached the maximum size of {}. "
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
            if (emfBean.getObject() != null) emfBean.getObject().close();

            log.info("Invoice-Service tables initialised in schema: {}", schemaName);
        } catch (Exception e) {
            log.error("Failed to initialise invoice tables in schema {}: {}", schemaName, e.getMessage());
        }
    }

    private String buildUrl(String schemaName) {
        String url = baseJdbcUrl.replaceAll("[?&]currentSchema=[^&]*", "");
        return url.contains("?") ? url + "&currentSchema=" + schemaName : url + "?currentSchema=" + schemaName;
    }
}
