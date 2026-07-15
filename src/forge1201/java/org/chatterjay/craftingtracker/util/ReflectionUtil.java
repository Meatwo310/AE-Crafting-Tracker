package org.chatterjay.craftingtracker.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import javax.annotation.Nullable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ReflectionUtil {
    private ReflectionUtil() {}

    public static boolean looksLikeAeObject(Object value) {
        if (value == null) return false;
        String name = value.getClass().getName().toLowerCase(Locale.ROOT);
        return name.startsWith("appeng.") || name.contains("extendedae") || name.contains("advanced_ae")
                || name.contains("appmek") || name.contains("appliedmekanistics");
    }

    public static boolean looksLikePatternProvider(Object value) {
        if (value == null) return false;
        String name = value.getClass().getName().toLowerCase(Locale.ROOT);
        if (name.contains("patternprovider") || name.contains("pattern_provider")
                || name.contains("assemblermatrixpattern") || name.contains("pattern_provider_part")) {
            return true;
        }
        Object logic = invoke(value, "getLogic", "getPatternProviderLogic");
        if (logic == null) return false;
        return hasMethod(logic, "getAvailablePatterns") && hasMethod(logic, "isBusy");
    }

    public static boolean isBusy(Object provider) {
        Boolean value = invokeBoolean(provider, "isBusy");
        if (value != null) return value;
        Object logic = invoke(provider, "getLogic", "getPatternProviderLogic");
        value = invokeBoolean(logic, "isBusy");
        return value != null && value;
    }

    public static boolean isLocked(Object provider) {
        Object reason = invoke(provider, "getCraftingLockedReason", "getLockReason");
        if (reason == null) {
            Object logic = invoke(provider, "getLogic", "getPatternProviderLogic");
            reason = invoke(logic, "getCraftingLockedReason", "getLockReason");
        }
        if (reason == null) return false;
        String text = String.valueOf(reason);
        return !text.equalsIgnoreCase("none") && !text.equalsIgnoreCase("false") && !text.equals("0");
    }

    @Nullable
    public static Object findGrid(Object source) {
        if (source == null) return null;
        Object grid = invoke(source, "getGrid");
        if (grid != null && grid != source) return grid;

        for (String accessor : new String[]{"getMainNode", "getGridNode", "getNode"}) {
            Object node = invoke(source, accessor);
            if (node == null) node = invokeOneArg(source, accessor, null);
            if (node != null) {
                grid = invoke(node, "getGrid");
                if (grid != null) return grid;
            }
        }
        Object logic = invoke(source, "getLogic", "getPatternProviderLogic");
        if (logic != null && logic != source) {
            grid = findGrid(logic);
            if (grid != null) return grid;
        }
        return null;
    }

    public static List<Object> gridOwners(Object grid) {
        Object nodes = invoke(grid, "getNodes", "getMachines");
        List<Object> result = new ArrayList<>();
        for (Object node : iterable(nodes)) {
            Object owner = invoke(node, "getOwner", "getMachine");
            if (owner != null) result.add(owner);
        }
        return result;
    }

    @Nullable
    public static BlockPos getPosition(Object owner) {
        if (owner instanceof BlockEntity be) return be.getBlockPos().immutable();
        Object pos = invoke(owner, "getBlockPos", "getPos");
        if (pos instanceof BlockPos bp) return bp.immutable();
        Object host = invoke(owner, "getHost");
        Object be = invoke(host, "getBlockEntity");
        if (be instanceof BlockEntity blockEntity) return blockEntity.getBlockPos().immutable();
        be = invoke(owner, "getBlockEntity");
        if (be instanceof BlockEntity blockEntity) return blockEntity.getBlockPos().immutable();
        return null;
    }

    public record PatternInfo(List<ResourceLocation> itemIds, List<Object> keys) {}

    public static PatternInfo getPatternInfo(Object owner) {
        Object patterns = invoke(owner, "getAvailablePatterns", "getPatterns");
        if (patterns == null) {
            Object logic = invoke(owner, "getLogic", "getPatternProviderLogic");
            patterns = invoke(logic, "getAvailablePatterns", "getPatterns");
        }
        Map<ResourceLocation, Object> found = new LinkedHashMap<>();
        for (Object pattern : iterable(patterns)) {
            collectOutput(invoke(pattern, "getPrimaryOutput", "getOutput"), found);
            Object outputs = invoke(pattern, "getOutputs");
            for (Object output : iterable(outputs)) collectOutput(output, found);
            if (found.size() >= 3) break;
        }
        List<ResourceLocation> ids = new ArrayList<>(found.keySet());
        List<Object> keys = new ArrayList<>(found.values());
        return new PatternInfo(ids, keys);
    }

    private static void collectOutput(Object output, Map<ResourceLocation, Object> found) {
        if (output == null || found.size() >= 3) return;
        Object key = invoke(output, "what", "getKey", "key");
        if (key == null) key = output;
        ResourceLocation id = resourceIdForKey(key);
        if (id != null) found.putIfAbsent(id, key);
    }

    @Nullable
    public static ResourceLocation resourceIdForKey(Object key) {
        if (key == null) return null;
        if (key instanceof ItemStack stack) return BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (key instanceof Item item) return BuiltInRegistries.ITEM.getKey(item);
        Object item = invoke(key, "getItem", "item");
        if (item instanceof Item i) return BuiltInRegistries.ITEM.getKey(i);
        Object id = invoke(key, "getId", "id");
        if (id instanceof ResourceLocation rl) return rl;
        if (id instanceof String s) return ResourceLocation.tryParse(s);
        String text = String.valueOf(key);
        if (text.contains(":")) {
            int start = Math.max(0, text.lastIndexOf(' ')+1);
            ResourceLocation parsed = ResourceLocation.tryParse(text.substring(start).replace("]", "").replace("}", ""));
            if (parsed != null) return parsed;
        }
        return null;
    }

    public static boolean isAnyRequested(Object provider, List<Object> keys) {
        if (keys.isEmpty()) return false;
        Object grid = findGrid(provider);
        if (grid == null) return false;
        try {
            Class<?> serviceClass = Class.forName("appeng.api.networking.crafting.ICraftingService");
            Object service = invokeOneArg(grid, "getService", serviceClass);
            if (service == null) return false;
            for (Object key : keys) {
                Boolean requested = invokeCompatibleBoolean(service, "isRequesting", key);
                if (Boolean.TRUE.equals(requested)) return true;
            }
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    public static List<Object> iterable(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return new ArrayList<>(list);
        if (value instanceof Iterable<?> iterable) {
            List<Object> out = new ArrayList<>();
            iterable.forEach(out::add);
            return out;
        }
        if (value instanceof Iterator<?> iterator) {
            List<Object> out = new ArrayList<>();
            iterator.forEachRemaining(out::add);
            return out;
        }
        if (value instanceof Map<?, ?> map) return new ArrayList<>(map.values());
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> out = new ArrayList<>(length);
            for (int i = 0; i < length; i++) out.add(Array.get(value, i));
            return out;
        }
        return List.of(value);
    }

    @Nullable
    public static Object invoke(Object target, String... names) {
        if (target == null) return null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    @Nullable
    public static Object invokeOneArg(Object target, String name, Object arg) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            Class<?> type = method.getParameterTypes()[0];
            if (arg != null && !isCompatible(type, arg.getClass())) continue;
            try {
                method.setAccessible(true);
                return method.invoke(target, arg);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
        }
        return null;
    }

    private static boolean isCompatible(Class<?> parameter, Class<?> value) {
        if (parameter.isAssignableFrom(value)) return true;
        if (!parameter.isPrimitive()) return false;
        return (parameter == int.class && value == Integer.class)
                || (parameter == long.class && value == Long.class)
                || (parameter == boolean.class && value == Boolean.class)
                || (parameter == double.class && value == Double.class)
                || (parameter == float.class && value == Float.class)
                || (parameter == short.class && value == Short.class)
                || (parameter == byte.class && value == Byte.class)
                || (parameter == char.class && value == Character.class);
    }

    @Nullable
    public static Boolean invokeBoolean(Object target, String name) {
        Object value = invoke(target, name);
        return value instanceof Boolean b ? b : null;
    }

    @Nullable
    public static Boolean invokeCompatibleBoolean(Object target, String name, Object arg) {
        Object value = invokeOneArg(target, name, arg);
        return value instanceof Boolean b ? b : null;
    }

    public static boolean hasMethod(Object target, String name) {
        if (target == null) return false;
        for (Method method : target.getClass().getMethods()) if (method.getName().equals(name)) return true;
        return false;
    }
}
