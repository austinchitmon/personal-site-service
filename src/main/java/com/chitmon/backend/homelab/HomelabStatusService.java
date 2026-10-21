package com.chitmon.backend.homelab;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class HomelabStatusService {
    public record Api(String state, long uptimeSeconds) { }
    public record Collector(String state, Instant collectedAt, Long ageSeconds, String detail) { }
    public record Status(Instant checkedAt, Api api, DatabaseHealthProbe.Check database,
                         Collector collector, HomelabSnapshot snapshot) { }
    private static final Logger log = LoggerFactory.getLogger(HomelabStatusService.class);
    private static final int MAX_SNAPSHOT_BYTES = 1024 * 1024;
    private final Path snapshotFile;
    private final ObjectMapper mapper;
    private final DatabaseHealthProbe database;
    private String lastCollectorState;

    public HomelabStatusService(@Value("${homelab.snapshot-path:./monitoring-data/status.json}") String file,
                               ObjectMapper mapper, DatabaseHealthProbe database) {
        this.snapshotFile = Path.of(file);
        this.mapper = mapper;
        this.database = database;
    }

    public Status status() {
        HomelabSnapshot snapshot = null;
        Collector collector;
        try (var input = Files.newInputStream(snapshotFile)) {
            byte[] bytes = input.readNBytes(MAX_SNAPSHOT_BYTES + 1);
            if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IOException("Snapshot exceeds limit");
            snapshot = mapper.readValue(bytes, HomelabSnapshot.class);
            if (snapshot == null || snapshot.schemaVersion() != 1 || snapshot.collectedAt() == null
                    || snapshot.host() == null || snapshot.docker() == null
                    || snapshot.docker().containers() == null || snapshot.services() == null
                    || snapshot.warnings() == null) {
                throw new IOException("Invalid monitoring snapshot");
            }
            long age = Duration.between(snapshot.collectedAt(), Instant.now()).getSeconds();
            boolean fresh = age >= -10 && age <= 60;
            collector = new Collector(fresh ? "fresh" : "stale", snapshot.collectedAt(), Math.max(age, 0),
                    fresh ? "Host collector is reporting" : "Monitoring data is old or the host clock is incorrect");
        } catch (Exception ex) {
            snapshot = null;
            collector = new Collector("unavailable", null, null,
                    "No usable host snapshot. Check the homelab collector and its read-only mount.");
        }
        logCollectorTransition(collector.state());
        return new Status(Instant.now(), new Api("up", ManagementFactory.getRuntimeMXBean().getUptime() / 1000),
                database.check(), collector, snapshot);
    }

    private synchronized void logCollectorTransition(String state) {
        if (!state.equals(lastCollectorState)) {
            if (state.equals("fresh")) log.info("Homelab collector reporting fresh data");
            else log.warn("Homelab collector state={} (check collector service and snapshot mount)", state);
            lastCollectorState = state;
        }
    }
}
