package local.smartbiz;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import static local.smartbiz.Models.*;

@Component
@Order(1)
@ConditionalOnProperty(name="smartbiz.seed", havingValue="true")
public class DemoSeed implements ApplicationRunner {
    private final MongoTemplate mongo;
    private final BCryptPasswordEncoder encoder;
    public DemoSeed(MongoTemplate mongo, BCryptPasswordEncoder encoder) { this.mongo = mongo; this.encoder = encoder; }
    @Override public void run(ApplicationArguments args) {
        account("admin@smartbiz.local", "Administrador Demo", Role.ADMIN);
        account("caja@smartbiz.local", "Cajero Demo", Role.CASHIER);
        account("bodega@smartbiz.local", "Bodeguero Demo", Role.WAREHOUSE);
        mongo.upsert(Query.query(Criteria.where("normalizedName").is("demostracion")),
                new Update().setOnInsert("name", "Demostracion").setOnInsert("normalizedName", "demostracion"), Category.class);
        Category category = mongo.findOne(Query.query(Criteria.where("normalizedName").is("demostracion")), Category.class);
        product("DEMO-001", "Cafe Demo 500g", category.id(), 18000, 30);
        product("DEMO-002", "Arroz Demo 1kg", category.id(), 4500, 50);
        product("DEMO-003", "Agua Demo 600ml", category.id(), 2500, 40);
    }
    private void account(String email, String name, Role role) {
        mongo.upsert(Query.query(Criteria.where("email").is(email)), new Update().setOnInsert("name", name)
                .setOnInsert("email", email).setOnInsert("role", role).setOnInsert("active", true)
                .setOnInsert("passwordHash", encoder.encode("DemoSmartBiz2026!")), User.class);
    }
    private void product(String code, String name, String categoryId, long price, long stock) {
        mongo.upsert(Query.query(Criteria.where("code").is(code)), new Update().setOnInsert("code", code)
                .setOnInsert("name", name).setOnInsert("categoryId", categoryId)
                .setOnInsert("price", price).setOnInsert("stock", stock), Product.class);
    }
}
