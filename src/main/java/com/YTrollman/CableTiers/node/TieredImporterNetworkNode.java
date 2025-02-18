package com.YTrollman.CableTiers.node;

import com.YTrollman.CableTiers.CableTier;
import com.YTrollman.CableTiers.ContentType;
import com.YTrollman.CableTiers.blockentity.TieredImporterBlockEntity;
import com.YTrollman.CableTiers.config.CableConfig;
import com.refinedmods.refinedstorage.RS;
import com.refinedmods.refinedstorage.api.network.node.ICoverable;
import com.refinedmods.refinedstorage.api.util.Action;
import com.refinedmods.refinedstorage.api.util.IComparer;
import com.refinedmods.refinedstorage.apiimpl.network.node.cover.CoverManager;
import com.refinedmods.refinedstorage.blockentity.DiskDriveBlockEntity;
import com.refinedmods.refinedstorage.blockentity.config.IComparable;
import com.refinedmods.refinedstorage.blockentity.config.IType;
import com.refinedmods.refinedstorage.blockentity.config.IWhitelistBlacklist;
import com.refinedmods.refinedstorage.inventory.fluid.FluidInventory;
import com.refinedmods.refinedstorage.inventory.item.BaseItemHandler;
import com.refinedmods.refinedstorage.inventory.item.UpgradeItemHandler;
import com.refinedmods.refinedstorage.inventory.listener.NetworkNodeFluidInventoryListener;
import com.refinedmods.refinedstorage.inventory.listener.NetworkNodeInventoryListener;
import com.refinedmods.refinedstorage.item.UpgradeItem;
import com.refinedmods.refinedstorage.util.StackUtils;
import com.refinedmods.refinedstorage.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

public class TieredImporterNetworkNode extends TieredNetworkNode<TieredImporterNetworkNode> implements IComparable, IWhitelistBlacklist, IType, ICoverable {

    private static final String NBT_COMPARE = "Compare";
    private static final String NBT_MODE = "Mode";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_FLUID_FILTERS = "FluidFilters";

    private static final int BASE_SPEED = 9;
    private static final int SPEED_INCREASE = 2;

    private final BaseItemHandler itemFilters;
    private final FluidInventory fluidFilters;

    private final UpgradeItemHandler upgrades;

    private int compare = IComparer.COMPARE_NBT;
    private int mode = IWhitelistBlacklist.BLACKLIST;
    private int type = IType.ITEMS;

    private int currentSlot;

    private final CoverManager coverManager;

    private final List<String> blacklist = (List<String>) CableConfig.CREATIVE_IMPORTER_FLUID_BLOCK_BLACKLIST.get();

    public TieredImporterNetworkNode(Level level, BlockPos pos, CableTier tier) {
        super(level, pos, ContentType.IMPORTER, tier);
        this.coverManager = new CoverManager(this);
        this.itemFilters = new BaseItemHandler(9 * tier.getSlotsMultiplier()).addListener(new NetworkNodeInventoryListener(this));
        this.fluidFilters = new FluidInventory(9 * tier.getSlotsMultiplier()).addListener(new NetworkNodeFluidInventoryListener(this));
        this.upgrades = (UpgradeItemHandler) new UpgradeItemHandler(
                tier == CableTier.CREATIVE ? 0 : 4,
                tier == CableTier.ELITE ? new UpgradeItem.Type[] { UpgradeItem.Type.SPEED, UpgradeItem.Type.STACK } : new UpgradeItem.Type[] { UpgradeItem.Type.SPEED }
        ).addListener(new NetworkNodeInventoryListener(this));
    }

    @Override
    public int getEnergyUsage() {
        if(getTier() == CableTier.ELITE)
        {
            return (4 * (RS.SERVER_CONFIG.getImporter().getUsage() + upgrades.getEnergyUsage())) * CableConfig.ELITE_ENERGY_COST.get();
        }
        else if(getTier() == CableTier.ULTRA)
        {
            return (4 * (RS.SERVER_CONFIG.getImporter().getUsage() + upgrades.getEnergyUsage())) * CableConfig.ULTRA_ENERGY_COST.get();
        }
        else if(getTier() == CableTier.CREATIVE)
        {
            return (4 * (RS.SERVER_CONFIG.getImporter().getUsage() + upgrades.getEnergyUsage())) * CableConfig.CREATIVE_ENERGY_COST.get();
        }
        return 0;
    }

