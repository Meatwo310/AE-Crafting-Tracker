package org.chatterjay.crafting_tracker.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class NetworkLocatorTool extends Item {

    private static final String TAG_BOUND_X = "bound_x";
    private static final String TAG_BOUND_Y = "bound_y";
    private static final String TAG_BOUND_Z = "bound_z";
    private static final String TAG_BOUND_DIM = "bound_dim";
    private static final String TAG_FILTER_PREFIX = "FilterItem_";

    public NetworkLocatorTool(Properties properties) {
        super(properties.stacksTo(1));
    }

    // --- Interaction ---

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof IInWorldGridNodeHost host)) {
            player.sendSystemMessage(Component.translatable("msg.crafting_tracker.locator.not_ae"));
            return InteractionResult.FAIL;
        }

        IGridNode node = findNode(host);
        if (node == null || node.getGrid() == null) {
            player.sendSystemMessage(Component.translatable("msg.crafting_tracker.locator.not_ae"));
            return InteractionResult.FAIL;
        }

        bind(stack, pos, level.dimension().location());
        player.sendSystemMessage(Component.translatable("msg.crafting_tracker.locator.bound", pos.toShortString()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, new MenuProvider() {
                @Override
                public Component getDisplayName() {
                    return Component.translatable("item.crafting_tracker.network_locator");
                }

                @Override
                public AbstractContainerMenu createMenu(int id, Inventory inv, Player p) {
                    return new NetworkLocatorMenu(id, inv, stack);
                }
            });
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Nullable
    private static IGridNode findNode(IInWorldGridNodeHost host) {
        IGridNode node = host.getGridNode(null);
        if (node != null) return node;
        for (Direction dir : Direction.values()) {
            node = host.getGridNode(dir);
            if (node != null) return node;
        }
        return null;
    }

    // --- NBT: Binding ---

    public static void bind(ItemStack stack, BlockPos pos, ResourceLocation dim) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(TAG_BOUND_X, pos.getX());
        tag.putInt(TAG_BOUND_Y, pos.getY());
        tag.putInt(TAG_BOUND_Z, pos.getZ());
        tag.putString(TAG_BOUND_DIM, dim.toString());
    }

    public static void unbind(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(TAG_BOUND_X);
        tag.remove(TAG_BOUND_Y);
        tag.remove(TAG_BOUND_Z);
        tag.remove(TAG_BOUND_DIM);
    }

    @Nullable
    public static BlockPos getBoundPos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        if (!tag.contains(TAG_BOUND_X) || !tag.contains(TAG_BOUND_Y) || !tag.contains(TAG_BOUND_Z))
            return null;
        return new BlockPos(tag.getInt(TAG_BOUND_X), tag.getInt(TAG_BOUND_Y), tag.getInt(TAG_BOUND_Z));
    }

    @Nullable
    public static ResourceLocation getBoundDimension(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        if (!tag.contains(TAG_BOUND_DIM)) return null;
        return ResourceLocation.tryParse(tag.getString(TAG_BOUND_DIM));
    }

    public static boolean isBound(ItemStack stack) {
        return getBoundPos(stack) != null;
    }

    // --- NBT: Filters ---

    public static void setFilter(ItemStack stack, int slot, ItemStack filter) {
        if (slot < 0 || slot >= 9) return;
        CompoundTag tag = stack.getOrCreateTag();
        String key = TAG_FILTER_PREFIX + slot;
        if (filter.isEmpty()) {
            tag.remove(key);
        } else {
            ItemStack copy = filter.copy();
            copy.setCount(1);
            tag.put(key, copy.save(new CompoundTag()));
        }
    }

    public static ItemStack getFilter(ItemStack stack, int slot) {
        if (slot < 0 || slot >= 9) return ItemStack.EMPTY;
        CompoundTag tag = stack.getTag();
        if (tag == null) return ItemStack.EMPTY;
        String key = TAG_FILTER_PREFIX + slot;
        if (!tag.contains(key)) return ItemStack.EMPTY;
        return ItemStack.of(tag.getCompound(key));
    }

    public static List<ItemStack> getFilters(ItemStack stack) {
        List<ItemStack> filters = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            filters.add(getFilter(stack, i));
        }
        return filters;
    }

    public static void setAllFilters(ItemStack stack, List<ItemStack> filters) {
        for (int i = 0; i < 9; i++) {
            if (i < filters.size()) {
                setFilter(stack, i, filters.get(i));
            } else {
                setFilter(stack, i, ItemStack.EMPTY);
            }
        }
    }
}
