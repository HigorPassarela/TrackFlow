package dev.higor.trackflow.api.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.application.MemberService;
import dev.higor.trackflow.api.project.application.MemberView;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID MEMBER = UUID.fromString("00000000-0000-0000-0000-00000000000c");
    private static final String MEMBERS = "/api/projects/{projectId}/members";

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private MemberService service;

    @Test
    void listsMembersWithLowercaseRoles() {
        given(service.list(PROJECT, USER))
                .willReturn(List.of(new MemberView(USER, "Produtora A", ProjectRole.OWNER, Instant.EPOCH)));

        var result = mvc.get().uri(MEMBERS, PROJECT).with(signedIn()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$[0].role").isEqualTo("owner");
    }

    @Test
    void changesRole() {
        given(service.changeRole(PROJECT, MEMBER, ProjectRole.EDITOR, USER))
                .willReturn(new MemberView(MEMBER, "Ouvinte", ProjectRole.EDITOR, Instant.EPOCH));

        var result = mvc.patch()
                .uri(MEMBERS + "/{memberId}", PROJECT, MEMBER)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"editor\"}")
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.role").isEqualTo("editor");
    }

    @Test
    void refusesToAssignOwnershipThroughValidation() {
        var result = mvc.patch()
                .uri(MEMBERS + "/{memberId}", PROJECT, MEMBER)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\": \"owner\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("role");
        then(service).should(never()).changeRole(any(), any(), any(), any());
    }

    @Test
    void removesMemberWithNoContent() {
        var result = mvc.delete()
                .uri(MEMBERS + "/{memberId}", PROJECT, MEMBER)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        then(service).should().remove(PROJECT, MEMBER, USER);
    }

    @Test
    void routesMeToLeaveInsteadOfRemove() {
        var result = mvc.delete()
                .uri(MEMBERS + "/me", PROJECT)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.NO_CONTENT);
        then(service).should().leave(PROJECT, USER);
    }

    @Test
    void explainsThatTheProjectNeedsAnOwner() {
        willThrow(new BusinessRuleException("owner-cannot-leave", "The project needs an owner"))
                .given(service)
                .remove(PROJECT, USER, USER);

        var result = mvc.delete()
                .uri(MEMBERS + "/{memberId}", PROJECT, USER)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("owner-cannot-leave");
    }

    private static OAuth2LoginRequestPostProcessor signedIn() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Produtora A");
        });
    }
}
