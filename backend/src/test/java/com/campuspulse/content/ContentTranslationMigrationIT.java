package com.campuspulse.content;

import com.campuspulse.bootstrap.Bootstrap;
import com.campuspulse.security.PasswordHasher;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class ContentTranslationMigrationIT {
    @Container static MySQLContainer<?> mysql = new MySQLContainer<>(System.getenv().getOrDefault("TEST_MYSQL_IMAGE", "mysql:8.0"))
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_unicode_ci");
    private DriverManagerDataSource dataSource;
    private JdbcTemplate db;
    private final ContentCatalog catalog = new ContentCatalog();

    @BeforeEach void setup() {
        dataSource = new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        Flyway.configure().dataSource(dataSource).cleanDisabled(false).load().clean();
        db = new JdbcTemplate(dataSource);
    }

    @Test void upgradeFromVersionSixKeepsDataAndBindsOnlyOriginalPreInitializationContent() {
        Flyway.configure().dataSource(dataSource).target("6").load().migrate();
        db.update("INSERT INTO users(id,username,password_hash,nickname) VALUES(1,'org','saved-password','Saved organizer')");
        insertActivity(11, "ai", "2026-01-01 11:00:00");
        insertActivity(12, "music", "2026-01-01 11:00:00");
        db.update("UPDATE activities SET title='My revised event',description='My own description',version=19 WHERE id=12");
        insertActivity(13, "ai", "2026-01-01 13:00:00"); // Exact later copy is user content.
        insertActivity(14, "ai", "2026-01-01 11:00:00");
        db.update("UPDATE activities SET description='A different event with the same title' WHERE id=14");
        var team = catalog.get("TEAM", "ai_team");
        db.update("INSERT INTO teams(id,title,description,creator_id,activity_id,created_at) VALUES(11,?,?,1,11,'2026-01-01 11:00:00')",
                team.source().get("title"), team.source().get("description"));
        db.update("UPDATE app_initialization SET completed=1,completed_at='2026-01-01 12:00:00' WHERE name='demo-v1'");
        List<Map<String,Object>> savedActivities = db.queryForList("SELECT * FROM activities ORDER BY id");
        List<Map<String,Object>> savedTeams = db.queryForList("SELECT * FROM teams ORDER BY id");

        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(status -> new Bootstrap(db, new PasswordHasher()).run());

        assertEquals(savedActivities, db.queryForList("SELECT * FROM activities ORDER BY id"));
        assertEquals(savedTeams, db.queryForList("SELECT * FROM teams ORDER BY id"));
        assertEquals("saved-password", db.queryForObject("SELECT password_hash FROM users WHERE id=1", String.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM content_translation_binding", Integer.class));
        var loaded = new ContentTranslationService(db).load(List.of(
                new ContentTranslationService.Identity("ACTIVITY", 11), new ContentTranslationService.Identity("TEAM", 11),
                new ContentTranslationService.Identity("ACTIVITY", 12), new ContentTranslationService.Identity("ACTIVITY", 13),
                new ContentTranslationService.Identity("ACTIVITY", 14)));
        assertEquals(2, loaded.size());
        assertEquals("AI and the Future of Society", loaded.get(new ContentTranslationService.Identity("ACTIVITY", 11)).en().get("title"));
        assertEquals("ai_team", loaded.get(new ContentTranslationService.Identity("TEAM", 11)).key());
        Flyway.configure().dataSource(dataSource).target("7").load().validate();
    }

    @Test void freshDemoHasAllTranslationsAndRestartPreservesUserEdits() {
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        var transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        var bootstrap = new Bootstrap(db, new PasswordHasher());
        transaction.executeWithoutResult(status -> bootstrap.run());
        assertEquals(40, db.queryForObject("SELECT COUNT(*) FROM activities", Integer.class));
        assertEquals(33, db.queryForObject("SELECT COUNT(*) FROM teams", Integer.class));
        assertEquals(73, db.queryForObject("SELECT COUNT(*) FROM content_translation_binding", Integer.class));
        long id = db.queryForObject("SELECT content_id FROM content_translation_binding WHERE content_type='ACTIVITY' AND seed_key='ai'", Long.class);
        db.update("UPDATE activities SET title='User revised title',location='New venue',version=23 WHERE id=?", id);
        transaction.executeWithoutResult(status -> bootstrap.run());
        assertEquals("User revised title", db.queryForObject("SELECT title FROM activities WHERE id=?", String.class, id));
        assertEquals("New venue", db.queryForObject("SELECT location FROM activities WHERE id=?", String.class, id));
        assertEquals(23, db.queryForObject("SELECT version FROM activities WHERE id=?", Integer.class, id));
        var entry = new ContentTranslationService(db).load(List.of(new ContentTranslationService.Identity("ACTIVITY", id)))
                .get(new ContentTranslationService.Identity("ACTIVITY", id));
        assertNull(entry.translate("title", "User revised title"));
        assertNull(entry.translate("location", "New venue"));
    }


    @Test void englishAndChineseSearchWorkAcrossLobbiesAndGlobalSearchWithoutStaleMatches() {
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        db.update("INSERT INTO users(id,username,password_hash,nickname) VALUES(1,'org','password','Organizer')");
        insertActivity(11, "ai", "2026-01-01 11:00:00");
        insertActivity(12, "ai", "2026-01-01 11:00:00"); // User copy with identical source and no seed binding.
        db.update("UPDATE activities SET start_time=NOW()+INTERVAL 2 DAY,end_time=NOW()+INTERVAL 3 DAY");
        var teamSource = catalog.get("TEAM", "ai_team").source();
        db.update("INSERT INTO teams(id,title,description,creator_id,activity_id) VALUES(11,?,?,1,11),(12,?,?,1,NULL)",
                teamSource.get("title"), teamSource.get("description"), teamSource.get("title"), teamSource.get("description"));
        var translationService = new ContentTranslationService(db);
        translationService.bindSeeds("ACTIVITY", Map.of("ai", 11L));
        translationService.bindSeeds("TEAM", Map.of("ai_team", 11L));
        var repository = new com.campuspulse.activity.ActivityRepository(db);
        var activityService = new com.campuspulse.activity.ActivityService(db, repository, org.mockito.Mockito.mock(com.campuspulse.upload.UploadService.class));
        var teamService = new com.campuspulse.team.TeamService(db, new com.campuspulse.team.TeamRepository(db, repository), repository);
        var search = new com.campuspulse.search.SearchController(db, repository);
        String activityEnglish = catalog.get("ACTIVITY", "ai").en().get("title");
        String teamEnglish = catalog.get("TEAM", "ai_team").en().get("title");
        String activityChinese = catalog.get("ACTIVITY", "ai").source().get("title");

        assertEquals(List.of(11L), activityService.list(activityEnglish, null, null, "all", null, 1, 10).data().items().stream().map(com.campuspulse.activity.ActivityService.ActivityCard::id).toList());
        assertEquals(2, activityService.list(activityChinese, null, null, "all", null, 1, 10).data().total());
        assertEquals(List.of(11L), teamService.page(null, teamEnglish, null, 1, 10).data().items().stream().map(com.campuspulse.team.TeamService.TeamCard::id).toList());
        assertEquals(2, teamService.page(null, teamSource.get("title"), null, 1, 10).data().total());
        assertEquals(1, teamService.page(null, activityEnglish, null, 1, 10).data().total());
        assertEquals(1, search.search(activityEnglish.toLowerCase(java.util.Locale.ROOT), "activity", 1, 10).data().total());
        assertEquals(1, search.search(teamEnglish, "team", 1, 10).data().total());
        assertEquals(2, search.search(activityEnglish, "all", 1, 1).data().total());
        var first = search.search(activityEnglish, "all", 1, 1).data().items().get(0);
        var second = search.search(activityEnglish, "all", 2, 1).data().items().get(0);
        assertNotEquals(first.targetType(), second.targetType());
        assertEquals(0, search.search("' OR 1=1 --", "all", 1, 10).data().total());
        assertThrows(com.campuspulse.common.Api.ApiException.class, () -> search.search("x".repeat(201), "all", 1, 10));

        db.update("UPDATE activities SET title='用户更新后的活动' WHERE id=11");
        assertEquals(0, activityService.list(activityEnglish, null, null, "all", null, 1, 10).data().total());
        assertEquals(0, teamService.page(null, activityEnglish, null, 1, 10).data().total());
        assertEquals(0, search.search(activityEnglish, "all", 1, 10).data().total());
        assertEquals(1, search.search("用户更新后的活动", "activity", 1, 10).data().total());
        assertEquals(1, search.search(teamEnglish, "team", 1, 10).data().total());
        db.update("UPDATE teams SET title='用户更新后的队伍' WHERE id=11");
        assertEquals(0, teamService.page(null, teamEnglish, null, 1, 10).data().total());
        assertEquals(0, search.search(teamEnglish, "team", 1, 10).data().total());
        assertEquals(1, search.search("用户更新后的队伍", "team", 1, 10).data().total());
    }

    @Test void englishPlatformTagsFindUserContentWithoutDemoBindings() {
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        db.update("INSERT INTO users(id,username,password_hash,nickname) VALUES(1,'writer','password','Writer')");
        db.update("INSERT INTO activities(id,title,description,location,organizer_id,start_time,end_time) VALUES(11,'自建活动','自己填写的说明','学校',1,NOW()+INTERVAL 2 DAY,NOW()+INTERVAL 3 DAY)");
        db.update("INSERT INTO teams(id,title,description,creator_id) VALUES(11,'自建队伍','自己填写的说明',1)");
        long tag = db.queryForObject("SELECT id FROM tags WHERE name='运动健身'", Long.class);
        db.update("INSERT INTO activity_tag(activity_id,tag_id) VALUES(11,?)", tag);
        db.update("INSERT INTO team_tag(team_id,tag_id) VALUES(11,?)", tag);
        var repository = new com.campuspulse.activity.ActivityRepository(db);
        var search = new com.campuspulse.search.SearchController(db, repository);
        assertEquals(2, search.search("Sports & Fitness", "all", 1, 10).data().total());
        assertEquals(2, search.search("运动健身", "all", 1, 10).data().total());
        var activities = new com.campuspulse.activity.ActivityService(db, repository, org.mockito.Mockito.mock(com.campuspulse.upload.UploadService.class));
        var teams = new com.campuspulse.team.TeamService(db, new com.campuspulse.team.TeamRepository(db, repository), repository);
        assertEquals(1, activities.list(null,"Sports & Fitness",null,"all",null,1,10).data().total());
        assertEquals(1, teams.page(null,null,"Sports & Fitness",1,10).data().total());
        db.update("UPDATE tags SET name='自定义标签' WHERE id=?", tag);
        assertEquals(0, search.search("Sports & Fitness", "all", 1, 10).data().total());
    }

    @Test void supportDemoRefreshPreservesUserEditsAndFrozenV7Checksum() {
        Flyway.configure().dataSource(dataSource).target("7").load().migrate();
        db.update("INSERT INTO users(id,username,password_hash,nickname) VALUES(1,'wangyi','password','Writer')");
        var old = new ContentCatalog(false);
        db.update("INSERT INTO teams(id,title,description,creator_id) VALUES(11,?,?,1),(12,?,'My updated team description',1)",
                old.get("TEAM","qa_team").source().get("title"),old.get("TEAM","qa_team").source().get("description"),old.get("TEAM","agent_team").source().get("title"));
        db.update("INSERT INTO content_translation_binding(content_type,content_id,seed_key) VALUES('TEAM',11,'qa_team'),('TEAM',12,'agent_team')");
        Flyway.configure().dataSource(dataSource).load().migrate();
        assertEquals(catalog.get("TEAM","qa_team").source().get("description"),db.queryForObject("SELECT description FROM teams WHERE id=11",String.class));
        assertEquals("My updated team description",db.queryForObject("SELECT description FROM teams WHERE id=12",String.class));
        Flyway.configure().dataSource(dataSource).load().validate();
    }

    private void insertActivity(long id, String key, String createdAt) {
        var source = catalog.get("ACTIVITY", key).source();
        db.update("""
                INSERT INTO activities(id,title,description,location,organizer_id,start_time,end_time,created_at)
                VALUES(?,?,?,?,1,'2027-01-01 12:00:00','2027-01-01 14:00:00',?)
                """, id, source.get("title"), source.get("description"), source.get("location"), createdAt);
    }
}
