#!/usr/bin/env python3
"""Temporal conversion ranking with immutable releases and a genuinely read-only dry run.

Features use only event history before each exposure. Mutable user profiles and
current item popularity are deliberately excluded from historical evaluation.
This is a small-data gradient-boosting classifier, not LambdaMART or vector search.
"""
from __future__ import annotations
import argparse
from bisect import bisect_left, bisect_right
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta
import hashlib
import json
import math
import os
from pathlib import Path
import re
import tempfile

CONVERSIONS = {"REGISTER", "FAVORITE", "TEAM_APPLY", "TEAM_JOIN"}
EXPOSURES = {"IMPRESSION", "FEATURED_IMPRESSION"}
FEATURES = ["past_user_exposures", "past_user_conversions", "past_item_conversions", "past_pair_conversions", "past_item_exposures"]
ROOT = Path(__file__).resolve().parent

@dataclass(frozen=True)
class Event:
    user: int
    item: int
    time: datetime
    kind: str
    source: str
    sequence: int = 0

class History:
    """Sorted timestamp indexes; strict <t excludes the current/future outcome."""
    def __init__(self, events):
        self.ue, self.uc, self.ic, self.pc, self.ie = (defaultdict(list) for _ in range(5))
        for e in sorted(events, key=lambda e: (e.time,e.sequence)):
            if e.kind in EXPOSURES:
                self.ue[e.user].append((e.time,e.sequence)); self.ie[e.item].append((e.time,e.sequence))
            if e.source == "SERVER" and e.kind in CONVERSIONS:
                self.uc[e.user].append((e.time,e.sequence)); self.ic[e.item].append((e.time,e.sequence)); self.pc[e.user, e.item].append((e.time,e.sequence))
    def features(self, user, item, at, sequence=0):
        return [math.log1p(bisect_left(index.get(key, []), (at,sequence))) for index, key in
                ((self.ue, user), (self.uc, user), (self.ic, item), (self.pc, (user, item)), (self.ie, item))]
    def converted(self, user, item, at, horizon, sequence=0):
        times = self.pc.get((user, item), [])
        return int(bisect_right(times, (at+horizon,2**63-1)) > bisect_right(times, (at,sequence)))


def samples(events, now, horizon):
    history = History(events)
    rows, seen = [], set()
    for e in sorted(events, key=lambda e: (e.time,e.sequence)):
        if e.kind not in EXPOSURES or e.time + horizon > now: continue
        key = (e.user, e.item, e.time.date())
        if key in seen: continue
        seen.add(key)
        rows.append({"user": e.user, "item": e.item, "at": e.time, "x": history.features(e.user, e.item, e.time,e.sequence),
                     "y": history.converted(e.user, e.item, e.time, horizon,e.sequence)})
    return rows, history


def temporal_split(rows, horizon):
    if not rows: return [], [], None
    ordered = sorted(rows, key=lambda r: r["at"])
    cutoff = ordered[min(len(ordered)-1, int(len(ordered)*.75))]["at"]
    # Purge training samples whose label window overlaps the validation period.
    return [r for r in ordered if r["at"] + horizon < cutoff], [r for r in ordered if r["at"] >= cutoff], cutoff


def ranking_metrics(rows, scores, k=5):
    import numpy as np
    groups = defaultdict(list)
    for r, score in zip(rows, scores): groups[(r["user"], r["at"].date())].append((r["y"], float(score), r["item"]))
    values = []
    for entries in groups.values():
        relevant = sum(y for y, _, _ in entries)
        if len(entries) < 2 or relevant == 0: continue
        ranked = sorted(entries, key=lambda v: (-v[1], v[2]))[:k]
        dcg = sum(y / math.log2(i+2) for i, (y, _, _) in enumerate(ranked))
        ideal = sum(1 / math.log2(i+2) for i in range(min(k, relevant)))
        values.append((dcg/ideal, sum(y for y, _, _ in ranked)/relevant))
    result = {"groups": len(values), "ndcg@5": None, "recall@5": None, "bootstrap95": None}
    if values:
        arr = np.array(values)
        result.update({"ndcg@5": float(arr[:, 0].mean()), "recall@5": float(arr[:, 1].mean())})
        if len(values) >= 5:
            rng = np.random.default_rng(42)
            means = np.array([arr[rng.integers(0, len(arr), len(arr))].mean(axis=0) for _ in range(500)])
            result["bootstrap95"] = {name: np.quantile(means[:, col], [.025, .975]).tolist() for col, name in enumerate(("ndcg@5", "recall@5"))}
    return result