    @Override
    public void update() {
        super.update();

        if (!canUpdate() || !level.isLoaded(pos) || !level.isLoaded(pos.relative(getDirection()))) {
            return;
        }

        if (getTier() != CableTier.CREATIVE) {
            int baseSpeed = BASE_SPEED / getSpeedMultiplier();
            int speed = Math.max(1, upgrades.getSpeed(baseSpeed, SPEED_INCREASE));
            if (speed > 1 && ticks % speed != 0) {
                return;
            }
        }

        if (type == IType.ITEMS) {
            itemUpdate();
        } else if (type == IType.FLUIDS) {
            fluidUpdate();
        }
    }

    private int getSpeedMultiplier() {
        switch (getTier()) {
            case ELITE:
                return CableConfig.ELITE_IMPORTER_SPEED.get();
            case ULTRA:
                return CableConfig.ULTRA_IMPORTER_SPEED.get();
            default:
                throw new RuntimeException("illegal tier " + getTier());
        }
    }

    private boolean interactWithStacks() {
        return getTier() != CableTier.ELITE || upgrades.hasUpgrade(UpgradeItem.Type.STACK);
    }

    private void itemUpdate() {
        BlockEntity facing = getFacingBlockEntity();
        if (facing == null || facing instanceof DiskDriveBlockEntity) {
            return;
        }

        IItemHandler handler = WorldUtils.getItemHandler(facing, getDirection().getOpposite());
        if (handler == null || handler.getSlots() <= 0) {
            return;
        }

        if (currentSlot >= handler.getSlots()) {
            currentSlot = 0;
        }

        if (getTier() == CableTier.CREATIVE) {
            while (doItemExtraction(handler)) {
            }
        } else {
            doItemExtraction(handler);
        }
    }

    private boolean doItemExtraction(IItemHandler handler) {
        int startSlot = currentSlot;

        while (true) {
            ItemStack stack = handler.getStackInSlot(currentSlot);
            if (!stack.isEmpty() && IWhitelistBlacklist.acceptsItem(itemFilters, mode, compare, stack)) {
                int interactionCount = interactWithStacks() ? stack.getCount() : 1;

                ItemStack result = handler.extractItem(currentSlot, interactionCount, true);
                if (!result.isEmpty()) {
                    int remaining = network.insertItem(result, result.getCount(), Action.SIMULATE).getCount();
                    int inserted = result.getCount() - remaining;
                    if (inserted > 0) {
                        result = handler.extractItem(currentSlot, inserted, false);

                        network.insertItemTracked(result, result.getCount());

                        return true;
                    }
                }
            }

            if (++currentSlot >= handler.getSlots()) {
                currentSlot = 0;
            }

            if (currentSlot == startSlot) {
                return false;
            }
        }
    }

    private void fluidUpdate() {
        IFluidHandler handler = WorldUtils.getFluidHandler(getFacingBlockEntity(), getDirection().getOpposite());
        BlockEntity facing = getFacingBlockEntity();
        for(int i = 0; i < blacklist.size(); i++) {
            if (facing == null || facing.getBlockState().is(ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blacklist.get(i))))) {
                return;
            }
            if (handler == null) {
                return;
            }

            if (currentSlot >= handler.getTanks()) {
                currentSlot = 0;
            }

