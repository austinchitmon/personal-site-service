package com.chitmon.backend.config;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs Flyway independently per-schema/subproject instead of relying on Spring
 * Boot's auto-configured single-schema Flyway bean (which can't target
 * multiple schema/location pairs).
 *
 * To add a new subproject schema (e.g. "blog"):
 *   1. Create src/main/resources/db/migration/&lt;schema&gt;/V1__init_schema.sql
 *   2. Add "&lt;schema&gt;" to MANAGED_SCHEMAS below
 * Each schema gets its own independent version history
 * (&lt;schema&gt;.flyway_schema_history), so subprojects never collide.
 */
@Configuration
public class FlywayMultiSchemaConfig {

    private static final List<String> MANAGED_SCHEMAS = List.of("pokemon");

    @Bean
    public ApplicationRunner flywayMultiSchemaRunner(DataSource dataSource) {
        return args -> {
            for (String schema : MANAGED_SCHEMAS) {
                Flyway.configure()
                        .dataSource(dataSource)
                        .schemas(schema)
                        .defaultSchema(schema)
                        .createSchemas(true)
                        .table("flyway_schema_history")
                        .locations("classpath:db/migration/" + schema)
                        .load()
                        .migrate();
            }
        };
    }
}
