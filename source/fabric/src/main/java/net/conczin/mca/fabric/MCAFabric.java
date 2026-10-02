package com.ancientkira.mca.fabric;

import com.ancientkira.mca.MCA;
import com.ancientkira.mca.block.BlockEntityTypesMCA;
import com.ancientkira.mca.entity.ai.ActivitiesMCA;
import com.ancientkira.mca.entity.ai.MemoryModuleTypeMCA;
import com.ancientkira.mca.entity.ai.SchedulesMCA;
import com.ancientkira.mca.entity.ai.SensorsMCA;
import com.ancientkira.mca.entity.interaction.gifts.GiftLoader;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.MessagesMCA;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.registry.*;
import com.ancientkira.mca.resources.*;
import com.ancientkira.mca.server.ServerInteractionManager;
import com.ancientkira.mca.server.command.AdminCommand;
import com.ancientkira.mca.server.command.Command;
import com.ancientkira.mca.server.world.data.VillageManager;
import com.ancientkira.mca.util.network.datasync.MCAEntityDataSerializers;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public final class MCAFabric implements ModInitializer {
    static {
        MCA.platformHelper = new FabricPlatformHelper();
    }

    Network.Registrar fabricRegistrar = new Network.Registrar() {
        @Override
        public <T extends HandleablePayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean isServer) {
            if (isServer) {
                PayloadTypeRegistry.serverboundPlay().register(type, codec);
                ServerPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> ctx.server().execute(() -> payload.handle(ctx.player())));
            } else {
                PayloadTypeRegistry.clientboundPlay().register(type, codec);
                if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
                    ClientProxy.register(type);
                }
            }
        }
    };

    private static <T> void registerHelper(Registry<T> register, Consumer<MCA.RegisterHelper<T>> consumer) {
        consumer.accept((name, value) -> Registry.register(register, name, value));
    }

    private static void registerReloadListener(ResourceLoader loader, net.minecraft.resources.Identifier id, PreparableReloadListener listener) {
        loader.registerReloadListener(id, listener);
    }

    @Override
    public void onInitialize() {
        FabricEntityDataRegistry.register(MCAEntityDataSerializers.COMPOUND_TAG_ID, MCAEntityDataSerializers.COMPOUND_TAG);
        FabricEntityDataRegistry.register(MCAEntityDataSerializers.OPTIONAL_UUID_ID, MCAEntityDataSerializers.OPTIONAL_UUID);

        registerHelper(BuiltInRegistries.ITEM, ItemsMCA::registerItems);
        registerHelper(BuiltInRegistries.BLOCK, BlocksMCA::registerBlocks);
        registerHelper(BuiltInRegistries.SOUND_EVENT, SoundsMCA::registerSounds);
        registerHelper(BuiltInRegistries.PARTICLE_TYPE, ParticleTypesMCA::registerParticles);
        registerHelper(BuiltInRegistries.ENTITY_TYPE, EntitiesMCA::registerEntities);
        registerHelper(BuiltInRegistries.SENSOR_TYPE, SensorsMCA::registerSensors);
        registerHelper(BuiltInRegistries.ACTIVITY, ActivitiesMCA::registerActivities);
        registerHelper(BuiltInRegistries.MEMORY_MODULE_TYPE, MemoryModuleTypeMCA::registerTypes);
        registerHelper(BuiltInRegistries.ENVIRONMENT_ATTRIBUTE, SchedulesMCA::registerSchedules);
        registerHelper(BuiltInRegistries.VILLAGER_PROFESSION, ProfessionsMCA::registerProfessions);
        registerHelper(BuiltInRegistries.DATA_COMPONENT_TYPE, DataComponentsMCA::registerProfessions);
        registerHelper(BuiltInRegistries.TRIGGER_TYPES, CriterionMCA::registerCriteria);

        SchedulesMCA.bootstrap();
        TagsMCA.Blocks.bootstrap();
        TagsMCA.Items.bootstrap();

        BlockEntityTypesMCA.registerBlockEntityTypes((name, factory, blocks) ->
                Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, name, FabricBlockEntityTypeBuilder.create(factory::create, blocks).build()));

        EntitiesMCA.registerAttributes(FabricDefaultAttributeRegistry::register);
        MessagesMCA.register(fabricRegistrar);
        Network.registerSender(ServerPlayNetworking::send);

        // Register resource reload listeners
        ResourceLoader resourceLoader = ResourceLoader.get(PackType.SERVER_DATA);
        registerReloadListener(resourceLoader, ApiReloadListener.ID, new ApiReloadListener());
        registerReloadListener(resourceLoader, BodySkinList.ID, new BodySkinList());
        registerReloadListener(resourceLoader, ClothingList.ID, new ClothingList());
        registerReloadListener(resourceLoader, HairStyleList.ID, new HairStyleList());
        registerReloadListener(resourceLoader, LayeredHairList.ID, new LayeredHairList());
        registerReloadListener(resourceLoader, GiftLoader.ID, new GiftLoader());
        registerReloadListener(resourceLoader, Dialogues.ID, new Dialogues());
        registerReloadListener(resourceLoader, Tasks.ID, new Tasks());
        registerReloadListener(resourceLoader, Names.ID, new Names());
        registerReloadListener(resourceLoader, BuildingTypes.ID, new BuildingTypes());

        // Create the creative mode tab
        ResourceKey<CreativeModeTab> mcaTab = ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), MCA.locate("mca_tab"));
        CreativeModeTab build = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.mca.mca_tab"))
                .icon(() -> new ItemStack(ItemsMCA.ENGAGEMENT_RING))
                .displayItems((params, output) -> {
                    for (Item item : ItemsMCA.ITEMS.values()) {
                        output.accept(new ItemStack(item));
                    }
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, mcaTab, build);

        // Register events
        ServerTickEvents.END_LEVEL_TICK.register(w -> VillageManager.get(w).tick());
        ServerTickEvents.END_SERVER_TICK.register(s -> ServerInteractionManager.getInstance().tick());
        ServerTickEvents.END_SERVER_TICK.register(MCA::setServer);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                ServerInteractionManager.getInstance().onPlayerJoin(handler.player)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AdminCommand.register(dispatcher);
            Command.register(dispatcher);
        });
    }

    private static final class ClientProxy {
        public static <T extends HandleablePayload> void register(HandleablePayload.Type<T> type) {
            ClientPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> ctx.client().execute(() -> payload.handle(ctx.player())));
        }
    }
}

