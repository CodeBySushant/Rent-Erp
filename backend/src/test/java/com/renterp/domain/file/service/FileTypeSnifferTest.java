package com.renterp.domain.file.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FileTypeSnifferTest {

    private static byte[] bytes(int... b) {
        byte[] out = new byte[b.length];
        for (int i = 0; i < b.length; i++) {
            out[i] = (byte) b[i];
        }
        return out;
    }

    @Test
    void recognisesTheAcceptedFormatsFromTheirBytes() {
        assertEquals(FileTypeSniffer.JPEG, FileTypeSniffer.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0)).orElseThrow());
        assertEquals(FileTypeSniffer.PNG,
                FileTypeSniffer.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0)).orElseThrow());
        assertEquals(FileTypeSniffer.WEBP,
                FileTypeSniffer.detect("RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1)).orElseThrow());
        assertEquals(FileTypeSniffer.PDF,
                FileTypeSniffer.detect("%PDF-1.7\n".getBytes(StandardCharsets.US_ASCII)).orElseThrow());
    }

    @Test
    void refusesEverythingElseWhateverItIsCalled() {
        assertTrue(FileTypeSniffer.detect("<html><script>".getBytes(StandardCharsets.US_ASCII)).isEmpty());
        assertTrue(FileTypeSniffer.detect(bytes('M', 'Z', 0x90, 0)).isEmpty());          // Windows .exe
        assertTrue(FileTypeSniffer.detect("RIFF\0\0\0\0WAVEfmt ".getBytes(StandardCharsets.ISO_8859_1)).isEmpty());
        assertTrue(FileTypeSniffer.detect(new byte[0]).isEmpty());
        assertTrue(FileTypeSniffer.detect(null).isEmpty());
        assertTrue(FileTypeSniffer.detect(bytes(0xFF, 0xD8)).isEmpty());                // truncated
    }

    @Test
    void cleanNameDropsFoldersAndUnsafeCharacters() {
        assertEquals("passwd", FileService.cleanName("../../etc/passwd"));
        assertEquals("evil.png", FileService.cleanName("C:\\Users\\x\\evil.png"));
        assertEquals("ab.jpg", FileService.cleanName("a\u0000b.jpg"));
        assertNull(FileService.cleanName("folder/"));
        assertNull(FileService.cleanName(null));
        assertEquals(100, FileService.cleanName("x".repeat(300)).length());
    }
}
