package org.chatterjay.crafting_tracker.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

public final class NetworkLocatorTool extends Item {
    private static final String TAG_BOUND = "Bound";
    private static final String TAG_DIMENSION = "Dimension";
    private static final String TAG_POS = "Pos";

    public NetworkLocatorTool(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getPlayer() == null) {
            return InteractionResult.PASS;
        }

        ItemStack stack = context.getItemInHand();
        if (context.getPlayer().isShiftKeyDown()) {
            if (!context.getLevel().isClientSide) {
                CompoundTag tag = stack.getOrCreateTag();
                tag.putBoolean(TAG_BOUND, true);
                tag.putString(TAG_DIMENSION, context.getLevel().dimension().location().toString());
                tag.putLong(TAG_POS, context.getClickedPos().asLong());
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.crafting_tracker.locator_bound", context.getClickedPos().toShortString()),
                        true
                );
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }

        if (!context.getLevel().isClientSide) {
            CompoundTag tag = stack.getTag();
            if (tag != null && tag.getBoolean(TAG_BOUND)) {
                BlockPos pos = BlockPos.of(tag.getLong(TAG_POS));
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.crafting_tracker.locator_status", tag.getString(TAG_DIMENSION), pos.toShortString()),
                        true
                );
            } else {
                context.getPlayer().displayClientMessage(
                        Component.translatable("message.crafting_tracker.locator_unbound"),
                        true
                );
            }
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_BOUND);
    }
}
