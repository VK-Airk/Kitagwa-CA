/* SPDX-License-Identifier: GPL-3.0-only
 * Standalone Java adaptation of Synnerz/devonian's PurplePadTimer (1.34.9).
 */
package dev.purplepad;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.nio.file.Path;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class PurplePadClient implements ClientModInitializer {
    private static final Logger LOG = LoggerFactory.getLogger("purple-pad-timer");
    private static final TimerState TIMER = new TimerState();
    private static final Pattern FORMATTING = Pattern.compile("\u00a7[0-9A-FK-ORa-fk-or]");
    private static final String[] LABELS = new String[TimerState.DURATION + 1];
    private static TimerConfig config = new TimerConfig();
    private static Path configPath;
    private static int previewTicks;
    private static boolean openSettings, openMain;
    static void openTimer(net.minecraft.client.gui.screens.Screen menu) {
        Minecraft.getInstance().gui.setScreen(new TimerHudEditor(menu,config.copy()));
    }

    static {
        for (int i = 0; i < LABELS.length; i++) LABELS[i] = String.format(Locale.ROOT, "%.2fs", i * 0.05);
    }

    @Override public void onInitializeClient() {
        if(KcaEdition.KUUDRA)ClientCommandRegistrationCallback.EVENT.register((dispatcher,context)->
            dispatcher.register(literal("kca").then(literal("kuudra").executes(c->KcaMenu.openKuudra()))));
        if(KcaEdition.DUNGEONS)RunStats.init();
        if(KcaEdition.CHAT){PartyChat.init();Shortcuts.load();}
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
            var root=KcaEdition.NUCLEUS?com.nucleus.client.KcaCommands.root():literal("kca");
            root.executes(c->{openMain=true;return 1;});
            if(KcaEdition.GENERAL)root.then(literal("settings").executes(c->KcaMenu.open(KcaMenu.Page.GLOBAL,null)));
            if(KcaEdition.DUNGEONS)root.then(literal("dungeons").executes(c->KcaMenu.open(KcaMenu.Page.DUNGEONS,null))
                .then(literal("pbs").executes(c->com.nucleus.client.DungeonRecords.scan(c.getSource()::sendFeedback)))
                .then(literal("best").then(argument("floor",StringArgumentType.word())
                    .executes(c->com.nucleus.client.DungeonRecords.best(StringArgumentType.getString(c,"floor"),c.getSource()::sendFeedback))
                    .then(literal("fish").executes(c->com.nucleus.client.DungeonRecords.fishBest(StringArgumentType.getString(c,"floor"),c.getSource()::sendFeedback))))));
            if(KcaEdition.NUCLEUS)root.then(literal("waypoint").executes(c -> KcaMenu.openWaypointSettings())
                .then(literal("mode").executes(c -> KcaMenu.openWaypointSettings()))
                .then(literal("rightguardian").executes(c -> placeWaypoints(c.getSource(), true)))
                .then(argument("query",StringArgumentType.greedyString()).executes(c ->
                    KcaMenu.open(KcaMenu.Page.ROOT,"waypoint " + StringArgumentType.getString(c,"query")))))
            .then(literal("set").executes(c -> KcaMenu.open(KcaMenu.Page.ROOT,"set"))
                .then(literal("waypoint").executes(c -> placeWaypoints(c.getSource(), com.nucleus.NucleusMod.CONFIG.useReferenceOffsets)))
                .then(argument("query",StringArgumentType.greedyString()).executes(c ->
                    KcaMenu.open(KcaMenu.Page.ROOT,"set " + StringArgumentType.getString(c,"query")))));
            root.then(literal("search").executes(c->KcaMenu.open(KcaMenu.Page.ROOT,"")).then(argument("setting",StringArgumentType.greedyString()).executes(c->KcaMenu.open(KcaMenu.Page.ROOT,StringArgumentType.getString(c,"setting")))))
            .then(argument("setting",StringArgumentType.greedyString()).executes(c->KcaMenu.open(KcaMenu.Page.ROOT,StringArgumentType.getString(c,"setting"))));
            if(KcaEdition.CHAT)root.then(literal("shortcuts").executes(c->{var mc=Minecraft.getInstance();mc.schedule(()->mc.gui.setScreen(KcaMenu.chatScreen(new KcaMenu(KcaMenu.Page.ROOT),true)));return 1;}))
            .then(literal("chat").executes(c->{var mc=Minecraft.getInstance();mc.schedule(()->mc.gui.setScreen(KcaMenu.chatScreen(new KcaMenu(KcaMenu.Page.ROOT),false)));return 1;}));
            root.then(literal("reload").executes(c->{try{if(KcaEdition.DUNGEONS){config=TimerConfig.load(configPath);RunStats.reload();}com.nucleus.NucleusMod.CONFIG.load();if(KcaEdition.NUCLEUS || KcaEdition.GENERAL)com.nucleus.NucleusMod.SPEEDRUN.load();if(KcaEdition.CHAT)Shortcuts.load();if(!enabled())disableRuntime();return reply(c.getSource(),"KCA settings reloaded.");}catch(Exception e){return reply(c.getSource(),"KCA reload failed: "+e.getMessage());}}));
            if(KcaEdition.HAS_HUD)root.then(literal("hud").executes(c->{com.nucleus.client.MoveHudScreen.open(null);return 1;}));
            root.then(literal("help").executes(c->reply(c.getSource(),
                "/kca search <setting> | /kca reload"
                +(KcaEdition.GENERAL?" | /kca settings":"")
                +(KcaEdition.DUNGEONS?" | /kca dungeons [pbs|best <floor>] | /purplepad help":"")
                +(KcaEdition.NUCLEUS?" | /kca nucleus [pb|times|runs|skip|count|stop|lobbyStats|safe|syncRuns] | /kca waypoint":"")
                +(KcaEdition.CHAT?" | /kca chat | /kca shortcuts":"")
                +(KcaEdition.KUUDRA?" | /kca kuudra":"")
                +(KcaEdition.HAS_HUD?" | /kca hud":""))));
            var previews=literal("test");
            if(KcaEdition.NUCLEUS)previews.then(literal("gamble").executes(test(c->{
                    var cfg=com.nucleus.NucleusMod.CONFIG;
                    if(!cfg.showGameplayHud || !cfg.jackpotEnabled)return reply(c.getSource(),"Enable Gameplay HUD and Gambling animation to see the preview.");
                    com.nucleus.client.JackpotAnimation.startPreview(cfg.jackpotPreview);
                    return 1;
                })));
            if(KcaEdition.DUNGEONS)previews.then(literal("lowest").executes(test(c->RunStats.previewReport(true))))
                .then(literal("report").executes(test(c->RunStats.previewReport(false))))
                .then(literal("timer").executes(test(PurplePadClient::previewTimer)))
                .then(literal("secrets").executes(test(c -> {
                    Minecraft.getInstance().schedule(() -> Minecraft.getInstance().gui.setScreen(new RunHudEditor(null)));
                    return 1;
                })));
            if(KcaEdition.HAS_HUD){
                previews.executes(test(c->reply(c.getSource(),"/kca test "
                    +(KcaEdition.NUCLEUS?"gamble | debug | unlock | season ":"")
                    +(KcaEdition.DUNGEONS?"timer | secrets | report | lowest":""))));
                root.then(previews);
            }
            dispatcher.register(root);
            if(KcaEdition.NUCLEUS)dispatcher.register(literal("kac").then(literal("set").then(literal("waypoint")
                .executes(c -> placeWaypoints(c.getSource(), com.nucleus.NucleusMod.CONFIG.useReferenceOffsets)))));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(openMain){openMain=false;client.gui.setScreen(new KcaMenu(KcaMenu.Page.ROOT));}
        });
        if(!KcaEdition.DUNGEONS)return;
        configPath = FabricLoader.getInstance().getConfigDir().resolve("purple-pad-timer.json");
        try { config = TimerConfig.load(configPath); }
        catch (Exception e) { LOG.warn("Could not load Purple Pad Timer config; using defaults", e); }

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { reset();LocalDungeonSplits.reset(); });
        // Only the optional preview uses client ticks. Actual timing uses server ping packets.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (previewTicks > 0) previewTicks--;
            if (openSettings) {
                openSettings = false;
                client.gui.setScreen(new TimerSettingsScreen(config.copy()));
            }
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("purple_pad_timer", "countdown"), (graphics, delta) -> {
            int ticks = TIMER.remaining();
            if (ticks == 0) ticks = previewTicks;
            Minecraft client = Minecraft.getInstance();
            if (!hudEnabled() || !config.enabled || ticks <= 0 || client.level == null || client.gui.hud.isHidden()
                    || client.gui.screen() instanceof com.nucleus.client.MoveHudScreen || client.gui.screen() instanceof GuiSettingsScreen || client.gui.screen() instanceof TimerSettingsScreen || client.gui.screen() instanceof TimerHudEditor) return;
            var bounds = TimerHudRenderer.bounds(client.font, config, graphics.guiWidth(), graphics.guiHeight());
            TimerHudRenderer.draw(graphics, client.font, config, LABELS[ticks], bounds.x(), bounds.y(), config.scale);
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
            literal("purplepad")
                .executes(c -> { openSettings = true; return 1; })
                .then(literal("gui").executes(c -> { openSettings = true; return 1; }))
                .then(literal("help").executes(c -> help(c.getSource())))
                .then(literal("simple")
                    .then(literal("on").executes(c -> { config.setStyle(HudStyle.SIMPLE); return save(c.getSource()); }))
                    .then(literal("off").executes(c -> { config.setStyle(HudStyle.FANCY); return save(c.getSource()); })))
                .then(literal("preview").executes(test(PurplePadClient::previewTimer)))
                .then(literal("on").executes(c -> { config.enabled = true; return save(c.getSource()); }))
                .then(literal("off").executes(c -> { config.enabled = false; reset(); return save(c.getSource()); }))
                .then(literal("position")
                    .then(argument("x", IntegerArgumentType.integer(-1, 10000))
                    .then(argument("y", IntegerArgumentType.integer(0, 10000)).executes(c -> {
                        config.x = IntegerArgumentType.getInteger(c, "x");
                        config.y = IntegerArgumentType.getInteger(c, "y");
                        previewTicks = TimerState.DURATION;
                        return save(c.getSource());
                    }))))
                .then(literal("scale").then(argument("value", FloatArgumentType.floatArg(0.5f, 6f)).executes(c -> {
                    config.scale = FloatArgumentType.getFloat(c, "value");
                    previewTicks = TimerState.DURATION;
                    return save(c.getSource());
                })))
                .then(literal("reload").executes(c -> {
                    try { config = TimerConfig.load(configPath); reset(); return reply(c.getSource(), "Purple Pad Timer config reloaded."); }
                    catch (Exception e) { return reply(c.getSource(), "Could not reload config: " + e.getMessage()); }
                }))
        ));
    }

    public static void onMessage(String message) {
        if(!enabled())return;
        if (message.indexOf('\u00a7') >= 0) message = FORMATTING.matcher(message).replaceAll("");
        LocalDungeonSplits.onChat(message);
        RunStats.message(message);
        if (!config.enabled) return;
        int before=TIMER.remaining();
        TIMER.onMessage(message);
        if(before==0 && TIMER.remaining()>0)com.nucleus.client.Achievements.unlockFun("kca_d_storm");
    }

    public static void onPing(int id) {
        if (enabled() && config.enabled) TIMER.onPing(id);
    }

    public static void reset() { TIMER.reset(); previewTicks = 0; }

    public static boolean enabled(){return com.nucleus.NucleusMod.CONFIG.kcaEnabled;}
    public static boolean hudEnabled() { return hudVisible(false); }
    public static boolean hudVisible(boolean preview) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() == null && client.options.keyPlayerList.isDown()) return false;
        return preview || (enabled() && com.nucleus.NucleusMod.CONFIG.showGameplayHud);
    }
    public static boolean testsEnabled(){return enabled() && com.nucleus.NucleusMod.CONFIG.testCommands;}
    public static void disableRuntime(){if(KcaEdition.DUNGEONS){reset();LocalDungeonSplits.reset();RunStats.reset();}if(KcaEdition.NUCLEUS)com.nucleus.client.NucleusClient.resetLobbyState();}
    static TimerConfig timerConfig(){return config.copy();}
    static String label(int ticks) { return LABELS[Math.clamp(ticks, 0, TimerState.DURATION)]; }

    static void applySettings(TimerConfig next) throws java.io.IOException {
        next.save(configPath);
        config = next.copy();
        if (!config.enabled) reset();
    }

    private static int help(FabricClientCommandSource source) {
        return reply(source, "\u00a7d\u00a7lPURPLE PAD TIMER\u00a7r\n"
            + "\u00a7b/purplepad\u00a77 or \u00a7b/purplepad gui\u00a77 - Open settings\n"
            + "\u00a7b/purplepad preview\u00a77 - Preview the HUD for 2.8 seconds\n"
            + "\u00a7b/purplepad on\u00a77 | \u00a7boff\u00a77 - Enable or disable\n"
            + "\u00a7b/purplepad position <x> <y>\u00a77 - GUI pixels; x=-1 centers\n"
            + "\u00a7b/purplepad scale <0.5-6>\u00a77 - Change text size\n"
            + "\u00a7b/purplepad simple on\u00a77 | \u00a7boff\u00a77 - Plain text or pixel card\n"
            + "\u00a7b/purplepad reload\u00a77 - Reload saved settings\n"
            + "\u00a77Settings > Move & Resize: drag timer; drag corner or scroll to resize.\n"
            + "\u00a77Done returns to settings; then Save applies the layout.\n"
            + "\u00a77Save applies changes; Cancel or Escape discards them.");
    }

    public static com.mojang.brigadier.Command<FabricClientCommandSource> test(com.mojang.brigadier.Command<FabricClientCommandSource> action) {
        return ctx -> {
            if (!testsEnabled()) {
                ctx.getSource().sendFeedback(Component.literal(enabled() ? "Open /kca settings and turn Test commands ON." : "Open /kca settings and turn KCA and Test commands ON."));
                return 0;
            }
            return action.run(ctx);
        };
    }
    private static int previewTimer(com.mojang.brigadier.context.CommandContext<FabricClientCommandSource> ctx) {
        if (!hudEnabled() || !config.enabled || Minecraft.getInstance().gui.hud.isHidden())
            return reply(ctx.getSource(),"For the countdown preview, enable Game HUD in General, use /purplepad on, and show the Minecraft HUD (F1).");
        previewTicks=TimerState.DURATION;
        return reply(ctx.getSource(),"Previewing the Purple Pad Timer for 2.8 seconds.");
    }
    private static int placeWaypoints(FabricClientCommandSource source, boolean rightGuardian) {
        return com.nucleus.client.NucleusKeybinds.captureFromPlayer(source.getClient(), rightGuardian, source::sendFeedback) ? 1 : 0;
    }

    private static int reply(FabricClientCommandSource source, String message) {
        source.sendFeedback(Component.literal(message));
        return 1;
    }

    private static int save(FabricClientCommandSource source) {
        try { config.save(configPath); return reply(source, "Purple Pad Timer settings saved."); }
        catch (Exception e) { return reply(source, "Settings changed for this session, but could not save: " + e.getMessage()); }
    }
}
