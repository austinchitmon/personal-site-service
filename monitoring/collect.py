#!/usr/bin/env python3
"""Read-only host/Docker observations. Write only an allowlisted, atomic JSON snapshot."""
import argparse
import datetime as dt
import json
import math
import os
from pathlib import Path
import platform
import re
import shutil
import socket
import subprocess
import tempfile
import time

DOCKER = ["/usr/bin/docker", "--host", "unix:///var/run/docker.sock"]
SERVICES = {
    "cloudflared.service": "Cloudflare Tunnel",
    "actions.runner.austinchitmon-personal-site-service.homelab.service": "Backend deployment runner",
}
# Never request Config.Env, healthcheck output, logs, mounts or arbitrary labels.
INSPECT = ('{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"imageId":{{json .Image}},'
           '"project":{{json (index .Config.Labels "com.docker.compose.project")}},'
           '"service":{{json (index .Config.Labels "com.docker.compose.service")}},'
           '"state":{{json .State.Status}},'
           '"health":{{if .State.Health}}{{json .State.Health.Status}}{{else}}null{{end}},'
           '"startedAt":{{json .State.StartedAt}},"finishedAt":{{json .State.FinishedAt}},'
           '"exitCode":{{json .State.ExitCode}},"restartCount":{{json .RestartCount}},'
           '"ports":{{json .NetworkSettings.Ports}}}')


def command(arguments, timeout=4, allow_failure=False):
    # Fixed argument arrays, no shell, no user-supplied executable/command parameters.
    result = subprocess.run(arguments, capture_output=True, text=True, timeout=timeout,
                            env={"PATH": "/usr/sbin:/usr/bin:/sbin:/bin", "LC_ALL": "C"})
    if result.returncode and not allow_failure:
        raise RuntimeError("Monitoring command failed")
    return result.stdout.strip()


def size_bytes(value):
    match = re.fullmatch(r"\s*([\d.]+)\s*([kmgtpe]?i?b)\s*", value, re.I)
    if not match:
        return None
    amount = float(match[1])
    unit = match[2].lower()
    exponent = "bkmgtpe".index(unit[0]) if unit != "b" else 0
    return round(amount * (1024 if "i" in unit else 1000) ** exponent) if math.isfinite(amount) else None


def pair_bytes(value):
    parts = value.split(" / ")
    return [size_bytes(part) for part in parts] if len(parts) == 2 else [None, None]


def percent(value):
    try:
        number = float(value.rstrip("%"))
        return round(number, 2) if math.isfinite(number) and number >= 0 else None
    except (ValueError, TypeError, AttributeError):
        return None


def timestamp(value):
    return value if value and not value.startswith("0001-") else None


def container_view(metadata, stats):
    memory = pair_bytes(stats.get("MemUsage", ""))
    network = pair_bytes(stats.get("NetIO", ""))
    ports = []
    for endpoint, bindings in (metadata.get("ports") or {}).items():
        container_port, protocol = endpoint.split("/", 1)
        for binding in bindings or [{}]:
            ports.append({"containerPort": int(container_port), "protocol": protocol,
                          "hostIp": binding.get("HostIp"),
                          "hostPort": int(binding["HostPort"]) if binding.get("HostPort") else None})
    return {
        "id": metadata["id"][:12], "name": metadata["name"].lstrip("/"),
        "image": metadata.get("image", ""), "imageId": metadata.get("imageId"), "project": metadata.get("project"),
        "service": metadata.get("service"), "state": metadata.get("state", "unknown"),
        "health": metadata.get("health"), "startedAt": timestamp(metadata.get("startedAt")),
        "finishedAt": timestamp(metadata.get("finishedAt")),
        "exitCode": metadata.get("exitCode", 0), "restartCount": metadata.get("restartCount", 0),
        "cpuPercent": percent(stats.get("CPUPerc")), "memoryUsedBytes": memory[0],
        "memoryLimitBytes": memory[1], "networkReceivedBytes": network[0],
        "networkSentBytes": network[1], "ports": sorted(ports, key=lambda port: (
            port["containerPort"], port["hostIp"] or "", port["hostPort"] or 0)),
    }


