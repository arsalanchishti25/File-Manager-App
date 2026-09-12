package com.example.mainapp.service;

import com.example.mainapp.model.File;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileServiceUpdateIdentityTest {

    private static final LocalDateTime VERSION =
            LocalDateTime.of(2026, 9, 12, 22, 0, 0, 123456789);

    @Test
    void acceptsUnchangedRemoteMetadataAndNormalizesDatabasePrecision() {
        File snapshot = file(VERSION);
        File current = file(VERSION.withNano(123456000));

        assertDoesNotThrow(() -> FileService.validateUpdateIdentity(snapshot, current, 4));
    }

    @Test
    void rejectsGenuineRemoteVersionChange() {
        File snapshot = file(VERSION);
        File current = file(VERSION.plusSeconds(1));

        assertThrows(IllegalStateException.class,
                () -> FileService.validateUpdateIdentity(snapshot, current, 4));
    }

    @Test
    void rejectsFileIdOwnerAndFilenameChanges() {
        File snapshot = file(VERSION);

        assertThrows(IllegalArgumentException.class,
                () -> FileService.validateUpdateIdentity(snapshot,
                        new File(8, 4, "name.txt", 1, VERSION, VERSION), 4));
        assertThrows(IllegalArgumentException.class,
                () -> FileService.validateUpdateIdentity(snapshot,
                        new File(7, 9, "name.txt", 1, VERSION, VERSION), 4));
        assertThrows(IllegalArgumentException.class,
                () -> FileService.validateUpdateIdentity(snapshot,
                        new File(7, 4, "other.txt", 1, VERSION, VERSION), 4));
    }

    private static File file(LocalDateTime version) {
        return new File(7, 4, "name.txt", 10, version, version);
    }
}
