package comfortable_andy.roman_numeral;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ItemEditing {

    public static ItemStack editItem(ItemStack item) {
        if (item == null) return null;
        if (!item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) return item;
        Map<Enchantment, Integer> enchantments = item.getEnchantments();
        if (enchantments.isEmpty()
                || enchantments.values().stream().noneMatch(v -> v > 10)) return item;
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        List<Component> prepending = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
            Integer value = entry.getValue();
            Enchantment key = entry.getKey();
            Component component = Component.translatable(key)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false);
            if (key.getMaxLevel() > 1 || value > 1)
                component = component.append(Component.space()).append(Component.text(asRomanNumeral(value)));
            prepending.add(component);
        }
        List<Component> newLore = Optional.ofNullable(meta.lore()).orElse(new ArrayList<>());
        prepending.addAll(newLore);
        meta.lore(prepending);
        item.setItemMeta(meta);
        return item;
    }

    public static String asRomanNumeral(int val) {
        StringBuilder builder = new StringBuilder();
        while (val > 0) {
            if (val >= 1000) {
                builder.append("M");
                val -= 1000;
            } else if (val >= 500) {
                // 999
                // 500 D
                // 100 * 4 CCCC
                // 50 L
                // 40 XXXX
                //
                builder.append("D");
                val -= 500;
            } else if (val >= 100) {
                builder.append("C");
                val -= 100;
            } else if (val >= 50) {
                builder.append("L");
                val -= 50;
            } else if (val >= 10) {
                builder.append("X");
                val -= 10;
            } else {
                if (val == 9) builder.append("IX");
                else if (val >= 5) builder.append("V").append("I".repeat(val - 5));
                else if (val == 4) builder.append("IV");
                else builder.append("I".repeat(val));
                val = 0;
            }
        }
        return builder.toString();
    }
}
