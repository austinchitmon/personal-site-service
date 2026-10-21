import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("collect", Path(__file__).parents[1] / "collect.py")
collect = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collect)


class CollectorTest(unittest.TestCase):
    def test_docker_units_and_missing_samples(self):
        self.assertEqual(collect.pair_bytes("512MiB / 2GiB"), [536870912, 2147483648])
        self.assertEqual(collect.pair_bytes("1.5kB / 2MB"), [1500, 2000000])
        self.assertEqual(collect.pair_bytes(""), [None, None])
        self.assertIsNone(collect.percent("NaN%"))
        self.assertEqual(collect.percent("145.25%"), 145.25)

    def test_only_allowlisted_metadata_reaches_snapshot(self):
        metadata = {"id": "a" * 64, "name": "/database", "image": "postgres:17-alpine",
                    "project": "website", "service": "postgres", "state": "running", "health": "healthy",
                    "startedAt": "2026-10-21T00:00:00Z", "finishedAt": "0001-01-01T00:00:00Z",
                    "env": ["PASSWORD=SECRET"], "logs": "SECRET", "mounts": ["SECRET"],
                    "ports": {"5432/tcp": [{"HostIp": "127.0.0.1", "HostPort": "5432"}]}}
        view = collect.container_view(metadata, {"CPUPerc": "1.5%", "MemUsage": "32MiB / 16GiB",
                                                 "NetIO": "2kB / 3kB"})
        self.assertNotIn("SECRET", json.dumps(view))
        self.assertEqual(view["name"], "database")
        self.assertIsNone(view["finishedAt"])
        self.assertEqual(view["memoryUsedBytes"], 33554432)
        self.assertEqual(view["ports"][0]["hostIp"], "127.0.0.1")

    def test_stopped_container_has_no_fabricated_usage(self):
        result = collect.container_view({"id": "a" * 64, "name": "/old-app", "state": "exited"}, {})
        self.assertEqual(result["state"], "exited")
        self.assertIsNone(result["cpuPercent"])
        self.assertIsNone(result["memoryUsedBytes"])

    @patch.object(collect, "command", side_effect=RuntimeError("daemon unavailable"))
    def test_unreachable_docker_is_not_reported_as_zero_healthy_containers(self, _command):
        warnings = []
        result = collect.docker_snapshot(warnings)
        self.assertFalse(result["available"])
        self.assertFalse(result["statsAvailable"])
        self.assertTrue(warnings)

    def test_failed_resource_sample_keeps_inventory(self):
        identifier = "a" * 64
        metadata = {"id": identifier, "name": "/api", "state": "running"}
        with patch.object(collect, "command", side_effect=["29.7.1", identifier, json.dumps(metadata),
                                                           RuntimeError("stats unavailable")]):
            warnings = []
            result = collect.docker_snapshot(warnings)
        self.assertTrue(result["available"])
        self.assertFalse(result["statsAvailable"])
        self.assertEqual(len(result["containers"]), 1)
        self.assertIsNone(result["containers"][0]["cpuPercent"])

    def test_atomic_replacement_is_readable_and_leaves_no_temporary_files(self):
        with tempfile.TemporaryDirectory() as directory:
            target = Path(directory) / "status.json"
            collect.write_snapshot(target, {"version": 1})
            collect.write_snapshot(target, {"version": 2})
            self.assertEqual(json.loads(target.read_text()), {"version": 2})
            self.assertEqual(target.stat().st_mode & 0o777, 0o644)
            self.assertEqual([item.name for item in Path(directory).iterdir()], ["status.json"])


if __name__ == "__main__":
    unittest.main()
