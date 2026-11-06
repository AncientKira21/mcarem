package com.ancientkira.mca.client.tts;
public final class SpeechWorkers {
    private SpeechWorkers() { }
    public static final java.util.concurrent.ExecutorService EXECUTOR = java.util.concurrent.Executors.newFixedThreadPool(4, task -> {
        Thread thread=new Thread(task,"MCA speech"); thread.setDaemon(true); return thread;
    });
}
