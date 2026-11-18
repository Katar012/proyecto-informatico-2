package local.smartbiz;

import com.mongodb.client.MongoClients;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_MONGODB_URI", matches=".+")
class MigrationIntegrationTest {
    @Test void incompatibleExistingDocumentsFailWithoutChangingDataOrRecordingMigration() {
        try (var client = MongoClients.create(System.getenv("TEST_MONGODB_URI"))) {
            for (String collection : List.of("users", "categories", "products", "sales", "movements")) {
                // Only this randomly named database is dropped, never the configured test database.
                String database = "smartbiz_v001_test_" + UUID.randomUUID().toString().replace("-", "");
                var mongo = new MongoTemplate(client, database);
                try {
                    Document existing = new Document("_id", "incompatible-existing");
                    if (collection.equals("products")) {
                        existing.append("code", "OLD").append("name", "Existing product")
                                .append("categoryId", "1234567890abcdef12345678").append("price", 100L).append("stock", -1L);
                    }
                    mongo.getCollection(collection).insertOne(existing);
                    var migration = new V001(mongo);
                    assertThatThrownBy(() -> migration.run(null)).isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("V001").hasMessageContaining(collection).hasMessageContaining("incompatible-existing");
                    assertThat(mongo.getCollection(collection).find().first()).isEqualTo(existing);
                    assertThat(mongo.getCollection(collection).countDocuments()).isEqualTo(1);
                    assertThat(mongo.getCollection("schema_migrations").countDocuments()).isZero();
                    // Explicit fixture cleanup lets the same migration complete; it never repairs data itself.
                    mongo.getCollection(collection).deleteOne(new Document("_id", existing.get("_id")));
                    migration.run(null);
                    assertThat(mongo.getCollection("schema_migrations").find(new Document("_id", "V001")).first()).isNotNull();
                } finally {
                    mongo.getDb().drop();
                }
            }
        }
    }
}
