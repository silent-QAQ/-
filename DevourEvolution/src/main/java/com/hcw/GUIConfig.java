package com.hcw;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GUIConfig {
    
    private String guiName;
    private Map<Integer, GUIItem> guiItems = new HashMap<>();
    private Map<Character, GUIItem> layoutItems = new HashMap<>();
    private List<String> layoutPatterns = new ArrayList<>();
    
    public GUIConfig(ConfigurationSection guiSection) {
        if (guiSection == null) {
            CardEnhancementPlugin.getInstance().getLogger().warning("GUI configuration section not found! Using default GUI layout.");
            // Initialize with empty configuration
            this.guiName = "卡牌强化";
        } else {
            loadConfig(guiSection);
        }
    }
    
    private void loadConfig(ConfigurationSection guiSection) {
        this.guiName = guiSection.getString("name", "卡牌强化");
        
        // Check if this is the new character-based layout format
        List<String> patterns = guiSection.getStringList("layout");
        ConfigurationSection settingsSection = guiSection.getConfigurationSection("settings");
        
        if (!patterns.isEmpty() && settingsSection != null) {
            // Load character-based layout
            this.layoutPatterns = patterns;
            
            // Load layout item definitions
            for (String key : settingsSection.getKeys(false)) {
                if (key.length() != 1) continue;
                
                char itemChar = key.charAt(0);
                ConfigurationSection itemSection = settingsSection.getConfigurationSection(key);
                if (itemSection != null) {
                    GUIItem guiItem = new GUIItem();
                    guiItem.material = Material.valueOf(itemSection.getString("material", "AIR"));
                guiItem.name = itemSection.getString("name", "");
                guiItem.lore = itemSection.getStringList("lore");
                // 支持中文"作用"和英文"function"两种配置
                guiItem.function = itemSection.getString("作用", itemSection.getString("function", "none"));
                    
                    layoutItems.put(itemChar, guiItem);
                }
            }
            
            // Generate slot mapping from layout patterns
            generateSlotMapping();
        } else {
            // Load legacy slot-based items (backward compatibility)
            ConfigurationSection itemsSection = guiSection.getConfigurationSection("items");
            if (itemsSection != null) {
                for (String key : itemsSection.getKeys(false)) {
                    try {
                        int slot = Integer.parseInt(key);
                        ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                        if (itemSection != null) {
                            GUIItem guiItem = new GUIItem();
                            guiItem.material = Material.valueOf(itemSection.getString("material", "BLACK_STAINED_GLASS_PANE"));
                            guiItem.name = itemSection.getString("name", "");
                            guiItem.lore = itemSection.getStringList("lore");
                            guiItem.function = itemSection.getString("function", "none");
                            
                            guiItems.put(slot, guiItem);
                        }
                    } catch (NumberFormatException e) {
                        // Skip non-numeric keys
                    }
                }
            }
        }
    }
    
    /**
     * Generate slot mapping from layout patterns
     */
    private void generateSlotMapping() {
        int slot = 0;
        for (String pattern : layoutPatterns) {
            for (char c : pattern.toCharArray()) {
                if (slot >= 54) break;
                
                GUIItem layoutItem = layoutItems.get(c);
                if (layoutItem != null) {
                    guiItems.put(slot, layoutItem);
                }
                slot++;
            }
        }
    }
    
    public String getGuiName() {
        return guiName;
    }
    
    public Map<Integer, GUIItem> getGuiItems() {
        return guiItems;
    }
    
    public ItemStack getItemForSlot(int slot) {
        GUIItem guiItem = guiItems.get(slot);
        if (guiItem == null) {
            return null;
        }
        
        // Skip AIR items, don't display them
        if (guiItem.material == Material.AIR) {
            return null;
        }
        
        ItemStack item = new ItemStack(guiItem.material);
        ItemMeta meta = item.getItemMeta();
        
        // Only set meta if it's not null (some items might not support meta)
        if (meta != null) {
            if (guiItem.name != null && !guiItem.name.isEmpty()) {
                meta.setDisplayName(guiItem.name);
            }
            
            if (guiItem.lore != null && !guiItem.lore.isEmpty()) {
                meta.setLore(guiItem.lore);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    public String getFunctionForSlot(int slot) {
        GUIItem guiItem = guiItems.get(slot);
        if (guiItem == null) {
            return "none";
        }
        return guiItem.function;
    }
    
    public class GUIItem {
        public Material material;
        public String name;
        public List<String> lore = new ArrayList<>();
        public String function;
    }
}