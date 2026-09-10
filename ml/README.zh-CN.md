# 离线推荐训练

[English](README.md) · [运行与运维](../docs/operations.zh-CN.md) · [验证记录](../docs/remediation-status.zh-CN.md)

`train_recommendation.py` 根据历史曝光和服务端转化，分别训练活动与队伍的 **GradientBoostingClassifier**，输出转化概率并写入供 Java API 读取的推荐分表。当前不实现 LambdaMART、深度推荐网络或语义向量检索，历史 `item_embedding` 表不参与本训练流程。

无需训练也能运行系统：在线按兴趣、时间、热度、精选权重和名额进行规则排序。有有效模型分数时使用 `概率 × 100 + 规则分 × 0.25`；用户/内容没有分数或版本过期时保留规则推荐。API 请求不会启动 Python 或加载模型文件。

## 安装与只读评估

建议使用 Python 3.11 或 3.12 的虚拟环境，在项目根目录执行：

```bash
python3 -m venv .venv
.venv/bin/python -m pip install -r ml/requirements.txt

# 先启动后端完成 Flyway 建表/迁移
# 只读数据库，在内存训练，不写推荐分或模型文件
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --dry-run

# 单元测试，不依赖运行中的数据库
.venv/bin/python -m unittest discover -s ml/tests
```

Windows 将 `.venv/bin/python` 替换为 `.venv/Scripts/python.exe`。依赖版本固定在 `requirements.txt`。环境工具把 `.env` 数据库配置映射为 `CAMPUS_DB_HOST`、`CAMPUS_DB_PORT`、`CAMPUS_DB_USER`、`CAMPUS_DB_PASSWORD`、`CAMPUS_DB_NAME`，进程环境变量优先。连接外部数据库时可直接配置这些 `CAMPUS_DB_*` 变量。

`--dry-run` **仍需要连接数据库**，在只读事务中查询输入，在内存中训练并输出 JSON。它不执行 DDL，不写数据库分数、版本或模型/报告文件。数据库结构由 Flyway 管理。

新建演示库通常返回 `mode: "insufficient-data"`、`published: false`，因为合成演示和旧版行为被排除。不能把演示分数或历史示例指标当作模型实验结果。

数据库发布协议检查见[隔离集成演练](../docs/operations.zh-CN.md#隔离集成演练)。`ml/tests/integration_publication.py` 要求 `CAMPUS_TEST_DATABASE=1`，临时写入合成测试版本并恢复原 active 版本；只应连接明确隔离的测试数据库，不等同于训练脚本的只读 dry-run。

## 样本、特征与时序验证

仅查询窗口内标记为 `SERVER` 或 `CLIENT` 的行为；迁移前记录为 `LEGACY`，合成演示使用独立来源。前端只能上报观察行为，可信转化由后端业务事务生成。

- 曝光为 `IMPRESSION`、`FEATURED_IMPRESSION`。
- 同一用户/内容在曝光之后、标签窗口内出现服务端 `REGISTER`、`FAVORITE`、`TEAM_APPLY`、`TEAM_JOIN`，记为正例。
- 标签窗口已成熟但没有上述转化，记为负例；默认窗口 24 小时，未成熟曝光不进入样本。
- 每个用户/内容/自然日最多保留一个曝光样本。
- 特征为历史用户曝光、用户转化、内容转化、用户–内容转化及内容曝光次数的 `log1p`。

事件按 `(event_time, id)` 排序，同秒较晚事件不会进入较早曝光的特征。由于没有历史快照，训练不使用当前画像标签或当前热度来还原过去；在线规则仍可使用当前兴趣和热度。点击不直接当作正标签，也不制造样本来让演示库可训练。

按时间约 75% 的分位划分训练与验证，移除标签窗口跨入验证期的训练样本。至少需要 40 个训练样本、10 个验证样本，训练中每类至少五个，验证中两类均存在。否则该实体类型不训练、不发布模型。

报告包括 ROC-AUC、平均精确率，以及已观察到的用户/自然日分组 NDCG@5、Recall@5。排序分组至少有两条曝光和一个正例；至少五个有效分组才生成 95% bootstrap 区间，否则相关指标为 `null`。报告同时给出历史内容转化热度基线、移除个人转化特征后的消融模型，以及无历史曝光/回访用户分组。冻结验证指标后，才用全部成熟样本重训用于服务的模型。

这些指标来自观察性曝光，受曝光选择偏差、样本稀疏、单次时序划分和特征不足影响，不代表完整候选库效果、因果提升或生产准确率。脚本仅检查数据是否足够，没有自动设定效果达标门槛，发布前应审阅实际报告。

## 资源边界、发布与回滚

默认读取 90 天历史，最多 100,000 条事件、2,000 个有效用户，每类最多 300 个候选。活动优先选择未来可用活动，队伍优先选择近期开放队伍。事件或用户超出上限会报错退出，不静默截断；候选集本身明确采用有界筛选，不覆盖全库。

```bash
# 先审阅只读报告
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --dry-run

# 数据足够时发布一个全新的版本
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --version review-run-001
```

产物默认在 `ml/models/<version>/`，包括 `model.joblib` 与 `metrics.json`，均属于本地忽略文件。先完成不可覆盖的版本目录，再在一个数据库事务中写入版本元数据、分数、模型记录与 active 指针；元数据记录模型 SHA-256。数据库失败可能留下未被引用的完整文件目录，但不会激活部分分数。

若活动和队伍都缺少数据，不生成新发布，既有版本保持不变；只有一类可训练时可发布该类，另一类缺失分数继续使用规则。版本自创建起七天过期；服务端只读取 active 且未过期版本。

重新启用先前**尚未过期**的版本：

```bash
python3 tools/dev.py run .venv/bin/python ml/train_recommendation.py --activate review-run-001
```

回滚仅原子修改 active 指针，不训练，也不延长有效期。不存在或过期版本会被拒绝；`--activate` 不能与 `--dry-run` 同用。当前没有自动训练调度器或模型注册服务。

运行 `--help` 可查看 `--days`、`--horizon-hours`、`--max-events`、`--max-users`、`--candidates`、`--output`。提高上限会增加内存、评分及数据库写入量；默认两类总共最多产生约 120 万条分数。当前是同步执行的有界训练流程，没有吞吐保证。

查看数据可配置独立只读账号；正式发布需分数/版本写权限，训练不应负责建表。应用备份维护窗口中需暂停独立训练写入。数据库备份包含分表与版本元数据，不含本地模型文件，应单独保管产物。仅加载可信 `joblib` 文件；Java 服务并不反序列化它们。
