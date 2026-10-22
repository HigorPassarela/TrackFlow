package dev.higor.trackflow.api.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class TokenGeneratorTest {

    private final TokenGenerator generator = new TokenGenerator();

    @Test
    void generatesUrlSafeTokenFrom32RandomBytes() {
        var token = generator.generate();

        assertThat(token.value()).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(Base64.getUrlDecoder().decode(token.value())).hasSize(32);
    }

    @Test
    void generatesDifferentTokensEachTime() {
        var first = generator.generate();
        var second = generator.generate();

        assertThat(first.value()).isNotEqualTo(second.value());
    }

    @Test
    void hashesWithSha256AsLowercaseHex() {
        assertThat(generator.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void returnsTheSameHashForTheSameToken() {
        var token = generator.generate();

        assertThat(generator.hash(token.value())).isEqualTo(token.hash()).hasSize(64);
    }

    @Test
    void matchesOnlyTheTokenThatProducedTheHash() {
        var token = generator.generate();

        assertThat(generator.matches(token.value(), token.hash())).isTrue();
        assertThat(generator.matches(generator.generate().value(), token.hash()))
                .isFalse();
    }
}
