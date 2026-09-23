package com.ultravyx.leaks;

import com.ultravyx.leaks.api.ApiDtos.CreateLeadRequest;
import com.ultravyx.leaks.api.ApiDtos.UploadResult;
import com.ultravyx.leaks.api.LeaksController;
import com.ultravyx.leaks.api.LeadsController;
import com.ultravyx.leaks.domain.LeadStatus;
import com.ultravyx.leaks.domain.LeakType;
import com.ultravyx.leaks.service.AnalyticsService;
import com.ultravyx.leaks.service.ConflictException;
import com.ultravyx.leaks.service.CsvImportService;
import com.ultravyx.leaks.service.LeadService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in real-PostgreSQL integration check: ./mvnw -Ppg-integration verify */
class PostgresIntegrationIT {
    private static final String HEADER = "lead_id,name,status,created_at,contacted_at,assigned_to,source,campaign,appointment_at,attended_at,revenue\n";
    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }
    @Test void flywayPersistenceImportFilteringConstraintsAndExportWorkAgainstPostgres() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.builder().setLocaleConfig("locale", "C").start();
             ConfigurableApplicationContext context = new SpringApplicationBuilder(UltravyxApplication.class)
                     .web(WebApplicationType.NONE)
                     .run("--spring.datasource.url=" + postgres.getJdbcUrl("postgres", "postgres"),
                             "--spring.datasource.username=postgres", "--spring.datasource.password=",
                             "--spring.jpa.hibernate.ddl-auto=validate")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Long.class));
            assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM organizations", Long.class));
            CsvImportService importer = context.getBean(CsvImportService.class);
            LeadService leads = context.getBean(LeadService.class);
            AnalyticsService analytics = context.getBean(AnalyticsService.class);
            Instant old = Instant.now().minusSeconds(9 * 86400L);
            String created = old.toString();
            UploadResult first = importer.importFile(csv(HEADER
                    + "A,Alpha,NEW," + created + ",,,,,,,\n"
                    + "B,Beta,WON," + created + ",,Owner,Ads,,,,100.00\n"));
            assertEquals(2, first.inserted());
            assertEquals(2, analytics.summary().totalLeads());
            assertEquals(new BigDecimal("100.00"), analytics.summary().recordedRevenue());
            assertEquals(1L, leads.search(null, null, "Unknown", null, 0, 20, "createdAt,desc").totalElements());
            assertEquals(1L, leads.byLeak(LeakType.UNCONTACTED, 0, 20, "name,asc").totalElements());
            assertEquals(2L, leads.search(null, null, null, null, 0, 1, "name,asc").totalElements());
            assertEquals(1, leads.search(null, null, null, null, 0, 1, "name,asc").items().size());

            UploadResult second = importer.importFile(csv(HEADER + "A,Alpha Updated,QUALIFIED," + created + ",,Owner,,,,,\n"));
            assertEquals(1, second.updated());
            assertEquals(2, analytics.summary().totalLeads());
            assertEquals(1L, leads.byLeak(LeakType.QUALIFIED_NOT_PROGRESSED, 0, 20, "name,asc").totalElements());
            assertThrows(ConflictException.class, () -> leads.create(new CreateLeadRequest("A", "Duplicate",
                    LeadStatus.NEW, OffsetDateTime.parse(created), null, null, null, null, null, null, null)));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "INSERT INTO leads (id,organization_id,external_lead_id,name,status,lead_created_at,updated_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(), LeadService.DEFAULT_ORG_ID, "A", "Duplicate", "NEW", Timestamp.from(old), Timestamp.from(Instant.now())));

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            context.getBean(LeaksController.class).export(LeakType.QUALIFIED_NOT_PROGRESSED).getBody().writeTo(output);
            byte[] bytes = output.toByteArray();
            assertEquals((byte) 0xEF, bytes[0]);
            assertEquals((byte) 0xBB, bytes[1]);
            assertEquals((byte) 0xBF, bytes[2]);
            String exported = new String(bytes, StandardCharsets.UTF_8);
            assertTrue(exported.contains("Alpha Updated"));
            assertTrue(exported.contains("QUALIFIED_NOT_PROGRESSED"));

            ByteArrayOutputStream filteredOutput = new ByteArrayOutputStream();
            context.getBean(LeadsController.class).export("Beta", LeadStatus.WON, "Ads", null, "name,asc")
                    .getBody().writeTo(filteredOutput);
            String filteredCsv = filteredOutput.toString(StandardCharsets.UTF_8);
            assertTrue(filteredCsv.contains("Beta"));
            assertFalse(filteredCsv.contains("Alpha Updated"));
            assertTrue(filteredCsv.contains("100.00"));
        }
    }
}
