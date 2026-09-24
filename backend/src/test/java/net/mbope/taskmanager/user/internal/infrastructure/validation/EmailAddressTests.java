package net.mbope.taskmanager.user.internal.infrastructure.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

class EmailAddressTests {
    private static final jakarta.validation.ValidatorFactory VALIDATORS = jakarta.validation.Validation.buildDefaultValidatorFactory();
    private final EmailAddress emailAddress = new EmailAddress(VALIDATORS.getValidator());

    @org.junit.jupiter.api.AfterAll
    static void closeValidatorFactory() {
        VALIDATORS.close();
    }

    @Test
    void normalizesWhitespaceAndCaseIndependentlyOfDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(emailAddress.normalize("  ALICE@EXAMPLE.COM  ")).isEqualTo("alice@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "missing-at", "@example.com", "a@b@c.com", "a b@example.com"})
    void rejectsInvalidAddresses(String email) {
        assertThatIllegalArgumentException().isThrownBy(() -> emailAddress.normalize(email));
    }

    @Test
    void rejectsAddressesOverTheContractLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> emailAddress.normalize("a".repeat(245) + "@example.com"));
    }
}
