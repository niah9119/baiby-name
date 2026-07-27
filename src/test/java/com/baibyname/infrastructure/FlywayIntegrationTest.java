package com.baibyname.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that verifies Flyway migrations apply cleanly on an empty database.
 */
@SpringBootTest
@Testcontainers
class FlywayIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void configureTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void flywayMigrationsApplyCleanly() throws SQLException {
        // Verify the migrations created expected tables
        assertThat(tableExists("country")).isTrue();
        assertThat(tableExists("given_name")).isTrue();
        assertThat(tableExists("name_stat")).isTrue();
        assertThat(tableExists("name_style")).isTrue();
        assertThat(tableExists("famous_bearer")).isTrue();
        assertThat(tableExists("name_famous_bearer")).isTrue();
        assertThat(tableExists("account")).isTrue();
        assertThat(tableExists("shortlist")).isTrue();
        assertThat(tableExists("shortlist_member")).isTrue();
        assertThat(tableExists("shortlist_entry")).isTrue();
    }

    @Test
    void seedDataIsLoaded() throws SQLException {
        // Verify country seed data exists
        assertThat(countryCodeExists("SE")).isTrue();
        assertThat(countryCodeExists("NO")).isTrue();
        assertThat(countryCodeExists("DK")).isTrue();
        assertThat(countryCodeExists("GB")).isTrue();
        assertThat(countryCodeExists("US")).isTrue();
    }

    private boolean tableExists(String tableName) throws SQLException {
        String query = "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = ?)";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, tableName);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
                return false;
            }
        }
    }

    private boolean countryCodeExists(String code) throws SQLException {
        String query = "SELECT EXISTS (SELECT 1 FROM country WHERE code = ?)";
        try (var conn = dataSource.getConnection();
             var stmt = conn.prepareStatement(query)) {
            stmt.setString(1, code);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                }
                return false;
            }
        }
    }
}
