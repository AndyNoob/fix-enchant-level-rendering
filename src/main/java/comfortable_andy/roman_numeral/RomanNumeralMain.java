package comfortable_andy.roman_numeral;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.utility.MinecraftVersion;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class RomanNumeralMain extends JavaPlugin {

    private final Gson gson = new GsonBuilder().setLenient().create();

    @Override
    public void onEnable() {
        // Plugin startup logic
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(
                this,
                PacketType.Play.Server.WINDOW_ITEMS,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.ENTITY_EQUIPMENT,
                PacketType.Play.Server.CHAT,
                PacketType.Play.Server.SYSTEM_CHAT
        ) {
            @Override
            public void onPacketSending(PacketEvent event) {
                PacketContainer packet = event.getPacket();
                PacketType type = packet.getType();
                if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;
                if (type == PacketType.Play.Server.WINDOW_ITEMS) {
                    List<ItemStack> stacks = packet.getItemListModifier().read(0);
                    ItemStack cursor = packet.getItemModifier().readSafely(0);
                    packet.getItemModifier().writeSafely(0, ItemEditing.editItem(cursor));
                    stacks.replaceAll(ItemEditing::editItem);
                    packet.getItemListModifier().write(0, stacks);
                } else if (type == PacketType.Play.Server.SET_SLOT) {
                    ItemStack item = packet.getItemModifier().read(0);
                    packet.getItemModifier().write(0, ItemEditing.editItem(item));
                } else if (type == PacketType.Play.Server.ENTITY_EQUIPMENT) {
                    if (packet.getIntegers().read(0) != event.getPlayer().getEntityId()) return;
                    List<Pair<EnumWrappers.ItemSlot, ItemStack>> list = packet.getSlotStackPairLists().read(0);
                    list.replaceAll(p -> {
                        p.setSecond(ItemEditing.editItem(p.getSecond()));
                        return p;
                    });
                    packet.getSlotStackPairLists().write(0, list);
                } else if (type == PacketType.Play.Server.CHAT) {
                    AbstractStructure editStruct;
                    if (MinecraftVersion.WILD_UPDATE.atOrAbove()) {
                        InternalStructure structure = packet.getStructures().read(0);
                        editStruct = structure.getStructures().readSafely(2).getStructures().readSafely(0);
                    } else if (MinecraftVersion.CAVES_CLIFFS_2.atOrAbove())
                        editStruct = packet;
                    else return;
                    ComponentEditing.editComponents(gson, editStruct);
//                    throw new RuntimeException();
                } else if (type == PacketType.Play.Server.SYSTEM_CHAT) {
                    String edited = ComponentEditing.editJson(gson, packet.getStrings().read(0));
                    if (edited == null) return;
                    packet.getStrings().write(0, edited);
                }
            }
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        ItemStack stack = new ItemStack(Material.ITEM_FRAME);
        stack.editMeta(s -> s.addEnchant(Enchantment.CHANNELING, 1, true));
        sender.sendMessage(Component
                .text("yooooo")
                .hoverEvent(stack.asHoverEvent())
        );
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
