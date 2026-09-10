#!/usr/bin/env python3
"""Back up a Compose deployment or restore into a new isolated Compose project."""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tarfile
from dev import ROOT, environment


def run(command, env, **kwargs):
    return subprocess.run(command, cwd=ROOT, env=env, check=True, **kwargs)


def digest(path):
    with path.open("rb") as f:
        return hashlib.file_digest(f, "sha256").hexdigest()


def backup(compose, env, target, project):
    target.mkdir(parents=True, exist_ok=False)
    os.chmod(target, 0o700)
    # Stop the writer gracefully; MySQL and existing data volumes stay running.
    running = run(compose+["ps", "--status", "running", "--services"], env, capture_output=True, text=True).stdout.split()
    if "backend" not in running or "mysql" not in running:
        raise ValueError("Backup requires a running backend and MySQL")
    run(compose+["stop", "backend"], env)
    try:
        with (target/"database.sql").open("wb") as f:
            run(compose+["exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump --single-transaction --skip-lock-tables --no-tablespaces --set-gtid-purged=OFF -u "$MYSQL_USER" "$MYSQL_DATABASE"'], env, stdout=f)
        with (target/"uploads.tar.gz").open("wb") as f:
            run(compose+["run", "--rm", "--no-deps", "-T", "--entrypoint", "tar", "backend", "-C", "/app/uploads", "-czf", "-", "."], env, stdout=f)
        manifest={"format":1,"project":project,"created_at":datetime.now(timezone.utc).isoformat(),
                  "files":{name:digest(target/name) for name in ("database.sql","uploads.tar.gz")}}
        (target/"manifest.json").write_text(json.dumps(manifest, indent=2))
    finally:
        run(compose+["start", "backend"], env)
    print("Backup complete:", target)


def restore(compose, env, source, project):
    manifest=json.loads((source/"manifest.json").read_text())
    if manifest.get("format")!=1 or set(manifest.get("files",{}))!={"database.sql","uploads.tar.gz"}: raise ValueError("Unsupported backup")
    for name, sha in manifest["files"].items():
        if digest(source/name)!=sha: raise ValueError("Backup checksum mismatch: "+name)
    with tarfile.open(source/"uploads.tar.gz") as archive:
        for item in archive.getmembers():
            if item.name.startswith("/") or ".." in Path(item.name).parts or not (item.isfile() or item.isdir()):
                raise ValueError("Unsafe uploads archive entry")
    if project==manifest.get("project"): raise ValueError("Restore must use a new project name")
    for resource in ("container", "volume"):
        listing=["docker",resource,"ls","-q"]
        if resource == "container": listing.append("--all")
        existing=run(listing+["--filter",f"label=com.docker.compose.project={project}"],env,capture_output=True,text=True).stdout.strip()
        if existing: raise ValueError("Restore target already has containers or data volumes; choose another project")
    run(compose+["up","-d","--wait","--wait-timeout","180","mysql"],env)
    with (source/"database.sql").open("rb") as f:
        run(compose+["exec","-T","mysql","sh","-c",'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u "$MYSQL_USER" "$MYSQL_DATABASE"'],env,stdin=f)
    with (source/"uploads.tar.gz").open("rb") as f:
        run(compose+["run","--rm","--no-deps","--build","-T","--user","root","--entrypoint","sh","backend","-c","tar -xzf - -C /app/uploads && chown -R campus:campus /app/uploads"],env,stdin=f)
    run(compose+["up","--build","-d","--wait","--wait-timeout","240"],env)
    print("Restored into new project:",project)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("action",choices=("backup","restore"));p.add_argument("path",type=Path)
    p.add_argument("--project",required=True);p.add_argument("--env-file",type=Path,default=ROOT/".env")
    a=p.parse_args()
    if not re.fullmatch("[a-z0-9][a-z0-9_-]{0,49}",a.project):p.error("Invalid Compose project name")
    env=environment(a.env_file)
    compose=["docker","compose","-p",a.project]
    if a.env_file.exists():compose.extend(["--env-file",str(a.env_file)])
    (backup if a.action=="backup" else restore)(compose,env,a.path.resolve(),a.project)

if __name__=="__main__":main()
