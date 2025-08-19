package comfortable_andy.roman_numeral;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.utility.MinecraftRegistryAccess;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.event.HoverEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class RomanNumeralMain extends JavaPlugin implements Listener {

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

    private static net.kyori.adventure.text.Component modifyChildren(net.kyori.adventure.text.Component component) {
        var children = new ArrayList<>(component.children());
        children.replaceAll(RomanNumeralMain::modifyChildren);
        children.replaceAll(RomanNumeralMain::modifyComponentHoverEvent);
        return component.children(children);
    }

    private static net.kyori.adventure.text.Component modifyComponentHoverEvent(net.kyori.adventure.text.Component component) {
        HoverEvent<?> hover = component.hoverEvent();
        if (hover == null || hover.action() != HoverEvent.Action.SHOW_ITEM) return component;
        if (!(hover.value() instanceof HoverEvent.ShowItem show)) return component;
        Key key = show.item();
        var location = ResourceLocation.fromNamespaceAndPath(
                key.namespace(),
                key.value()
        );
        var ref = BuiltInRegistries.ITEM.get(location).orElse(null);
        if (ref == null) return component;
        var stack = new net.minecraft.world.item.ItemStack(
                ref,
                show.count(),
                PaperAdventure.asVanilla(show.dataComponents())
        );
        return component.hoverEvent(
                ItemEditing.editItem(
                        CraftItemStack.asCraftMirror(stack)
                ).asHoverEvent()
        );
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
                } else if (type == PacketType.Play.Server.CHAT || type == PacketType.Play.Server.SYSTEM_CHAT) {
                    WrappedChatComponent wrapped = packet.getChatComponents().readSafely(0);
                    if (wrapped == null) return;
                    var component = modifyComponentHoverEvent(
                            modifyChildren(
                                    PaperAdventure.asAdventure(
                                            (Component) wrapped.getHandle()
                                    )
                            )
                    );
                    packet.getChatComponents().write(
                            0,
                            WrappedChatComponent.fromHandle(
                                    PaperAdventure.asVanilla(component)
                            )
                    );
                }
                /* else if (type == PacketType.Play.Server.CHAT) {
                    if (true) return;
                    AbstractStructure editStruct;
                    /*if (MinecraftVersion.TRAILS_AND_TAILS.atOrAbove()) {
                        editStruct = null;
                    } else *//*
                    if (MinecraftVersion.v1_20_5.atOrAbove()) {
                        System.out.println("he-fucking-llo");
                        ComponentEditing.editFancyComponents(gson, packet, event.getPlayer().getWorld());
                        return;
                    } else if (MinecraftVersion.FEATURE_PREVIEW_2.atOrAbove()) {
                        editStruct = null;
                    } else if (MinecraftVersion.WILD_UPDATE.atOrAbove()) {
                        editStruct = null;
                    } else if (MinecraftVersion.CAVES_CLIFFS_2.atOrAbove())
                        editStruct = packet;
                    else return;
                    ComponentEditing.editTagComponents(gson, editStruct);
                } else if (type == PacketType.Play.Server.SYSTEM_CHAT) {
                    if (true) return;
                    if (MinecraftVersion.v1_20_5.atOrAbove()) {
                        System.out.println("what the fuck");
                        ComponentEditing.editFancyComponents(gson, packet, event.getPlayer().getWorld());
                    } else if (MinecraftVersion.FEATURE_PREVIEW_2.atOrAbove()) {
                        Object buf = packet.serializeToBuffer();
                        String content = (String) or(() -> BUF_READ_UTF.invoke(buf), () -> "");
                        String edited = ComponentEditing.editJsonTags(gson, content);
                        System.out.println("content '" + content + "'");
                        System.out.println("len " + content.length());
                        if (edited == null) return;
                        packet.getStrings().write(0, edited);
                        System.out.println(packet.getModifier().write(0, null));
                    }
                }*/
            }
        });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        System.out.println(Component.Serializer.fromJson(
                """
                                {
                                  "translate": "chat.type.admin",
                                  "with": [
                                    "@",
                                    {
                                      "translate": "commands.give.success.single",
                                      "with": [
                                        1,
                                        {
                                          "translate": "chat.square_brackets",
                                          "with": [
                                            {
                                              "text": "",
                                              "extra": [
                                                {
                                                  "translate": "item.minecraft.item_frame"
                                                }
                                              ]
                                            }
                                          ],
                                          "hoverEvent": {
                                            "contents": {
                                              "id": "minecraft:item_frame",
                                              "count": 1,
                                              "components": {
                                                "minecraft:enchantments": {
                                                  "levels": {
                                                    "minecraft:sharpness": 11
                                                  }
                                                }
                                              }
                                            },
                                            "action": "show_item"
                                          },
                                          "color": "aqua"
                                        },
                                        {
                                          "text": "Comfortable_Andy",
                                          "hoverEvent": {
                                            "contents": {
                                              "type": "minecraft:player",
                                              "id": [
                                                -1804111798,
                                                -2053096004,
                                                -1738699474,
                                                829284367
                                              ],
                                              "name": "Comfortable_Andy"
                                            },
                                            "action": "show_entity"
                                          },
                                          "insertion": "Comfortable_Andy",
                                          "clickEvent": {
                                            "action": "suggest_command",
                                            "value": "/tell Comfortable_Andy "
                                          }
                                        }
                                      ]
                                    }
                                  ],
                                  "italic": true,
                                  "color": "gray"
                                }
                        """,
                (HolderLookup.Provider) MinecraftRegistryAccess.get()
        ));
        reloadConfig();
        System.out.println(getConfig().getString("test"));
        System.out.println(Component.Serializer.fromJson(
                getConfig().getString("test", ""),
                (HolderLookup.Provider) MinecraftRegistryAccess.get()
        ));
        return true;
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
