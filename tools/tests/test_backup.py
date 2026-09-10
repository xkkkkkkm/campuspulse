import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import backup


class BackupSafetyTest(unittest.TestCase):
    def fixture(self, root):
        (root / "database.sql").write_text("SELECT 1;\n")
        with tarfile.open(root / "uploads.tar.gz", "w:gz"):
            pass
        (root / "manifest.json").write_text(json.dumps({"format": 1, "project": "source",
            "files": {name: backup.digest(root / name) for name in ("database.sql", "uploads.tar.gz")}}))

    def test_failed_dump_restarts_source_backend(self):
        calls = []

        def run(command, env, **kwargs):
            calls.append(command)
            if "ps" in command:
                return subprocess.CompletedProcess(command, 0, stdout="mysql\nbackend\n")
            if "exec" in command:
                raise subprocess.CalledProcessError(1, command)
            return subprocess.CompletedProcess(command, 0)

        with tempfile.TemporaryDirectory() as directory, patch.object(backup, "run", side_effect=run):
            target = Path(directory) / "snapshot"
            with self.assertRaises(subprocess.CalledProcessError):
                backup.backup(["docker", "compose"], {}, target, "source")
            self.assertEqual(calls[-1][-2:], ["start", "backend"])
            self.assertFalse((target / "manifest.json").exists())

    def test_tampered_backup_rejected_before_docker(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(backup, "run") as run:
            root = Path(directory)
            self.fixture(root)
            (root / "database.sql").write_text("ALTER TABLE users DROP password;\n")
            with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                backup.restore(["docker", "compose"], {}, root, "new-project")
            run.assert_not_called()

    def test_stopped_target_container_rejected_without_creating_resources(self):
        def run(command, env, **kwargs):
            if command[:3] == ["docker", "container", "ls"] and "--all" in command:
                return subprocess.CompletedProcess(command, 0, stdout="stopped-container-id\n")
            self.fail("Stopped target containers must be detected before creating resources")

        with tempfile.TemporaryDirectory() as directory, patch.object(backup, "run", side_effect=run):
            root = Path(directory)
            self.fixture(root)
            with self.assertRaisesRegex(ValueError, "already has containers"):
                backup.restore(["docker", "compose"], {}, root, "new-project")


if __name__ == "__main__":
    unittest.main()
