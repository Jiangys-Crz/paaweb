package com.cwnupaa.backend.utils;

import com.cwnupaa.backend.dto.RegisterDTO;
import com.cwnupaa.backend.dto.UserActivateDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountRulesTests {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsThreeToTwentyAsciiLettersOrDigitsWithAtLeastOneLetter() {
        assertTrue(UserAccountRules.isValid("Abc"));
        assertTrue(UserAccountRules.isValid("abcdefghij"));
        assertTrue(UserAccountRules.isValid("ABCdef"));
        assertTrue(UserAccountRules.isValid("abc12345678901234567"));

        assertFalse(UserAccountRules.isValid(null));
        assertFalse(UserAccountRules.isValid("ab"));
        assertFalse(UserAccountRules.isValid("abcdefghij12345678901"));
        assertFalse(UserAccountRules.isValid("abc_def"));
        assertFalse(UserAccountRules.isValid("123456"));
        assertFalse(UserAccountRules.isValid("账号abc"));
    }

    @Test
    void pendingActivationAccountsFollowTheSameRule() {
        Set<String> generated = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String account = SecurityUtils.pendingActivationAccount();
            assertTrue(UserAccountRules.isValid(account));
            generated.add(account);
        }
        assertEquals(1_000, generated.size());
    }

    @Test
    void registrationAndActivationDtosEnforceTheRule() {
        RegisterDTO register = new RegisterDTO();
        register.setUserAccount("ab");
        assertTrue(hasUserAccountViolation(register));
        register.setUserAccount("ValidName123");
        assertFalse(hasUserAccountViolation(register));

        UserActivateDTO activation = new UserActivateDTO();
        activation.setUserAccount("ab_");
        assertTrue(hasUserAccountViolation(activation));
        activation.setUserAccount("NewUser123");
        assertFalse(hasUserAccountViolation(activation));
    }

    private boolean hasUserAccountViolation(Object dto) {
        return validator.validate(dto).stream()
                .anyMatch(violation -> "userAccount".equals(violation.getPropertyPath().toString()));
    }
}
