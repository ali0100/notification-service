package tj.tajnav.notificationservice.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationStatusTransitionTest {

    @ParameterizedTest
    @CsvSource({"ACCEPTED, PROCESSING", "PROCESSING, SENT", "PROCESSING, FAILED"})
    void validTransitions(NotificationStatus from, NotificationStatus to) {
        assertThatCode(() -> from.validateTransitionTo(to)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({
        "ACCEPTED, SENT",
        "ACCEPTED, FAILED",
        "SENT, PROCESSING",
        "SENT, FAILED",
        "FAILED, SENT",
        "FAILED, PROCESSING"
    })
    void invalidTransitions(NotificationStatus from, NotificationStatus to) {
        assertThatThrownBy(() -> from.validateTransitionTo(to))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void acceptedCanOnlyGoToProcessing() {
        assertThatThrownBy(() -> NotificationStatus.ACCEPTED.validateTransitionTo(NotificationStatus.ACCEPTED))
            .isInstanceOf(InvalidStatusTransitionException.class);
    }
}