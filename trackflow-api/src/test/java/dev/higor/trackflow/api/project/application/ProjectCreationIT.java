package dev.higor.trackflow.api.project.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import dev.higor.trackflow.api.identity.domain.UserFixtures;
import dev.higor.trackflow.api.identity.domain.UserRepository;
import dev.higor.trackflow.api.project.domain.ProjectMember;
import dev.higor.trackflow.api.project.domain.ProjectMemberRepository;
import dev.higor.trackflow.api.project.domain.ProjectRepository;
import dev.higor.trackflow.api.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@IntegrationTest
class ProjectCreationIT {

    @Autowired
    private ProjectService service;

    @Autowired
    private ProjectRepository projects;

    @Autowired
    private UserRepository users;

    @MockitoSpyBean
    private ProjectMemberRepository members;

    @Test
    void rollsBackTheProjectWhenTheOwnerMembershipCannotBeStored() {
        var owner = users.save(UserFixtures.githubUser("rollback-owner"));
        long projectsBefore = projects.count();
        willThrow(new DataAccessResourceFailureException("simulated failure"))
                .given(members)
                .save(any(ProjectMember.class));

        assertThatThrownBy(() -> service.create(new CreateProject(owner.getId(), "Ibiza Vibes", null, null)))
                .isInstanceOf(DataAccessResourceFailureException.class);

        assertThat(projects.count()).isEqualTo(projectsBefore);
    }
}
