package com.chitmon.backend.homelab;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.DeserializationFeature;

class HomelabStatusServiceTest {
    @TempDir Path directory;
    private final List<DatabaseHealthProbe> probes = new ArrayList<>();

    @AfterEach
    void closeProbes() { probes.forEach(DatabaseHealthProbe::close); }
    private String fixture(Instant collectedAt) {
        return """
                {"schemaVersion":1,"collectedAt":"%s","host":{"hostname":"homelab","cpuCores":4,
                 "operatingSystem":"Ubuntu","kernel":"7.0","uptimeSeconds":100,"cpuPercent":1.5,
                 "loadAverage":[0.1,0.2,0.3],"memoryTotalBytes":16000,"memoryAvailableBytes":14000,
                 "swapTotalBytes":4000,"swapFreeBytes":4000,"diskTotalBytes":100000,
                 "diskUsedBytes":13000,"diskFreeBytes":87000},
                 "docker":{"available":true,"statsAvailable":true,"version":"29.7.1","containers":[]},
                 "services":[],"warnings":[]}
                """.formatted(collectedAt);
    }

    private HomelabStatusService service() {
        var probe = new DatabaseHealthProbe(Optional.empty());
        probes.add(probe);
        return new HomelabStatusService(directory.resolve("status.json").toString(), JsonMapper.builder().build(),
                probe);
    }

    @Test
    void freshHostSnapshotAndApiStatusRemainIndependentOfDatabaseConfiguration() throws Exception {
        Files.writeString(directory.resolve("status.json"), fixture(Instant.now()));
        var result = service().status();
        assertThat(result.api().state()).isEqualTo("up");
        assertThat(result.database().state()).isEqualTo("unknown");
        assertThat(result.collector().state()).isEqualTo("fresh");
        assertThat(result.snapshot().host().hostname()).isEqualTo("homelab");
    }

    @Test
    void staleDataIsClearlyMarkedAndRetainedForDiagnosis() throws Exception {
        Files.writeString(directory.resolve("status.json"), fixture(Instant.now().minusSeconds(90)));
        var result = service().status();
        assertThat(result.collector().state()).isEqualTo("stale");
        assertThat(result.collector().ageSeconds()).isGreaterThanOrEqualTo(90);
        assertThat(result.snapshot()).isNotNull();
    }

    @Test
    void futureHostClockIsNotReportedAsFresh() throws Exception {
        Files.writeString(directory.resolve("status.json"), fixture(Instant.now().plusSeconds(120)));
        assertThat(service().status().collector().state()).isEqualTo("stale");
    }

    @Test
    void missingMalformedAndOversizedSnapshotsDoNotBreakTheApi() throws Exception {
        var service = service();
        assertThat(service.status().collector().state()).isEqualTo("unavailable");
        Files.writeString(directory.resolve("status.json"), "{invalid}");
        assertThat(service.status().snapshot()).isNull();
        Files.write(directory.resolve("status.json"), new byte[1024 * 1024 + 1]);
        var result = service.status();
        assertThat(result.api().state()).isEqualTo("up");
        assertThat(result.collector().state()).isEqualTo("unavailable");
        assertThat(result.collector().detail()).doesNotContain(directory.toString());
    }

    @Test
    void unexpectedSensitiveFieldsNeverReachTheResponseEvenWithAPermissiveMapper() throws Exception {
        Files.writeString(directory.resolve("status.json"), fixture(Instant.now())
                .replace("\"host\":", "\"environment\":[\"PASSWORD=SECRET\"],\"host\":"));
        var mapper = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        var probe = new DatabaseHealthProbe(Optional.empty());
        probes.add(probe);
        var service = new HomelabStatusService(directory.resolve("status.json").toString(), mapper, probe);
        var result = service.status();
        assertThat(result.collector().state()).isEqualTo("fresh");
        assertThat(mapper.writeValueAsString(result)).doesNotContain("SECRET", "environment");
    }
}
