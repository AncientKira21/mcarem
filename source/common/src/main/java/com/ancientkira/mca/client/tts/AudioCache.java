package com.ancientkira.mca.client.tts;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.tts.sound.PCMAudioStream;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AudioCache {
    private static final int MIN_SIZE = 128;
    private static final String CACHE_DIR = "tts_cache/";
    private static final MessageDigest MESSAGEDIGEST;
    private static final Map<String, ByteBuffer> inMemory = java.util.Collections.synchronizedMap(
            new java.util.LinkedHashMap<>(64, .75F, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String,ByteBuffer> eldest) { return size()>32; }
            });
    private static final Object[] LOCKS = java.util.stream.IntStream.range(0,256).mapToObj(i->new Object()).toArray();

    static {
        try { MESSAGEDIGEST = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }
    private static Object lock(String identifier) { return LOCKS[(identifier.hashCode()&Integer.MAX_VALUE)%LOCKS.length]; }
    private static void setInMemoryAudio(String identifier, ByteBuffer buffer) {
        inMemory.put(identifier,buffer.asReadOnlyBuffer());
    }
    public static PCMAudioStream getPCMAudioStream(String identifier) {
        ByteBuffer buffer=inMemory.get(identifier);
        if(buffer==null) buffer=readFromDisk(identifier);
        // Every playback gets an independent cursor, including simultaneous speakers.
        return new PCMAudioStream(buffer==null?null:buffer.asReadOnlyBuffer());
    }

    public static boolean get(String identifier, Consumer<OutputStream> retriever, boolean persistent) {
        synchronized(lock(identifier)) {
            if(inMemory.containsKey(identifier))return true;
            if(persistent) return cachedRetrieve(identifier,retriever);
            ByteBuffer buffer=retrieve(retriever);
            if(buffer==null)return false;
            setInMemoryAudio(identifier,buffer);
            return inMemory.containsKey(identifier);
        }
    }

    private static ByteBuffer retrieve(Consumer<OutputStream> retriever) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            retriever.accept(baos);
            return baos.size() > MIN_SIZE ? ByteBuffer.wrap(baos.toByteArray()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static ByteBuffer readFromDisk(String identifier) {
        File cacheFile = new File(CACHE_DIR, identifier);
        if (!isSane(cacheFile)) return null;

        try (FileInputStream fis = new FileInputStream(cacheFile)) {
            return ByteBuffer.wrap(fis.readAllBytes());
        } catch (IOException e) {
            MCA.LOGGER.error("Failed to retrieve cached audio file: {}", identifier, e);
            return null;
        }
    }

    public static boolean cachedRetrieve(String identifier, Consumer<OutputStream> retriever) {
        synchronized(lock(identifier)) {
        try {
            File cacheFile = new File(CACHE_DIR, identifier);
            if (isSane(cacheFile)) {
                return true;
            } else {
                //noinspection ResultOfMethodCallIgnored
                cacheFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    retriever.accept(fos);
                }
                return isSane(cacheFile);
            }
        } catch (IOException | RuntimeException e) {
            new File(CACHE_DIR, identifier).delete();
            MCA.LOGGER.warn("Failed to cache audio file: {}", identifier);
            return false;
        }
    }

    }

    private static boolean isSane(File cacheFile) {
        return cacheFile.exists() && cacheFile.length() > MIN_SIZE;
    }

    public static synchronized String getHash(String text) {
        MESSAGEDIGEST.update(text.getBytes());
        return toHex(MESSAGEDIGEST.digest()).toLowerCase(Locale.ROOT);
    }

    public static String toHex(byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format(Locale.ROOT, "%0" + (bytes.length << 1) + "X", bi);
    }
}