def fit(events, now, horizon):
    from sklearn.ensemble import GradientBoostingClassifier
    from sklearn.metrics import average_precision_score, roc_auc_score
    rows, history = samples(events, now, horizon)
    train, test, cutoff = temporal_split(rows, horizon)
    report = {"algorithm": "GradientBoostingClassifier", "features": FEATURES, "rows": len(rows),
              "train": len(train), "test": len(test), "cutoff": cutoff.isoformat() if cutoff else None,
              "horizon_hours": horizon.total_seconds()/3600, "mode": "insufficient-data", "metrics": None}
    if len(train) < 40 or len(test) < 10 or min(sum(r["y"] for r in train), len(train)-sum(r["y"] for r in train)) < 5 or len({r["y"] for r in test}) < 2:
        return None, history, report
    model = GradientBoostingClassifier(n_estimators=80, max_depth=2, learning_rate=.05, random_state=42)
    model.fit([r["x"] for r in train], [r["y"] for r in train])
    probability = model.predict_proba([r["x"] for r in test])[:, 1]
    labels = [r["y"] for r in test]
    def evaluate(scores):
        return {"auc": float(roc_auc_score(labels, scores)), "average_precision": float(average_precision_score(labels, scores)),
                **ranking_metrics(test, scores)}
    report["metrics"] = {"model": evaluate(probability), "past-popularity-baseline": evaluate([r["x"][2] for r in test])}
    # Ablation is fitted on training history, not selected or tuned against the holdout.
    ablated = GradientBoostingClassifier(n_estimators=80, max_depth=2, learning_rate=.05, random_state=42)
    ablated.fit([[r["x"][i] for i in (0, 2, 4)] for r in train], [r["y"] for r in train])
    report["metrics"]["without-personal-conversions"] = evaluate(ablated.predict_proba([[r["x"][i] for i in (0, 2, 4)] for r in test])[:, 1])
    report["cold_start"] = {name: ranking_metrics([r for r in test if (r["x"][0] == 0) == cold],
        [s for r, s in zip(test, probability) if (r["x"][0] == 0) == cold]) for name, cold in (("no-past-exposures", True), ("returning", False))}
    report["mode"] = "trained"
    # Refit for serving only after metrics have been frozen.
    model.fit([r["x"] for r in rows], [r["y"] for r in rows])
    return model, history, report


def query(conn, sql, args=()):
    with conn.cursor() as cursor:
        cursor.execute(sql, args)
        return cursor.fetchall()


def bounded_rows(conn, sql, args, cap, label):
    rows = query(conn, sql + " LIMIT %s", (*args, cap+1))
    if len(rows) > cap: raise ValueError(f"{label} exceeds {cap}; choose a smaller time window or a reviewed higher cap")
    return rows


def load(conn, now, args):
    users = bounded_rows(conn, "SELECT id FROM users WHERE status=1 ORDER BY id", (), args.max_users, "Active users")
    events = bounded_rows(conn, """SELECT id,user_id,target_id,target_type,event_type,event_time,source FROM user_behavior_log
        WHERE source IN ('SERVER','CLIENT') AND target_id IS NOT NULL AND event_time>=%s AND event_time<%s
        ORDER BY event_time,id""", (now-timedelta(days=args.days), now), args.max_events, "Event window")
    candidates = {
        "activity": query(conn, """SELECT id FROM activities WHERE status='PUBLISHED' AND audit_status='APPROVED'
            AND archived_at IS NULL AND start_time>NOW() ORDER BY start_time,id LIMIT %s""", (args.candidates,)),
        "team": query(conn, """SELECT t.id FROM teams t LEFT JOIN activities a ON a.id=t.activity_id WHERE t.status='OPEN'
            AND t.archived_at IS NULL AND (t.end_time IS NULL OR t.end_time>NOW())
            AND (t.activity_id IS NULL OR (a.status='PUBLISHED' AND a.audit_status='APPROVED' AND a.teaming_enabled=1 AND COALESCE(a.end_time,a.start_time)>NOW()))
            ORDER BY t.created_at DESC,t.id LIMIT %s""", (args.candidates,))}
    by_kind = {kind: [Event(int(r["user_id"]), int(r["target_id"]), r["event_time"], r["event_type"], r["source"],int(r["id"]))
                     for r in events if r["target_type"] == kind.upper()] for kind in candidates}
    return users, by_kind, candidates


def activate(conn, version):
    with conn.cursor() as cur:
        cur.execute("SELECT id FROM recommendation_active WHERE id=1 FOR UPDATE")
        cur.execute("SELECT version FROM recommendation_release WHERE version=%s AND expires_at>NOW()", (version,))
        if not cur.fetchone(): raise ValueError("Release missing or expired; it cannot be activated")
        cur.execute("UPDATE recommendation_active SET version=%s WHERE id=1", (version,))
        cur.execute("UPDATE recommend_model_version SET active_flag=(version=%s)", (version,))
    conn.commit()


