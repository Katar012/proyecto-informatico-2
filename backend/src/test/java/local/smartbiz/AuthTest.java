package local.smartbiz;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static local.smartbiz.Models.*;

class AuthTest {
    @Test void bcryptLoginRotatesSessionSetsTimeoutAndLogoutRevokesIt() {
        var mongo = mock(MongoTemplate.class);
        var encoder = new BCryptPasswordEncoder(4);
        var api = new Api(mongo, encoder, mock(Checkout.class));
        var user = new User("1234567890abcdef12345678", "Demo", "demo@example.com", encoder.encode("password"), Role.ADMIN, true);
        when(mongo.findOne(any(Query.class), eq(User.class))).thenReturn(user);
        var request = new MockHttpServletRequest();
        var previous = new MockHttpSession();
        request.setSession(previous);
        assertThat(api.login(new Inputs.Login("demo@example.com", "password"), request)).isEqualTo(user.publicUser());
        assertThat(previous.isInvalid()).isTrue();
        assertThat(request.getSession().getMaxInactiveInterval()).isEqualTo(1800);
        assertThat(request.getSession().getAttribute(Access.USER_ID)).isEqualTo(user.id());
        var session = (MockHttpSession) request.getSession();
        var response = new MockHttpServletResponse();
        api.logout(request, response);
        assertThat(session.isInvalid()).isTrue();
        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0", "HttpOnly", "SameSite=Strict");
        assertThatThrownBy(() -> api.login(new Inputs.Login("demo@example.com", "wrong"), new MockHttpServletRequest()))
                .isInstanceOf(ApiErrors.Failure.class).hasMessage("Correo o password incorrectos");
    }
}
