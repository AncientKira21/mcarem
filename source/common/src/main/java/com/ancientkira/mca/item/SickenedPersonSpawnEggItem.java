package com.ancientkira.mca.item;
import com.ancientkira.mca.registry.EntitiesMCA;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.TypedEntityData;
/** MCA person variant; distinct from Sickened Mobs' vanilla-style villager egg. */
public final class SickenedPersonSpawnEggItem extends SpawnEggItem {
    public SickenedPersonSpawnEggItem(Properties properties) {
        super(properties.spawnEgg(EntitiesMCA.SICKENED_PERSON).component(DataComponents.ENTITY_DATA,
                TypedEntityData.of(EntitiesMCA.SICKENED_PERSON,data())));
    }
    private static CompoundTag data() {
        var data=new CompoundTag();var tags=new net.minecraft.nbt.ListTag();tags.add(net.minecraft.nbt.StringTag.valueOf("sickened_mob"));
        data.put("Tags",tags);data.putBoolean("PersistenceRequired",true);
        var mca = new CompoundTag();
        mca.putBoolean("Sickened", true);
        mca.putFloat("SicknessProgress", 1.0F);
        mca.putString("SickenedId", java.util.UUID.randomUUID().toString());
        data.put("MCAData", mca);
        return data;
    }
    @Override public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if(context.getLevel().getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)return net.minecraft.world.InteractionResult.FAIL;
        var result=super.useOn(context);
        if(result.consumesAction() && context.getLevel() instanceof net.minecraft.server.level.ServerLevel world
                && world.getBlockEntity(context.getClickedPos()) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity spawner) {
            var output=net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,world.registryAccess());
            spawner.getSpawner().save(output);var entity=data();entity.putString("id","mca:sickened_person");
            var spawnData=new net.minecraft.world.level.SpawnData(entity,java.util.Optional.empty(),java.util.Optional.empty());
            output.store("SpawnData",net.minecraft.world.level.SpawnData.CODEC,spawnData);
            output.store("SpawnPotentials",net.minecraft.world.level.SpawnData.LIST_CODEC,net.minecraft.util.random.WeightedList.of(spawnData));
            spawner.getSpawner().load(world,context.getClickedPos(),net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING,world.registryAccess(),output.buildResult()));
            spawner.setChanged();var state=world.getBlockState(context.getClickedPos());world.sendBlockUpdated(context.getClickedPos(),state,state,3);
        }return result;
    }
    @Override public net.minecraft.world.InteractionResult use(net.minecraft.world.level.Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        return level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL?net.minecraft.world.InteractionResult.FAIL:super.use(level,player,hand);
    }
}
