# Offline Recommendation Pipeline

[简体中文](README.zh-CN.md) · [Architecture](../docs/architecture.md) · [Operations](../docs/operations.md) · [Validation record](../docs/remediation-status.md)

`train_recommendation.py` trains separate activity and team **GradientBoostingClassifier** models from historical exposures and server-recorded conversions. It predicts conversion probability and exports scores consumed by the Java API. It is a small tabular model, not LambdaMART, a neural recommendation system or semantic vector retrieval. The legacy `item_embedding` table is not used by this pipeline.

The application runs without any training: rules rank eligible activities and teams using interests, time, popularity, featured weights and available places. If there is no active, unexpired score for an item/user, the API keeps the rule score. Where a model score exists, the API combines `probability × 100 + ruleScore × 0.25`. It does not run Python or load a model file for each API request.

## Setup and read-only evaluation

Use Python 3.11 or 3.12 in a virtual environment. From the repository root:

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r ml/requirements.txt

# The API must have created/migrated the database first.
# Reads database observations; trains in memory; writes no scores or model files.
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --dry-run

# Unit tests; no running database required
.venv/bin/python -m unittest discover -s ml/tests
```

On Windows, replace `.venv/bin/python` with `.venv/Scripts/python.exe`. `requirements.txt` pins the Python dependencies. The helper loads local `.env` values and maps them to `CAMPUS_DB_HOST`, `CAMPUS_DB_PORT`, `CAMPUS_DB_USER`, `CAMPUS_DB_PASSWORD`, `CAMPUS_DB_NAME`. Explicit process environment values take precedence. For an external database, set those `CAMPUS_DB_*` variables directly.

`--dry-run` connects to MySQL and uses a read-only transaction for input queries. It may train models in memory and prints a JSON report, but makes no schema changes, score writes, release changes or model/report artifact writes. It is not an offline no-database mode. The schema remains the responsibility of Flyway.

A fresh demo will normally report `mode: "insufficient-data"` and `published: false`. This is expected: synthetic fixtures and legacy telemetry are excluded from the data used for evaluation. Do not present fixture scores or historical demo metrics as model results.

For the database publication protocol check, see [isolated integration exercises](../docs/operations.md#isolated-integration-exercises). `ml/tests/integration_publication.py` requires `CAMPUS_TEST_DATABASE=1`, temporarily writes synthetic test releases and restores the previous active version. Run it only with explicitly isolated test database settings; it is separate from the read-only trainer dry run.

## Data and labels

The query loads only `SERVER` and `CLIENT` behavior events after the configured window start. Older migrated observations are marked `LEGACY`; synthetic demo observations use a separate source. Client telemetry accepts observations such as exposures and clicks, while trusted conversion events are emitted by application business transactions.

- Exposures: `IMPRESSION`, `FEATURED_IMPRESSION`.
- Positive label: at least one `SERVER` conversion (`REGISTER`, `FAVORITE`, `TEAM_APPLY`, `TEAM_JOIN`) for the same user/item after exposure within the label horizon.
- Negative label: a mature exposure without such a conversion in that horizon.
- Default horizon: 24 hours. Exposures whose label window has not elapsed are excluded.
- Deduplication: at most one exposure sample per user/item/calendar day.

Features are `log1p` counts of past user exposures, past user conversions, past item conversions, past user–item conversions, and past item exposures. Event ordering uses `(event_time, id)`, so later events within the same second do not leak into an earlier feature vector. Historical evaluation excludes mutable current profile tags and current item popularity because there are no historical snapshots of those fields.

The model does not use clicks as positive labels, current interests as historical features, or fabricated labels to make a demo look trainable. The online rule ranking separately uses current interests and popularity.

## Temporal evaluation

Samples are split chronologically at approximately the 75th percentile. Training samples whose conversion window overlaps the holdout are purged. Training requires at least 40 training samples, 10 holdout samples, five examples of each class in training and both classes in the holdout. Otherwise that entity type remains `insufficient-data` and has no model to publish.

The report contains ROC-AUC, average precision, and ranking NDCG@5/Recall@5 for observed user/day groups. Ranking groups require at least two observed items and at least one positive outcome. A 95% bootstrap interval is produced only with at least five eligible groups; missing metrics are `null`, not invented zeros.

Comparisons use a past-item-conversion popularity baseline and a separately fitted ablation excluding personal conversion counts. The report also separates users with no prior exposure from returning users. Only after holdout metrics are frozen does the serving model refit on all mature samples.

These are observational results on logged exposures. They do not measure the full unobserved catalog, causal uplift or production accuracy. Exposure-selection bias, sparse groups, a single holdout, limited features and no historical content/profile snapshots constrain interpretation. The script does not impose a minimum quality threshold beyond the data sufficiency checks; review its actual report before choosing to publish.

## Bounded training and publishing

Defaults are a 90-day history window, at most 100,000 events, at most 2,000 active users and at most 300 candidates of each entity type. Candidate selection prioritizes upcoming eligible activities and recent open teams. An event/user cap overflow aborts rather than silently truncating the training data. Candidate selection is intentionally bounded and does not score the whole catalog.

```bash
# First inspect the read-only report.
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --dry-run

# Explicitly publish a newly named immutable version if data is sufficient.
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --version review-run-001
```

The version must be new. The output defaults to `ml/models/<version>/model.joblib` and `metrics.json`; both are local ignored artifacts. Publication first completes that artifact directory, then writes release metadata, scores, model metadata and the active pointer in a single database transaction. The artifact SHA-256 is stored as metadata. A database failure can leave a completed, unreferenced artifact for diagnosis; it does not activate a partial score release.

If neither model has sufficient data, publication returns `false` and leaves the existing release unchanged. If one model trains, the release may contain only that type; missing scores for the other type use online rules. Releases expire seven days after creation. Scores are filtered through the active release and its expiry at serving time; expired scores cannot keep serving indefinitely.

To reactivate a previous **unexpired** release:

```bash
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --activate review-run-001
```

This changes the database pointer atomically, without training, and does not extend the expiry. An absent or expired version is rejected. `--activate` cannot be combined with `--dry-run`. There is no implemented automatic retraining schedule or model-registry service.

Supported limits are exposed by `--help`: `--days`, `--horizon-hours`, `--max-events`, `--max-users`, `--candidates`, `--output`. Increasing them increases memory, scoring and database-write cost; defaults imply up to 1.2 million scores across both types. Training is synchronous and intended for a bounded single-instance project, with no throughput guarantee.

## Artifact and data handling

Use a separate read-only database account for inspection if appropriate; publishing requires score/release write privileges. Do not give the trainer schema-management duties. Stop independent training/publishing during the application backup maintenance window so the database and upload snapshot have no unexpected writer. Back up model artifacts separately from the application dump, which contains score and release tables but not local Python files. Only load trusted `joblib` artifacts: the Java serving path does not need to deserialize them.
