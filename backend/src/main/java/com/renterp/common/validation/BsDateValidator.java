package com.renterp.common.validation;

import com.renterp.common.util.BsCalendar;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link BsDate} with {@link BsCalendar#isValid(String)}. */
public class BsDateValidator implements ConstraintValidator<BsDate, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || BsCalendar.isValid(value);
    }
}
