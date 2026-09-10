import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("dev", Path(__file__).resolve().parents[1] / "dev.py")
dev = importlib.util.module_from_spec(spec)
spec.loader.exec_module(dev)

class EnvironmentTest(unittest.TestCase):
    def test_values_are_data_not_shell(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/".env"
            p.write_text("SENDER='CampusPulse <mail@example.org>'\nPASSWORD=$(echo forbidden)&x\n")
            self.assertEqual(dev.read_env(p), {"SENDER": "CampusPulse <mail@example.org>", "PASSWORD": "$(echo forbidden)&x"})
    def test_local_ports_map_to_application_settings(self):
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/".env";p.write_text("BACKEND_PORT=18080\nFRONTEND_PORT=18125\n")
            with patch.dict("os.environ",{},clear=True):
                env=dev.environment(p)
            self.assertEqual(env["SERVER_PORT"],"18080")
            self.assertEqual(env["PORT"],"18125")
            self.assertEqual(env["TARGET"],"http://127.0.0.1:18080")
    def test_init_is_private_and_preserves_custom_secrets(self):
        with tempfile.TemporaryDirectory() as d:
            p = Path(d)/".env"
            p.write_text("APP_SECURITY_TOKEN_SECRET=" + "ab12cd34"*8 + "\nDB_PASSWORD=existing-db-password\n")
            dev.initialize(p)
            first = dev.read_env(p)
            dev.initialize(p)
            self.assertEqual(first, dev.read_env(p))
            self.assertEqual(first["DB_PASSWORD"], "existing-db-password")
            self.assertNotEqual(first["APP_SECURITY_EMAIL_SECRET"], first["APP_SECURITY_TOKEN_SECRET"])
            self.assertEqual(p.stat().st_mode & 0o777, 0o600)

if __name__ == "__main__": unittest.main()