def publish(conn, users, candidates, models, histories, report, version, now, output):
    import joblib
    if not any(model is not None for model in models.values()):
        return False
    final = output/version
    if final.exists(): raise ValueError("Artifact version already exists; use a new immutable version")
    output.mkdir(parents=True, exist_ok=True)
    # Artifact becomes complete before the database pointer can refer to it.
    with tempfile.TemporaryDirectory(dir=output) as tmp:
        path = Path(tmp)
        joblib.dump({"models": models, "features": FEATURES, "report": report}, path/"model.joblib")
        (path/"metrics.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
        digest = hashlib.sha256((path/"model.joblib").read_bytes()).hexdigest()
        os.rename(path, final)
    try:
        with conn.cursor() as cur:
            cur.execute("SELECT id FROM recommendation_active WHERE id=1 FOR UPDATE")
            cur.execute("INSERT INTO recommendation_release(version,metrics_json,artifact_sha256,expires_at) VALUES(%s,%s,%s,%s)",
                        (version, json.dumps(report), digest, now+timedelta(days=7)))
            for kind, model in models.items():
                if model is None: continue
                ids = [int(row["id"]) for row in candidates[kind]]
                for user in users:
                    uid = int(user["id"])
                    if not ids: continue
                    scores = model.predict_proba([histories[kind].features(uid, item, now) for item in ids])[:, 1]
                    cur.executemany(f"INSERT INTO recommend_{kind}_score(user_id,{kind}_id,score,reason,model_version) VALUES(%s,%s,%s,%s,%s)",
                        [(uid, item, float(score), "Historical conversion model", version) for item, score in zip(ids, scores)])
                cur.execute("INSERT INTO recommend_model_version(model_name,version,metrics_json,active_flag) VALUES(%s,%s,%s,0)",
                            (kind+"-conversion", version, json.dumps(report[kind])))
            cur.execute("UPDATE recommendation_active SET version=%s WHERE id=1", (version,))
            cur.execute("UPDATE recommend_model_version SET active_flag=(version=%s)", (version,))
        conn.commit()
        return True
    except Exception:
        conn.rollback()
        # Completed but unreferenced artifact is safe and retained for diagnosis.
        raise


def main():
    import pymysql
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dry-run", action="store_true", help="read-only database transaction; no DDL, score updates or artifacts")
    parser.add_argument("--version", default="gbdt-"+datetime.now().strftime("%Y%m%d-%H%M%S"))
    parser.add_argument("--activate", help="atomically reactivate an unexpired release; performs no training")
    parser.add_argument("--days", type=int, default=90)
    parser.add_argument("--horizon-hours", type=int, default=24)
    parser.add_argument("--max-events", type=int, default=100000)
    parser.add_argument("--max-users", type=int, default=2000)
    parser.add_argument("--candidates", type=int, default=300)
    parser.add_argument("--output", type=Path, default=ROOT/"models")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}", args.activate or args.version): parser.error("Invalid version")
    if args.dry_run and args.activate: parser.error("--activate cannot be combined with --dry-run")
    if not (1<=args.days<=365 and 1<=args.horizon_hours<=168 and 1<=args.candidates<=2000 and 1<=args.max_users<=10000 and 1<=args.max_events<=1000000): parser.error("Training resource limits out of range")
    with pymysql.connect(host=os.getenv("CAMPUS_DB_HOST", "127.0.0.1"), port=int(os.getenv("CAMPUS_DB_PORT", "3306")),
            user=os.getenv("CAMPUS_DB_USER", os.getenv("DB_USERNAME", "campus_pulse")),
            password=os.getenv("CAMPUS_DB_PASSWORD", os.getenv("DB_PASSWORD", "campus_pulse_123")),
            database=os.getenv("CAMPUS_DB_NAME", os.getenv("MYSQL_DATABASE", "campus_pulse")),
            charset="utf8mb4", cursorclass=pymysql.cursors.DictCursor, autocommit=False, connect_timeout=10, read_timeout=30, write_timeout=30) as conn:
        if args.activate:
            activate(conn, args.activate); print("Activated", args.activate); return
        query(conn, "SET TRANSACTION READ ONLY")
        now = query(conn, "SELECT NOW() AS now")[0]["now"]
        users, events, candidates = load(conn, now, args)
        conn.rollback()
        models, histories, report = {}, {}, {}
        for kind in ("activity", "team"):
            models[kind], histories[kind], report[kind] = fit(events[kind], now, timedelta(hours=args.horizon_hours))
        report["data"] = {"source": "post-migration observations and server conversions; legacy/synthetic excluded",
                          "as_of": now.isoformat(), "window_days": args.days, "users": len(users),
                          "candidate_cap": args.candidates, "max_score_rows": len(users)*sum(map(len, candidates.values())),
                          "limitations": "Observational exposure bias; no causal lift or production accuracy claim. Ranking is within observed user/day groups. Historical profiles are not snapshotted."}
        written = False if args.dry_run else publish(conn, users, candidates, models, histories, report, args.version, now, args.output)
        print(json.dumps({"dry_run": args.dry_run, "published": written, "version": args.version if written else None, **report}, indent=2))

if __name__ == "__main__": main()