def docker_snapshot(warnings):
    try:
        version = command(DOCKER + ["version", "--format", "{{.Server.Version}}"])
        ids = command(DOCKER + ["ps", "--all", "--quiet", "--no-trunc"]).splitlines()
        ids = [identifier for identifier in ids if re.fullmatch(r"[a-f0-9]{64}", identifier)]
        if len(ids) > 512:
            ids = ids[:512]
            warnings.append("Container inventory is limited to 512 entries")
        metadata = [json.loads(line) for line in command(
            DOCKER + ["inspect", "--format", INSPECT] + ids).splitlines()] if ids else []
    except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired):
        warnings.append("Docker inventory is unavailable. Check the daemon and collector permissions.")
        return {"available": False, "statsAvailable": False, "version": None, "containers": []}
    stats = {}
    stats_available = True
    running = [item["id"] for item in metadata if item["state"] == "running"]
    if running:
        try:
            output = command(DOCKER + ["stats", "--no-stream", "--no-trunc", "--format", "json"] + running,
                             timeout=6)
            stats = {row["ID"]: row for row in (json.loads(line) for line in output.splitlines())}
        except (RuntimeError, OSError, ValueError, subprocess.TimeoutExpired):
            stats_available = False
            warnings.append("Container resource samples are unavailable; inventory is still shown.")
    return {"available": True, "statsAvailable": stats_available, "version": version,
            "containers": sorted([container_view(item, stats.get(item["id"], {})) for item in metadata],
                                 key=lambda item: (item["project"] or "", item["name"]))}


def cpu_ticks():
    values = [int(value) for value in Path("/proc/stat").read_text().splitlines()[0].split()[1:9]]
    return sum(values), values[3] + values[4]


def host_snapshot():
    before_total, before_idle = cpu_ticks()
    time.sleep(0.12)
    after_total, after_idle = cpu_ticks()
    elapsed = after_total - before_total
    memory = {line.split(":")[0]: int(line.split()[1]) * 1024
              for line in Path("/proc/meminfo").read_text().splitlines() if ":" in line}
    disk = shutil.disk_usage("/")
    operating_system = platform.system()
    for line in Path("/etc/os-release").read_text().splitlines():
        if line.startswith("PRETTY_NAME="):
            operating_system = line.split("=", 1)[1].strip('"')
    return {"hostname": socket.gethostname(), "operatingSystem": operating_system,
            "kernel": platform.release(), "uptimeSeconds": int(float(Path("/proc/uptime").read_text().split()[0])),
            "cpuCores": os.cpu_count() or 1,
            "cpuPercent": round(max(0, min(100, 100 * (1 - (after_idle - before_idle) / elapsed))), 2)
            if elapsed else None, "loadAverage": [round(value, 2) for value in os.getloadavg()],
            "memoryTotalBytes": memory["MemTotal"], "memoryAvailableBytes": memory["MemAvailable"],
            "swapTotalBytes": memory["SwapTotal"], "swapFreeBytes": memory["SwapFree"],
            "diskTotalBytes": disk.total, "diskUsedBytes": disk.used, "diskFreeBytes": disk.free}


def service_snapshot():
    services = []
    for name, label in SERVICES.items():
        try:
            state = command(["/usr/bin/systemctl", "is-active", name], timeout=2, allow_failure=True)
        except (OSError, subprocess.TimeoutExpired):
            state = "unknown"
        if state not in {"active", "inactive", "failed", "activating", "deactivating", "reloading"}:
            state = "unknown"
        services.append({"name": name, "label": label, "state": state})
    return services


def collect():
    warnings = []
    docker = docker_snapshot(warnings)
    host = host_snapshot()
    services = service_snapshot()
    return {"schemaVersion": 1,
            "collectedAt": dt.datetime.now(dt.timezone.utc).isoformat().replace("+00:00", "Z"),
            "host": host, "docker": docker, "services": services, "warnings": warnings}


def write_snapshot(target, snapshot):
    # Directory must be installed explicitly; never create arbitrary server paths.
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=target.parent,
                                         prefix=".status-", delete=False) as stream:
            temporary = stream.name
            os.fchmod(stream.fileno(), 0o644)
            json.dump(snapshot, stream, allow_nan=False, separators=(",", ":"))
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, target)
        temporary = None
    finally:
        if temporary:
            Path(temporary).unlink(missing_ok=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("/var/lib/chitmon-monitor/status.json"))
    arguments = parser.parse_args()
    write_snapshot(arguments.output, collect())
