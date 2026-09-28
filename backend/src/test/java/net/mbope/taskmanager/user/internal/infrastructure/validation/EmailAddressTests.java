package net.mbope.taskmanager.user.internal.infrastructure.validation;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;

import java.util.Locale;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class EmailAddressTests {
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();
    private final EmailAddress emailAddress = new EmailAddress(VALIDATORS.getValidator());

    @AfterAll
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
