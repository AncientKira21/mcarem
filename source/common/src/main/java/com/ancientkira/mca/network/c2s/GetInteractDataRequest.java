package com.ancientkira.mca.network.c2s;

import com.ancientkira.mca.entity.VillagerLike;
import com.ancientkira.mca.entity.ai.relationship.CompassionateEntity;
import com.ancientkira.mca.entity.ai.relationship.EntityRelationship;
import com.ancientkira.mca.entity.ai.relationship.RelationshipState;
import com.ancientkira.mca.entity.interaction.Constraint;
import com.ancientkira.mca.network.HandleablePayload;
import com.ancientkira.mca.network.Network;
import com.ancientkira.mca.network.s2c.GetInteractDataResponse;
import com.ancientkira.mca.server.world.data.FamilyTreeNode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Optional;
import java.util.Set;

public record GetInteractDataRequest(int id) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetInteractDataRequest> TYPE = new CustomPacketPayload.Type<>(com.ancientkira.mca.MCA.locate("get_interact_data_request"));
    public static final StreamCodec<FriendlyByteBuf, GetInteractDataRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, GetInteractDataRequest::id,
            GetInteractDataRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity entity = player.level().getEntity(id);
        if (entity instanceof VillagerLike<?> villager) {
            Set<Constraint> constraints = Constraint.allMatching(villager, player);
            EntityRelationship relationship = ((CompassionateEntity<?>) villager).getRelationships();
            FamilyTreeNode family = relationship.getFamilyEntry();
            Optional<String> fatherName = relationship.getFamilyTree().getOrEmpty(family.father()).map(FamilyTreeNode::getName);
            Optional<String> motherName = relationship.getFamilyTree().getOrEmpty(family.mother()).map(FamilyTreeNode::getName);
            Optional<String> spouseName = relationship.getFamilyTree().getOrEmpty(family.partner()).map(FamilyTreeNode::getName);
            RelationshipState marriageState = relationship.getRelationshipState();
            Network.sendToPlayer(new GetInteractDataResponse(constraints, fatherName, motherName, spouseName, marriageState), player);
        }
    }

    @Override
    public Type<GetInteractDataRequest> type() {
        return TYPE;
    }
}
