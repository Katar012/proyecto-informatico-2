package local.smartbiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import static local.smartbiz.Models.*;

@Component
public class Access extends OncePerRequestFilter {
    static final String USER_ID = "userId";
    private final Set<String> origins;
    private final MongoTemplate mongo;
    private final ObjectMapper json;
    public Access(@Value("${smartbiz.allowed-origins}") String origins, MongoTemplate mongo, ObjectMapper json) {
        this.origins = Arrays.stream(origins.split(",")).map(String::strip).collect(Collectors.toUnmodifiableSet());
        this.mongo = mongo;
        this.json = json;
    }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getRequestURI();
        res.setHeader("Cache-Control", "no-store");
        // Inspect the raw URI before MVC can decode or discard matrix parameters.
        if (!path.matches("/api/(auth/(login|me|logout)|categories|products|sales)")) {
            reject(res, 404, "Ruta no canonica o no encontrada"); return;
        }
        String method = req.getMethod();
        String origin = req.getHeader("Origin");
        if (!Set.of("GET", "HEAD", "OPTIONS").contains(req.getMethod()) &&
                (origin == null || !origins.contains(origin) || "cross-site".equals(req.getHeader("Sec-Fetch-Site")))) {
            reject(res, 403, "Origen no permitido"); return;
        }
        if (path.equals("/api/auth/login") && req.getMethod().equals("POST")) {
            chain.doFilter(req, res); return;
        }
        var session = req.getSession(false);
        String id = session == null ? null : (String) session.getAttribute(USER_ID);
        User user;
        try { user = id == null ? null : mongo.findById(id, User.class); }
        catch (org.springframework.dao.DataAccessException ex) {
            reject(res, 503, "Base de datos no disponible"); return;
        }
        if (user == null || !user.active()) {
            if (session != null) session.invalidate();
            reject(res, 401, "Inicia sesion para continuar"); return;
        }
        boolean allowed = switch (method + " " + path) {
            case "GET /api/auth/me", "POST /api/auth/logout", "GET /api/categories", "GET /api/products" ->
                    Set.of(Role.ADMIN, Role.CASHIER, Role.WAREHOUSE).contains(user.role());
            case "POST /api/categories" -> user.role() == Role.ADMIN;
            case "POST /api/products" -> Set.of(Role.ADMIN, Role.WAREHOUSE).contains(user.role());
            case "GET /api/sales", "POST /api/sales" -> Set.of(Role.ADMIN, Role.CASHIER).contains(user.role());
            default -> false;
        };
        if (!allowed) { reject(res, 403, "Tu cargo no permite esta operacion"); return; }
        req.setAttribute("user", user);
        chain.doFilter(req, res);
    }
    private void reject(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        json.writeValue(res.getOutputStream(), Map.of("message", message));
    }
}
