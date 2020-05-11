package me.kyrxstudios.proofofkill;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("ALL")
public class ProofOfKill extends JavaPlugin implements Listener {
    FileConfiguration config = getConfig();

    Calendar cal = Calendar.getInstance();

    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

    public static Economy econ = null;

    public static Permission perms = null;

    public static Chat chat = null;

    private boolean economySetup;

    public void onEnable() {
        if (setupEconomy()) {
            this.economySetup = true;
            setupEconPermissions();
            setupEconChat();
        }
        this.config.addDefault("ItemName", "&4Proof of %player%'s death");
        List<String> loreList = new ArrayList<>();
        loreList.add("&fAt %time%, &4%player%");
        loreList.add("&fwas killed by &4%killer%");
        this.config.addDefault("Lore", loreList);
        this.config.addDefault("Economy.UseEconomy", Boolean.valueOf(true));
        this.config.addDefault("Economy.BalancePercentage", Double.valueOf(0.05D));
        this.config.addDefault("Economy.RedeemMessage", "&2You have redeemed a Proof of Kill Certificate for %money%");
        this.config.options().copyDefaults(true);
        saveConfig();
        Bukkit.getPluginManager().registerEvents(this, (Plugin)this);
    }

    public void onDisable() {
        getLogger().log(Level.INFO, "Disabled ProofOfKill");
    }

    public boolean onCommand(CommandSender sender, Command command, String commandlabel, String[] args) {
        Player player = (Player) sender;
        if(args.length > 0){
            if (command.getName().equalsIgnoreCase("proofofkill")) {
                if (args[0].equals("reload")) {
                    reloadConfig();
                    this.config = getConfig();
                    sender.sendMessage("ProofOfKill config has been reloaded!");
                }
                return true;
            }
        }else{
            player.sendMessage("[reload]");
        }

        return false;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getEntity();
        Player player1 = player.getKiller();
        if (player1 instanceof Player) {
            Player klr = player1;
            String itemName = this.config.getString("ItemName");
            List<String> lores = this.config.getStringList("Lore");
            List<String> coloredLores = new ArrayList<>();
            for (String lore : lores) {
                lore = replacePlaceHolders(lore, player, klr);
                lore = colorize(lore);
                coloredLores.add(lore);
            }
            if (this.economySetup) {
                double playerBalance = econ.getBalance((OfflinePlayer)player);
                double percentage = this.config.getDouble("Economy.BalancePercentage");
                double takeaway = Math.round(playerBalance * percentage);
                EconomyResponse response = econ.withdrawPlayer((OfflinePlayer)player, takeaway);
                if (response.transactionSuccess()) {
                    player.sendMessage("You were killed in battle and lost " + takeaway);
                } else {
                    player.sendMessage("An error occurred in the economy system");
                }
                coloredLores.add(ChatColor.DARK_GREEN + "$" + takeaway);
            }
            itemName = replacePlaceHolders(itemName, player, klr);
            ItemStack deathItem = new ItemStack(Material.PAPER, 1);
            ItemMeta itemMeta = deathItem.getItemMeta();
            itemMeta.setDisplayName(colorize(itemName));
            itemMeta.setLore(coloredLores);
            deathItem.setItemMeta(itemMeta);
            klr.getInventory().addItem(new ItemStack[] { deathItem });
        }
    }

    private final Pattern MONEY_PATTERN = Pattern.compile("((([1-9]\\d{0,2}(,\\d{3})*)|(([1-9]\\d*)?\\d))(\\.?\\d?\\d?)?$)");

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (this.economySetup) {
            Player player = e.getPlayer();
            if (player.getInventory().getItemInMainHand().getType() == Material.PAPER) {
                ItemMeta meta = player.getInventory().getItemInMainHand().getItemMeta();
                List<String> lore = meta.getLore();
                for (String str : lore) {
                    Matcher matcher = this.MONEY_PATTERN.matcher(str);
                    if (matcher.find()) {
                        String moneyFound = matcher.group(1);
                        Double money = Double.valueOf(Double.parseDouble(moneyFound.replaceAll(",", "")));
                        EconomyResponse r = econ.depositPlayer((OfflinePlayer)player, money.doubleValue());
                        if (r.transactionSuccess()) {
                            String successMessage = this.config.getString("Economy.RedeemMessage");
                            successMessage = successMessage.replaceAll("%money%", String.valueOf(money));
                            successMessage = colorize(successMessage);
                            player.sendMessage(successMessage);
                            ItemStack item = player.getInventory().getItemInMainHand();
                            player.getInventory().removeItem(new ItemStack[] { item });
                            continue;
                        }
                        player.sendMessage("Transaction failed. Contact an administrator.");
                    }
                }
            }
        }
    }

    private String replacePlaceHolders(String str, Player player, Player killer) {
        str = str.replaceAll("%player%", player.getName());
        str = str.replaceAll("%time%", this.sdf.format(this.cal.getTime()));
        str = str.replace("%killer%", killer.getName());
        return str;
    }

    private String colorize(String msg) {
        msg = ChatColor.translateAlternateColorCodes('&', msg);
        return msg;
    }

    private boolean setupEconomy() {
        if (this.config.getBoolean("Economy.UseEconomy")) {
            getLogger().log(Level.INFO, "Economy option enabled checking for vault");
            if (getServer().getPluginManager().getPlugin("Vault") == null)
                return false;
            RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null)
                return false;
            econ = (Economy)rsp.getProvider();
            return (econ != null);
        }
        return false;
    }

    private boolean setupEconPermissions() {
        RegisteredServiceProvider<Permission> rsp = getServer().getServicesManager().getRegistration(Permission.class);
        perms = (Permission)rsp.getProvider();
        return (perms != null);
    }

    private boolean setupEconChat() {
        RegisteredServiceProvider<Chat> rsp = getServer().getServicesManager().getRegistration(Chat.class);
        chat = (Chat)rsp.getProvider();
        return (chat != null);
    }
}