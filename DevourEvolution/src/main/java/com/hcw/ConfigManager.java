package com.hcw;

import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {
    
    private FileConfiguration config;
    
    // Inner class to store insurance cost information
    public static class InsuranceCost {
        private double amount;
        private String type; // "money" or "points"
        
        public InsuranceCost(double amount, String type) {
            this.amount = amount;
            this.type = type;
        }
        
        public double getAmount() {
            return amount;
        }
        
        public String getType() {
            return type;
        }
    }
    
    public void loadConfig() {
        // Get the plugin instance
        CardEnhancementPlugin plugin = CardEnhancementPlugin.getInstance();
        
        // Save default config if it doesn't exist
        plugin.saveDefaultConfig();
        
        // Load the config
        config = plugin.getConfig();
    }
    
    /**
     * Parse insurance cost string with suffix (D for points, G for money)
     * @param costStr Insurance cost string (e.g., "100D", "50G")
     * @return InsuranceCost object with amount and type
     */
    public InsuranceCost parseInsuranceCost(String costStr) {
        double amount = 0;
        String type = "points"; // Default to points
        
        if (costStr == null || costStr.isEmpty()) {
            return new InsuranceCost(0, type);
        }
        
        // Check for suffix
        if (costStr.endsWith("D")) {
            // Points
            type = "points";
            costStr = costStr.substring(0, costStr.length() - 1);
        } else if (costStr.endsWith("G")) {
            // Money
            type = "money";
            costStr = costStr.substring(0, costStr.length() - 1);
        }
        
        // Parse amount
        try {
            amount = Double.parseDouble(costStr);
        } catch (NumberFormatException e) {
            // If parsing fails, return 0
            amount = 0;
        }
        
        return new InsuranceCost(amount, type);
    }
    
    public String getCardIdentifier() {
        return config.getString("card-identifier", "道具类型：卡牌");
    }
    
    public String getGuiTitle() {
        return config.getString("gui-title", "卡牌强化");
    }
    
    public double getBaseSuccessRate() {
        return config.getDouble("base-success-rate", 50.0);
    }
    
    public double get副卡BonusPerCard() {
        return config.getDouble("副卡-bonus-per-card", 10.0);
    }
    
    public double get幸运符Bonus() {
        return config.getDouble("幸运符-bonus", 5.0);
    }
    
    public String get幸运符NamePattern() {
        return config.getString("幸运符-name-pattern", "四叶草");
    }
    
    public int get保险金Amount() {
        return config.getInt("保险金-amount", 1000);
    }
    
    /**
     * Get insurance cost based on star transition
     * @param fromStar Current star level
     * @param toStar Target star level
     * @return Insurance cost for this transition
     */
    public String get保险金Cost(int fromStar, int toStar) {
        String transitionKey = fromStar + "-" + toStar;
        return config.getString("success-rate-table.insurance-costs." + transitionKey, "0D");
    }
    
    /**
     * Get insurance cost for the next star level
     * @param currentStar Current star level
     * @return Insurance cost for next level
     */
    public String getNextLevelInsuranceCost(int currentStar) {
        return get保险金Cost(currentStar, currentStar + 1);
    }
    
    /**
     * Get insurance type (money or points)
     * @return Insurance type
     */
    public String getInsuranceType() {
        return config.getString("insurance-type", "points");
    }
    
    /**
     * Get enhancement success message
     * @return Success message
     */
    public String getSuccessMessage() {
        return config.getString("messages.success", "物品强化成功，你可以到物品栏查看该卡的属性");
    }
    
    /**
     * Get enhancement failure message
     * @return Failure message
     */
    public String getFailureMessage() {
        return config.getString("messages.failure", "不够好运，强化失败");
    }
    
    /**
     * Get broadcast message for specific star level
     * @param star Star level
     * @return Broadcast message with placeholders
     */
    public String getBroadcastMessage(int star) {
        return config.getString("messages.broadcasts." + star, "");
    }
}