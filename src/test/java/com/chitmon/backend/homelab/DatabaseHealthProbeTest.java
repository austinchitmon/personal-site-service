package com.chitmon.backend.homelab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DatabaseHealthProbeTest {
    @Test
    void validatesAndClosesOnePooledConnectionAndCachesTheResult() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);
        var probe = new DatabaseHealthProbe(Optional.of(source));
        try {
            assertThat(probe.check().state()).isEqualTo("up");
            assertThat(probe.check().state()).isEqualTo("up");
            verify(source, times(1)).getConnection();
            verify(connection).close();
        } finally { probe.close(); }
    }

    @Test
    void databaseFailureIsReportedWithoutCredentialsOrExceptionText() throws Exception {
        DataSource source = mock(DataSource.class);
        when(source.getConnection()).thenThrow(new SQLException("password=SECRET", "08001"));
        var probe = new DatabaseHealthProbe(Optional.of(source));
        try {
            var result = probe.check();
            assertThat(result.state()).isEqualTo("down");
            assertThat(result.detail()).doesNotContain("SECRET");
        } finally { probe.close(); }
    }

    @Test
    void stuckPoolDoesNotBlockTheDashboardOrAccumulateQueuedConnections() throws Exception {
        DataSource source = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        CountDownLatch release = new CountDownLatch(1);
        when(source.getConnection()).thenAnswer(invocation -> { release.await(); return connection; });
        var probe = new DatabaseHealthProbe(Optional.of(source));
        var callers = Executors.newFixedThreadPool(3);
        try {
            long start = System.nanoTime();
            var results = IntStream.range(0, 3).mapToObj(index -> callers.submit(probe::check)).toList();
            for (var result : results) assertThat(result.get(5, TimeUnit.SECONDS).state()).isEqualTo("unknown");
            assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(5000);
            verify(source, times(1)).getConnection();
        } finally {
            release.countDown();
            probe.close();
            callers.shutdownNow();
        }
    }
}
