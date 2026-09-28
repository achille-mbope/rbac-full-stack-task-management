package net.mbope.taskmanager.user.internal.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTests {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"short password", "12345678901234"})
    void rejectsNewPasswordsShorterThanFifteenCodePoints(String password) {
        assertThatIllegalArgumentException().isThrownBy(() -> PasswordPolicy.validateNew(password, "password"));
    }

    @Test
    void countsCodePointsInsteadOfUtf16Units() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PasswordPolicy.validateNew("😀".repeat(14), "password"));
        assertThatCode(() -> PasswordPolicy.validateNew("😀".repeat(15), "password")).doesNotThrowAnyException();
    }

    @Test
    void enforcesUtf8ByteLimitWithoutTruncation() {
        assertThatCode(() -> PasswordPolicy.validateNew("é".repeat(36), "password")).doesNotThrowAnyException();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PasswordPolicy.validateNew("é".repeat(37), "password"));
        assertThatCode(() -> PasswordPolicy.validateNew("a".repeat(72), "password")).doesNotThrowAnyException();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PasswordPolicy.validateNew("a".repeat(73), "password"));
    }

    @Test
    void acceptsSpacesAndUnicodeWithoutCompositionRules() {
        assertThatCode(() -> PasswordPolicy.validateNew(" ".repeat(15), "password")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validateNew("päss phrase 日本語 hello", "password")).doesNotThrowAnyException();
    }

    @Test
    void currentPasswordsDoNotUseTheNewPasswordMinimum() {
        assertThatCode(() -> PasswordPolicy.validateCurrent("old", "currentPassword")).doesNotThrowAnyException();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PasswordPolicy.validateCurrent("", "currentPassword"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PasswordPolicy.validateCurrent("😀".repeat(19), "currentPassword"));
    }
}
