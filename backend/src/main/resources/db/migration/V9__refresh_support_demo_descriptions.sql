-- Update only unchanged, provenance-bound demonstration descriptions.

UPDATE teams t JOIN content_translation_binding b ON b.content_type='TEAM' AND b.content_id=t.id
SET t.description='准备一个 LangGraph 知识库客服演示，缺负责流程图和测试问题整理的同学。'
WHERE b.seed_key='agent_team' AND BINARY t.description=BINARY '准备一个 Dify 知识库客服演示，缺负责流程图和测试问题整理的同学。';

UPDATE teams t JOIN content_translation_binding b ON b.content_type='TEAM' AND b.content_id=t.id
SET t.description='围绕 LangGraph 客服准备常见问题、兜底问题和转人工测试用例。'
WHERE b.seed_key='qa_team' AND BINARY t.description=BINARY '围绕 Dify 客服准备常见问题、兜底问题和转人工测试用例。';
