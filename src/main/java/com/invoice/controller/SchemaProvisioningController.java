package com.invoice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/internal")
@Slf4j
public class SchemaProvisioningController {

    private static final String SOURCE_SCHEMA = "invoice";
    private static final List<String> EXCLUDED_TABLES = List.of("flyway_schema_history");

    private final DataSource rawDataSource;

    public SchemaProvisioningController(@Qualifier("rawDataSource") DataSource rawDataSource) {
        this.rawDataSource = rawDataSource;
    }

    /**
     * On startup: re-clone invoice schema tables into every existing tenant schema.
     * This picks up any new entity tables Hibernate just created via ddl-auto=update.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void syncAllSchemasOnStartup() {
        log.info("Invoice-Service startup: syncing all tenant schemas from '{}'", SOURCE_SCHEMA);
        try (Connection conn = rawDataSource.getConnection()) {
            List<String> tenants = getTenantSchemas(conn);
            log.info("Found {} tenant schema(s) to sync: {}", tenants.size(), tenants);
            for (String tenant : tenants) {
                try {
                    cloneSchema(conn, SOURCE_SCHEMA, tenant);
                    log.info("Synced tenant schema '{}'", tenant);
                } catch (Exception e) {
                    log.error("Failed to sync tenant schema '{}': {}", tenant, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Startup schema sync failed: {}", e.getMessage());
        }
    }

    /** Provision (or re-provision) a single tenant schema. Idempotent — safe to call multiple times. */
    @PostMapping("/provision-schema/{schemaName}")
    public ResponseEntity<String> provisionSchema(@PathVariable("schemaName") String schemaName) {
        log.info("Invoice-Service: provisioning schema '{}'", schemaName);
        try (Connection conn = rawDataSource.getConnection()) {
            createSchema(conn, schemaName);
            List<String> cloned = cloneSchema(conn, SOURCE_SCHEMA, schemaName);
            return ResponseEntity.ok("Provisioned " + cloned.size() + " tables into '" + schemaName + "' for Invoice-Service");
        } catch (Exception e) {
            log.error("Failed to provision schema '{}': {}", schemaName, e.getMessage());
            return ResponseEntity.internalServerError().body("Failed: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Core helpers
    // -----------------------------------------------------------------------

    private List<String> getTenantSchemas(Connection conn) throws SQLException {
        List<String> schemas = new ArrayList<>();
        String sql = "SELECT schema_name FROM information_schema.schemata " +
                     "WHERE schema_name NOT IN ('public','invoice','information_schema','pg_catalog','pg_toast') " +
                     "AND schema_name NOT LIKE 'pg_%'";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) schemas.add(rs.getString("schema_name"));
        }
        return schemas;
    }

    private static void createSchema(Connection conn, String schemaName) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS \"" + schemaName + "\"");
        }
    }

    /** Clones every BASE TABLE from sourceSchema into targetSchema. Returns list of cloned table names. */
    private static List<String> cloneSchema(Connection conn, String sourceSchema, String targetSchema)
            throws SQLException {
        List<String> tables = getBaseTables(conn, sourceSchema);
        List<String> cloned = new ArrayList<>();
        log.info("Cloning {} tables from '{}' → '{}'", tables.size(), sourceSchema, targetSchema);
        for (String table : tables) {
            try {
                cloneTable(conn, sourceSchema, targetSchema, table);
                cloned.add(table);
            } catch (Exception e) {
                log.warn("Skipped table '{}': {}", table, e.getMessage());
            }
        }
        return cloned;
    }

    private static List<String> getBaseTables(Connection conn, String schema) throws SQLException {
        List<String> tables = new ArrayList<>();
        String sql = "SELECT table_name FROM information_schema.tables " +
                     "WHERE table_schema = ? AND table_type = 'BASE TABLE' " +
                     "AND table_name NOT IN ('flyway_schema_history') " +
                     "ORDER BY table_name";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) tables.add(rs.getString("table_name"));
        }
        return tables;
    }

    private static void cloneTable(Connection conn, String src, String tgt, String table) throws SQLException {
        String ddl = String.format(
            "CREATE TABLE IF NOT EXISTS \"%s\".\"%s\" (LIKE \"%s\".\"%s\" INCLUDING DEFAULTS INCLUDING CONSTRAINTS INCLUDING INDEXES)",
            tgt, table, src, table);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(ddl);
        }
        fixSequences(conn, tgt, table);
        log.debug("Cloned table '{}' → '{}'", table, tgt);
    }

    private static void fixSequences(Connection conn, String tgtSchema, String table) throws SQLException {
        String sql = "SELECT column_name FROM information_schema.columns " +
                     "WHERE table_schema = ? AND table_name = ? AND column_default LIKE 'nextval%'";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tgtSchema);
            ps.setString(2, table);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String col = rs.getString("column_name");
                String seqName = table + "_" + col + "_seq";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(String.format(
                        "CREATE SEQUENCE IF NOT EXISTS \"%s\".\"%s\"", tgtSchema, seqName));
                    stmt.execute(String.format(
                        "ALTER TABLE \"%s\".\"%s\" ALTER COLUMN \"%s\" SET DEFAULT nextval('\"%s\".\"%s\"')",
                        tgtSchema, table, col, tgtSchema, seqName));
                    stmt.execute(String.format(
                        "ALTER SEQUENCE \"%s\".\"%s\" OWNED BY \"%s\".\"%s\".\"%s\"",
                        tgtSchema, seqName, tgtSchema, table, col));
                }
            }
        }
    }
}
