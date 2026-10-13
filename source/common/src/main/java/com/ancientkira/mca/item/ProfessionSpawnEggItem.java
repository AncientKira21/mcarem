package com.ancientkira.mca.item;

import com.ancientkira.mca.registry.EntitiesMCA;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;

/** A person egg with saved profession data, also honored by dispensers and spawners. */
public class ProfessionSpawnEggItem extends SpawnEggItem {
    private final String professionId;

    public ProfessionSpawnEggItem(Properties properties, String professionId) {
        super(properties.spawnEgg(EntitiesMCA.PERSON).component(DataComponents.ENTITY_DATA,
                TypedEntityData.of(EntitiesMCA.PERSON, data(professionId))));
        this.professionId = professionId;
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        net.minecraft.world.InteractionResult result = super.useOn(context);
        if (result.consumesAction() && context.getLevel() instanceof net.minecraft.server.level.ServerLevel world
                && world.getBlockEntity(context.getClickedPos()) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
            // Vanilla copies only the entity type into a spawner. Preserve the profession as well.
            var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
                    net.minecraft.util.ProblemReporter.DISCARDING, world.registryAccess());
            spawner.getSpawner().save(output);
            CompoundTag entity = data(professionId);
            entity.putString("id", "mca:person");
            var spawnData = new net.minecraft.world.level.SpawnData(entity, java.util.Optional.empty(), java.util.Optional.empty());
            output.store("SpawnData", net.minecraft.world.level.SpawnData.CODEC, spawnData);
            output.store("SpawnPotentials", net.minecraft.world.level.SpawnData.LIST_CODEC,
                    net.minecraft.util.random.WeightedList.of(spawnData));
            spawner.getSpawner().load(world, context.getClickedPos(),
                    net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,
                            world.registryAccess(), output.buildResult()));
            spawner.setChanged();
            var state = world.getBlockState(context.getClickedPos());
            world.sendBlockUpdated(context.getClickedPos(), state, state, 3);
        }
        return result;
    }

    private static CompoundTag data(String professionId) {
        CompoundTag profession = new CompoundTag();
        profession.putString("profession", professionId);
        profession.putString("type", "minecraft:plains");
        profession.putInt("level", 1);
        CompoundTag data = new CompoundTag();
        data.put("VillagerData", profession);
        data.putInt("Age", 0);
        return data;
    }
}
