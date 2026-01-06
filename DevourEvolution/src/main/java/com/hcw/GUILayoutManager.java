package com.hcw;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GUILayoutManager {
    
    private final CardEnhancementPlugin plugin;
    private Map<Character, LayoutItem> layoutItems = new HashMap<>();
    private List<String> layoutPatterns = new ArrayList<>();
    private String guiName;
    
    public GUILayoutManager() {
        this.plugin = CardEnhancementPlugin.getInstance();
        loadLayoutConfig();
    }
    
    public void loadLayoutConfig() {
        ConfigurationSection guiSection = plugin.getConfig().getConfigurationSection("gui-layout");
        if (guiSection == null) {
            plugin.getLogger().warning("GUI layout configuration not found!");
            return;
        }
        
        // Load GUI name
        guiName = guiSection.getString("name", "卡牌强化");
        
        // Load layout patterns
        layoutPatterns = guiSection.getStringList("layout");
        
        // Load layout item settings
        ConfigurationSection settingsSection = guiSection.getConfigurationSection("settings");
        if (settingsSection != null) {
            for (String key : settingsSection.getKeys(false)) {
                if (key.length() != 1) continue;
                
                char itemChar = key.charAt(0);
                ConfigurationSection itemSection = settingsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    LayoutItem layoutItem = new LayoutItem();
                    layoutItem.function = itemSection.getString("function", itemSection.getString("作用", "none"));
                    layoutItem.material = Material.valueOf(itemSection.getString("material", "WHITE_STAINED_GLASS_PANE"));
                    layoutItem.name = itemSection.getString("name", "");
                    layoutItem.lore = itemSection.getStringList("lore");
                    
                    layoutItems.put(itemChar, layoutItem);
                }
            }
        }
    }
    
    public String getGuiName() {
        return guiName;
    }
    
    public List<String> getLayoutPatterns() {
        return layoutPatterns;
    }
    
    public Map<Character, LayoutItem> getLayoutItems() {
        return layoutItems;
    }
    
    public ItemStack createLayoutItem(char itemChar) {
        LayoutItem layoutItem = layoutItems.get(itemChar);
        if (layoutItem == null) {
            return new ItemStack(Material.WHITE_STAINED_GLASS_PANE);
        }
        
        ItemStack item = new ItemStack(layoutItem.material);
        ItemMeta meta = item.getItemMeta();
        
        if (layoutItem.name != null && !layoutItem.name.isEmpty()) {
            meta.setDisplayName(layoutItem.name);
        }
        
        if (layoutItem.lore != null && !layoutItem.lore.isEmpty()) {
            meta.setLore(layoutItem.lore);
        }
        
        item.setItemMeta(meta);
        return item;
    }
    
    public class LayoutItem {
        public String function;
        public Material material;
        public String name;
        public List<String> lore;
    }
}