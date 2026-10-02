package com.ancientkira.mca.neoforge;

import com.google.common.reflect.TypeToken;
import com.ancientkira.mca.ClientProxyAbstractImpl;
import com.ancientkira.mca.Config;
import com.ancientkira.mca.MCA;
import com.ancientkira.mca.MCAClient;
import com.ancientkira.mca.block.BlockEntityTypesMCA;
import com.ancientkira.mca.client.gui.MCAScreens;
import com.ancientkira.mca.client.particle.InteractionParticle;
import com.ancientkira.mca.client.render.*;
import com.ancientkira.mca.client.resources.ColorPaletteLoader;
import com.ancientkira.mca.client.resources.GeneratedEyeTextureReloadListener;
import com.ancientkira.mca.registry.EntitiesMCA;
import com.ancientkira.mca.registry.ParticleTypesMCA;
import com.ancientkira.mca.resources.ApiReloadListener;
import com.ancientkira.mca.resources.FaceList;
import com.ancientkira.mca.resources.Supporters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.VillagerRenderer;
import net.minecraft.client.renderer.entity.ZombieVillagerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

@Mod(value = MCA.MOD_ID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = MCA.MOD_ID, value = Dist.CLIENT)
public final class ClientNeoForge extends ClientProxyAbstractImpl {
    public ClientNeoForge() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Block entity renderers
        event.registerBlockEntityRenderer(BlockEntityTypesMCA.TOMBSTONE, TombstoneBlockEntityRenderer::new);

        // Entity renderers
        if (Config.getInstance().useSquidwardModels) {
            event.registerEntityRenderer(EntitiesMCA.MALE_VILLAGER, VillagerRenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_VILLAGER, VillagerRenderer::new);
            event.registerEntityRenderer(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerRenderer::new);
        } else {
            event.registerEntityRenderer(EntitiesMCA.MALE_VILLAGER, VillagerEntityMCARenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_VILLAGER, VillagerEntityMCARenderer::new);
            event.registerEntityRenderer(EntitiesMCA.MALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
            event.registerEntityRenderer(EntitiesMCA.FEMALE_ZOMBIE_VILLAGER, ZombieVillagerEntityMCARenderer::new);
        }
        event.registerEntityRenderer(EntitiesMCA.GRIM_REAPER, GrimReaperRenderer::new);
        event.registerEntityRenderer(EntitiesMCA.CRIB, CribEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ParticleTypesMCA.NEG_INTERACTION, InteractionParticle.Factory::new);
        event.registerSpriteSet(ParticleTypesMCA.POS_INTERACTION, InteractionParticle.Factory::new);
    }

    @SubscribeEvent
    public static void data(AddClientReloadListenersEvent event) {
        event.addListener(MCA.locate("screens"), new MCAScreens());
        event.addListener(MCA.locate("color_palettes"), new ColorPaletteLoader());
        event.addListener(MCA.locate("supporters"), new Supporters());
        event.addListener(MCA.locate("api"), new ApiReloadListener());
        event.addListener(MCA.locate("faces_client"), new FaceList());
        event.addListener(GeneratedEyeTextureReloadListener.ID, GeneratedEyeTextureReloadListener.INSTANCE);
    }

    @SubscribeEvent
    public static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        com.ancientkira.mca.KeyBindings.list.forEach(event::register);
    }

    @SubscribeEvent
    public static void onRegisterRenderStateModifiers(RegisterRenderStateModifiersEvent event) {
        event.registerEntityModifier(
                new TypeToken<LivingEntityRenderer<LivingEntity, LivingEntityRenderState, ?>>() {
                },
                (entity, state) -> {
                    VillagerRenderStateHooks.extract(entity, state);
                    VillagerRenderStateHooks.extractScaledBounds(entity, state);
                });
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
        });
    }

    @SubscribeEvent
    public static void onClientConnected(ClientPlayerNetworkEvent.LoggingIn event) {
        MCAClient.onLogin();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        MCAClient.tickClient(Minecraft.getInstance());
    }
}
