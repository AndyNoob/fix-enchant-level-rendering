package comfortable_andy.roman_numeral;

import com.comphenix.protocol.events.AbstractStructure;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("JavaReflectionMemberAccess")
public class ComponentEditing {

    private static final Method CRAFT_TO_NMS_COPY;
    private static final Field NMS_TAG_DATA;

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
            NMS_TAG_DATA = or(
                    () -> nmsItemStack.getDeclaredField("components"), // post 1.20.6
                    () -> nmsItemStack.getDeclaredField("tag"), // pre 1.20.6
                    () -> MinecraftVersion.FEATURE_PREVIEW_2.atOrAbove() ? nmsItemStack.getDeclaredField("v") : nmsItemStack.getDeclaredField("u"), // pre 1.20.6
                    () -> nmsItemStack.getDeclaredField("r") // post 1.20.6
            );
            NMS_TAG_DATA.trySetAccessible();
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
    @NotNull
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

    private static final Pattern HOVER_CAPTURE = Pattern.compile("\\{\\s*\"action\":\\s*\"show_item\",\\s*\"contents\":\\s*\\{\\s*\"id\":\\s*\".+\",\\s*\"tag\":\\s*\".+(?>[]}])\"\\s*}\\s*}");

    public static void editComponents(Gson gson, AbstractStructure structure) {
        if (structure == null) return;
        WrappedChatComponent component = structure.getChatComponents().readSafely(0);
        if (component == null) return;
        String original = component.getJson();
        String edited = editJson(gson, original);
        if (edited == null) return;
        System.out.println(edited);
        try {
            component.setJson(edited);
        } catch (Exception e) {
            System.out.println("we done messed up");
        }
        structure.getChatComponents().writeSafely(0, component);
    }

    @SuppressWarnings("deprecation")
    public static @Nullable String editJson(Gson gson, String original) {
        if (original == null) return null;
        System.out.println("original " + original);
        Matcher matcher = HOVER_CAPTURE.matcher(original);
        String edited = matcher.replaceAll(result -> {
            String match = result.group();
            ShowItemSection section = gson.fromJson(match, ShowItemSection.class);
            if (section == null) return match;
            NamespacedKey key = NamespacedKey.fromString(section.contents.id);
            if (key == null) return match;
            Material material = Registry.MATERIAL.get(key);
            if (material == null) return match;
            ItemStack stack = new ItemStack(material);
            stack = Bukkit.getUnsafe().modifyItemStack(stack, section.contents.tag);
            System.out.println();
            System.out.println(match);
            System.out.println();
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
            public String tag;
        }
    }
}
