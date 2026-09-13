package com.example.mainapp.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import javafx.collections.FXCollections;

class MainControllerCreateNewFileTest {

    @Test
    void trimsAndPreservesValidFilename() {
        assertEquals("notes.md", MainController.validateNewFilename("  notes.md  "));
    }

    @Test
    void rejectsBlankAndUnsafeFilenames() {
        assertThrows(IllegalArgumentException.class,
                () -> MainController.validateNewFilename("   "));
        assertThrows(IllegalArgumentException.class,
                () -> MainController.validateNewFilename("../notes.txt"));
        assertThrows(IllegalArgumentException.class,
                () -> MainController.validateNewFilename("folder\\notes.txt"));
    }

    @Test
    void writesAtLeastOneMillionCharactersAsUtf8WithoutTruncation() throws Exception {
        Path source = Files.createTempFile("create-new-file-test", ".txt");
        try {
            String content = "é".repeat(1_000_000);
            long expectedBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            assertEquals(expectedBytes, MainController.writeNewFileSource(source, content));
            assertEquals(expectedBytes, Files.size(source));
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void cleanupRemovesOnlyTheNewFileTemporaryArtifact() throws Exception {
        Path directory = Files.createTempDirectory("create-new-file-cleanup");
        Path source = directory.resolve("notes.txt");
        Files.writeString(source, "content");

        MainController.cleanupNewFileSource(source, directory);

        assertEquals(false, Files.exists(source));
        assertEquals(false, Files.exists(directory));
    }

    @Test
    void appendingCreatedFileOncePreventsDuplicateRows() {
        var files = FXCollections.<com.example.mainapp.model.File>observableArrayList();
        var created = new com.example.mainapp.model.File(42, 7, "notes.txt", 7,
                LocalDateTime.now(), LocalDateTime.now());

        MainController.appendCreatedFileOnce(files, created);
        MainController.appendCreatedFileOnce(files, created);

        assertEquals(1, files.size());
    }

    @Test
    void readsLargeUtf8TextWithUnicodeAndLineBreaks() throws Exception {
        Path source = Files.createTempFile("large-text", ".txt");
        try {
            String content = ("Wikipedia — café “text”\n").repeat(12_000);
            Files.writeString(source, content, StandardCharsets.UTF_8);
            assertEquals(content, MainController.readTextFile(source).content());
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void stripsUtf8Bom() throws Exception {
        Path source = Files.createTempFile("bom-text", ".txt");
        try {
            Files.write(source, concat(new byte[]{(byte) 0xef, (byte) 0xbb, (byte) 0xbf},
                    "hello".getBytes(StandardCharsets.UTF_8)));
            assertEquals("hello", MainController.readTextFile(source).content());
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void fallsBackToWindows1252ForText() throws Exception {
        Path source = Files.createTempFile("windows-text", ".txt");
        try {
            Files.write(source, "smart \u201cquotes\u201d".getBytes(Charset.forName("windows-1252")));
            assertEquals("Opened using Windows-1252 text decoding.",
                    MainController.readTextFile(source).status());
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void rejectsBinaryNulBytes() throws Exception {
        Path source = Files.createTempFile("binary", ".bin");
        try {
            Files.write(source, new byte[]{'a', 0, 'b'});
            assertThrows(java.nio.charset.CharacterCodingException.class,
                    () -> MainController.readTextFile(source));
        } finally {
            Files.deleteIfExists(source);
        }
    }

    @Test
    void readsOneMillionUtf8CharactersWithoutTruncation() throws Exception {
        Path source = Files.createTempFile("million-text", ".txt");
        try {
            String content = "\u00e9".repeat(1_000_000);
            Files.writeString(source, content, StandardCharsets.UTF_8);
            assertEquals(content, MainController.readTextFile(source).content());
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = java.util.Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
