package dev.higor.trackflow.api.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void renamesWithTrimmedDisplayName() {
        var user = UserFixtures.githubUser("42");

        user.rename("  VERXT ");

        assertThat(user.getDisplayName()).isEqualTo("VERXT");
    }

    @Test
    void rejectsDisplayNameShorterThanTwoCharacters() {
        var user = UserFixtures.githubUser("42");

        assertThatThrownBy(() -> user.rename("V")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsDisplayNameLongerThanSixtyCharacters() {
        var user = UserFixtures.githubUser("42");

        assertThatThrownBy(() -> user.rename("x".repeat(61))).isInstanceOf(IllegalArgumentException.class);
    }
}
