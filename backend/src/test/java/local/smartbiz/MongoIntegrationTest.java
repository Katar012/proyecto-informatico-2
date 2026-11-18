package local.smartbiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.*;
import static local.smartbiz.Models.*;

@EnabledIfEnvironmentVariable(named="TEST_MONGODB_URI", matches=".+")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties="smartbiz.seed=true")
class MongoIntegrationTest {
    @DynamicPropertySource static void mongo(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> System.getenv("TEST_MONGODB_URI"));
    }
    @LocalServerPort int port;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper json;
    @Autowired DemoSeed seed;
    @Autowired V001 migration;
    private final HttpClient client = HttpClient.newHttpClient();
    private record Result(int status, JsonNode body, String cookie) {}
    private Result call(String method, String path, String body, String cookie, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api" + path));
        if (cookie != null) builder.header("Cookie", cookie);
        if (origin != null) builder.header("Origin", origin);
        builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        return new Result(response.statusCode(), response.body().isBlank() ? null : json.readTree(response.body()),
                response.headers().firstValue("set-cookie").orElse(""));
    }
    private Result post(String path, String body, String cookie) throws Exception { return call("POST", path, body, cookie, "http://localhost:5173"); }
    private String login(String email) throws Exception {
        Result result = post("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"DemoSmartBiz2026!\"}", null);
        assertThat(result.status()).isEqualTo(200);
        assertThat(result.body().properties()).extracting(java.util.Map.Entry::getKey).containsExactlyInAnyOrder("id", "name", "email", "role");
        assertThat(result.cookie()).contains("HttpOnly", "SameSite=Strict");
        return result.cookie().split(";", 2)[0];
    }
    @Test void rejectsNonCanonicalRoutesForEveryRoleOverHttp() throws Exception {
        for (String email : List.of("admin@smartbiz.local", "caja@smartbiz.local", "bodega@smartbiz.local")) {
            String cookie = login(email);
            for (String path : List.of("/categories;x=1", "/products;x=1", "/sales;x=1",
                    "/categories%3Bx=1", "/products%3bx=1", "/sales%3Bx=1",
                    "/%63ategories", "/%70roducts", "/%73ales", "/products%253Bx=1",
                    "/products/", "//products", "/./products", "/auth/login;x=1")) {
                var result = post(path, "{}", cookie);
                assertThat(result.status()).as("%s POST %s", email, path).isEqualTo(404);
                assertThat(result.body().hasNonNull("message")).isTrue();
            }
            assertThat(call("GET", "/sales;x=1", null, cookie, null).status()).isEqualTo(404);
            assertThat(call("DELETE", "/products", null, cookie, "http://localhost:5173").status()).isEqualTo(403);
        }
        // Encoding the API prefix must not escape the filter either.
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/%61pi/products"))
                .header("Origin", "http://localhost:5173").POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(404);
    }
    @Test void completeContractAndAtomicConcurrentCheckout() throws Exception {
        assertThat(call("GET", "/products", null, null, null).status()).isEqualTo(401);
        assertThat(call("POST", "/auth/login", "{}", null, "https://evil.example").status()).isEqualTo(403);
        assertThat(call("POST", "/auth/login", "{}", null, null).status()).isEqualTo(403);
        assertThat(post("/auth/login", "{\"email\":\"admin@smartbiz.local\",\"password\":\"wrong\"}", null).status()).isEqualTo(401);
        String admin = login("admin@smartbiz.local"), cashier = login("caja@smartbiz.local"), warehouse = login("bodega@smartbiz.local");
        assertThat(post("/categories", "{\"name\":\"blocked\"}", cashier).status()).isEqualTo(403);
        assertThat(post("/products", "{}", cashier).status()).isEqualTo(403);
        assertThat(call("GET", "/sales", null, warehouse, null).status()).isEqualTo(403);
        assertThat(post("/sales", "{}", warehouse).status()).isEqualTo(403);
        String label = "Test-" + UUID.randomUUID();
        Result category = post("/categories", json.writeValueAsString(java.util.Map.of("name", label)), admin);
        assertThat(category.status()).isEqualTo(201);
        String categoryId = category.body().path("id").asText();
        assertThat(post("/categories", json.writeValueAsString(java.util.Map.of("name", "  " + label.toUpperCase() + "  ")), admin).status()).isEqualTo(409);
        assertThat(call("GET", "/categories", null, cashier, null).body().isArray()).isTrue();
        String code = "T-" + UUID.randomUUID().toString().substring(0, 12);
        String productInput = json.writeValueAsString(java.util.Map.of("code", code, "name", label, "categoryId", categoryId, "price", 1500, "stock", 3));
        Result product = post("/products", productInput, warehouse);
        assertThat(product.status()).isEqualTo(201);
        assertThat(post("/products", productInput, admin).status()).isEqualTo(409);
        assertThat(post("/products", productInput.replace("1500", "1.5"), admin).status()).isEqualTo(400);
        assertThat(post("/products", productInput.replace("1500", "9223372036854775808"), admin).status()).isEqualTo(400);
        String id = product.body().path("id").asText();
        assertThat(call("GET", "/products?q=" + code + "&categoryId=" + categoryId, null, cashier, null).body()).hasSize(1);
        assertThat(call("GET", "/products?q=nomatch" + UUID.randomUUID(), null, cashier, null).body()).isEmpty();
        String sale = "{\"items\":[{\"productId\":\"" + id + "\",\"quantity\":2}],\"tendered\":5000}";
        assertThat(post("/sales", sale.replace("5000", "1"), cashier).status()).isEqualTo(400);
        assertThat(mongo.findById(id, Product.class).stock()).isEqualTo(3);
        String repeated = "{\"items\":[{\"productId\":\"" + id + "\",\"quantity\":1},{\"productId\":\"" + id + "\",\"quantity\":1}],\"tendered\":5000}";
        assertThat(post("/sales", repeated, cashier).status()).isEqualTo(400);
        Result successful;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> post("/sales", sale, cashier));
            var b = executor.submit(() -> post("/sales", sale, cashier));
            Result first = a.get(), second = b.get();
            assertThat(List.of(first.status(), second.status())).containsExactlyInAnyOrder(201, 409);
            successful = first.status() == 201 ? first : second;
        }
        assertThat(successful.body().path("total").asLong()).isEqualTo(3000);
        assertThat(successful.body().path("change").asLong()).isEqualTo(2000);
        assertThat(successful.body().path("paymentMethod").asText()).isEqualTo("CASH");
        assertThat(successful.body().path("items").get(0).path("unitPrice").asLong()).isEqualTo(1500);
        assertThat(mongo.findById(id, Product.class).stock()).isEqualTo(1);
        long movementCount = mongo.count(Query.query(Criteria.where("productId").is(id)), Movement.class);
        assertThat(mongo.count(Query.query(Criteria.where("saleId").is(successful.body().path("id").asText())), Movement.class)).isEqualTo(1);
        // The first item's decrement must roll back when a later product cannot be sold.
        String missing = new org.bson.types.ObjectId().toHexString();
        String rollback = "{\"items\":[{\"productId\":\"" + id + "\",\"quantity\":1},{\"productId\":\"" + missing + "\",\"quantity\":1}],\"tendered\":5000}";
        assertThat(post("/sales", rollback, cashier).status()).isEqualTo(404);
        assertThat(mongo.findById(id, Product.class).stock()).isEqualTo(1);
        assertThat(mongo.count(Query.query(Criteria.where("productId").is(id)), Movement.class)).isEqualTo(movementCount);
        mongo.updateFirst(Query.query(Criteria.where("_id").is(id)),
                new org.springframework.data.mongodb.core.query.Update().set("price", 2500), Product.class);
        Sale stored = mongo.findById(successful.body().path("id").asText(), Sale.class);
        assertThat(stored.items().getFirst().unitPrice()).isEqualTo(1500);
        assertThat(stored.total()).isEqualTo(3000);
        assertThatThrownBy(() -> mongo.getCollection("products").updateOne(
                new org.bson.Document("_id", new org.bson.types.ObjectId(id)),
                new org.bson.Document("$set", new org.bson.Document("stock", -1))))
                .isInstanceOf(com.mongodb.MongoWriteException.class);
        migration.run(null); seed.run(null);
        assertThat(mongo.findById(id, Product.class).stock()).isEqualTo(1);
        assertThat(call("GET", "/sales", null, admin, null).body().isArray()).isTrue();
        assertThat(post("/auth/logout", "{}", cashier).status()).isEqualTo(204);
        assertThat(call("GET", "/auth/me", null, cashier, null).status()).isEqualTo(401);
    }
}
