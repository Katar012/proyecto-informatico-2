package local.smartbiz;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static local.smartbiz.Models.*;

class AccessTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final Access access = new Access("http://localhost:5173", mongo, new ObjectMapper());
    private final FilterChain chain = mock(FilterChain.class);
    @Test void rejectsNonCanonicalAndUnknownPathsBeforeSessionLookup() throws Exception {
        for (String path : new String[] {"/api/products;x=1", "/api/products%3bx=1", "/api/%70roducts",
                "/%61pi/products", "/api//products", "/api/./products", "/api/products/", "/api/unknown", "/other"}) {
            var req = new MockHttpServletRequest("POST", path);
            req.addHeader("Origin", "http://localhost:5173");
            var res = new MockHttpServletResponse();
            access.doFilter(req, res, chain);
            assertThat(res.getStatus()).as(path).isEqualTo(404);
        }
        verifyNoInteractions(mongo, chain);
    }
    @Test void unlistedMethodsAreDeniedEvenForAdmin() throws Exception {
        for (String method : new String[] {"PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"}) {
            var req = new MockHttpServletRequest(method, "/api/products");
            req.addHeader("Origin", "http://localhost:5173");
            req.getSession().setAttribute(Access.USER_ID, "user");
            when(mongo.findById("user", User.class)).thenReturn(new User("user", "Demo", "demo@example.com", "hash", Role.ADMIN, true));
            var res = new MockHttpServletResponse();
            access.doFilter(req, res, chain);
            assertThat(res.getStatus()).as(method).isEqualTo(403);
        }
        verifyNoInteractions(chain);
    }
    @Test void missingAndForeignOriginRejectBeforeDatabaseAccess() throws Exception {
        for (String origin : new String[] {null, "https://evil.example", "null"}) {
            var req = new MockHttpServletRequest("POST", "/api/auth/login");
            if (origin != null) req.addHeader("Origin", origin);
            var res = new MockHttpServletResponse();
            access.doFilter(req, res, chain);
            assertThat(res.getStatus()).isEqualTo(403);
            assertThat(res.getContentAsString()).contains("message");
        }
        verifyNoInteractions(mongo, chain);
    }
    @Test void publicLoginRequiresAllowedOrigin() throws Exception {
        var req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.addHeader("Origin", "http://localhost:5173");
        var res = new MockHttpServletResponse();
        access.doFilter(req, res, chain);
        verify(chain).doFilter(req, res);
        verifyNoInteractions(mongo);
    }
    @Test void missingSessionAndInactiveAccountAreUnauthorized() throws Exception {
        var req = new MockHttpServletRequest("GET", "/api/auth/me");
        var res = new MockHttpServletResponse();
        access.doFilter(req, res, chain);
        assertThat(res.getStatus()).isEqualTo(401);
        var session = new MockHttpSession();
        session.setAttribute(Access.USER_ID, "user");
        req.setSession(session);
        when(mongo.findById("user", User.class)).thenReturn(new User("user", "Demo", "demo@example.com", "hash", Role.ADMIN, false));
        access.doFilter(req, new MockHttpServletResponse(), chain);
        assertThat(session.isInvalid()).isTrue();
        verifyNoInteractions(chain);
    }
    @Test void checksRolesOnServer() throws Exception {
        for (Role role : Role.values()) {
            for (String path : new String[] {"/api/categories", "/api/products", "/api/sales"}) {
                var req = new MockHttpServletRequest("POST", path);
                req.addHeader("Origin", "http://localhost:5173");
                req.getSession().setAttribute(Access.USER_ID, "user");
                when(mongo.findById("user", User.class)).thenReturn(new User("user", "Demo", "demo@example.com", "hash", role, true));
                var res = new MockHttpServletResponse();
                var localChain = mock(FilterChain.class);
                access.doFilter(req, res, localChain);
                boolean allowed = path.endsWith("categories") ? role == Role.ADMIN :
                        path.endsWith("products") ? role != Role.CASHIER : role != Role.WAREHOUSE;
                assertThat(res.getStatus()).isEqualTo(allowed ? 200 : 403);
                if (allowed) verify(localChain).doFilter(req, res);
                else verifyNoInteractions(localChain);
            }
        }
    }
}
