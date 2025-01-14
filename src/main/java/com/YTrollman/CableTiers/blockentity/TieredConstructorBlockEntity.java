package com.YTrollman.CableTiers.blockentity;

import com.YTrollman.CableTiers.CableTier;
import com.YTrollman.CableTiers.ContentType;
import com.YTrollman.CableTiers.node.TieredConstructorNetworkNode;
import com.refinedmods.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.refinedmods.refinedstorage.blockentity.config.IComparable;
import com.refinedmods.refinedstorage.blockentity.config.IType;
import com.refinedmods.refinedstorage.blockentity.data.BlockEntitySynchronizationManager;
import com.refinedmods.refinedstorage.blockentity.data.BlockEntitySynchronizationParameter;
import com.refinedmods.refinedstorage.util.WorldUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraftforge.client.model.data.IModelData;
import net.minecraftforge.client.model.data.ModelDataMap;

import javax.annotation.Nonnull;

public class TieredConstructorBlockEntity extends TieredBlockEntity<TieredConstructorNetworkNode> {

    public static final BlockEntitySynchronizationParameter<CompoundTag, TieredConstructorBlockEntity> COVER_MANAGER = new BlockEntitySynchronizationParameter<>(EntityDataSerializers.COMPOUND_TAG, new CompoundTag(),
            t -> t.getNode().getCoverManager().writeToNbt(),
            (t, v) -> t.getNode().getCoverManager().readFromNbt(v),
            (initial, p) -> {});

    public static final BlockEntitySynchronizationParameter<Integer, TieredConstructorBlockEntity> COMPARE = IComparable.createParameter();
    public static final BlockEntitySynchronizationParameter<Integer, TieredConstructorBlockEntity> TYPE = IType.createParameter();
    public static final BlockEntitySynchronizationParameter<Boolean, TieredConstructorBlockEntity> DROP = new BlockEntitySynchronizationParameter<>(EntityDataSerializers.BOOLEAN, false, t -> t.getNode().isDrop(), (t, v) -> {
        t.getNode().setDrop(v);
        t.getNode().markDirty();
    });

    static {
        BlockEntitySynchronizationManager.registerParameter(COVER_MANAGER);
    }

    public TieredConstructorBlockEntity(CableTier tier) {
        super(ContentType.CONSTRUCTOR, tier);
        dataManager.addWatchedParameter(COMPARE);
        dataManager.addWatchedParameter(TYPE);
        dataManager.addWatchedParameter(DROP);
        dataManager.addWatchedParameter(COVER_MANAGER);
    }

    @Nonnull
    @Override
    public IModelData getModelData() {
        return new ModelDataMap.Builder().withInitial(CoverManager.PROPERTY, this.getNode().getCoverManager()).build();
    }

    @Override
    public CompoundTag writeUpdate(CompoundTag tag) {
        super.writeUpdate(tag);
        tag.put(CoverManager.NBT_COVER_MANAGER, this.getNode().getCoverManager().writeToNbt());

        return tag;
    }

    @Override
    public void readUpdate(CompoundTag tag) {
        super.readUpdate(tag);

        this.getNode().getCoverManager().readFromNbt(tag.getCompound(CoverManager.NBT_COVER_MANAGER));

        requestModelDataUpdate();

        WorldUtils.updateBlock(level, worldPosition);
    }
}
