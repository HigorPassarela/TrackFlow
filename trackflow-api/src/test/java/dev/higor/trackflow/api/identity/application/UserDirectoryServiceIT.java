package dev.higor.trackflow.api.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.identity.UserDirectory;
import dev.higor.trackflow.api.identity.domain.UserFixtures;
import dev.higor.trackflow.api.identity.domain.UserRepository;
import dev.higor.trackflow.api.support.PostgresTestConfiguration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfiguration.class, UserDirectoryService.class})
class UserDirectoryServiceIT {

    @Autowired
    private UserRepository users;

    @Autowired
    private UserDirectory directory;

    @Test
    void returnsDisplayNamesOfKnownUsersInOneCall() {
        var ana = users.save(UserFixtures.githubUser("1"));
        var bia = users.save(UserFixtures.githubUser("2"));
        users.save(UserFixtures.githubUser("3"));
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

        var names = directory.displayNames(Set.of(ana.getId(), bia.getId(), unknown));

        assertThat(names)
                .containsOnlyKeys(ana.getId(), bia.getId())
                .containsEntry(ana.getId(), "Produtor 1")
                .containsEntry(bia.getId(), "Produtor 2");
    }

    @Test
    void returnsEmptyMapForNoIds() {
        assertThat(directory.displayNames(Set.of())).isEmpty();
    }
}
