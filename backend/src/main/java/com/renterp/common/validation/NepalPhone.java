package com.renterp.common.validation;

/**
 * The one phone rule for the whole API (M15): a 10-digit Nepal mobile number
 * starting with 97 or 98. Use with {@code @Pattern(regexp = NepalPhone.REGEX,
 * message = NepalPhone.MESSAGE)}.
 */
public final class NepalPhone {

    public static final String REGEX = "^(97|98)\\d{8}$";

    public static final String MESSAGE = "Phone must be a valid 10-digit Nepal mobile number starting with 97 or 98";

    private NepalPhone() {
    }
}
