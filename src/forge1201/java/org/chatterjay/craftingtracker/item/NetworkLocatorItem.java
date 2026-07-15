package org.chatterjay.craftingtracker.item;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkHooks;
import org.chatterjay.craftingtracker.util.ReflectionUtil;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class NetworkLocatorItem extends Item {
    private static final String BOUND = "Bound";
    private static final String DIMENSION = "Dimension";
    private static final String FILTERS = "Filters";

    public NetworkLocatorItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity blockEntity = level.getBlockEntity(context.getClickedPos());
        Object grid = ReflectionUtil.findGrid(blockEntity);
        if (grid == null) {
            player.sendSystemMessage(Component.translatable("msg.crafting_tracker.locator.not_ae"));
            return InteractionResult.FAIL;
        }

        bind(context.getItemInHand(), context.getClickedPos(), level.dimension().location());
        player.sendSystemMessage(Component.translatable("msg.crafting_tracker.locator.bound", context.getClickedPos().toShortString()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crafting_tracker.network_locator");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inventory, Player menuPlayer) {
                    return new NetworkLocatorMenu(id, inventory, hand);
                }
            };
            NetworkHooks.openScreen(serverPlayer, provider, buf -> buf.writeEnum(hand));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void bind(ItemStack stack, BlockPos pos, ResourceLocation dimension) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putLong(BOUND, pos.asLong());
        tag.putString(DIMENSION, dimension.toString());
    }

    @Nullable
    public static BlockPos getBoundPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(BOUND) ? BlockPos.of(tag.getLong(BOUND)) : null;
    }

    @Nullable
    public static ResourceLocation getBoundDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(DIMENSION) ? ResourceLocation.tryParse(tag.getString(DIMENSION)) : null;
    }

    public static void setFilter(ItemStack stack, int slot, ItemStack filter) {
        if (slot < 0 || slot >= 9) return;
        CompoundTag root = stack.getOrCreateTag();
        CompoundTag filters = root.getCompound(FILTERS);
        if (filter.isEmpty()) filters.remove(Integer.toString(slot));
        else {
            ItemStack copy = filter.copy();
            copy.setCount(1);
            filters.put(Integer.toString(slot), copy.save(new CompoundTag()));
        }
        root.put(FILTERS, filters);
    }

    public static ItemStack getFilter(ItemStack stack, int slot) {
        CompoundTag root = stack.getTag();
        if (root == null || !root.contains(FILTERS)) return ItemStack.EMPTY;
        CompoundTag filters = root.getCompound(FILTERS);
        return filters.contains(Integer.toString(slot)) ? ItemStack.of(filters.getCompound(Integer.toString(slot))) : ItemStack.EMPTY;
    }

    public static List<ItemStack> getFilters(ItemStack stack) {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            ItemStack filter = getFilter(stack, i);
            if (!filter.isEmpty()) result.add(filter);
        }
        return result;
    }
}
