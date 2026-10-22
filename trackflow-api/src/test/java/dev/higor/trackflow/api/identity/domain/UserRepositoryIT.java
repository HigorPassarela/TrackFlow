package dev.higor.trackflow.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.support.PostgresTestConfiguration;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class UserRepositoryIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void rejectsSecondAccountForSameProviderAndSubject() {
        users.save(UserFixtures.githubUser("42"));
        entityManager.flush();

        assertThatThrownBy(() -> {
                    users.save(UserFixtures.githubUser("42"));
                    entityManager.flush();
                })
                .isInstanceOf(ConstraintViolationException.class);
    }

    @Test
    void allowsSameSubjectOnDifferentProviders() {
        users.save(UserFixtures.user(AuthProvider.GITHUB, "42"));
        users.save(UserFixtures.user(AuthProvider.GOOGLE, "42"));
        entityManager.flush();

        assertThat(users.findByProviderAndProviderSubject(AuthProvider.GOOGLE, "42"))
                .isPresent();
    }
}
