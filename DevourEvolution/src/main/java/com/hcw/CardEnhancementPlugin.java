package com.hcw;

import org.bukkit.command.Command;
import org.bukkit.plugin.java.JavaPlugin;

public class CardEnhancementPlugin extends JavaPlugin {
    
    private static CardEnhancementPlugin instance;
    private ConfigManager configManager;
    private GUIManager guiManager;
    private CardManager cardManager;
    private GUIConfig guiConfig;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Initialize config manager
        configManager = new ConfigManager();
        configManager.loadConfig();
        
        // Initialize GUI config (frontend definition)
        guiConfig = new GUIConfig(getConfig().getConfigurationSection("gui-layout"));
        
        // Initialize card manager
        cardManager = new CardManager();
        
        // Initialize GUI manager (backend logic)
        guiManager = new GUIManager();
        
        // Register commands
        var hcwCommand = getCommand("hcw");
        if (hcwCommand != null) {
            HCWCommand hcwCommandExecutor = new HCWCommand();
            hcwCommand.setExecutor(hcwCommandExecutor);
            hcwCommand.setTabCompleter(hcwCommandExecutor);
        } else {
            getLogger().warning("Failed to register 'hcw' command! It may be missing from plugin.yml");
        }
        
        var deCommand = getCommand("de");
        if (deCommand != null) {
            DECommand deCommandExecutor = new DECommand();
            deCommand.setExecutor(deCommandExecutor);
            deCommand.setTabCompleter(deCommandExecutor);
        } else {
            getLogger().warning("Failed to register 'de' command! It may be missing from plugin.yml");
        }
        
        // Output group information to console using plugin logger
        getLogger().info("加入1078284723插件群，获得更多插件");
        
        getLogger().info("CardEnhancementPlugin has been enabled!");
    }
    
    @Override
    public void onDisable() {
        getLogger().info("CardEnhancementPlugin has been disabled!");
    }
    
    public static CardEnhancementPlugin getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public GUIManager getGuiManager() {
        return guiManager;
    }
    
    public CardManager getCardManager() {
        return cardManager;
    }
    
    public GUIConfig getGuiConfig() {
        return guiConfig;
    }
}