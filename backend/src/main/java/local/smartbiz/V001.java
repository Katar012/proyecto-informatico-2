package local.smartbiz;

import java.util.List;
import org.bson.Document;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class V001 implements ApplicationRunner {
    private final MongoTemplate mongo;
    public V001(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public void run(ApplicationArguments args) {
        Document hello = mongo.executeCommand(new Document("hello", 1));
        if (!hello.containsKey("setName")) throw new IllegalStateException("SmartBiz requiere MongoDB replica set para ventas atomicas");
        // Reapplying this additive migration also repairs missing indexes after an interrupted startup.
        collection("users", new Document("name", text(1, 160)).append("email", text(1, 254))
                .append("passwordHash", text(60, 60)).append("role", new Document("enum", List.of("ADMIN", "CASHIER", "WAREHOUSE")))
                .append("active", new Document("bsonType", "bool")));
        collection("categories", new Document("name", text(1, 100)).append("normalizedName", text(1, 100)));
        collection("products", new Document("code", text(1, 40)).append("name", text(1, 160))
                .append("categoryId", reference()).append("price", number(1, Inputs.MAX_PRICE))
                .append("stock", number(0, Inputs.MAX_STOCK)));
        Document itemProperties = new Document("productId", reference()).append("code", text(1, 40)).append("name", text(1, 160))
                .append("quantity", number(1, Inputs.MAX_STOCK)).append("unitPrice", number(1, Inputs.MAX_PRICE))
                .append("subtotal", number(1, Inputs.MAX_MONEY));
        Document saleProperties = new Document("cashierId", reference()).append("cashierName", text(1, 160))
                .append("createdAt", new Document("bsonType", "date"))
                .append("items", new Document("bsonType", "array").append("minItems", 1).append("maxItems", 100)
                        .append("items", schema(itemProperties)))
                .append("total", number(1, Inputs.MAX_MONEY)).append("tendered", number(1, Inputs.MAX_MONEY))
                .append("change", number(0, Inputs.MAX_MONEY)).append("paymentMethod", new Document("enum", List.of("CASH")));
        collection("sales", saleProperties);
        collection("movements", new Document("productId", reference()).append("saleId", reference())
                .append("quantity", number(-Inputs.MAX_STOCK, -1)).append("authorId", reference())
                .append("createdAt", new Document("bsonType", "date")));
        mongo.indexOps("users").ensureIndex(new Index().on("email", Sort.Direction.ASC).unique());
        mongo.indexOps("categories").ensureIndex(new Index().on("normalizedName", Sort.Direction.ASC).unique());
        mongo.indexOps("products").ensureIndex(new Index().on("code", Sort.Direction.ASC).unique());
        mongo.indexOps("products").ensureIndex(new Index().on("categoryId", Sort.Direction.ASC));
        mongo.indexOps("sales").ensureIndex(new Index().on("createdAt", Sort.Direction.DESC));
        mongo.indexOps("movements").ensureIndex(new Index().on("saleId", Sort.Direction.ASC).on("productId", Sort.Direction.ASC).unique());
        mongo.indexOps("movements").ensureIndex(new Index().on("productId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        mongo.getCollection("schema_migrations").updateOne(new Document("_id", "V001"),
                new Document("$setOnInsert", new Document("description", "Initial constraints and indexes")),
                new com.mongodb.client.model.UpdateOptions().upsert(true));
    }
    private void collection(String name, Document properties) {
        if (!mongo.collectionExists(name)) mongo.createCollection(name);
        Document validator = new Document("$jsonSchema", schema(properties));
        mongo.executeCommand(new Document("collMod", name).append("validator", validator)
                .append("validationLevel", "strict").append("validationAction", "error"));
        // collMod guards future writes only; explicitly inspect pre-existing documents too.
        Document incompatible = new Document("$nor", List.of(validator));
        var invalidIds = mongo.getCollection(name).find(incompatible).projection(new Document("_id", 1))
                .limit(5).into(new java.util.ArrayList<>()).stream().map(doc -> String.valueOf(doc.get("_id"))).toList();
        if (!invalidIds.isEmpty()) {
            throw new IllegalStateException("V001: documentos incompatibles en " + name + "; _id (hasta 5): " + invalidIds
                    + ". Corrige los datos explicitamente antes de reiniciar; no se modificaron documentos ni se registro V001.");
        }
    }
    private static Document schema(Document properties) {
        return new Document("bsonType", "object").append("required", List.copyOf(properties.keySet())).append("properties", properties);
    }
    private static Document text(int min, int max) {
        return new Document("bsonType", "string").append("minLength", min).append("maxLength", max).append("pattern", "\\S");
    }
    private static Document reference() { return new Document("bsonType", "string").append("pattern", "^[a-f0-9]{24}$"); }
    private static Document number(long min, long max) {
        return new Document("bsonType", List.of("int", "long")).append("minimum", min).append("maximum", max);
    }
}
