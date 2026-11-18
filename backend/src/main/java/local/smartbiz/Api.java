package local.smartbiz;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import static local.smartbiz.Inputs.*;
import static local.smartbiz.Models.*;
import static local.smartbiz.ApiErrors.Failure;

@RestController
@RequestMapping("/api")
public class Api {
    private final MongoTemplate mongo;
    private final BCryptPasswordEncoder encoder;
    private final Checkout checkout;
    private final String dummyHash;
    public Api(MongoTemplate mongo, BCryptPasswordEncoder encoder, Checkout checkout) {
        this.mongo = mongo; this.encoder = encoder; this.checkout = checkout;
        dummyHash = encoder.encode("not-a-real-password");
    }
    @PostMapping("/auth/login")
    PublicUser login(@Valid @RequestBody Login input, HttpServletRequest request) {
        if (input.password().getBytes(StandardCharsets.UTF_8).length > 72) throw new Failure(400, "Password demasiado largo");
        User user = mongo.findOne(Query.query(Criteria.where("email").is(input.email().strip().toLowerCase(Locale.ROOT))), User.class);
        boolean matches = encoder.matches(input.password(), user == null ? dummyHash : user.passwordHash());
        if (!matches || user == null || !user.active()) throw new Failure(401, "Correo o password incorrectos");
        var previous = request.getSession(false);
        if (previous != null) previous.invalidate();
        var session = request.getSession(true);
        session.setMaxInactiveInterval(1800);
        session.setAttribute(Access.USER_ID, user.id());
        return user.publicUser();
    }
    @GetMapping("/auth/me")
    PublicUser me(@RequestAttribute User user) { return user.publicUser(); }
    @PostMapping("/auth/logout")
    void logout(HttpServletRequest request, HttpServletResponse response) {
        request.getSession(false).invalidate();
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from("JSESSIONID", "").path("/")
                .httpOnly(true).secure(request.isSecure()).sameSite("Strict").maxAge(0).build().toString());
        response.setStatus(204);
    }
    @GetMapping("/categories")
    List<PublicCategory> categories() {
        return mongo.find(new Query().with(Sort.by("name", "id")), Category.class).stream().map(Category::publicCategory).toList();
    }
    @PostMapping("/categories")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    PublicCategory category(@Valid @RequestBody CategoryInput input) {
        String name = clean(input.name());
        return mongo.insert(new Category(null, name, normalized(name))).publicCategory();
    }
    @GetMapping("/products")
    List<Product> products(@RequestParam(required=false) String q, @RequestParam(required=false) String categoryId) {
        Query query = new Query();
        if (q != null && !q.isBlank()) {
            if (q.length() > 160) throw new Failure(400, "Busqueda demasiado larga");
            Pattern literal = Pattern.compile(Pattern.quote(q.strip()), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            query.addCriteria(new Criteria().orOperator(Criteria.where("name").regex(literal), Criteria.where("code").regex(literal)));
        }
        if (categoryId != null && !categoryId.isBlank()) {
            if (!categoryId.matches("[a-fA-F0-9]{24}")) throw new Failure(400, "Categoria invalida");
            query.addCriteria(Criteria.where("categoryId").is(categoryId.toLowerCase(Locale.ROOT)));
        }
        return mongo.find(query.with(Sort.by("name", "id")), Product.class);
    }
    @PostMapping("/products")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Product product(@Valid @RequestBody ProductInput input) {
        String categoryId = input.categoryId().toLowerCase(Locale.ROOT);
        if (!mongo.exists(Query.query(Criteria.where("_id").is(categoryId)), Category.class)) throw new Failure(404, "Categoria no encontrada");
        return mongo.insert(new Product(null, input.code().toUpperCase(Locale.ROOT), clean(input.name()), categoryId, input.price(), input.stock()));
    }
    @GetMapping("/sales")
    List<Sale> sales() { return mongo.find(new Query().with(Sort.by(Sort.Direction.DESC, "createdAt", "id")), Sale.class); }
    @PostMapping("/sales")
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    Sale sale(@Valid @RequestBody SaleInput input, @RequestAttribute User user) { return checkout.sell(input, user); }
}
