package com.hcw;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GUIManager implements Listener {
    
    private final CardEnhancementPlugin plugin;
    private Economy economy;
    private boolean playerPointsEnabled = false;
    // 跟踪每个GUI的保险金启用状态
    private Map<Inventory, Boolean> insuranceEnabledMap = new HashMap<>();
    
    public GUIManager() {
        this.plugin = CardEnhancementPlugin.getInstance();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        // Setup Vault economy
        setupEconomy();
        // Check for PlayerPoints
        checkPlayerPoints();
    }
    
    /**
     * Setup Vault economy service
     */
    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("Vault not found! Economy features will be disabled.");
            return;
        }
        
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No Economy service found! Economy features will be disabled.");
            return;
        }
        
        economy = rsp.getProvider();
        plugin.getLogger().info("Vault economy service found: " + economy.getName());
    }
    
    /**
     * Check if PlayerPoints plugin is available
     */
    private void checkPlayerPoints() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") != null) {
            playerPointsEnabled = true;
            plugin.getLogger().info("PlayerPoints plugin found! Alternative economy features enabled.");
        }
    }
    
    /**
     * Check if player has enough points
     */
    private boolean hasPlayerPoints(Player player, int amount) {
        if (!playerPointsEnabled) {
            return false;
        }
        
        try {
            // Directly use the PlayerPoints class instance from Bukkit
            Object playerPointsInstance = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (playerPointsInstance == null) {
                plugin.getLogger().warning("PlayerPoints instance is null!");
                return false;
            }
            
            plugin.getLogger().info("PlayerPoints instance found: " + playerPointsInstance.getClass().getName());
            
            // Try to get the API instance using the plugin instance directly
            // This approach avoids the static method call issue
            Object api = playerPointsInstance;
            
            // Try to call look method on the plugin instance itself
            try {
                // First try: look method on plugin instance
                Object balanceObj = api.getClass().getMethod("look", UUID.class).invoke(api, player.getUniqueId());
                
                // Handle both int and long return types
                long balance = 0;
                if (balanceObj instanceof Integer) {
                    balance = (Integer) balanceObj;
                } else if (balanceObj instanceof Long) {
                    balance = (Long) balanceObj;
                }
                
                plugin.getLogger().info("Player " + player.getName() + " has " + balance + " points, needs " + amount);
                return balance >= amount;
            } catch (NoSuchMethodException e) {
                // Second try: getAPI method might be an instance method
                plugin.getLogger().info("Trying instance getAPI() method...");
                Object apiInstance = api.getClass().getMethod("getAPI").invoke(api);
                Object balanceObj = apiInstance.getClass().getMethod("look", UUID.class).invoke(apiInstance, player.getUniqueId());
                
                long balance = 0;
                if (balanceObj instanceof Integer) {
                    balance = (Integer) balanceObj;
                } else if (balanceObj instanceof Long) {
                    balance = (Long) balanceObj;
                }
                
                plugin.getLogger().info("Player " + player.getName() + " has " + balance + " points, needs " + amount);
                return balance >= amount;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking PlayerPoints balance: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace(); // Print full stack trace for debugging
            return false;
        }
    }
    
    /**
     * Deduct points from player using PlayerPoints
     */
    private boolean deductPlayerPoints(Player player, int amount) {
        if (!playerPointsEnabled) {
            return false;
        }
        
        try {
            // Get PlayerPoints plugin instance
            Object playerPointsInstance = Bukkit.getPluginManager().getPlugin("PlayerPoints");
            if (playerPointsInstance == null) {
                plugin.getLogger().warning("PlayerPoints instance is null!");
                return false;
            }
            
            // Use the same flexible approach as hasPlayerPoints
            Object api = playerPointsInstance;
            
            try {
                // First try: take method on plugin instance
                return (boolean) api.getClass().getMethod("take", UUID.class, int.class).invoke(api, player.getUniqueId(), amount);
            } catch (NoSuchMethodException e) {
                // Second try: getAPI method as instance method
                plugin.getLogger().info("Trying instance getAPI() method for take...");
                Object apiInstance = api.getClass().getMethod("getAPI").invoke(api);
                return (boolean) apiInstance.getClass().getMethod("take", UUID.class, int.class).invoke(apiInstance, player.getUniqueId(), amount);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error deducting PlayerPoints: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get Economy service
     */
    public Economy getEconomy() {
        return economy;
    }
    
    public void openEnhancementGUI(Player player) {
        // Create GUI with title from config
        String guiTitle = plugin.getConfigManager().getGuiTitle();
        Inventory gui = Bukkit.createInventory(player, 54, guiTitle);
        
        // Initialize GUI items according to the exact layout specified
        initializeExactLayout(gui);
        
        // Initialize insurance status for this GUI (disabled by default)
        insuranceEnabledMap.put(gui, false);
        
        player.openInventory(gui);
    }
    
    private void initializeExactLayout(Inventory gui) {
        // Clear all slots first
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, null);
        }
        
        // Get GUI config from plugin
        GUIConfig guiConfig = plugin.getGuiConfig();
        
        // Apply items from GUI config
        for (int slot = 0; slot < 54; slot++) {
            ItemStack configItem = guiConfig.getItemForSlot(slot);
            if (configItem != null) {
                // Process placeholder values in item name
                ItemMeta meta = configItem.getItemMeta();
                String name = meta.getDisplayName();
                
                // Replace ${保险金-amount} placeholder with actual cost based on star transition
                if (name.contains("${保险金-amount}")) {
                    // Default value if no main card is present yet
                    String insuranceCost = "0D";
                    
                    // We don't have the main card yet at GUI initialization, so use a placeholder
                    // The actual insurance cost will be updated when a main card is placed
                    name = name.replace("${保险金-amount}", insuranceCost);
                    meta.setDisplayName(name);
                    configItem.setItemMeta(meta);
                }
                
                // Replace ${base-success-rate} placeholder
                if (name.contains("${base-success-rate}")) {
                    name = name.replace("${base-success-rate}", String.valueOf(plugin.getConfigManager().getBaseSuccessRate()));
                    meta.setDisplayName(name);
                    configItem.setItemMeta(meta);
                }
                
                gui.setItem(slot, configItem);
            }
        }
        
        // Initialize insurance slots with proper status display
        for (int slot = 0; slot < 54; slot++) {
            ItemStack slotItem = gui.getItem(slot);
            if (slotItem != null) {
                ItemMeta meta = slotItem.getItemMeta();
                if (meta != null && meta.hasDisplayName()) {
                    String displayName = meta.getDisplayName();
                    
                    // Check if this is an insurance slot
                    if (displayName.contains("保险金：")) {
                        // Update insurance slot display with initial status
                        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
                        
                        // Add insurance status to lore
                        lore.removeIf(line -> line.contains("保险金状态："));
                        lore.add("保险金状态：已禁用");
                        
                        // Add instruction lore
                        lore.removeIf(line -> line.contains("点击切换保险金状态"));
                        lore.add("点击切换保险金状态");
                        
                        meta.setLore(lore);
                        slotItem.setItemMeta(meta);
                        
                        // Ensure initial material is blue (disabled)
                        slotItem.setType(Material.BLUE_STAINED_GLASS_PANE);
                        
                        gui.setItem(slot, slotItem);
                    }
                }
            }
        }
        
        // Update success rate display
        updateSuccessRate(gui);
    }
    
    // Helper method to find slot with specific function
    private int findSlotByFunction(String function) {
        GUIConfig guiConfig = plugin.getGuiConfig();
        for (int slot = 0; slot < 54; slot++) {
            // Check if this slot has the specified function
            // This is a simplified check - we need to get the function from the GUIConfig
            // For now, we'll keep the existing slot mapping for core functionality
            // TODO: Implement dynamic slot mapping based on config functions
            return slot;
        }
        return -1;
    }
    
    private ItemStack createItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getWhoClicked();
        Inventory clickedInventory = event.getClickedInventory();
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        
        // Check if the clicked inventory is the GUI we created
        if (event.getView().getTitle().equals(plugin.getConfigManager().getGuiTitle())) {
            // Handle clicks on the top inventory (GUI)
            if (clickedInventory != null && clickedInventory.equals(topInventory)) {
                int slot = event.getSlot();
                GUIConfig guiConfig = plugin.getGuiConfig();
                String slotFunction = guiConfig.getFunctionForSlot(slot);
                
                // Handle enhancement button click
                if (slotFunction.equals("开始强化")) {
                    event.setCancelled(true);
                    performEnhancement(player, topInventory);
                    return;
                }
                
                // Handle insurance slot click
                if (slotFunction.equals("保险金")) {
                    event.setCancelled(true);
                    toggleInsurance(topInventory, slot);
                    return;
                }
                
                // Prevent clicking on protected slots
                if (isProtectedSlot(slot)) {
                    event.setCancelled(true);
                    return;
                }
                
                // Get the item in the clicked slot and cursor
                ItemStack clickedItem = topInventory.getItem(slot);
                ItemStack cursorItem = event.getCursor();
                
                // For item frame slots (主卡、副卡、幸运符):
                // 1. Don't allow taking out the item frame itself
                // 2. Only allow placing 1 item at a time
                if (clickedItem != null && clickedItem.getType() == Material.ITEM_FRAME) {
                    // Prevent taking the item frame itself
                    if (event.getAction() == InventoryAction.PICKUP_ALL || 
                        event.getAction() == InventoryAction.PICKUP_ONE || 
                        event.getAction() == InventoryAction.PICKUP_SOME || 
                        event.getAction() == InventoryAction.PICKUP_HALF || 
                        event.getAction() == InventoryAction.DROP_ALL_SLOT || 
                        event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                        event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                        event.setCancelled(true);
                        return;
                    }
                    
                    // Handle placing items into item frame slots - allow replacing item frames
                    if (event.getAction() == InventoryAction.PLACE_ALL || 
                        event.getAction() == InventoryAction.PLACE_ONE || 
                        event.getAction() == InventoryAction.PLACE_SOME ||
                        event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
                        
                        // Check if cursor item is valid for this slot
                        boolean canPlace = false;
                        if (slotFunction.equals("主卡") && plugin.getCardManager().isCard(cursorItem)) {
                            canPlace = true;
                        } else if (slotFunction.equals("幸运符") && plugin.getCardManager().isLuckyCharm(cursorItem)) {
                            canPlace = true;
                        } else if (slotFunction.equals("副卡") && plugin.getCardManager().isCard(cursorItem)) {
                            canPlace = true;
                        }
                        
                        if (!canPlace) {
                            event.setCancelled(true);
                            player.sendMessage("此槽位只能放入有效的" + slotFunction + "！");
                            return;
                        }
                        
                        // Only place 1 item at a time, even if player has a stack
                        ItemStack itemToPlace = cursorItem.clone();
                        itemToPlace.setAmount(1);
                        
                        // Directly set the item to the slot, replacing the item frame
                        topInventory.setItem(slot, itemToPlace);
                        
                        // Reduce cursor stack by 1
                        if (cursorItem.getAmount() > 1) {
                            cursorItem.setAmount(cursorItem.getAmount() - 1);
                        } else {
                            event.setCursor(null);
                        }
                        
                        event.setCancelled(true);
                        
                        // Update success rate
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            updateSuccessRate(topInventory);
                        }, 1L);
                        return;
                    }
                }
                
                // For slots that already have player-placed items (not item frames)
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    // Allow taking items out normally
                    if (event.getAction() == InventoryAction.PICKUP_ALL || 
                        event.getAction() == InventoryAction.PICKUP_ONE || 
                        event.getAction() == InventoryAction.DROP_ALL_SLOT || 
                        event.getAction() == InventoryAction.DROP_ONE_SLOT ||
                        event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                        // Update success rate after item is taken
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            updateSuccessRate(topInventory);
                        }, 1L);
                        return;
                    }
                    
                    // Prevent placing more items into a filled slot
                    if (event.getAction() == InventoryAction.PLACE_ALL || 
                        event.getAction() == InventoryAction.PLACE_ONE || 
                        event.getAction() == InventoryAction.PLACE_SOME ||
                        event.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
                        event.setCancelled(true);
                        player.sendMessage("此槽位已放入物品，无法再放入其他物品！");
                        return;
                    }
                }
                
                // Handle placing items into empty slots (not item frames)
                if (clickedItem == null || clickedItem.getType() == Material.AIR) {
                    // Check if slot allows item placement
                    if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                        boolean canPlace = false;
                        if (slotFunction.equals("主卡") && plugin.getCardManager().isCard(cursorItem)) {
                            canPlace = true;
                        } else if (slotFunction.equals("幸运符") && plugin.getCardManager().isLuckyCharm(cursorItem)) {
                            canPlace = true;
                        } else if (slotFunction.equals("副卡") && plugin.getCardManager().isCard(cursorItem)) {
                            canPlace = true;
                        }
                        
                        if (canPlace) {
                            // Only place 1 item at a time
                            ItemStack itemToPlace = cursorItem.clone();
                            itemToPlace.setAmount(1);
                            topInventory.setItem(slot, itemToPlace);
                            
                            // Reduce cursor stack by 1
                            cursorItem.setAmount(cursorItem.getAmount() - 1);
                            
                            event.setCancelled(true);
                            
                            // Update success rate
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                updateSuccessRate(topInventory);
                            }, 1L);
                            return;
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // Check if the dragged inventory is our GUI
        if (!event.getView().getTitle().equals(plugin.getConfigManager().getGuiTitle())) {
            return;
        }
        
        Inventory topInventory = event.getView().getTopInventory();
        
        // Check if any of the dragged slots are in our GUI
        for (int slot : event.getRawSlots()) {
            if (slot < topInventory.getSize()) {
                // This is a slot in our GUI
                event.setCancelled(true);
                return;
            }
        }
    }
    
    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        // Prevent items from being moved into/out of our GUI by hoppers, etc.
        // Check if destination has viewers (meaning it's a player GUI)
        if (event.getDestination().getViewers().size() > 0) {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        // Update success rate when GUI is opened
        if (event.getView().getTitle().equals(plugin.getConfigManager().getGuiTitle())) {
            updateSuccessRate(event.getInventory());
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getPlayer();
        Inventory inventory = event.getInventory();
        
        // Check if the closed inventory is our GUI
        if (!event.getView().getTitle().equals(plugin.getConfigManager().getGuiTitle())) {
            return;
        }
        
        // Return items to player - only return items that players placed, not GUI items
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                // Check if this is a GUI item
                if (!isGUIItem(item)) {
                    // Add item to player's inventory
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                    // Drop any items that can't fit in inventory
                    for (ItemStack leftover : leftovers.values()) {
                        player.getWorld().dropItem(player.getLocation(), leftover);
                    }
                    // Clear the slot
                    inventory.setItem(i, null);
                }
            }
        }
        
        // Clean up insurance status mapping to prevent memory leaks
        insuranceEnabledMap.remove(inventory);
    }
    
    // Helper method to find all slots with specific function
    private List<Integer> findSlotsByFunction(String function) {
        List<Integer> slots = new ArrayList<>();
        GUIConfig guiConfig = plugin.getGuiConfig();
        for (int slot = 0; slot < 54; slot++) {
            String slotFunction = guiConfig.getFunctionForSlot(slot);
            if (slotFunction.equals(function)) {
                slots.add(slot);
            }
        }
        return slots;
    }
    
    private void performEnhancement(Player player, Inventory gui) {
        if (player == null || gui == null) {
            return;
        }
        
        // Get items from GUI based on functions
        List<Integer> mainCardSlots = findSlotsByFunction("主卡");
        List<Integer> luckyCharmSlots = findSlotsByFunction("幸运符");
        List<Integer> subCardSlots = findSlotsByFunction("副卡");
        
        ItemStack mainCard = null;
        if (!mainCardSlots.isEmpty()) {
            mainCard = gui.getItem(mainCardSlots.get(0));
        }
        
        ItemStack luckyCharm = null;
        if (!luckyCharmSlots.isEmpty()) {
            luckyCharm = gui.getItem(luckyCharmSlots.get(0));
        }
        
        ItemStack[] subCards = new ItemStack[subCardSlots.size()];
        for (int i = 0; i < subCardSlots.size(); i++) {
            subCards[i] = gui.getItem(subCardSlots.get(i));
        }
        
        // Check if main card is present
        if (mainCard == null || !plugin.getCardManager().isCard(mainCard)) {
            player.sendMessage("请在指定槽位放入主卡！");
            return;
        }
        
        // Check if there's at least one sub card
        boolean hasSubCard = false;
        for (ItemStack subCard : subCards) {
            if (subCard != null && plugin.getCardManager().isCard(subCard)) {
                hasSubCard = true;
                break;
            }
        }
        
        if (!hasSubCard) {
            player.sendMessage("请至少放入一张副卡！");
            return;
        }
        
        // Check if lucky charm is valid (if present)
        if (luckyCharm != null && !plugin.getCardManager().isLuckyCharm(luckyCharm)) {
            player.sendMessage("幸运符槽位只能放入四叶草！");
            return;
        }
        
        // Calculate success rate
        double successRate = plugin.getCardManager().calculateSuccessRate(mainCard, luckyCharm, subCards);
        
        // Check if enhancement is possible
        if (successRate == 0.0) {
            player.sendMessage("无法进行强化，可能是主卡已达到最高星级！");
            return;
        }
        
        // Check if main card is already at max star level
        int currentStar = plugin.getCardManager().getCardStar(mainCard);
        if (currentStar >= 15) {
            player.sendMessage("主卡已达到最高星级15星！");
            return;
        }
        
        // Get current star level before enhancement
        int oldStar = plugin.getCardManager().getCardStar(mainCard);
        
        // Check insurance status
        boolean insuranceEnabled = insuranceEnabledMap.getOrDefault(gui, false);
        String insuranceCostStr = plugin.getConfigManager().getNextLevelInsuranceCost(currentStar);
        
        // Parse insurance cost (supports D for points, G for money)
        ConfigManager.InsuranceCost insuranceCost = plugin.getConfigManager().parseInsuranceCost(insuranceCostStr);
        double costAmount = insuranceCost.getAmount();
        String costType = insuranceCost.getType();
        
        // Check if player has enough balance if insurance is enabled
        if (insuranceEnabled) {
            if (costType.equals("money")) {
                // Using money (Vault economy)
                if (economy == null) {
                    player.sendMessage("Vault economy not available! Insurance feature is disabled.");
                    return;
                }
                
                if (!economy.has(player, costAmount)) {
                    player.sendMessage("你的金币不足，无法启用保险金！需要: " + insuranceCostStr);
                    return;
                }
            } else {
                // Using points (Vault or PlayerPoints)
                if (economy == null && !playerPointsEnabled) {
                    player.sendMessage("Vault economy and PlayerPoints not available! Insurance feature is disabled.");
                    return;
                }
                
                // Check balance using Vault or PlayerPoints
                boolean hasEnough = false;
                if (economy != null) {
                    hasEnough = economy.has(player, costAmount);
                } else if (playerPointsEnabled) {
                    hasEnough = hasPlayerPoints(player, (int) costAmount);
                }
                
                if (!hasEnough) {
                    player.sendMessage("你的点券不足，无法启用保险金！需要: " + insuranceCostStr);
                    return;
                }
            }
        }
        
        // Perform enhancement
        boolean success = plugin.getCardManager().enhanceCard(mainCard, luckyCharm, subCards, insuranceEnabled, player);
        
        if (success) {
            player.sendMessage(plugin.getConfigManager().getSuccessMessage());
            
            // Get new star level after enhancement
            int newStar = plugin.getCardManager().getCardStar(mainCard);
            
            // Check if it's a high star achievement (8+ stars)
            if (newStar >= 8 && newStar <= 16 && newStar > oldStar) {
                // Get card name from item display name
                String cardName = mainCard.getItemMeta() != null && mainCard.getItemMeta().hasDisplayName() 
                    ? mainCard.getItemMeta().getDisplayName() 
                    : "神秘卡牌";
                
                // Get player name
                String playerName = player.getName();
                
                // Get broadcast message template from config
                String broadcastTemplate = plugin.getConfigManager().getBroadcastMessage(newStar);
                
                // Replace placeholders if template exists
                if (!broadcastTemplate.isEmpty()) {
                    String broadcastMessage = broadcastTemplate
                        .replace("%card%", "§6" + cardName + "§e")
                        .replace("%player%", "§e" + playerName + "§e")
                        .replace("8星", "§68星§e")
                        .replace("9星", "§69星§e")
                        .replace("10星", "§610星§e")
                        .replace("11星", "§611星§e")
                        .replace("12星", "§612星§e")
                        .replace("13星", "§613星§e")
                        .replace("14星", "§614星§e")
                        .replace("15星", "§615星§e")
                        .replace("16星", "§616星§e");
                    
                    // Broadcast to all players
                    Bukkit.broadcastMessage(broadcastMessage);
                }
            }
        } else {
            player.sendMessage(plugin.getConfigManager().getFailureMessage());
            
            // If insurance is enabled, deduct insurance cost but keep main and sub cards
            if (insuranceEnabled) {
                if (costType.equals("money")) {
                    // Using money (Vault economy)
                    if (economy != null && costAmount > 0) {
                        economy.withdrawPlayer(player, costAmount);
                        player.sendMessage("已扣除保险金: " + insuranceCostStr);
                    }
                } else {
                    // Using points (Vault or PlayerPoints)
                    // Deduct insurance cost from player using Vault or PlayerPoints
                    boolean deductionSuccess = false;
                    if (economy != null) {
                        economy.withdrawPlayer(player, costAmount);
                        deductionSuccess = true;
                    } else if (playerPointsEnabled) {
                        deductionSuccess = deductPlayerPoints(player, (int) costAmount);
                    }
                    
                    if (deductionSuccess) {
                        player.sendMessage("已扣除保险金: " + insuranceCostStr);
                    } else {
                        player.sendMessage("扣除保险金失败！");
                    }
                }
            }
        }
        
        // Remove used items based on insurance status
        if (success || !insuranceEnabled) {
            // On success or insurance disabled, remove all used items
            for (int slot : subCardSlots) {
                gui.setItem(slot, null);
            }
            if (luckyCharm != null) {
                for (int slot : luckyCharmSlots) {
                    gui.setItem(slot, null);
                }
            }
        } else {
            // On failure with insurance enabled, only remove lucky charm
            if (luckyCharm != null) {
                for (int slot : luckyCharmSlots) {
                    gui.setItem(slot, null);
                }
            }
            // Keep main card and sub cards
        }
        
        // Update GUI
        updateSuccessRate(gui);
    }
    
    private void updateSuccessRate(Inventory gui) {
        // Get items from GUI based on functions
        List<Integer> mainCardSlots = findSlotsByFunction("主卡");
        List<Integer> luckyCharmSlots = findSlotsByFunction("幸运符");
        List<Integer> subCardSlots = findSlotsByFunction("副卡");
        
        ItemStack mainCard = null;
        if (!mainCardSlots.isEmpty()) {
            mainCard = gui.getItem(mainCardSlots.get(0));
        }
        
        ItemStack luckyCharm = null;
        if (!luckyCharmSlots.isEmpty()) {
            luckyCharm = gui.getItem(luckyCharmSlots.get(0));
        }
        
        ItemStack[] subCards = new ItemStack[subCardSlots.size()];
        for (int i = 0; i < subCardSlots.size(); i++) {
            subCards[i] = gui.getItem(subCardSlots.get(i));
        }
        
        // Get player from inventory viewers
        Player player = null;
        if (!gui.getViewers().isEmpty() && gui.getViewers().get(0) instanceof Player) {
            player = (Player) gui.getViewers().get(0);
        }
        
        plugin.getCardManager().updateSuccessRate(gui, mainCard, luckyCharm, subCards, player);
    }
    
    private boolean isProtectedSlot(int slot) {
        GUIConfig guiConfig = plugin.getGuiConfig();
        String slotFunction = guiConfig.getFunctionForSlot(slot);
        
        // Protected functions
        List<String> protectedFunctions = Arrays.asList(
            "none", // 分隔符
            "无", // 分隔符
            "成功率", // 成功率
            "开始强化" // 强化按钮
        );
        
        return protectedFunctions.contains(slotFunction);
    }
    
    private void toggleInsurance(Inventory gui, int slot) {
        // Get the current item in the insurance slot
        ItemStack currentItem = gui.getItem(slot);
        if (currentItem == null) {
            return;
        }
        
        // Toggle insurance status
        boolean currentStatus = insuranceEnabledMap.getOrDefault(gui, false);
        boolean newStatus = !currentStatus;
        insuranceEnabledMap.put(gui, newStatus);
        
        // Get the item meta to update name and lore
        ItemMeta meta = currentItem.getItemMeta();
        if (meta == null) {
            return;
        }
        
        // Update lore to show current insurance status
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        
        // Update lore to show current insurance status
        lore.removeIf(line -> line.contains("保险金状态："));
        lore.add("保险金状态：" + (newStatus ? "已启用" : "已禁用"));
        
        // Add instruction lore if not present
        lore.removeIf(line -> line.contains("点击切换保险金状态"));
        lore.add("点击切换保险金状态");
        
        meta.setLore(lore);
        currentItem.setItemMeta(meta);
        
        // Toggle material based on status (blue for disabled, green for enabled)
        if (newStatus) {
            currentItem.setType(Material.GREEN_STAINED_GLASS_PANE);
        } else {
            currentItem.setType(Material.BLUE_STAINED_GLASS_PANE);
        }
        
        gui.setItem(slot, currentItem);
    }
    
    private boolean isGUIItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        // Check if it's a dynamic GUI item
        if (meta.hasDisplayName()) {
            String displayName = meta.getDisplayName();
            if (displayName.equals("分隔符") || displayName.equals("祝你好运") || 
                displayName.startsWith("保险金：") || displayName.startsWith("成功率：") ||
                displayName.equals("请放入主卡") || displayName.equals("请放入幸运符") ||
                displayName.equals("请放入副卡") || displayName.equals("点击强化")) {
                return true;
            }
        }
        
        // Check if it's a layout item from config
        // Compare with GUI items by material and name/lore
        Map<Integer, GUIConfig.GUIItem> guiItems = plugin.getGuiConfig().getGuiItems();
        for (GUIConfig.GUIItem guiItem : guiItems.values()) {
            if (item.getType() == guiItem.material) {
                // Check name match
                boolean nameMatch = true;
                if (meta.hasDisplayName() && guiItem.name != null && !guiItem.name.isEmpty()) {
                    nameMatch = meta.getDisplayName().equals(guiItem.name);
                } else if (!meta.hasDisplayName() && guiItem.name != null && !guiItem.name.isEmpty()) {
                    nameMatch = false;
                }
                
                // Check lore match
                boolean loreMatch = true;
                if (meta.hasLore() && guiItem.lore != null && !guiItem.lore.isEmpty()) {
                    loreMatch = meta.getLore().equals(guiItem.lore);
                } else if (!meta.hasLore() && guiItem.lore != null && !guiItem.lore.isEmpty()) {
                    loreMatch = false;
                }
                
                if (nameMatch && loreMatch) {
                    return true;
                }
            }
        }
        
        return false;
    }
}