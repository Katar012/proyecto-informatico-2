package local.smartbiz;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

public final class Models {
    private Models() {}
    public enum Role { ADMIN, CASHIER, WAREHOUSE }
    @Document("users")
    public record User(@Id String id, String name, String email, String passwordHash, Role role, boolean active) {
        public PublicUser publicUser() { return new PublicUser(id, name, email, role); }
    }
    public record PublicUser(String id, String name, String email, Role role) {}
    @Document("categories")
    public record Category(@Id String id, String name, String normalizedName) {
        public PublicCategory publicCategory() { return new PublicCategory(id, name); }
    }
    public record PublicCategory(String id, String name) {}
    @Document("products")
    public record Product(@Id String id, String code, String name, String categoryId, long price, long stock) {}
    public record SaleItem(String productId, String code, String name, long quantity, long unitPrice, long subtotal) {}
    @Document("sales")
    public record Sale(@Id String id, String cashierId, String cashierName, Instant createdAt,
                       List<SaleItem> items, long total, long tendered, long change, String paymentMethod) {}
    @Document("movements")
    public record Movement(@Id String id, String productId, String saleId, long quantity, String authorId, Instant createdAt) {}
}
