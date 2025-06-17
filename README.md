# Fix Minecraft enchant level rendering
This paper (and theoretically spigot) plugin hijacks packets that contains items. Essentially, for each item that has enchantment level over 10, the plugin:
1. Set hide enchant flag
2. Process enchant level into roman numerals
3. Prepend lore into the front of existing item lore
