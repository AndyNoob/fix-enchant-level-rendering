package comfortable_andy.roman_numeral;

import com.comphenix.protocol.events.AbstractStructure;
import com.comphenix.protocol.utility.MinecraftRegistryAccess;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.logging.log4j.core.appender.rolling.action.IfAll;
import org.bukkit.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("JavaReflectionMemberAccess")
public class ComponentEditing {

    private static final Method CRAFT_TO_NMS_COPY;
    private static final Field NMS_TAG_DATA;
    private static final Method NMS_COMPONENT_PARSE_ITEM;
    private static final Method NMS_WORLD_HANDLE;
    private static final Method NMS_ITEM_SAVE;
    private static final Method NMS_REGISTRY_ACCESS;
    private static final Method NMS_TO_CRAFT_MIRROR;

    static {
        String craftBukkit = Bukkit.getServer().getClass().getPackageName();
        try {
            Class<?> craftItemStack = Class.forName(
                    craftBukkit + ".inventory.CraftItemStack"
            );
            CRAFT_TO_NMS_COPY = craftItemStack.getDeclaredMethod(
                    "asNMSCopy",
                    ItemStack.class
            );
            Class<?> nmsItemStack = net.minecraft.world.item.ItemStack.class;
            NMS_TO_CRAFT_MIRROR = craftItemStack.getDeclaredMethod(
                    "asCraftMirror",
                    nmsItemStack
            );
            NMS_TAG_DATA = or(
                    () -> nmsItemStack.getDeclaredField("components"), // post 1.20.6
                    () -> nmsItemStack.getDeclaredField("tag"), // pre 1.20.6
                    () -> MinecraftVersion.FEATURE_PREVIEW_2.atOrAbove() ? nmsItemStack.getDeclaredField("v") : nmsItemStack.getDeclaredField("u"), // pre 1.20.6
                    () -> nmsItemStack.getDeclaredField("r") // post 1.20.6
            );
            NMS_TAG_DATA.trySetAccessible();
            ThrowingSupplier<Method> fallback = () -> {
                if (MinecraftVersion.v1_20_5.atOrAbove())
                    throw new IllegalStateException("Could not locate nms method.");
                return null;
            };
            NMS_COMPONENT_PARSE_ITEM = or(
                    () -> findFirst(nmsItemStack, "parse", 2),
                    fallback
            );
            NMS_WORLD_HANDLE = or(
                    () -> findFirst(Class.forName(
                            craftBukkit + ".CraftWorld"
                    ), "getHandle", 0),
                    fallback
            );
            NMS_ITEM_SAVE = or(
                    () -> findFirst(nmsItemStack, "save", 1),
                    () -> null
            );
            System.out.println(NMS_WORLD_HANDLE.getReturnType());
            NMS_REGISTRY_ACCESS = or(
                    () -> Level.class.getDeclaredMethod("registryAccess"),
                    fallback
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not initialize component editing", e);
        }
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T supply() throws Exception;
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    @SafeVarargs
    public static <V> V or(ThrowingSupplier<V>... as) {
        for (int i = 0; i < as.length; i++) {
            ThrowingSupplier<V> a = as[i];
            try {
                return a.supply();
            } catch (Exception e) {
                if (i + 1 >= as.length) throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException();
    }

    public static void run(ThrowingRunnable... as) {
        for (ThrowingRunnable a : as) {
            try {
                a.run();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Method findFirst(Class<?> clazz, String name, int numParameter) {
        return Arrays.stream(clazz.getDeclaredMethods()).filter(m -> m.getName().equals(name) && m.getParameterCount() == numParameter).findFirst().orElse(null);
    }

    private static final Pattern HOVER_TAG_CAPTURE = Pattern.compile("\\{\\s*\"action\":\\s*\"show_item\",\\s*\"contents\":\\s*\\{\\s*\"id\":\\s*\".+\",(?>\\s*\"count\":\\s*\\d+,)?\"tag\":\\s*\".+(?>[]}])\"\\s*}(?>,\\s*\"count\":\\s*\\d+)?}");

    public static void editFancyComponents(Gson gson, AbstractStructure structure, World world) {
        if (structure == null) return;
        WrappedChatComponent component = structure.getChatComponents().readSafely(0);
        if (component == null) return;
        String raw = component.getJson();
        JsonObject json = gson.fromJson(raw, JsonObject.class);
        if (!json.has("with")) return;
        List<JsonElement> objects = new ArrayList<>(json.getAsJsonArray("with").asList());
        List<JsonObject> contents = new ArrayList<>();
        pullItemHoverEvent(json, contents);
        while (!objects.isEmpty()) {
            JsonElement element = objects.remove(0);
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            pullItemHoverEvent(object, contents);
            if (object.has("with"))
                objects.addAll(object.getAsJsonArray("with").asList());
        }
        for (JsonObject object : contents) {
            Dynamic<JsonElement> dynamic = new Dynamic<>(JsonOps.INSTANCE, object);
            Tag tag = dynamic.convert(NbtOps.INSTANCE).getValue();
            Object provider = or(() -> NMS_REGISTRY_ACCESS.invoke(NMS_WORLD_HANDLE.invoke(world)));
            Optional<?> nmsItem = (Optional<?>) or(() -> NMS_COMPONENT_PARSE_ITEM.invoke(
                    null,
                    provider,
                    tag
            ));
            @SuppressWarnings("JavaReflectionInvocation")
            ItemStack stack = ItemEditing.editItem(
                    (ItemStack) or(
                            () -> NMS_TO_CRAFT_MIRROR.invoke(null, nmsItem.get())
                    )
            );
            Object nmsStack = or(() -> CRAFT_TO_NMS_COPY.invoke(null, stack));
            Dynamic<Tag> itemTagDynamic = new Dynamic<>(
                    NbtOps.INSTANCE,
                    (Tag) or(() -> NMS_ITEM_SAVE.invoke(nmsStack, provider))
            );
            JsonElement serialized = itemTagDynamic.convert(JsonOps.INSTANCE).getValue();
            serialized.getAsJsonObject().asMap().forEach(object::add);
        }
        try {
            String finalJson = gson.toJson(json);

            System.out.println("raw " + raw);
            JsonArray array = new JsonArray();
            array.add(json);
            System.out.println(ComponentSerialization.CODEC.parse(
                    JsonOps.INSTANCE,
                    json
            ).getOrThrow());
            component.setJson(raw);
        } catch (Exception e) {
            System.out.println("we done messed up: " + e.getMessage());
            e.printStackTrace();
        }
        structure.getChatComponents().writeSafely(0, component);
    }

    public static void editTagComponents(Gson gson, AbstractStructure structure) {
        if (structure == null) return;
        WrappedChatComponent component = structure.getChatComponents().readSafely(0);
        if (component == null) return;
        String original = component.getJson();
        String edited = editJsonTags(gson, original);
        if (edited == null) return;
        System.out.println(edited);
        try {
            component.setJson(edited);
        } catch (Exception e) {
            System.out.println("we done messed up");
        }
        structure.getChatComponents().writeSafely(0, component);
    }

    private static void pullItemHoverEvent(JsonObject object, List<JsonObject> addTo) {
        if (object.has("hoverEvent")
                && object.getAsJsonObject("hoverEvent").has("contents")
                && object.getAsJsonObject("hoverEvent")
                .getAsJsonObject("contents")
                .has("components")) {
            addTo.add(object.getAsJsonObject("hoverEvent").getAsJsonObject("contents"));
        }
    }

    @SuppressWarnings("deprecation")
    public static @Nullable String editJsonTags(Gson gson, String original) {
        if (original == null) return null;
        System.out.println("original " + original);
        Matcher matcher = HOVER_TAG_CAPTURE.matcher(original);
        String edited = matcher.replaceAll(result -> {
            String match = result.group();
            System.out.println();
            System.out.println(match);
            System.out.println();
            ShowItemSection section = gson.fromJson(match, ShowItemSection.class);
            if (section == null) return match;
            NamespacedKey key = NamespacedKey.fromString(section.contents.id);
            if (key == null) return match;
            Material material = Registry.MATERIAL.get(key);
            if (material == null) return match;
            ItemStack stack = new ItemStack(material);
            stack = Bukkit.getUnsafe().modifyItemStack(stack, section.contents.tag);
            if (stack.getItemMeta().hasItemFlag(ItemFlag.HIDE_ENCHANTS)) return StringEscapeUtils.escapeJson(match);
            stack = ItemEditing.editItem(stack);
            try {
                Object nmsStack = CRAFT_TO_NMS_COPY.invoke(null, stack);
                section.contents.tag = StringEscapeUtils.escapeJson(NMS_TAG_DATA.get(nmsStack).toString());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
            String json = gson.toJson(section);
            System.out.println("/give @s diamond_axe" + StringEscapeUtils.unescapeJson(section.contents.tag));
            return json;
        }).replace("u0027", "'");
        if (edited.equals(original)) {
            System.out.println("same as before");
            return null;
        }
        return edited;
    }

    @SuppressWarnings("unused")
    public static class ShowItemSection {
        public final String action = "show_item";
        public Contents contents = null;

        public static class Contents {
            public String id;
            @SerializedName(value = "tag", alternate = {"components"})
            public String tag;
        }
    }
}
