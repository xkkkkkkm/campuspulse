"""Opt-in verification on a disposable demo stack, preserving its prior active release.
Run with CAMPUS_TEST_DATABASE=1 and CAMPUS_DB_* pointing at the isolated database.
All inserted release rows are named test-publication-<uuid> and removed afterward.
"""
from datetime import datetime, timedelta
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import sys
import tempfile
import uuid

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("ranking",ROOT/"train_recommendation.py")
m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m)


def main():
    import pymysql
    from sklearn.ensemble import GradientBoostingClassifier
    if os.getenv("CAMPUS_TEST_DATABASE")!="1":raise SystemExit("Set CAMPUS_TEST_DATABASE=1 only for a disposable test database")
    conn=pymysql.connect(host=os.getenv("CAMPUS_DB_HOST","127.0.0.1"),port=int(os.getenv("CAMPUS_DB_PORT","3306")),
        user=os.getenv("CAMPUS_DB_USER","campus_pulse"),password=os.environ["CAMPUS_DB_PASSWORD"],
        database=os.getenv("CAMPUS_DB_NAME","campus_pulse"),cursorclass=pymysql.cursors.DictCursor,autocommit=False)
    versions=["test-publication-"+uuid.uuid4().hex for _ in range(3)]
    original=m.query(conn,"SELECT version FROM recommendation_active WHERE id=1")[0]["version"]
    flags=m.query(conn,"SELECT id,active_flag FROM recommend_model_version")
    users=m.query(conn,"SELECT id FROM users ORDER BY id LIMIT 2")
    candidates={kind:m.query(conn,f"SELECT id FROM {'activities' if kind=='activity' else 'teams'} ORDER BY id LIMIT 2") for kind in ("activity","team")}
    assert users and all(candidates.values()),"Demo seed data is required"
    conn.rollback()
    now=m.query(conn,"SELECT NOW() AS now")[0]["now"];conn.rollback()
    model=GradientBoostingClassifier(n_estimators=3,random_state=42).fit([[0]*5,[1]*5,[2]*5,[3]*5],[0,0,1,1])
    models={kind:model for kind in candidates};histories={kind:m.History([]) for kind in candidates}
    report={kind:{"synthetic":True,"purpose":"publication protocol test, not model evaluation"} for kind in candidates}
    try:
        with tempfile.TemporaryDirectory() as d:
            out=Path(d)
            assert m.publish(conn,users,candidates,models,histories,report,versions[0],now,out)
            assert m.query(conn,"SELECT version FROM recommendation_active WHERE id=1")[0]["version"]==versions[0]
            expected=hashlib.sha256((out/versions[0]/"model.joblib").read_bytes()).hexdigest()
            assert m.query(conn,"SELECT artifact_sha256 FROM recommendation_release WHERE version=%s",(versions[0],))[0]["artifact_sha256"]==expected
            conn.rollback()
            class FailingCursor:
                def __enter__(self):self.cursor=conn.cursor();return self
                def __exit__(self,*args):self.cursor.close()
                def execute(self,*args,**kwargs):return self.cursor.execute(*args,**kwargs)
                def executemany(self,sql,values):
                    if "recommend_team_score" in sql:raise RuntimeError("Simulated failure after activity scores")
                    return self.cursor.executemany(sql,values)
            class FailingConnection:
                def cursor(self):return FailingCursor()
                def commit(self):return conn.commit()
                def rollback(self):return conn.rollback()
            try:m.publish(FailingConnection(),users,candidates,models,histories,report,versions[1],now,out)
            except RuntimeError:pass
            else:raise AssertionError("Failure injection did not execute")
            assert m.query(conn,"SELECT version FROM recommendation_active WHERE id=1")[0]["version"]==versions[0]
            assert not m.query(conn,"SELECT version FROM recommendation_release WHERE version=%s",(versions[1],))
            assert not m.query(conn,"SELECT user_id FROM recommend_activity_score WHERE model_version=%s",(versions[1],))
            conn.rollback()
            assert m.publish(conn,users,candidates,models,histories,report,versions[2],now,out)
            m.activate(conn,versions[0])
            assert m.query(conn,"SELECT version FROM recommendation_active WHERE id=1")[0]["version"]==versions[0]
            m.query(conn,"UPDATE recommendation_release SET expires_at=NOW()-INTERVAL 1 DAY WHERE version=%s",(versions[2],));conn.commit()
            try:m.activate(conn,versions[2])
            except ValueError:conn.rollback()
            else:raise AssertionError("Expired release was activated")
        print(json.dumps({"publication":"passed","atomic_failure_rollback":"passed","activation_rollback":"passed","expired_release_rejected":"passed","artifact_hash":"passed"},indent=2))
    finally:
        conn.rollback()
        m.query(conn,"UPDATE recommendation_active SET version=%s WHERE id=1",(original,))
        for version in versions:
            for table in ("recommend_activity_score","recommend_team_score"):
                m.query(conn,f"DELETE FROM {table} WHERE model_version=%s",(version,))
            m.query(conn,"DELETE FROM recommend_model_version WHERE version=%s",(version,))
            m.query(conn,"DELETE FROM recommendation_release WHERE version=%s",(version,))
        for row in flags:m.query(conn,"UPDATE recommend_model_version SET active_flag=%s WHERE id=%s",(row["active_flag"],row["id"]))
        conn.commit();conn.close()

if __name__=="__main__":main()
