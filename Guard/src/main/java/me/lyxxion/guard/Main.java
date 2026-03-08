package me.lyxxion.guard;

import cn.nukkit.Player;
import cn.nukkit.block.BlockID;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityArmorStand;
import cn.nukkit.entity.item.EntityPainting;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.block.ItemFrameUseEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.event.player.PlayerFoodLevelChangeEvent;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.form.response.CustomResponse;
import cn.nukkit.form.window.CustomForm;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class Main extends PluginBase implements Listener {

    private Config protections;
    private Config exemptBlocks;
    private final Map<String, Boolean> bypass = new HashMap<>();
    private final Map<String, Boolean> exemptMode = new HashMap<>();
    private final Map<String, Boolean> debugPlayers = new HashMap<>();

    @Override
    public void onEnable() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            getLogger().warning("Could not create data folder: " + dataFolder.getAbsolutePath());
        }
        this.protections = new Config(new File(dataFolder, "guard.yml"), Config.YAML);
        this.exemptBlocks = new Config(new File(dataFolder, "guard_exceptions.yml"), Config.YAML);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info(TextFormat.GREEN + "Guard Active - Author: Lyxxion");
    }

    private String worldName(Player player) {
        return player.getLevel().getName();
    }

    private String posKey(int x, int y, int z, String world) {
        return x + ":" + y + ":" + z + ":" + world;
    }

    private boolean getWorldSetting(String world, String key) {
        return protections.getBoolean(world + "." + key, false);
    }

    private void setWorldSetting(String world, String key, boolean value) {
        protections.set(world + "." + key, value);
    }

    private void debug(Player player, String message) {
        if (player != null && debugPlayers.containsKey(player.getName())) {
            player.sendMessage(TextFormat.GRAY + "[GuardDebug] " + message);
        }
    }

    private void openGuardMenu(Player player) {
        String world = worldName(player);
        CustomForm form = new CustomForm("Guard Settings: " + world)
                .addToggle("Block Breaking", getWorldSetting(world, "break"))
                .addToggle("Block Placing", getWorldSetting(world, "place"))
                .addToggle("Interaction", getWorldSetting(world, "interact"))
                .addToggle("Farm Decay Protection", getWorldSetting(world, "farmdecay"))
                .addToggle("PvP", getWorldSetting(world, "pvp"))
                .addToggle("Item Dropping", getWorldSetting(world, "drop"))
                .addToggle("Fall Damage", getWorldSetting(world, "fall"))
                .addToggle("Hunger", getWorldSetting(world, "hunger"))
                .addToggle("Ender Pearls", getWorldSetting(world, "pearl"))
                .submitButton("Save");
        form.onSubmit((p, response) -> handleMenuSubmit(p, world, response));
        form.send(player);
    }

    private void handleMenuSubmit(Player player, String world, CustomResponse response) {
        if (response == null) {
            return;
        }
        boolean br = response.getToggleResponse(0);
        boolean pl = response.getToggleResponse(1);
        boolean in = response.getToggleResponse(2);
        boolean fd = response.getToggleResponse(3);
        boolean pv = response.getToggleResponse(4);
        boolean dr = response.getToggleResponse(5);
        boolean fa = response.getToggleResponse(6);
        boolean hu = response.getToggleResponse(7);
        boolean pe = response.getToggleResponse(8);
        setWorldSetting(world, "break", br);
        setWorldSetting(world, "place", pl);
        setWorldSetting(world, "interact", in);
        setWorldSetting(world, "farmdecay", fd);
        setWorldSetting(world, "pvp", pv);
        setWorldSetting(world, "drop", dr);
        setWorldSetting(world, "fall", fa);
        setWorldSetting(world, "hunger", hu);
        setWorldSetting(world, "pearl", pe);
        protections.save();
        player.sendMessage(TextFormat.GREEN + "Guard settings saved!");
        debug(player, "save world=" + world + " break=" + br + " place=" + pl + " interact=" + in + " farmdecay=" + fd);
    }

    private boolean checkProtection(Player player, String type) {
        if (player == null) {
            return false;
        }
        if (bypass.containsKey(player.getName())) {
            debug(player, "check " + type + " bypass true");
            return false;
        }
        String world = worldName(player);
        boolean value = getWorldSetting(world, type);
        debug(player, "check " + type + " world=" + world + " value=" + value);
        return value;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (checkProtection(player, "break")) {
            event.setCancelled();
            debug(player, "block break cancelled");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (checkProtection(player, "place")) {
            event.setCancelled();
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getBlock() == null) {
            return;
        }
        String key = posKey(event.getBlock().getFloorX(), event.getBlock().getFloorY(), event.getBlock().getFloorZ(), worldName(player));
        if (exemptMode.containsKey(player.getName())) {
            exemptBlocks.set(key, true);
            exemptBlocks.save();
            exemptMode.remove(player.getName());
            player.sendMessage(TextFormat.GREEN + "The next block is excluded from protection!");
            event.setCancelled();
            return;
        }
        if (checkProtection(player, "farmdecay") && event.getAction() == PlayerInteractEvent.Action.PHYSICAL && BlockID.FARMLAND.equals(event.getBlock().getId())) {
            event.setCancelled();
            debug(player, "farm decay prevented");
            return;
        }
        if (checkProtection(player, "interact")) {
            if (!exemptBlocks.exists(key) && event.getAction() == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled();
            }
        }
        Item hand = event.getItem();
        if (hand != null && hand.getId() == ItemID.ENDER_PEARL && checkProtection(player, "pearl")) {
            event.setCancelled();
        }
        if (checkProtection(player, "break") && event.getAction() == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            event.setCancelled();
            debug(player, "left-click break blocked");
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (checkProtection(event.getPlayer(), "drop")) {
            event.setCancelled();
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player victim
                && event.getCause() == EntityDamageEvent.DamageCause.FALL
                && checkProtection(victim, "fall")) {
            event.setCancelled();
            return;
        }
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            return;
        }
        if (!(byEntity.getDamager() instanceof Player attacker)) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof Player && checkProtection(attacker, "pvp")) {
            event.setCancelled();
            return;
        }
        if ((victim instanceof EntityArmorStand || victim instanceof EntityPainting)
                && (checkProtection(attacker, "interact") || checkProtection(attacker, "break"))) {
            event.setCancelled();
        }
    }

    @EventHandler
    public void onFrameUse(ItemFrameUseEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == ItemFrameUseEvent.Action.DROP
                && (checkProtection(player, "interact") || checkProtection(player, "break"))) {
            event.setCancelled();
        }
    }

    @EventHandler
    public void onFood(PlayerFoodLevelChangeEvent event) {
        if (checkProtection(event.getPlayer(), "hunger")) {
            event.setCancelled();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("guard")) {
            return false;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextFormat.RED + "This command can only be used in-game.");
            return true;
        }
        if (!player.isOp() && !player.hasPermission("guard.perm")) {
            player.sendMessage(TextFormat.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            openGuardMenu(player);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "debug" -> {
                if (debugPlayers.containsKey(player.getName())) {
                    debugPlayers.remove(player.getName());
                    player.sendMessage(TextFormat.RED + "Guard Debug disabled.");
                } else {
                    debugPlayers.put(player.getName(), true);
                    player.sendMessage(TextFormat.GREEN + "Guard Debug enabled.");
                }
                return true;
            }
            case "bypass" -> {
                if (bypass.containsKey(player.getName())) {
                    bypass.remove(player.getName());
                    player.sendMessage(TextFormat.RED + "Guard Bypass disabled.");
                } else {
                    bypass.put(player.getName(), true);
                    player.sendMessage(TextFormat.GREEN + "Guard Bypass enabled.");
                }
                return true;
            }
            case "next" -> {
                exemptMode.put(player.getName(), true);
                player.sendMessage(TextFormat.YELLOW + "Right-click the block you want to exclude from protection!");
                return true;
            }
            default -> {
                player.sendMessage(TextFormat.YELLOW + "Usage: /guard [menu|debug|bypass|next]");
                return true;
            }
        }
    }
}
