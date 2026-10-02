package com.renterp.domain.property.service;

import com.renterp.domain.property.repository.PropertyRepository;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Makes a property's join code: two letters from the name, three from the
 * city, four random digits - "Shrestha Residency", "Kathmandu" → SR-KTM-4821.
 * Readable over the phone and unrelated to the internal id. Codes are unique;
 * a clash with an existing one just draws new digits.
 */
@Component
public class JoinCodeGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    private final PropertyRepository properties;

    public JoinCodeGenerator(PropertyRepository properties) {
        this.properties = properties;
    }

    public String newCode(String name, String city) {
        String prefix = initials(name, 2) + "-" + cityPart(city);
        for (int attempt = 0; attempt < 50; attempt++) {
            String code = prefix + "-" + String.format("%04d", RANDOM.nextInt(10_000));
            if (!properties.existsByJoinCode(code)) {
                return code;
            }
        }
        // 50 clashes means this prefix is crowded; a random prefix always has room.
        return randomLetters(2) + "-" + randomLetters(3) + "-" + String.format("%04d", RANDOM.nextInt(10_000));
    }

    /** Two letters: first letters of the first two words, else the first two letters, padded randomly. */
    static String initials(String name, int n) {
        String[] words = lettersOnly(name).split(" ");
        StringBuilder out = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty() && out.length() < n) {
                out.append(w.charAt(0));
            }
        }
        if (out.length() < n) {
            String joined = lettersOnly(name).replace(" ", "");
            for (int i = 1; i < joined.length() && out.length() < n; i++) {
                out.append(joined.charAt(i));
            }
        }
        while (out.length() < n) {
            out.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return out.toString();
    }

    /** Three letters: the city's consonant skeleton (Kathmandu → KTH), else padded randomly. */
    static String cityPart(String city) {
        String letters = lettersOnly(city).replace(" ", "");
        StringBuilder out = new StringBuilder();
        if (!letters.isEmpty()) {
            out.append(letters.charAt(0));
            for (int i = 1; i < letters.length() && out.length() < 3; i++) {
                char c = letters.charAt(i);
                if ("AEIOU".indexOf(c) < 0) {
                    out.append(c);
                }
            }
            for (int i = 1; i < letters.length() && out.length() < 3; i++) {
                out.append(letters.charAt(i));
            }
        }
        while (out.length() < 3) {
            out.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return out.substring(0, 3);
    }

    /** Upper-case A-Z and single spaces only (accents stripped, Devanagari dropped). */
    private static String lettersOnly(String s) {
        if (s == null) {
            return "";
        }
        String plain = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z]+", " ").trim();
    }

    private static String randomLetters(int n) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < n; i++) {
            out.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        return out.toString();
    }
}
