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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.List;

import static comfortable_andy.roman_numeral.ComponentEditing.or;

public final class RomanNumeralMain extends JavaPlugin implements Listener {

    private static final Class<?> FRIENDLY_BYTE_BUF = or(
            () -> Class.forName("net.minecraft.network.FriendlyByteBuf"),
            () -> Class.forName("net.minecraft.network.PacketDataSerializer")
    );
    private static final Method BUF_READ_UTF;

    static {
        BUF_READ_UTF = or(
                () -> FRIENDLY_BYTE_BUF.getDeclaredMethod("readUtf"),
                () -> FRIENDLY_BYTE_BUF.getDeclaredMethod("s")
        );
    }

    private final Gson gson = new GsonBuilder().setLenient().disableHtmlEscaping().create();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode().isInvulnerable()) {
            Bukkit.getScheduler().runTaskLater(
                    this,
                    () -> event.getPlayer().updateInventory(),
                    1
            );
        }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
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
                    /*if (MinecraftVersion.TRAILS_AND_TAILS.atOrAbove()) {
                        editStruct = null;
                    } else */if (MinecraftVersion.FEATURE_PREVIEW_2.atOrAbove()) {
                        editStruct = null;
                    } else if (MinecraftVersion.WILD_UPDATE.atOrAbove()) {
                        editStruct = null;
                    } else if (MinecraftVersion.CAVES_CLIFFS_2.atOrAbove())
                        editStruct = packet;
                    else return;
                    ComponentEditing.editComponents(gson, editStruct);
                } else if (type == PacketType.Play.Server.SYSTEM_CHAT) {
                    Object buf = packet.serializeToBuffer();
                    String content = (String) or(() -> BUF_READ_UTF.invoke(buf), () -> "");
                    System.out.println("content " + content);
                    String edited = ComponentEditing.editJson(gson, content);
                    if (edited == null) return;
                    if (MinecraftVersion.FEATURE_PREVIEW_2.atOrAbove()) {
                        packet.getStrings().write(0, edited);
                        System.out.println(packet.getModifier().write(0, null));
                    }
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
