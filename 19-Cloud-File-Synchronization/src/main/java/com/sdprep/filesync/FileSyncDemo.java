package com.sdprep.filesync;

import java.nio.charset.StandardCharsets;

/** Runnable walkthrough: first upload, sync to another device, delta upload, dedup, conflict, delete. */
public class FileSyncDemo {

    private static final int CHUNK_SIZE = 8; // tiny so a short text has several chunks

    public static void main(String[] args) {
        SyncServer cloud = new SyncServer();
        SyncClient laptop = new SyncClient("laptop", cloud, CHUNK_SIZE);
        SyncClient phone = new SyncClient("phone", cloud, CHUNK_SIZE);
        System.out.println("Chunk size: 8 bytes (production: ~4 MB)\n");

        laptop.writeLocal("notes.txt", bytes("Buy milk. Call mom. Gym at 6."));
        report("laptop creates notes.txt", laptop.upload("notes.txt"));
        System.out.println("phone pulls -> " + phone.pullChanges() + " file(s) updated: \"" + text(phone, "notes.txt") + "\"");

        laptop.writeLocal("notes.txt", bytes("Buy oats. Call mom. Gym at 6."));
        report("\nlaptop edits one word", laptop.upload("notes.txt"));

        phone.pullChanges();
        phone.writeLocal("notes-backup.txt", bytes("Buy oats. Call mom. Gym at 6."));
        report("phone saves an identical copy as notes-backup.txt", phone.upload("notes-backup.txt"));

        System.out.println("\nBoth devices edit notes.txt while offline...");
        phone.writeLocal("notes.txt", bytes("Buy oats. Call dad. Gym at 6."));
        laptop.writeLocal("notes.txt", bytes("Buy oats. Call mom. Gym at 7."));
        report("phone syncs first", phone.upload("notes.txt"));
        report("laptop syncs second", laptop.upload("notes.txt"));
        phone.pullChanges();
        System.out.println("Files on every device: " + cloud.livePaths());
        System.out.println("  notes.txt = \"" + text(laptop, "notes.txt") + "\" (phone's version won the race)");

        phone.deleteAndUpload("notes-backup.txt");
        laptop.pullChanges();
        System.out.println("\nphone deletes notes-backup.txt -> laptop now has: " + laptop.localPaths());
        System.out.println("Unique chunks stored in the cloud: " + cloud.storedChunkCount());
    }

    private static void report(String action, SyncClient.UploadResult result) {
        System.out.printf("%s -> uploaded %d of %d chunks%s%n", action, result.chunksUploaded(), result.chunksTotal(),
                result.conflictCopyPath().map(copy -> ", CONFLICT: saved as \"" + copy + "\"").orElse(""));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static String text(SyncClient device, String path) {
        return new String(device.readLocal(path).orElseThrow(), StandardCharsets.UTF_8);
    }
}
