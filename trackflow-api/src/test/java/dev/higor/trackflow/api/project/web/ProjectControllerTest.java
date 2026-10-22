package dev.higor.trackflow.api.project.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import dev.higor.trackflow.api.identity.AuthenticatedUser;
import dev.higor.trackflow.api.project.ProjectRole;
import dev.higor.trackflow.api.project.application.EditProject;
import dev.higor.trackflow.api.project.application.ProjectService;
import dev.higor.trackflow.api.project.application.ProjectView;
import dev.higor.trackflow.api.project.domain.ProjectListItem;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.shared.error.NotFoundException;
import dev.higor.trackflow.api.shared.error.PermissionDeniedException;
import dev.higor.trackflow.api.shared.web.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.OAuth2LoginRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(ProjectController.class)
class ProjectControllerTest {

    private static final UUID PROJECT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProjectService service;

    @Test
    void createsProjectAndPointsToIt() {
        given(service.create(any())).willReturn(view(ProjectRole.OWNER, null));

        var result = mvc.post()
                .uri("/api/projects")
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Ibiza Vibes\", \"genre\": \"tech house\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CREATED).hasHeader("Location", "/api/projects/" + PROJECT);
        assertThat(result).bodyJson().extractingPath("$.myRole").isEqualTo("owner");
        assertThat(result).bodyJson().extractingPath("$.owner.name").isEqualTo("Produtora A");
    }

    @Test
    void rejectsEmptyTitlePointingAtTheField() {
        var result = mvc.post()
                .uri("/api/projects")
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("title");
        then(service).should(never()).create(any());
    }

    @Test
    void listsMyProjectsAsAPage() {
        var item = new ProjectListItem(PROJECT, "Ibiza Vibes", "tech house", ProjectRole.LISTENER, null, NOW);
        given(service.listMine(USER, false, 0, 20)).willReturn(new PageResponse<>(List.of(item), 0, 20, 1));

        var result = mvc.get().uri("/api/projects").with(signedIn()).exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items[0].myRole").isEqualTo("listener");
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(1);
    }

    @Test
    void rejectsPageSizeAboveTheLimit() {
        var result = mvc.get().uri("/api/projects?size=500").with(signedIn()).exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("validation-failed");
    }

    @Test
    void returnsNotFoundForNonMember() {
        given(service.get(PROJECT, USER)).willThrow(NotFoundException.of("project", PROJECT));

        var result =
                mvc.get().uri("/api/projects/{id}", PROJECT).with(signedIn()).exchange();

        assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("project-not-found");
    }

    @Test
    void sendsOnlyTheChangedFieldsToTheService() {
        given(service.edit(new EditProject(PROJECT, USER, null, "kick novo", null)))
                .willReturn(view(ProjectRole.EDITOR, null));

        var result = mvc.patch()
                .uri("/api/projects/{id}", PROJECT)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"kick novo\"}")
                .exchange();

        assertThat(result).hasStatusOk();
    }

    @Test
    void rejectsBlankTitleOnUpdate() {
        var result = mvc.patch()
                .uri("/api/projects/{id}", PROJECT)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"  \"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("titleValid");
    }

    @Test
    void answersForbiddenWhenRoleIsInsufficient() {
        given(service.archive(PROJECT, USER)).willThrow(new PermissionDeniedException("Only the owner archives"));

        var result = mvc.post()
                .uri("/api/projects/{id}/archive", PROJECT)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("insufficient-role");
    }

    @Test
    void answersConflictWhenProjectIsArchived() {
        given(service.edit(any()))
                .willThrow(new BusinessRuleException("project-archived", "The project is archived and read-only"));

        var result = mvc.patch()
                .uri("/api/projects/{id}", PROJECT)
                .with(signedIn())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\": \"Novo\"}")
                .exchange();

        assertThat(result).hasStatus(HttpStatus.CONFLICT);
        assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("project-archived");
    }

    @Test
    void archivesAndReportsTheState() {
        given(service.archive(PROJECT, USER)).willReturn(view(ProjectRole.OWNER, NOW));

        var result = mvc.post()
                .uri("/api/projects/{id}/archive", PROJECT)
                .with(signedIn())
                .with(csrf())
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.archived").isEqualTo(true);
    }

    private static OAuth2LoginRequestPostProcessor signedIn() {
        return oauth2Login().attributes(attributes -> {
            attributes.put(AuthenticatedUser.USER_ID_ATTRIBUTE, USER.toString());
            attributes.put(AuthenticatedUser.DISPLAY_NAME_ATTRIBUTE, "Produtora A");
        });
    }

    private static ProjectView view(ProjectRole role, @Nullable Instant archivedAt) {
        return new ProjectView(
                PROJECT, "Ibiza Vibes", null, "tech house", USER, "Produtora A", role, archivedAt, NOW, NOW);
    }
}
