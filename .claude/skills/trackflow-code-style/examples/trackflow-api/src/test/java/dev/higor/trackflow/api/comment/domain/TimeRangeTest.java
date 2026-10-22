package dev.higor.trackflow.api.comment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

class TimeRangeTest {

    @Test
    void rejectsEndBeforeStart() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TimeRange(60_000, 50_000L));
    }

    @Test
    void acceptsRangeEndingExactlyAtTheEndOfTheTrack() {
        TimeRange range = new TimeRange(300_000, 360_000L);

        assertThatNoException().isThrownBy(() -> range.requireWithin(360_000));
    }

    @Test
    void positionBeyondVersionDurationIsABusinessRule() {
        TimeRange range = TimeRange.point(400_000);

        assertThatThrownBy(() -> range.requireWithin(360_000))
                .isInstanceOfSatisfying(BusinessRuleException.class, error ->
                        assertThat(error.code()).isEqualTo("position-beyond-duration"));
    }
}
