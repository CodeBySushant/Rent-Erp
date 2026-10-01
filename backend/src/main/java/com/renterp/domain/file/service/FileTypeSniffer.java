package com.renterp.domain.file.service;

import java.util.Optional;

/**
 * Decides a file's type from its first bytes, never from the name or the
 * client's Content-Type header. Only the formats the app accepts are known.
 */
public final class FileTypeSniffer {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";
    public static final String PDF = "application/pdf";

    private FileTypeSniffer() {
    }

    /** The detected type, or empty when the bytes are not one of the accepted formats. */
    public static Optional<String> detect(byte[] head) {
        if (head == null) {
            return Optional.empty();
        }
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(head, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (head.length >= 12 && startsWith(head, 'R', 'I', 'F', 'F')
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return Optional.of(WEBP);
        }
        if (startsWith(head, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xFF) != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
