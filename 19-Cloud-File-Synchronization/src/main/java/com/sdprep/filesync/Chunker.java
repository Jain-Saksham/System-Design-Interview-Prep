package com.sdprep.filesync;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * Splits files into fixed-size chunks and names each chunk by its SHA-256 hash.
 * Identical chunks get identical names, so they are uploaded and stored once.
 *
 * <p>Production uses ~4 MB chunks, often content-defined (boundaries chosen by a rolling hash), so
 * inserting a byte near the start of a file doesn't shift and change every following chunk.
 */
public final class Chunker {

    private Chunker() {
    }

    public static List<byte[]> split(byte[] data, int chunkSize) {
        List<byte[]> chunks = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += chunkSize) {
            chunks.add(Arrays.copyOfRange(data, offset, Math.min(offset + chunkSize, data.length)));
        }
        return chunks;
    }

    public static String hash(byte[] chunk) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(chunk));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in the JDK", e);
        }
    }

    static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
