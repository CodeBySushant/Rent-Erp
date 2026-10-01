package com.renterp.domain.file.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalFileStorageTest {

    @TempDir
    Path dir;

    @Test
    void storesOpensAndDeletes() throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir.toString());
        byte[] data = {1, 2, 3};
        storage.store("2026/10/abc", new ByteArrayInputStream(data), data.length, "image/png");
        try (InputStream in = storage.open("2026/10/abc")) {
            assertArrayEquals(data, in.readAllBytes());
        }
        storage.delete("2026/10/abc");
        assertFalse(Files.exists(dir.resolve("2026/10/abc")));
    }

    @Test
    void keysCannotLeaveTheUploadFolder() {
        LocalFileStorage storage = new LocalFileStorage(dir.toString());
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve("2026/../../outside"));
        assertThrows(IllegalArgumentException.class, () -> storage.resolve(""));
    }
}
