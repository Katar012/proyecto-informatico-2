package local.smartbiz;

import com.mongodb.MongoException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.mongodb.MongoTransactionManager;
import static local.smartbiz.Models.*;
import static local.smartbiz.Inputs.*;
import static local.smartbiz.ApiErrors.Failure;

@Service
public class Checkout {
    private final MongoTemplate mongo;
    private final TransactionTemplate transactions;
    public Checkout(MongoTemplate mongo, MongoTransactionManager manager) {
        this.mongo = mongo; this.transactions = new TransactionTemplate(manager);
    }
    public Sale sell(SaleInput input, User user) {
        var unique = new HashSet<String>();
        for (ItemInput item : input.items()) {
            if (!unique.add(item.productId().toLowerCase(Locale.ROOT))) throw new Failure(400, "Un producto no puede repetirse en la venta");
        }
        // Retry only aborted transactions, never an ambiguous commit (which could duplicate a sale).
        for (int attempt = 0; ; attempt++) {
            try { return transactions.execute(status -> writeSale(input, user)); }
            catch (RuntimeException ex) {
                boolean transientAbort = false;
                for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
                    if (cause instanceof MongoException mongoEx && mongoEx.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL)) transientAbort = true;
                    if (cause instanceof MongoException mongoEx && mongoEx.hasErrorLabel(MongoException.UNKNOWN_TRANSACTION_COMMIT_RESULT_LABEL)) throw new Failure(503, "Resultado del cobro incierto; consulta el historial antes de repetir");
                }
                if (!transientAbort) throw ex;
                if (attempt >= 4) throw new Failure(409, "Inventario ocupado; vuelve a consultar las existencias");
            }
        }
    }
    private Sale writeSale(SaleInput input, User user) {
        String saleId = new ObjectId().toHexString();
        Instant now = Instant.now();
        var items = new ArrayList<SaleItem>();
        long total = 0;
        for (ItemInput item : input.items()) {
            String productId = item.productId().toLowerCase(Locale.ROOT);
            Product product = mongo.findById(productId, Product.class);
            if (product == null) throw new Failure(404, "Producto no encontrado");
            long subtotal = Math.multiplyExact(product.price(), item.quantity());
            total = Math.addExact(total, subtotal);
            if (total > MAX_MONEY) throw new Failure(400, "Total fuera del limite permitido");
            var result = mongo.updateFirst(Query.query(Criteria.where("_id").is(productId).and("stock").gte(item.quantity())),
                    new Update().inc("stock", -item.quantity()), Product.class);
            if (result.getModifiedCount() != 1) throw new Failure(409, "Stock insuficiente para " + product.code());
            items.add(new SaleItem(product.id(), product.code(), product.name(), item.quantity(), product.price(), subtotal));
            mongo.insert(new Movement(null, product.id(), saleId, -item.quantity(), user.id(), now));
        }
        if (input.tendered() < total) throw new Failure(400, "El efectivo entregado es insuficiente");
        return mongo.insert(new Sale(saleId, user.id(), user.name(), now, items, total, input.tendered(), input.tendered() - total, "CASH"));
    }
}
