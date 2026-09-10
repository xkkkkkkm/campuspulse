package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.List;

/** Supports pre-Flyway course databases without discarding existing records. */
public class V2__UpgradeLegacySchema extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();
        try(var in=getClass().getResourceAsStream("/db/migration/V1__baseline.sql")) {
            if(in==null) throw new IllegalStateException("Baseline schema missing");
            String schema=new String(in.readAllBytes(),StandardCharsets.UTF_8).replaceAll("(?m)^--.*$","");
            for(String sql:schema.split(";")) if(!sql.isBlank()) try(var stmt=c.createStatement()){stmt.execute(sql);}
        }
        for(String definition:List.of(
                "activities.chat_enabled TINYINT NOT NULL DEFAULT 0",
                "activities.teaming_enabled TINYINT NOT NULL DEFAULT 1",
                "activities.version BIGINT NOT NULL DEFAULT 0",
                "activities.audit_status VARCHAR(16) NOT NULL DEFAULT 'APPROVED'",
                "teams.version BIGINT NOT NULL DEFAULT 0",
                "teams.start_time DATETIME NULL", "teams.end_time DATETIME NULL",
                "users.campus VARCHAR(32) NULL", "users.major VARCHAR(128) NULL",
                "users.education_level VARCHAR(16) NULL", "users.bio VARCHAR(300) NULL",
                "users.email_verified TINYINT NOT NULL DEFAULT 0", "users.phone_verified TINYINT NOT NULL DEFAULT 0",
                "users.failed_login_count INT NOT NULL DEFAULT 0", "users.lock_until DATETIME NULL")) {
            int dot=definition.indexOf('.'),space=definition.indexOf(' ');
            String table=definition.substring(0,dot),column=definition.substring(dot+1,space);
            try(var query=c.prepareStatement("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?")) {
                query.setString(1,table);query.setString(2,column);
                try(var rs=query.executeQuery()) {rs.next();if(rs.getInt(1)==0) try(var stmt=c.createStatement()){stmt.execute("ALTER TABLE "+table+" ADD COLUMN "+definition.substring(dot+1));}}
            }
        }
        try(var stmt=c.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS app_initialization (name VARCHAR(64) PRIMARY KEY, completed TINYINT NOT NULL DEFAULT 0, completed_at DATETIME NULL)");
            stmt.execute("INSERT IGNORE INTO app_initialization(name) VALUES ('demo-v1'),('production-admin')");
            // An existing course database is preserved, never re-seeded on adoption.
            stmt.execute("UPDATE app_initialization SET completed=1, completed_at=NOW() WHERE name='demo-v1' AND EXISTS(SELECT 1 FROM users WHERE username IN ('linzhixia','org','admin'))");
            stmt.execute("INSERT IGNORE INTO tags(name,type) VALUES ('运动健身','HOME'),('学术讲座','HOME'),('文艺演出','HOME'),('志愿服务','HOME'),('竞赛组队','HOME'),('兼职实习','HOME'),('兴趣社交','HOME'),('考研搭子','HOME'),('校园生活','HOME'),('其他','OTHER')");
        }
    }
}
