package com.renterp.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A real Bikram Sambat date "YYYY-MM-DD" (H16): the right shape AND a day that
 * exists in that BS month (29-32 days) within the supported years. Null passes;
 * add {@code @NotBlank} / {@code @NotNull} when the field is required.
 *
 * A regex alone accepted "2082-13-45" or day 32 of a 30-day month, which then
 * failed later inside billing.
 */
@Documented
@Constraint(validatedBy = BsDateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface BsDate {

    String message() default "must be a valid BS date (YYYY-MM-DD)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
