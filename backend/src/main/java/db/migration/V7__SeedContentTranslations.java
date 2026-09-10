package db.migration;

import com.campuspulse.content.ContentCatalog;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.zip.CRC32;

/** Adds provenance only. The existing activity, team, tag and initialization data is read-only. */
public class V7__SeedContentTranslations extends BaseJavaMigration {
    @Override public Integer getChecksum() {
        try (var input = ContentCatalog.class.getResourceAsStream(ContentCatalog.RESOURCE)) {
            if (input == null) throw new IllegalStateException("Content catalog missing");
            CRC32 checksum = new CRC32();
            checksum.update(input.readAllBytes());
            return (int) checksum.getValue();
        } catch (java.io.IOException e) { throw new IllegalStateException(e); }
    }

    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        try (var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE content_translation_binding (
                        content_type VARCHAR(16) NOT NULL,
                        content_id BIGINT NOT NULL,
                        seed_key VARCHAR(64) NOT NULL,
                        PRIMARY KEY(content_type, content_id),
                        UNIQUE KEY uq_translation_seed(content_type, seed_key)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        }
        for (ContentCatalog.Entry entry : new ContentCatalog(false).entries()) bindExisting(connection, entry);
    }

    private void bindExisting(Connection connection, ContentCatalog.Entry entry) throws SQLException {
        boolean activity = "ACTIVITY".equals(entry.type());
        String table = activity ? "activities" : "teams";
        String owner = activity ? "organizer_id" : "creator_id";
        // Only records predating completed demo initialization can be adopted. A full exact source
        // match and original owner are required; later user copies, even with the same title, do not qualify.
        String sql = """
                INSERT INTO content_translation_binding(content_type, content_id, seed_key)
                SELECT ?, MIN(c.id), ? FROM %s c JOIN users u ON u.id=c.%s
                JOIN app_initialization i ON i.name='demo-v1' AND i.completed=1
                WHERE BINARY c.title=BINARY ? AND BINARY c.description=BINARY ?
                  AND u.username=? AND c.created_at<=i.completed_at
                %s
                HAVING COUNT(*)=1
                """.formatted(table, owner, activity ? "AND BINARY c.location=BINARY ?" : "");
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, entry.type());
            statement.setString(2, entry.key());
            statement.setString(3, entry.source().get("title"));
            statement.setString(4, entry.source().get("description"));
            statement.setString(5, entry.owner());
            if (activity) statement.setString(6, entry.source().get("location"));
            statement.executeUpdate();
        }
    }
}
