package com.hcw;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DECommand implements CommandExecutor, TabCompleter {
    
    private final CardEnhancementPlugin plugin;
    
    public DECommand() {
        this.plugin = CardEnhancementPlugin.getInstance();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Handle kp subcommand (add card properties)
        if (args.length >= 1 && args[0].equalsIgnoreCase("kp")) {
            return handleKpSubcommand(sender, args);
        }
        // Handle give subcommand (give lucky charm)
        else if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return handleGiveSubcommand(sender, args);
        }
        // Handle p subcommand (pseudo-probability)
        else if (args.length >= 2 && args[0].equalsIgnoreCase("p")) {
            return handlePSubcommand(sender, args);
        }
        // Show help
        else {
            showHelp(sender);
            return true;
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        
        // First argument: subcommand
        if (args.length == 1) {
            completions.add("kp");
            completions.add("give");
            completions.add("p");
            return completions;
        }
        
        // Second argument: depends on subcommand
        else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("kp")) {
                // kp subcommand: second argument is star level (1-15)
                for (int i = 1; i <= 15; i++) {
                    completions.add(String.valueOf(i));
                }
                return completions;
            } else if (args[0].equalsIgnoreCase("give")) {
                // give subcommand: second argument is player name
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    completions.add(player.getName());
                }
                return completions;
            }
        }
        
        // Third argument: depends on subcommand
        else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("kp")) {
                // kp subcommand: third argument is quality (1-3)
                for (int i = 1; i <= 3; i++) {
                    completions.add(String.valueOf(i));
                }
                return completions;
            } else if (args[0].equalsIgnoreCase("give")) {
                // give subcommand: third argument is lucky charm name
                java.util.Set<String> charmKeys = plugin.getConfig().getConfigurationSection("lucky-charms").getKeys(false);
                for (String key : charmKeys) {
                    String charmName = plugin.getConfig().getString("lucky-charms." + key + ".name");
                    if (charmName != null) {
                        completions.add(charmName);
                    }
                }
                return completions;
            }
        }
        
        // Fourth argument: only for give subcommand, amount (1-64)
        else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // give subcommand: fourth argument is amount (1-64)
            for (int i = 1; i <= 64; i++) {
                completions.add(String.valueOf(i));
            }
            return completions;
        }
        
        return completions;
    }
    
    /**
     * Show command help
     */
    private void showHelp(CommandSender sender) {
        sender.sendMessage("§6=== Card Enhancement Plugin Help ===");
        sender.sendMessage("§e/de kp <star_level> <quality> §7- Add card properties to held item");
        sender.sendMessage("  §e<quality>: §73(优), 2(中), 1(差)");
        sender.sendMessage("§e/de give <player> <lucky_charm> [amount] §7- Give lucky charm to player");
        sender.sendMessage("  §e<player>: §7Player name");
        sender.sendMessage("  §e<lucky_charm>: §71级四叶草, 2级四叶草, 3级四叶草, 4级四叶草, 5级四叶草, 6级四叶草, s级四叶草, ss级四叶草, sss级四叶草, ssr级四叶草, ex级四叶草");
        sender.sendMessage("  §e[amount]: §7Optional, default is 1");
        sender.sendMessage("§e/de p <interval> <duration> [max_attempts] [threshold] §7- Set pseudo-probability parameters (单位: tick)");
        sender.sendMessage("  §e<interval>: §7间隔时间，即伪概率触发前的延迟，默认0");
        sender.sendMessage("  §e<duration>: §7持续时间，即伪概率生效的时间，默认0（0时不启用）");
        sender.sendMessage("  §e[max_attempts]: §7最大拦截次数，默认3");
        sender.sendMessage("  §e[threshold]: §7概率阈值，低于此概率可以无限拦截，默认0.05");
        sender.sendMessage("  §e示例: /de p 20 100 5 0.1 - 20 tick后开始，持续100 tick，最大拦截5次，阈值0.1");
        sender.sendMessage("§6==================================");
    }
    
    /**
     * Handle p subcommand (pseudo-probability)
     */
    private boolean handlePSubcommand(CommandSender sender, String[] args) {
        if (!(sender.hasPermission("cardenhancement.admin.pseudoprobability"))) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }
        
        try {
            // Parse command arguments with defaults
            int interval = args.length >= 2 ? Integer.parseInt(args[1]) : 0;
            int duration = args.length >= 3 ? Integer.parseInt(args[2]) : 0;
            int maxAttempts = args.length >= 4 ? Integer.parseInt(args[3]) : 3;
            double threshold = args.length >= 5 ? Double.parseDouble(args[4]) : 0.05;
            
            // Set pseudo-probability parameters
            plugin.getCardManager().setPseudoProbability(interval, duration, maxAttempts, threshold);
            
            sender.sendMessage("§6伪概率参数已设置：");
            sender.sendMessage("  §e间隔时间: §7" + interval + " ticks");
            sender.sendMessage("  §e持续时间: §7" + duration + " ticks");
            sender.sendMessage("  §e最大拦截次数: §7" + maxAttempts);
            sender.sendMessage("  §e概率阈值: §7" + threshold * 100 + "%");
            return true;
        } catch (NumberFormatException e) {
            sender.sendMessage("§c参数错误！请输入有效的数字。");
            return true;
        }
    }
    
    /**
     * Handle kp subcommand (add card properties)
     */
    private boolean handleKpSubcommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cOnly players can use this command!");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if player has an item in hand
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§c请手持一个物品！");
            return true;
        }
        
        // Check if we have enough arguments
        if (args.length < 3) {
            player.sendMessage("§c使用方法: /de kp <星级> <品级> (3:优, 2:中, 1:差)");
            return true;
        }
        
        // Parse star level
        int starLevel;
        try {
            starLevel = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c星级必须是一个数字！");
            return true;
        }
        
        // Validate star level (1-15)
        if (starLevel < 1 || starLevel > 15) {
            player.sendMessage("§c星级必须在1到15之间！");
            return true;
        }
        
        // Parse quality
        int qualityInt;
        try {
            qualityInt = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§c品级必须是一个数字 (3:优, 2:中, 1:差)！");
            return true;
        }
        
        // Validate quality (1-3)
        if (qualityInt < 1 || qualityInt > 3) {
            player.sendMessage("§c品级必须在1到3之间 (3:优, 2:中, 1:差)！");
            return true;
        }
        
        // Convert quality int to Chinese string
        String quality;
        switch (qualityInt) {
            case 3:
                quality = "优";
                break;
            case 2:
                quality = "中";
                break;
            case 1:
            default:
                quality = "差";
                break;
        }
        
        // Add card properties to item
        addCardProperties(player, item, starLevel, quality);
        return true;
    }
    
    /**
     * Handle give subcommand (give lucky charm to player)
     */
    private boolean handleGiveSubcommand(CommandSender sender, String[] args) {
        // Check if we have enough arguments
        if (args.length < 3) {
            sender.sendMessage("§c使用方法: /de give <玩家名> <四叶草种类> [数量]");
            sender.sendMessage("§e可用的四叶草: 1级四叶草, 2级四叶草, 3级四叶草, 4级四叶草, 5级四叶草, 6级四叶草, s级四叶草, ss级四叶草, sss级四叶草, ssr级四叶草, ex级四叶草");
            return true;
        }
        
        // Get player name
        String playerName = args[1];
        
        // Get lucky charm name from arguments
        StringBuilder charmName = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
                if (i == 2) {
                    charmName.append(args[i]);
                } else if (!args[i].matches("[0-9]+") && i > 2) {
                    charmName.append(" ");
                    charmName.append(args[i]);
                } else {
                    break; // Stop at amount
                }
            }
        
        // Get amount
        int amount = 1;
        for (int i = 2; i < args.length; i++) {
            try {
                amount = Integer.parseInt(args[i]);
                break;
            } catch (NumberFormatException e) {
                // Continue looking for amount
            }
        }
        
        // Validate amount
        if (amount < 1 || amount > 64) {
            sender.sendMessage("§c数量必须在1到64之间！");
            return true;
        }
        
        // Find target player
        Player targetPlayer = plugin.getServer().getPlayer(playerName);
        if (targetPlayer == null) {
            sender.sendMessage("§c找不到玩家: " + playerName);
            return true;
        }
        
        // Give lucky charm to player
        giveLuckyCharm(sender, targetPlayer, charmName.toString(), amount);
        return true;
    }
    
    /**
     * Add card properties to the given item
     */
    private void addCardProperties(Player player, ItemStack item, int starLevel, String quality) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage("Cannot add properties to this item!");
            return;
        }
        
        // Get or create lore
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        
        // Remove existing properties if they exist
        lore.removeIf(line -> line.contains("星级：") || line.contains("品级：") || line.contains(plugin.getConfigManager().getCardIdentifier()));
        
        // Add new properties
        lore.add("星级：" + starLevel);
        lore.add("品级：" + quality);
        lore.add(plugin.getConfigManager().getCardIdentifier());
        
        // Set the lore
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        // Update the item in player's hand
        player.getInventory().setItemInMainHand(item);
        
        player.sendMessage("Successfully added card properties to item!");
        player.sendMessage("Star Level: " + starLevel + ", Quality: " + quality);
    }
    
    /**
     * Give a lucky charm item to the player
     */
    private void giveLuckyCharm(CommandSender sender, Player targetPlayer, String charmName, int amount) {
        // Get lucky charm configurations from config
        java.util.Set<String> charmKeys = plugin.getConfig().getConfigurationSection("lucky-charms").getKeys(false);
        
        // Find the lucky charm by name
        org.bukkit.configuration.ConfigurationSection charmConfig = null;
        for (String key : charmKeys) {
            org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("lucky-charms." + key);
            if (section != null && section.getString("name").equals(charmName)) {
                charmConfig = section;
                break;
            }
        }
        
        if (charmConfig == null) {
            sender.sendMessage("§c无效的四叶草名称! 可用的四叶草:");
            sender.sendMessage("§e1级四叶草, 2级四叶草, 3级四叶草, 4级四叶草, 5级四叶草, 6级四叶草, s级四叶草, ss级四叶草, sss级四叶草, ssr级四叶草, ex级四叶草");
            return;
        }
        
        // Create the lucky charm item
        Material material = Material.valueOf(charmConfig.getString("material", "PINK_TULIP"));
        ItemStack charmItem = new ItemStack(material, amount);
        
        ItemMeta meta = charmItem.getItemMeta();
        if (meta == null) {
            sender.sendMessage("§c无法创建四叶草物品!");
            return;
        }
        
        // Set display name
        meta.setDisplayName(charmConfig.getString("name", "四叶草"));
        
        // Set lore
        List<String> lore = charmConfig.getStringList("lore");
        meta.setLore(lore);
        
        // Set the meta
        charmItem.setItemMeta(meta);
        
        // Give the item to the player
        Map<Integer, ItemStack> leftovers = targetPlayer.getInventory().addItem(charmItem);
        
        // Check for leftovers
        if (leftovers.isEmpty()) {
            sender.sendMessage("§6成功给玩家 §e" + targetPlayer.getName() + " §6发送了 §e" + amount + " §6个 §e" + charmName);
            targetPlayer.sendMessage("§6你收到了 §e" + amount + " §6个 §e" + charmName);
        } else {
            int givenAmount = amount - leftovers.get(0).getAmount();
            sender.sendMessage("§6给玩家 §e" + targetPlayer.getName() + " §6发送了 §e" + givenAmount + " §6个 §e" + charmName + " §c(背包已满，剩余 §e" + leftovers.get(0).getAmount() + " §c个未放入)");
            targetPlayer.sendMessage("§6你收到了 §e" + givenAmount + " §6个 §e" + charmName + " §c(背包已满，剩余 §e" + leftovers.get(0).getAmount() + " §c个未放入)");
        }
    }
}