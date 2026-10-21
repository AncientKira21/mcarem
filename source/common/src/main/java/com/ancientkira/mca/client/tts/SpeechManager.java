package com.ancientkira.mca.client.tts;

import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.client.tts.sound.CustomEntityBoundSoundInstance;
import com.ancientkira.mca.client.tts.sound.SingleWeighedSoundEvents;
import com.ancientkira.mca.entity.CommonSpeechManager;
import com.ancientkira.mca.entity.VillagerEntityMCA;
import com.ancientkira.mca.entity.ai.Genetics;
import com.ancientkira.mca.util.LimitedLinkedHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.EntityBoundSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class SpeechManager {
    public static final SpeechManager INSTANCE = new SpeechManager();

    private final Minecraft client;
    private final LimitedLinkedHashMap<UUID, EntityBoundSoundInstance> currentlyPlaying = new LimitedLinkedHashMap<>(10);

    private final RealtimeSpeechManager realtimeSpeechManager = new RealtimeSpeechManager(Config.getInstance().onlineTTSServer);
    private final Player2SpeechManager player2SpeechManager = new Player2SpeechManager(Config.getInstance().player2Url);
    private final VoiceboxSpeechManager voiceboxSpeechManager = new VoiceboxSpeechManager();
    private final ElevenlabsSpeechManager elevenlabsSpeechManager = new ElevenlabsSpeechManager();
    private final OnlineSpeechManager onlineSpeechManager = new OnlineSpeechManager();

    @SuppressWarnings("deprecation")
    private final RandomSource threadSafeRandom = RandomSource.createThreadSafe();
    private long lastHealthCheckTime = 0;
    private boolean firstRun = true;

    public SpeechManager() {
        client = Minecraft.getInstance();
    }

    public EntityBoundSoundInstance getSound(float pitch, Entity entity, Identifier soundLocation) {
        Sound sound = new Sound(soundLocation, ConstantFloat.of(1.0f), ConstantFloat.of(1.0f), 1, Sound.Type.FILE, true, false, 16);
        SingleWeighedSoundEvents weightedSoundEvents = new SingleWeighedSoundEvents(sound, soundLocation, "");
        return new CustomEntityBoundSoundInstance(weightedSoundEvents, SoundEvent.createVariableRangeEvent(soundLocation), SoundSource.NEUTRAL, 1.0f, pitch, entity, threadSafeRandom.nextLong());
    }

    public void playSound(float pitch, Entity entity, Identifier soundLocation) {
        client.execute(() -> client.getSoundManager().play(SpeechManager.INSTANCE.getSound(pitch, entity, soundLocation)));
    }

    private static final String[] GREETINGS = {"Hi!", "Hey!", "Hey, how are you?", "Hi there!", "Hello!"};
    private static final String[] SCREAM_REACTIONS = {"[scream] Aaaah!", "[scream] Aaagh!", "[scream] Aaaaaah!", "[scream] Aaargh!"};
    private static final String[] HURT_REACTIONS = {"Ouch!", "Ow!"};
    private final java.util.Set<UUID> pendingGreetings = new java.util.HashSet<>();
    private final java.util.Set<UUID> pendingScreams = new java.util.HashSet<>();
    private final java.util.Set<UUID> pendingHurtReactions = new java.util.HashSet<>();

    public void playGreeting(VillagerEntityMCA villager) {
        if (villager.getVillagerBrain().isPanicking()) return;
        playReaction(villager, false);
    }

    public void playHurt(VillagerEntityMCA villager) {
        playReaction(villager, true);
    }

    public void playScream(VillagerEntityMCA villager) {
        playReaction(villager, false, true);
    }

    private void playReaction(VillagerEntityMCA villager, boolean hurt) {
        playReaction(villager, hurt, false);
    }

    private void playReaction(VillagerEntityMCA villager, boolean hurt, boolean scream) {
        if (client.level == null || (!scream && villager.isRemoved()) || (!hurt && !scream && villager.isToYoungToSpeak())) return;
        if (!Config.getInstance().enableOnlineTTS || villager.isSpeechImpaired()) {
            if (hurt || scream || Config.getInstance().useMCAVoices) playReactionFallback(villager, hurt, scream);
            return;
        }
        var pending = scream ? pendingScreams : hurt ? pendingHurtReactions : pendingGreetings;
        if (!pending.add(villager.getUUID())) return;
        String[] phrases = scream ? SCREAM_REACTIONS : hurt ? HURT_REACTIONS : GREETINGS;
        String phrase = phrases[ThreadLocalRandom.current().nextInt(phrases.length)];
        String gender = villager.getGenetics().getGender().binary().getDataName();
        float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);
        float pitch = villager.getVoicePitch();
        String selectedVoice = villager.getTrackedValue(com.ancientkira.mca.entity.VillagerLike.VOICE_ID);
        String provider = Config.getInstance().onlineTTSModel;
        String gameLang = client.options.languageCode;
        // Bracketed non-verbal tags are only supported by our ElevenLabs route.
        if (scream && !"elevenlabs".equals(provider)) {
            pending.remove(villager.getUUID());
            playReactionFallback(villager, hurt, true);
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            switch (provider) {
                case "voicebox": {
                    return prepareGreeting(voiceboxSpeechManager, VoiceboxSpeechManager.voice(villager), phrase);
                }
                case "elevenlabs": {
                    String voice = selectedVoice.isBlank() ? ElevenlabsVoices.automaticVoice(gender, gene).join() : selectedVoice;
                    return prepareGreeting(elevenlabsSpeechManager, voice, phrase);
                }
                case "realtime": {
                    var voices = realtimeSpeechManager.getVoices(gameLang, gender);
                    if (voices == null || voices.isEmpty()) return null;
                    String voice = voices.get(Math.clamp((int) Math.floor(gene * voices.size()), 0, voices.size() - 1));
                    return prepareGreeting(realtimeSpeechManager, voice, phrase);
                }
                case "player2": {
                    var voices = player2SpeechManager.getVoices(gameLang, gender);
                    if (voices == null || voices.isEmpty()) return null;
                    String voice = voices.get(Math.clamp((int) Math.floor(gene * voices.size()), 0, voices.size() - 1));
                    // Player2 plays in its app; an acknowledged request needs no local sound.
                    return player2SpeechManager.playInApp(phrase, pitch, voice) ? "" : null;
                }
                default: {
                    String language = com.ancientkira.mca.client.tts.resources.OnlineLanguageMap.LANGUAGE_MAP.getOrDefault(gameLang, "");
                    if (language.isEmpty()) return null;
                    String voice = gender + "_" + Math.clamp((int) Math.floor(gene * OnlineSpeechManager.TOTAL_VOICES), 0, OnlineSpeechManager.TOTAL_VOICES - 1);
                    String hash = language + "-" + voice + "/" + AudioCache.getHash(phrase) + ".ogg";
                    boolean ready = AudioCache.cachedRetrieve(hash, output -> {
                        if (!onlineSpeechManager.downloadAudio(output, language, voice, phrase)) throw new IllegalStateException("Greeting unavailable");
                    });
                    return ready ? hash : null;
                }
            }
        }).orTimeout(20, java.util.concurrent.TimeUnit.SECONDS).whenComplete((hash, failure) -> client.execute(() -> {
            pending.remove(villager.getUUID());
            if (client.level != villager.level() || (!scream && villager.isRemoved())) return;
            if (!hurt && !scream && villager.getVillagerBrain().isPanicking()) return;
            if (failure != null || hash == null) playReactionFallback(villager, hurt, scream);
            else if (!hash.isEmpty()) {
                Identifier location = MCA.locate("tts_cache/" + hash);
                if (scream) {
                    Sound audio = new Sound(location, ConstantFloat.of(1.0f), ConstantFloat.of(1.0f), 1, Sound.Type.FILE, true, false, 16);
                    client.getSoundManager().play(new CustomEntityBoundSoundInstance(new SingleWeighedSoundEvents(audio, location, ""),
                            SoundEvent.createVariableRangeEvent(location), SoundSource.NEUTRAL, 1.0f, pitch, villager, threadSafeRandom.nextLong()) {
                        @Override public void tick() { }
                        @Override public boolean canPlaySound() { return true; }
                    });
                } else playSound(pitch, villager, location);
            }
        }));
    }

    private String prepareGreeting(RealtimeSpeechManager provider, String voice, String phrase) {
        if (voice == null || voice.isBlank() && !(provider instanceof VoiceboxSpeechManager)) return null;
        String hash = provider.cacheKey(voice, phrase);
        boolean ready = AudioCache.get(hash, output -> {
            if (!provider.downloadAudio(output, voice, phrase)) throw new IllegalStateException("Greeting unavailable");
        }, true);
        return ready ? hash : null;
    }

    private void playReactionFallback(VillagerEntityMCA villager, boolean hurt, boolean scream) {
        if (scream) {
            var sound = Config.getInstance().useMCAVoices
                    ? (villager.getGenetics().getGender() == com.ancientkira.mca.entity.ai.relationship.Gender.MALE
                        ? com.ancientkira.mca.registry.SoundsMCA.VILLAGER_MALE_SCREAM
                        : com.ancientkira.mca.registry.SoundsMCA.VILLAGER_FEMALE_SCREAM)
                    : Config.getInstance().useVanillaVoices ? net.minecraft.sounds.SoundEvents.VILLAGER_DEATH
                    : com.ancientkira.mca.registry.SoundsMCA.SILENT;
            client.getSoundManager().play(new EntityBoundSoundInstance(sound, SoundSource.NEUTRAL, 1.0F,
                    villager.getVoicePitch(), villager, threadSafeRandom.nextLong()) {
                @Override public void tick() { }
                @Override public boolean canPlaySound() { return true; }
            });
            return;
        }
        if (hurt) {
            var sound = Config.getInstance().useMCAVoices
                    ? (villager.getGenetics().getGender() == com.ancientkira.mca.entity.ai.relationship.Gender.MALE
                        ? com.ancientkira.mca.registry.SoundsMCA.VILLAGER_MALE_HURT
                        : com.ancientkira.mca.registry.SoundsMCA.VILLAGER_FEMALE_HURT)
                    : Config.getInstance().useVanillaVoices ? net.minecraft.sounds.SoundEvents.VILLAGER_HURT
                    : com.ancientkira.mca.registry.SoundsMCA.SILENT;
            client.getSoundManager().play(new EntityBoundSoundInstance(sound, SoundSource.NEUTRAL, 1.0F,
                    villager.getVoicePitch(), villager, threadSafeRandom.nextLong()));
            return;
        }
        var sound = villager.getGenetics().getGender() == com.ancientkira.mca.entity.ai.relationship.Gender.MALE
                ? com.ancientkira.mca.registry.SoundsMCA.VILLAGER_MALE_GREET
                : com.ancientkira.mca.registry.SoundsMCA.VILLAGER_FEMALE_GREET;
        client.getSoundManager().play(new EntityBoundSoundInstance(sound, SoundSource.NEUTRAL, 1.0F,
                villager.getVoicePitch(), villager, threadSafeRandom.nextLong()));
    }

    public void playPreview(VillagerEntityMCA villager) {
        stopPreview(villager);
        String phrase = VoicePreviewSamples.random(ThreadLocalRandom.current());
        if (Config.getInstance().enableOnlineTTS) {
            speak(phrase, villager.getUUID(), villager, true);
        } else {
            tryPlayVoicePackSound(phrase, villager.getUUID(), villager);
        }
    }

    public boolean canPreviewVoiceTone() {
        return Config.getInstance().enableOnlineTTS || hasVoicePackSounds();
    }

    public void stopPreview(VillagerEntityMCA villager) {
        EntityBoundSoundInstance sound = currentlyPlaying.remove(villager.getUUID());
        if (sound != null) {
            client.getSoundManager().stop(sound);
        }
    }

    public void onChatMessage(Component message, UUID sender) {
        ComponentContents content = message.getContents();
        if (CommonSpeechManager.INSTANCE.translations.containsKey(content)) {
            String key = CommonSpeechManager.INSTANCE.translations.get(content);
            if ("villager.scream".equals(key) || key.startsWith("villager.scream/")) {
                VillagerEntityMCA villager = getSpeaker(client, sender);
                if (villager != null) playScream(villager);
                return;
            }
            speak(key, sender, true);
        } else if (content instanceof PlainTextContents.LiteralContents(String text)) {
            speak(text, sender, false);
        }
    }

    private VillagerEntityMCA getSpeaker(Minecraft client, UUID sender) {
        if (client.level != null) {
            for (Entity entity : client.level.entitiesForRendering()) {
                if (entity instanceof VillagerEntityMCA v && entity.getUUID().equals(sender)) {
                    return v;
                }
            }
        }
        return null;
    }

    private void speak(String phrase, UUID sender, boolean translatable) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        if (currentlyPlaying.containsKey(sender) && client.getSoundManager().isActive(currentlyPlaying.get(sender))) {
            return;
        }

        VillagerEntityMCA villager = getSpeaker(client, sender);
        if (villager == null) return;

        speak(phrase, sender, villager, translatable);
    }

    private void speak(String phrase, UUID sender, VillagerEntityMCA villager, boolean translatable) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        if (villager.isSpeechImpaired()) return;
        if (villager.isToYoungToSpeak()) return;

        float pitch = villager.getVoicePitch();
        float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);

        String gender = villager.getGenetics().getGender().binary().getDataName();
        if (Config.getInstance().enableOnlineTTS) {
            if (translatable) {
                if (Language.getInstance().has(phrase)) {
                    phrase = Language.getInstance().getOrDefault(phrase);
                } else {
                    MCA.LOGGER.warn("Tried to play a TTS sound for a non-translatable phrase: {}", phrase);
                    return;
                }
            }

            String gameLang = client.options.languageCode;
            switch (Config.getInstance().onlineTTSModel) {
                case "realtime" ->
                        realtimeSpeechManager.play(phrase, gender, gameLang, pitch, gene, villager, translatable);
                case "player2" -> player2SpeechManager.play(phrase, gender, gameLang, pitch, gene);
                case "voicebox" -> {
                    String speech = phrase;
                    CompletableFuture.supplyAsync(() -> prepareGreeting(voiceboxSpeechManager,
                            VoiceboxSpeechManager.voice(villager), speech))
                            .orTimeout(18, java.util.concurrent.TimeUnit.SECONDS)
                            .whenComplete((hash, error) -> client.execute(() -> {
                                if (client.level != villager.level() || villager.isRemoved()) return;
                                if (error == null && hash != null) playSound(pitch, villager, MCA.locate("tts_cache/" + hash));
                                else if (translatable) tryPlayVoicePackSound(speech, sender, villager);
                            }));
                }
                case "elevenlabs" -> elevenlabsSpeechManager.play(phrase, gender, pitch, gene, villager, translatable);
                default -> onlineSpeechManager.play(phrase, gameLang, gender, pitch, gene, villager, translatable);
            }
        } else if (translatable) {
            tryPlayVoicePackSound(phrase, sender, villager);
        }
    }

    private boolean tryPlayVoicePackSound(String phrase, UUID sender, VillagerEntityMCA villager) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return false;
        }

        float pitch = villager.getVoicePitch();
        float gene = villager.getGenetics().getGene(Genetics.VOICE_TONE);
        String gender = villager.getGenetics().getGender().binary().getDataName();
        int tone = Math.min(9, (int) Math.floor(gene * 10.0f));
        Identifier sound = Identifier.fromNamespaceAndPath("mca_voices", phrase.toLowerCase(Locale.ROOT) + "/" + gender + "_" + tone);
        Collection<Identifier> keys = client.getSoundManager().getAvailableSounds();
        if (!keys.contains(sound)) {
            return false;
        }

        EntityBoundSoundInstance instance = new EntityBoundSoundInstance(SoundEvent.createVariableRangeEvent(sound), SoundSource.NEUTRAL, 1.0f, pitch, villager, threadSafeRandom.nextLong());
        currentlyPlaying.put(sender, instance);
        client.getSoundManager().play(instance);
        return true;
    }

    private boolean hasVoicePackSounds() {
        return client.getSoundManager().getAvailableSounds().stream()
                .anyMatch(sound -> sound.getNamespace().equals("mca_voices"));
    }

    public void tick(Minecraft client) {
        if (client.level != null) {
            long time = client.level.getGameTime();
            if (Math.abs(time - lastHealthCheckTime) > 1200) {
                boolean enabled = Config.getInstance().villagerChatAIModel.equals("player2");
                if (firstRun || enabled) {
                    CompletableFuture.runAsync(() -> {
                        if (player2SpeechManager.checkHealth() && firstRun && !enabled) {
                            client.gui.getChat().addClientSystemMessage(Component.translatable("command.chat_ai.player2.hint", "/mca chatAI player2"));
                            firstRun = false;
                        }
                    });
                }
                lastHealthCheckTime = time;
            }
        }
    }
}
