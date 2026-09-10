import importlib.util
from pathlib import Path
import sys
import unittest
from datetime import datetime, timedelta

spec=importlib.util.spec_from_file_location("ranking", Path(__file__).resolve().parents[1]/"train_recommendation.py")
m=importlib.util.module_from_spec(spec);sys.modules[spec.name]=m;spec.loader.exec_module(m)

class TemporalTest(unittest.TestCase):
    def test_future_labels_never_enter_features(self):
        t=datetime(2026,1,1)
        events=[m.Event(1,2,t,"IMPRESSION","CLIENT"),m.Event(1,2,t+timedelta(hours=1),"REGISTER","SERVER")]
        rows,_=m.samples(events,t+timedelta(days=2),timedelta(days=1))
        self.assertEqual(rows[0]["x"],[0]*5)
        self.assertEqual(rows[0]["y"],1)
    def test_forged_conversion_not_a_label_and_censored_exposure_excluded(self):
        t=datetime(2026,1,1)
        events=[m.Event(1,2,t,"IMPRESSION","CLIENT"),m.Event(1,2,t+timedelta(hours=1),"REGISTER","CLIENT"),
                m.Event(1,3,t+timedelta(hours=25),"IMPRESSION","CLIENT")]
        rows,_=m.samples(events,t+timedelta(hours=30),timedelta(days=1))
        self.assertEqual(len(rows),1);self.assertEqual(rows[0]["y"],0)
    def test_same_second_server_conversion_follows_exposure_by_event_id(self):
        t=datetime(2026,1,1)
        events=[m.Event(1,2,t,"IMPRESSION","CLIENT",10),m.Event(1,2,t,"FAVORITE","SERVER",11)]
        rows,_=m.samples(events,t+timedelta(days=2),timedelta(days=1))
        self.assertEqual(rows[0]["x"],[0]*5);self.assertEqual(rows[0]["y"],1)
    def test_training_label_window_is_purged(self):
        t=datetime(2026,1,1);horizon=timedelta(days=2)
        rows=[{"at":t+timedelta(days=i)} for i in range(20)]
        train,test,cutoff=m.temporal_split(rows,horizon)
        self.assertTrue(all(r["at"]+horizon<cutoff for r in train))
        self.assertTrue(all(r["at"]>=cutoff for r in test))
    def test_temporal_training_produces_computed_metrics_on_a_labelled_fixture(self):
        t=datetime(2026,1,1);events=[];sequence=0
        for day in range(30):
            for user in range(8):
                for item in range(3):
                    at=t+timedelta(days=day,hours=user,minutes=item*3)
                    sequence+=1;events.append(m.Event(user,item,at,"IMPRESSION","CLIENT",sequence))
                    if (user+item+day)%3==0:
                        sequence+=1;events.append(m.Event(user,item,at+timedelta(minutes=1),"REGISTER","SERVER",sequence))
        model,_,report=m.fit(events,t+timedelta(days=32),timedelta(hours=1))
        self.assertIsNotNone(model);self.assertEqual(report["mode"],"trained")
        self.assertGreater(report["metrics"]["model"]["groups"],0)
        self.assertTrue(0<=report["metrics"]["model"]["auc"]<=1)
        self.assertIn("past-popularity-baseline",report["metrics"])
    def test_ranking_metrics_use_user_groups(self):
        t=datetime(2026,1,1)
        rows=[{"at":t,"user":1,"item":i,"y":int(i==0)} for i in range(10)]
        result=m.ranking_metrics(rows,list(range(10)))
        self.assertEqual(result["recall@5"],0)
        self.assertEqual(m.ranking_metrics(rows,list(reversed(range(10))))["ndcg@5"],1)

if __name__ == "__main__": unittest.main()
