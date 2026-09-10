"""Opt-in backup/restore drill against disposable Compose projects; keeps results for inspection."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.request

from dev import ROOT, environment
from media_smoke_test import check as check_media
from smoke_test import checked, http_json


def counts(project, env):
    # Persistent user content; excludes login audit and scheduler bookkeeping.
    tables = ("users", "activities", "registrations", "teams", "team_member", "dm_message",
              "messages", "activity_chat_message", "uploaded_file", "support_ticket", "support_reply")
    sql = " UNION ALL ".join(f"SELECT '{table}', COUNT(*) FROM {table}" for table in tables)
    result = subprocess.run(["docker", "compose", "-p", project, "exec", "-T", "mysql", "sh", "-c",
                             'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -N -u "$MYSQL_USER" "$MYSQL_DATABASE"'],
                            cwd=ROOT, env=env, input=sql, capture_output=True, text=True, check=True)
    return {name: int(count) for name, count in (line.split("\t") for line in result.stdout.splitlines())}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-project", default="campuspulse")
    parser.add_argument("--target-project", default="campuspulse-restore-ci")
    parser.add_argument("--backup-path", type=Path, required=True)
    parser.add_argument("--source-url", default="http://127.0.0.1:8125")
    parser.add_argument("--mysql-port", default="13307")
    parser.add_argument("--backend-port", default="18081")
    parser.add_argument("--frontend-port", default="18126")
    args = parser.parse_args()
    if os.environ.get("CAMPUS_TEST_DATABASE") != "1":
        parser.error("Set CAMPUS_TEST_DATABASE=1 only for disposable test projects")
    env = environment(ROOT / ".env")
    media = check_media(args.source_url.rstrip("/"))
    before = counts(args.source_project, env)
    backup = args.backup_path.resolve()
    subprocess.run([sys.executable, str(ROOT / "tools/backup.py"), "backup", str(backup),
                    "--project", args.source_project], env=env, cwd=ROOT, check=True)
    restored_url = "http://127.0.0.1:" + args.frontend_port
    restored_env = env | {"MYSQL_PORT": args.mysql_port, "BACKEND_PORT": args.backend_port,
                          "FRONTEND_PORT": args.frontend_port,
                          "APP_CORS_ALLOWED_ORIGINS": restored_url + ",http://localhost:" + args.frontend_port}
    subprocess.run([sys.executable, str(ROOT / "tools/backup.py"), "restore", str(backup),
                    "--project", args.target_project], env=restored_env, cwd=ROOT, check=True)
    after = counts(args.target_project, restored_env)
    assert before == after, {"before": before, "after": after}
    assert counts(args.source_project, env) == before, "Recovery changed source content"
    session = checked(http_json("POST", restored_url + "/api/auth/login",
                                body={"usernameOrStudentNo": "chenze", "password": "demo12345"}))
    request = urllib.request.Request(restored_url + media["media_url"],
                                     headers={"Authorization": "Bearer " + session["token"]})
    with urllib.request.urlopen(request, timeout=20) as response:
        assert hashlib.sha256(response.read()).hexdigest() == media["sha256"], "Restored image bytes differ"
    subprocess.run([sys.executable, str(ROOT / "tools/smoke_test.py"), "--base-url", restored_url], check=True)
    report = {"restore": "passed", "content_table_counts": after, "private_image_sha256": media["sha256"],
              "private_image_restored": True, "source_project": args.source_project, "target_project": args.target_project}
    (backup / "verification.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
