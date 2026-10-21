package com.sdprep.filesync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.sdprep.filesync.Chunker.utf8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSyncTest {

    private static final int CHUNK = 4;

    private SyncServer server;
    private SyncClient laptop;
    private SyncClient phone;

    @BeforeEach
    void setUp() {
        server = new SyncServer();
        laptop = new SyncClient("laptop", server, CHUNK);
        phone = new SyncClient("phone", server, CHUNK);
    }

    @Test
    void firstUploadSendsEveryChunkAndOtherDeviceReceivesTheFile() {
        laptop.writeLocal("a.txt", utf8("aaaabbbbcccc"));

        SyncClient.UploadResult result = laptop.upload("a.txt");

        assertEquals(3, result.chunksUploaded());
        assertEquals(1, phone.pullChanges());
        assertArrayEquals(utf8("aaaabbbbcccc"), phone.readLocal("a.txt").orElseThrow());
    }

    @Test
    void editingOneChunkUploadsOnlyThatChunk() {
        laptop.writeLocal("a.txt", utf8("aaaabbbbcccc"));
        laptop.upload("a.txt");

        laptop.writeLocal("a.txt", utf8("aaaaXXXXcccc"));
        SyncClient.UploadResult result = laptop.upload("a.txt");

        assertEquals(3, result.chunksTotal());
        assertEquals(1, result.chunksUploaded());
    }

    @Test
    void identicalContentIsNeverUploadedTwice() {
        laptop.writeLocal("a.txt", utf8("aaaabbbb"));
        laptop.upload("a.txt");

        phone.writeLocal("copy.txt", utf8("aaaabbbb"));

        assertEquals(0, phone.upload("copy.txt").chunksUploaded());
        assertEquals(2, server.storedChunkCount());
    }

    @Test
    void concurrentEditsProduceAConflictedCopyAndLoseNothing() {
        laptop.writeLocal("a.txt", utf8("v1"));
        laptop.upload("a.txt");
        phone.pullChanges();

        phone.writeLocal("a.txt", utf8("phone edit"));
        laptop.writeLocal("a.txt", utf8("laptop edit"));
        assertTrue(phone.upload("a.txt").conflictCopyPath().isEmpty());
        SyncClient.UploadResult laptopResult = laptop.upload("a.txt");
        phone.pullChanges();

        String copy = laptopResult.conflictCopyPath().orElseThrow();
        assertEquals("a (conflicted copy from laptop).txt", copy);
        assertEquals(List.of("a (conflicted copy from laptop).txt", "a.txt"), server.livePaths());
        for (SyncClient device : List.of(laptop, phone)) {
            assertArrayEquals(utf8("phone edit"), device.readLocal("a.txt").orElseThrow());
            assertArrayEquals(utf8("laptop edit"), device.readLocal(copy).orElseThrow());
        }
    }

    @Test
    void deletionPropagatesToOtherDevices() {
        laptop.writeLocal("a.txt", utf8("hello"));
        laptop.upload("a.txt");
        phone.pullChanges();

        laptop.deleteAndUpload("a.txt");
        phone.pullChanges();

        assertTrue(phone.readLocal("a.txt").isEmpty());
        assertFalse(server.livePaths().contains("a.txt"));
    }

    @Test
    void pullingTwiceAppliesChangesOnce() {
        laptop.writeLocal("a.txt", utf8("hello"));
        laptop.upload("a.txt");

        assertEquals(1, phone.pullChanges());
        assertEquals(0, phone.pullChanges());
        assertEquals(0, laptop.pullChanges()); // its own upload isn't re-applied
    }

    @Test
    void serverRejectsCommitsWithMissingChunksOrCorruptedUploads() {
        assertThrows(IllegalStateException.class,
                () -> server.commit("a.txt", 0, List.of(Chunker.hash(utf8("never uploaded"))), 14, false, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> server.uploadChunk(Chunker.hash(utf8("abc")), utf8("abd")));
    }

    @Test
    void staleBaseVersionIsRejected() {
        server.uploadChunk(Chunker.hash(utf8("x")), utf8("x"));
        List<String> chunks = List.of(Chunker.hash(utf8("x")));
        server.commit("a.txt", 0, chunks, 1, false, "laptop");

        assertFalse(server.commit("a.txt", 0, chunks, 1, false, "phone").accepted());
        assertTrue(server.commit("a.txt", 1, chunks, 1, false, "phone").accepted());
    }
}
