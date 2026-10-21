package com.chitmon.backend.homelab;

import java.time.Instant;
import java.util.List;

// An explicit allowlist: Docker environment variables, logs, mounts and credentials are never exposed.
public record HomelabSnapshot(int schemaVersion, Instant collectedAt, Host host, Docker docker,
                              List<HostService> services, List<String> warnings) {
    public record Host(String hostname, String operatingSystem, String kernel, long uptimeSeconds,
                       int cpuCores, Double cpuPercent, List<Double> loadAverage,
                       long memoryTotalBytes, long memoryAvailableBytes, long swapTotalBytes,
                       long swapFreeBytes, long diskTotalBytes, long diskUsedBytes, long diskFreeBytes) { }
    public record Docker(boolean available, boolean statsAvailable, String version, List<Container> containers) { }
    public record Container(String id, String name, String image, String imageId, String project, String service,
                            String state, String health, Instant startedAt, Instant finishedAt,
                            int exitCode, int restartCount, Double cpuPercent, Long memoryUsedBytes,
                            Long memoryLimitBytes, Long networkReceivedBytes, Long networkSentBytes,
                            List<Port> ports) { }
    public record Port(int containerPort, String protocol, String hostIp, Integer hostPort) { }
    public record HostService(String name, String label, String state) { }
}