            if (getTier() == CableTier.CREATIVE) {
                while (doFluidExtraction(handler)) {
                }
            } else {
                doFluidExtraction(handler);
            }
        }
    }

    private boolean doFluidExtraction(IFluidHandler handler) {
        int startSlot = currentSlot;

        if(handler.getTanks() != 0) {
            while (true) {
                FluidStack stack = handler.getFluidInTank(currentSlot);
                if (!stack.isEmpty() && IWhitelistBlacklist.acceptsFluid(fluidFilters, mode, compare, stack)) {
                    int interactionAmount = interactWithStacks() ? (getTier() == CableTier.CREATIVE ? stack.getAmount() : 64 * FluidAttributes.BUCKET_VOLUME) : FluidAttributes.BUCKET_VOLUME;
                    FluidStack toExtract = stack.copy();
                    toExtract.setAmount(interactionAmount);

                    FluidStack result = handler.drain(toExtract, IFluidHandler.FluidAction.SIMULATE);
                    if (!result.isEmpty()) {
                        int remaining = network.insertFluid(result, result.getAmount(), Action.SIMULATE).getAmount();
                        int inserted = result.getAmount() - remaining;
                        if (inserted > 0) {
                            toExtract = stack.copy();
                            toExtract.setAmount(inserted);

                            result = handler.drain(toExtract, IFluidHandler.FluidAction.EXECUTE);
                            FluidStack actualRemainder = network.insertFluidTracked(result, result.getAmount());

                            if (!actualRemainder.isEmpty()) {
                                result.shrink(actualRemainder.getAmount());
                            }

                            return true;
                        }
                    }
                }

                if (++currentSlot >= handler.getTanks()) {
                    currentSlot = 0;
                }

                if (currentSlot == startSlot) {
                    return false;
                }
            }
        }
        else {
            return false;
        }
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public void setCompare(int compare) {
        this.compare = compare;
        markDirty();
    }

    @Override
    public int getWhitelistBlacklistMode() {
        return mode;
    }

    @Override
    public void setWhitelistBlacklistMode(int mode) {
        this.mode = mode;
        markDirty();
    }

    @Override
    public CompoundTag write(CompoundTag tag) {
        super.write(tag);
        tag.put(CoverManager.NBT_COVER_MANAGER, this.coverManager.writeToNbt());
        StackUtils.writeItems(upgrades, 1, tag);
        return tag;
    }

    @Override
    public CompoundTag writeConfiguration(CompoundTag tag) {
        super.writeConfiguration(tag);
        tag.putInt(NBT_COMPARE, compare);
        tag.putInt(NBT_MODE, mode);
        tag.putInt(NBT_TYPE, type);
        StackUtils.writeItems(itemFilters, 0, tag);
        tag.put(NBT_FLUID_FILTERS, fluidFilters.writeToNbt());
        return tag;
    }

    @Override
    public void read(CompoundTag tag) {
        super.read(tag);
        if (tag.contains(CoverManager.NBT_COVER_MANAGER)){
            this.coverManager.readFromNbt(tag.getCompound(CoverManager.NBT_COVER_MANAGER));
        }
        StackUtils.readItems(upgrades, 1, tag);
    }

    @Override
    public void readConfiguration(CompoundTag tag) {
        super.readConfiguration(tag);
        if (tag.contains(NBT_COMPARE)) {
            compare = tag.getInt(NBT_COMPARE);
        }
        if (tag.contains(NBT_MODE)) {
            mode = tag.getInt(NBT_MODE);
        }
        if (tag.contains(NBT_TYPE)) {
            type = tag.getInt(NBT_TYPE);
        }
        StackUtils.readItems(itemFilters, 0, tag);
        if (tag.contains(NBT_FLUID_FILTERS)) {
            fluidFilters.readFromNbt(tag.getCompound(NBT_FLUID_FILTERS));
        }
    }

    public UpgradeItemHandler getUpgrades() {
        return upgrades;
    }

    @Override
    public IItemHandler getDrops() {
        return upgrades;
    }

    @Override
    public int getType() {
        return level.isClientSide ? TieredImporterBlockEntity.TYPE.getValue() : type;
    }

    @Override
    public void setType(int type) {
        this.type = type;
        markDirty();
    }

    @Override
    public IItemHandlerModifiable getItemFilters() {
        return itemFilters;
    }

    @Override
    public FluidInventory getFluidFilters() {
        return fluidFilters;
    }

    @Override
    public CoverManager getCoverManager() {
        return coverManager;
    }
}
