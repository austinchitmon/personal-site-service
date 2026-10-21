package com.chitmon.backend.homelab;

import jakarta.annotation.PreDestroy;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealthProbe {
    public record Check(String state, String detail, Long latencyMs) { }
    private static final Logger log = LoggerFactory.getLogger(DatabaseHealthProbe.class);
    private final Optional<DataSource> dataSource;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("homelab-database-check").factory());
    private Future<Check> inFlight;
    private long nextCheckNanos;
    private Check cached = new Check("unknown", "Database has not been checked yet", null);

    public DatabaseHealthProbe(Optional<DataSource> dataSource) {
        this.dataSource = dataSource;
    }

    public Check check() {
        Future<Check> pending;
        synchronized (this) {
            long now = System.nanoTime();
            if (inFlight == null && (nextCheckNanos == 0 || now - nextCheckNanos >= 0)) {
                inFlight = worker.submit(this::validateConnection);
                nextCheckNanos = now + TimeUnit.SECONDS.toNanos(10);
            }
            pending = inFlight;
            if (pending == null) return cached;
        }
        try {
            // Concurrent HTTP requests share the probe and wait in parallel, never under the lock.
            Check result = pending.get(2500, TimeUnit.MILLISECONDS);
            synchronized (this) {
                if (inFlight == pending) { cached = result; inFlight = null; }
            }
            return result;
        } catch (TimeoutException ex) {
            // Do not queue more probes while JDBC is still waiting for the pool.
            // The one worker closes its connection when that attempt eventually finishes.
            return new Check("unknown", "Database check timed out; the API is still responding", 2500L);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Check("unknown", "Database check was interrupted", null);
        } catch (Exception ex) {
            Check failed = new Check("down", "Database connection check failed", null);
            synchronized (this) {
                if (inFlight == pending) { inFlight = null; cached = failed; }
            }
            log.warn("Homelab database check failed ({})", ex.getClass().getSimpleName());
            return failed;
        }
    }

    private Check validateConnection() {
        if (dataSource.isEmpty()) {
            return new Check("unknown", "This API process has no database configured", null);
        }
        long start = System.nanoTime();
        try (var connection = dataSource.get().getConnection()) {
            boolean valid = connection.isValid(2);
            return new Check(valid ? "up" : "down", valid
                    ? "API database connection validated" : "Database rejected the connection check",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        } catch (SQLException ex) {
            log.warn("Homelab database connection unavailable (SQL state={})", ex.getSQLState());
            return new Check("down", "API cannot obtain a valid database connection",
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
        }
    }

    @PreDestroy
    public void close() {
        worker.shutdownNow();
    }
}
