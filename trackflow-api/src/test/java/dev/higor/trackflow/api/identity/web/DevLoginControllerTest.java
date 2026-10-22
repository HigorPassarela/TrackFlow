package dev.higor.trackflow.api.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.identity.application.DevAccounts;
import dev.higor.trackflow.api.identity.domain.AuthProvider;
import dev.higor.trackflow.api.identity.domain.UserFixtures;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(DevLoginController.class)
@ActiveProfiles("local")
class DevLoginControllerTest {

    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private DevAccounts devAccounts;

    @Test
    void startsSessionForTestAccountWithTheGivenName() {
        var user = UserFixtures.user(AuthProvider.DEV, "produtor-a");
        user.rename("produtor-a");
        ReflectionTestUtils.setField(user, "id", USER);
        given(devAccounts.signIn("produtor-a")).willReturn(user);
        var session = new MockHttpSession();

        var result = mvc.post()
                .uri("/api/dev/login")
                .session(session)
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"produtor-a\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.displayName").isEqualTo("produtor-a");
        var context = (SecurityContext)
                session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(context).isNotNull();
        var principal = (OAuth2User) context.getAuthentication().getPrincipal();
        assertThat(principal.<String>getAttribute(AuthenticatedUser.USER_ID_ATTRIBUTE))
                .isEqualTo(USER.toString());
    }
}
