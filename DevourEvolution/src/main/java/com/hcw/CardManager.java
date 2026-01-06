package com.hcw;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.UUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardManager {
    
    private final CardEnhancementPlugin plugin;
    
    // 基础成功率映射表
    // 结构：主卡星级 -> 副卡星级 -> 副卡品级 -> 成功率
    private Map<Integer, Map<Integer, Map<String, Double>>> baseSuccessRates = new HashMap<>();
    
    // 伪概率功能相关字段
    private boolean pseudoProbabilityActive = false;
    private long pseudoProbabilityEndTime = 0;
    private int pseudoProbabilityAttempts = 0;
    private int pseudoProbabilityMaxAttempts = 3;
    private double pseudoProbabilityThreshold = 0.05;
    
    public CardManager() {
        this.plugin = CardEnhancementPlugin.getInstance();
        initializeBaseSuccessRates();
    }
    
    /**
     * Initialize base success rates from the provided table
     */
    private void initializeBaseSuccessRates() {
        // Clear existing data
        baseSuccessRates.clear();
        
        // Get the configuration
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection successRateTable = config.getConfigurationSection("success-rate-table");
        if (successRateTable == null) {
            plugin.getLogger().warning("Success rate table not found in config!");
            return;
        }
        
        // Get the success rates list
        List<Map<?, ?>> successRatesList = successRateTable.getMapList("success-rates");
        if (successRatesList.isEmpty()) {
            plugin.getLogger().warning("No success rates defined in config!");
            return;
        }
        
        // Process each success rate entry
        for (Map<?, ?> entry : successRatesList) {
            // Get star difference
            int starDifference = Integer.parseInt(entry.get("star-difference").toString());
            
            // Get quality
            String quality = entry.get("quality").toString();
            
            // Map quality names to the internal format
            String internalQuality;
            switch (quality) {
                case "好卡":
                    internalQuality = "优";
                    break;
                case "中卡":
                    internalQuality = "中";
                    break;
                case "差卡":
                    internalQuality = "差";
                    break;
                default:
                    internalQuality = "中";
                    break;
            }
            
            // Get rates map
            Map<?, ?> ratesMap = (Map<?, ?>) entry.get("rates");
            if (ratesMap == null || ratesMap.isEmpty()) {
                continue;
            }
            
            // Process each rate entry
            for (Map.Entry<?, ?> rateEntry : ratesMap.entrySet()) {
                // Parse the star transition (e.g., "1-2" -> from 1 to 2)
                String transition = rateEntry.getKey().toString();
                String[] parts = transition.split("-");
                if (parts.length != 2) {
                    continue;
                }
                
                int fromStar = Integer.parseInt(parts[0]);
                
                // Calculate sub star level based on star difference
                int subStar = fromStar + starDifference;
                
                // Parse the rate (e.g., "48%" -> 48.0)
                String rateStr = rateEntry.getValue().toString();
                double rate = Double.parseDouble(rateStr.replace("%", ""));
                
                // Initialize maps if they don't exist
                baseSuccessRates.computeIfAbsent(fromStar, k -> new HashMap<>());
                baseSuccessRates.get(fromStar).computeIfAbsent(subStar, k -> new HashMap<>());
                
                // Set the rate
                baseSuccessRates.get(fromStar).get(subStar).put(internalQuality, rate);
            }
        }
        
        plugin.getLogger().info("Success rates loaded from config!");
    }
    
    /**
     * Check if an item is a valid card
     */
    public boolean isCard(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        
        List<String> lore = meta.getLore();
        String cardIdentifier = plugin.getConfigManager().getCardIdentifier();
        
        for (String line : lore) {
            if (line.contains(cardIdentifier)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if an item is a lucky charm (四叶草)
     */
    public boolean isLuckyCharm(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return false;
        }
        
        String displayName = meta.getDisplayName();
        String luckyCharmPattern = plugin.getConfigManager().get幸运符NamePattern();
        
        // Check if the item is a lucky charm based on config
        return displayName.contains(luckyCharmPattern) || displayName.contains("幸运符") || displayName.contains("四叶草");
    }
    
    /**
     * Get the star level of a card
     */
    public int getCardStar(ItemStack card) {
        if (!isCard(card)) {
            return 0;
        }
        
        ItemMeta meta = card.getItemMeta();
        List<String> lore = meta.getLore();
        
        // Pattern to match "星级：x"
        Pattern pattern = Pattern.compile("星级：([0-9]+)");
        
        for (String line : lore) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        
        return 0;
    }
    
    /**
     * Get the quality of a card
     */
    public String getCardQuality(ItemStack card) {
        if (!isCard(card)) {
            return "差";
        }
        
        ItemMeta meta = card.getItemMeta();
        List<String> lore = meta.getLore();
        
        // Check for quality keywords
        for (String line : lore) {
            if (line.contains("品级：优")) {
                return "优";
            } else if (line.contains("品级：中")) {
                return "中";
            } else if (line.contains("品级：差")) {
                return "差";
            }
        }
        
        return "差";
    }
    
    /**
     * Get the level of a lucky charm
     */
    public int getLuckyCharmLevel(ItemStack charm) {
        if (!isLuckyCharm(charm)) {
            return 0;
        }
        
        ItemMeta meta = charm.getItemMeta();
        String displayName = meta.getDisplayName();
        
        // Pattern to match numeric level four-leaf clovers
        Pattern numericPattern = Pattern.compile("([0-9]+)级四叶草");
        Matcher numericMatcher = numericPattern.matcher(displayName);
        
        if (numericMatcher.find()) {
            return Integer.parseInt(numericMatcher.group(1));
        }
        
        // Handle special level four-leaf clovers
        if (displayName.contains("s级四叶草")) {
            return 7;
        } else if (displayName.contains("ss级四叶草")) {
            return 8;
        } else if (displayName.contains("sss级四叶草")) {
            return 9;
        } else if (displayName.contains("ssr级四叶草")) {
            return 10;
        } else if (displayName.contains("ex级四叶草")) {
            return 11;
        }
        
        return 1; // Default to level 1
    }
    
    /**
     * Calculate the base success rate based on card attributes
     */
    public double getBaseSuccessRate(ItemStack mainCard, ItemStack[] subCards) {
        int mainStar = getCardStar(mainCard);
        
        // If main card has max star level, return 0
        if (mainStar >= 15) {
            return 0.0;
        }
        
        // Find the best sub card (highest star + highest quality)
        ItemStack bestSubCard = null;
        for (ItemStack subCard : subCards) {
            if (subCard != null && isCard(subCard)) {
                if (bestSubCard == null) {
                    bestSubCard = subCard;
                } else {
                    // Compare star levels first
                    int currentStar = getCardStar(subCard);
                    int bestStar = getCardStar(bestSubCard);
                    if (currentStar > bestStar) {
                        bestSubCard = subCard;
                    } else if (currentStar == bestStar) {
                        // Compare quality
                        String currentQuality = getCardQuality(subCard);
                        String bestQuality = getCardQuality(bestSubCard);
                        if (qualityToInt(currentQuality) > qualityToInt(bestQuality)) {
                            bestSubCard = subCard;
                        }
                    }
                }
            }
        }
        
        if (bestSubCard == null) {
            return 0.0;
        }
        
        int subStar = getCardStar(bestSubCard);
        String subQuality = getCardQuality(bestSubCard);
        
        // Get base success rate from the map
        return baseSuccessRates.getOrDefault(mainStar, new HashMap<>())
                .getOrDefault(subStar, new HashMap<>())
                .getOrDefault(subQuality, 0.0);
    }
    
    /**
     * Helper method to convert quality to integer for comparison
     */
    private int qualityToInt(String quality) {
        switch (quality) {
            case "优":
                return 3;
            case "中":
                return 2;
            case "差":
                return 1;
            default:
                return 0;
        }
    }
    
    /**
     * Calculate the success rate for enhancement
     */
    public double calculateSuccessRate(ItemStack mainCard, ItemStack luckyCharm, ItemStack[] subCards) {
        int mainStar = getCardStar(mainCard);
        
        // If main card has max star level, return 0
        if (mainStar >= 15) {
            return 0.0;
        }
        
        // Collect all sub card success rates
        List<Double> subCardRates = new ArrayList<>();
        for (ItemStack subCard : subCards) {
            if (subCard != null && isCard(subCard)) {
                int subStar = getCardStar(subCard);
                String subQuality = getCardQuality(subCard);
                double rate = baseSuccessRates.getOrDefault(mainStar, new HashMap<>())
                        .getOrDefault(subStar, new HashMap<>())
                        .getOrDefault(subQuality, 0.0);
                if (rate > 0) {
                    subCardRates.add(rate);
                }
            }
        }
        
        // Sort success rates in descending order
        subCardRates.sort(Collections.reverseOrder());
        
        // Get p1, p2, p3 (p1 is highest, p3 is lowest)
        double p1 = subCardRates.size() > 0 ? subCardRates.get(0) : 0.0;
        double p2 = subCardRates.size() > 1 ? subCardRates.get(1) : 0.0;
        double p3 = subCardRates.size() > 2 ? subCardRates.get(2) : 0.0;
        
        // Calculate base rate using the formula: p1 + p2/3 + p3/3
        double baseRate = p1 + (p2 / 3) + (p3 / 3);
        if (baseRate == 0.0) {
            return 0.0;
        }
        
        // Calculate lucky charm bonus (L) from the table
        double luckyCharmBonus = 1.0;
        if (luckyCharm != null && isLuckyCharm(luckyCharm)) {
            int charmLevel = getLuckyCharmLevel(luckyCharm);
            // Use the lucky charm multiplier table from config
            switch (charmLevel) {
                case 1:
                    luckyCharmBonus = 1.2;
                    break;
                case 2:
                    luckyCharmBonus = 1.4;
                    break;
                case 3:
                    luckyCharmBonus = 1.7;
                    break;
                case 4:
                    luckyCharmBonus = 2.0;
                    break;
                case 5:
                    luckyCharmBonus = 2.4;
                    break;
                case 6:
                    luckyCharmBonus = 2.7;
                    break;
                case 7: // s
                    luckyCharmBonus = 3.0;
                    break;
                case 8: // ss
                    luckyCharmBonus = 3.2;
                    break;
                case 9: // sss
                    luckyCharmBonus = 3.6;
                    break;
                case 10: // ssr
                    luckyCharmBonus = 4.0;
                    break;
                case 11: // ex
                    luckyCharmBonus = 4.5;
                    break;
                default:
                    luckyCharmBonus = 1.0;
                    break;
            }
        }
        
        // Calculate VIP bonus (placeholder)
        double vipBonus = 0.0;
        // TODO: Implement VIP bonus based on player's VIP level
        
        // Calculate guild bonus (placeholder)
        double guildBonus = 0.0;
        // TODO: Implement guild bonus based on player's guild level
        
        // Calculate final success rate using the formula: L * (p1 + p2/3 + p3/3)
        double successRate = luckyCharmBonus * baseRate * (1 + vipBonus + guildBonus);
        
        // Ensure success rate is between 1% and 100%
        return Math.max(1.0, Math.min(successRate, 100.0));
    }
    
    /**
     * Set pseudo-probability parameters
     * @param interval Interval time in ticks before activating pseudo-probability
     * @param duration Duration in ticks for pseudo-probability to remain active
     * @param maxAttempts Maximum number of attempts to intercept (default: 3)
     * @param threshold Probability threshold below which interception is unlimited (default: 0.05)
     */
    public void setPseudoProbability(int interval, int duration, int maxAttempts, double threshold) {
        // If duration is 0, disable pseudo-probability immediately
        if (duration == 0) {
            pseudoProbabilityActive = false;
            pseudoProbabilityEndTime = 0;
            pseudoProbabilityAttempts = 0;
            plugin.getLogger().info("Pseudo-probability disabled!");
            return;
        }
        
        // Schedule activation after interval
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pseudoProbabilityActive = true;
            pseudoProbabilityEndTime = System.currentTimeMillis() + (duration * 50); // Convert ticks to milliseconds
            pseudoProbabilityAttempts = 0;
            pseudoProbabilityMaxAttempts = maxAttempts;
            pseudoProbabilityThreshold = threshold;
            plugin.getLogger().info("Pseudo-probability activated:");
            plugin.getLogger().info("  Duration: " + duration + " ticks");
            plugin.getLogger().info("  Max attempts: " + maxAttempts);
            plugin.getLogger().info("  Threshold: " + threshold);
            
            // Schedule deactivation after duration
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                pseudoProbabilityActive = false;
                pseudoProbabilityEndTime = 0;
                pseudoProbabilityAttempts = 0;
                plugin.getLogger().info("Pseudo-probability deactivated!");
            }, duration);
        }, interval);
        
        plugin.getLogger().info("Pseudo-probability will activate in " + interval + " ticks, lasting for " + duration + " ticks!");
    }
    
    /**
     * Calculate additional success rate based on player's VIP and guild levels
     * @param player The player to calculate for
     * @return Additional success rate percentage
     */
    public double calculateAdditionalSuccessRate(Player player) {
        // If player is null, return 0 additional rate
        if (player == null) {
            return 0.0;
        }
        
        double additionalRate = 0.0;
        
        // Get VIP level - use permission check directly for reliability
        int vipLevel = 0;
        try {
            for (int i = 12; i >= 1; i--) {
                if (player.hasPermission("vip." + i)) {
                    vipLevel = i;
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check VIP permissions: " + e.getMessage());
        }
        
        // Add VIP bonus
        additionalRate += plugin.getConfig().getDouble("additional-success-rate.vip-bonuses." + vipLevel, 0.0);
        
        // Get guild level - use permission check directly for reliability
        int guildLevel = 0;
        try {
            for (int i = 5; i >= 1; i--) {
                if (player.hasPermission("guild." + i)) {
                    guildLevel = i;
                    break;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check guild permissions: " + e.getMessage());
        }
        
        // Add guild bonus
        additionalRate += plugin.getConfig().getDouble("additional-success-rate.guild-bonuses." + guildLevel, 0.0);
        
        // Ensure additional success rate doesn't exceed 100%
        return Math.min(additionalRate, 100.0);
    }
    
    /**
     * Perform the enhancement process
     */
    public boolean enhanceCard(ItemStack mainCard, ItemStack luckyCharm, ItemStack[] subCards, boolean insuranceEnabled, Player player) {
        // Calculate success rate
        double successRate = calculateSuccessRate(mainCard, luckyCharm, subCards);
        
        // Check if enhancement is possible
        if (successRate == 0.0) {
            return false;
        }
        
        // Calculate additional success rate
        double additionalRate = calculateAdditionalSuccessRate(player);
        
        // Determine success or failure
        boolean success = Math.random() * 100 < successRate;
        
        // Apply pseudo-probability logic if active
        if (pseudoProbabilityActive && successRate < 100.0) {
            // Check if we should apply pseudo-probability
            boolean shouldApplyPseudo = false;
            
            // If success rate is below threshold, always apply
            if (successRate < (pseudoProbabilityThreshold * 100)) {
                shouldApplyPseudo = true;
            }
            // If we haven't reached max attempts, apply
            else if (pseudoProbabilityAttempts < pseudoProbabilityMaxAttempts) {
                shouldApplyPseudo = true;
                pseudoProbabilityAttempts++;
            }
            
            if (shouldApplyPseudo) {
                plugin.getLogger().info("Pseudo-probability applied - forcing enhancement failure!");
                plugin.getLogger().info("  Attempt: " + pseudoProbabilityAttempts + " / " + pseudoProbabilityMaxAttempts);
                plugin.getLogger().info("  Success rate: " + successRate + "%, Threshold: " + (pseudoProbabilityThreshold * 100) + "%");
                success = false;
            }
        }
        
        // If first attempt failed, try additional success rate
        if (!success && additionalRate > 0.0) {
            boolean additionalSuccess = Math.random() * 100 < additionalRate;
            if (additionalSuccess) {
                plugin.getLogger().info("Additional success rate applied - enhancement succeeded!");
                plugin.getLogger().info("  Additional rate: " + additionalRate + "%");
                success = true;
            }
        }
        
        if (success) {
            // Increase main card star level
            upgradeCardStar(mainCard);
        } else {
            // Handle failure - only downgrade if insurance is not enabled
            if (!insuranceEnabled) {
                downgradeCardStar(mainCard);
            }
        }
        
        return success;
    }
    
    /**
     * Upgrade the star level of a card
     */
    private void upgradeCardStar(ItemStack card) {
        if (card == null) return;
        
        int currentStar = getCardStar(card);
        if (currentStar >= 15) {
            return; // Already max star level
        }
        
        ItemMeta meta = card.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return; // No metadata to update
        }
        
        List<String> lore = meta.getLore();
        if (lore == null) {
            return;
        }
        
        // Update the star level in lore
        for (int i = 0; i < lore.size(); i++) {
            String line = lore.get(i);
            if (line.contains("星级：")) {
                lore.set(i, "星级：" + (currentStar + 1));
                break;
            }
        }
        
        meta.setLore(lore);
        card.setItemMeta(meta);
    }
    
    /**
     * Downgrade the star level of a card if applicable
     */
    private void downgradeCardStar(ItemStack card) {
        if (card == null) return;
        
        int currentStar = getCardStar(card);
        
        // Only downgrade if star level > 5 (5级以上失败时减少星级，5级以下无惩罚)
        if (currentStar > 5) {
            ItemMeta meta = card.getItemMeta();
            if (meta == null || !meta.hasLore()) {
                return; // No metadata to update
            }
            
            List<String> lore = meta.getLore();
            if (lore == null) {
                return;
            }
            
            // Update the star level in lore
            for (int i = 0; i < lore.size(); i++) {
                String line = lore.get(i);
                if (line.contains("星级：")) {
                    lore.set(i, "星级：" + (currentStar - 1));
                    break;
                }
            }
            
            meta.setLore(lore);
            card.setItemMeta(meta);
        }
    }
    
    /**
     * Update the success rate display in the GUI
     */
    public void updateSuccessRate(Inventory gui, ItemStack mainCard, ItemStack luckyCharm, ItemStack[] subCards, Player player) {
        // Update success rate display
        double successRate = calculateSuccessRate(mainCard, luckyCharm, subCards);
        
        // Calculate additional success rate
        double additionalRate = calculateAdditionalSuccessRate(player);
        
        // Format success rate display
        String successRateDisplay;
        if (additionalRate > 0) {
            successRateDisplay = String.format("成功率：%.1f%% (+%.1f%%)", successRate, additionalRate);
        } else {
            successRateDisplay = String.format("成功率：%.1f%%", successRate);
        }
        
        ItemStack paper = createItem(Material.PAPER, successRateDisplay);
        
        // Update insurance cost display
        String insuranceCost = "0D";
        if (mainCard != null && isCard(mainCard)) {
            int currentStar = getCardStar(mainCard);
            // Get insurance cost based on star transition from current star to next star
            insuranceCost = plugin.getConfigManager().getNextLevelInsuranceCost(currentStar);
        }
        
        // Find and update both success rate and insurance slots
        for (int slot = 0; slot < 54; slot++) {
            // Get item at slot
            ItemStack slotItem = gui.getItem(slot);
            if (slotItem != null) {
                ItemMeta meta = slotItem.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    String displayName = meta.getDisplayName();
                    
                    // Update success rate slot
                    if (displayName.contains("成功率：")) {
                        gui.setItem(slot, paper);
                    } 
                    // Update insurance slot
                    else if (displayName.contains("保险金：")) {
                        // Create new item with updated insurance cost
                        ItemStack updatedInsuranceItem = slotItem.clone();
                        ItemMeta updatedMeta = updatedInsuranceItem.getItemMeta();
                        if (updatedMeta != null) {
                            // Replace the insurance cost in the name
                            String updatedName = displayName.replaceAll("保险金：[^ ]+", "保险金：" + insuranceCost);
                            updatedMeta.setDisplayName(updatedName);
                            updatedInsuranceItem.setItemMeta(updatedMeta);
                            gui.setItem(slot, updatedInsuranceItem);
                        }
                    }
                }
            }
        }
    }
    
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}