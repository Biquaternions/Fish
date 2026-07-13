package me.biquaternions.fish;

import ca.spottedleaf.common.time.TickData;
import ca.spottedleaf.moonrise.patches.chunk_system.scheduling.task.ChunkFullTask;
import com.google.common.collect.Lists;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.PaperCommands;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.util.HSVLike;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.apache.commons.lang3.tuple.Triple;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import java.text.DecimalFormat;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

public class FishCommand {

    private static final Component PREFIX = MiniMessage.miniMessage().deserialize("<white><gradient:#feabff:#94ffeb>Fish</gradient> <#e79eff>⮞</#e79eff> </white>");
    private static final Component FEEDBACK_RELOAD_SUCCESS = PREFIX.append(Component.text("Fish configuration has been reloaded.", NamedTextColor.WHITE));
    private static final Component FEEDBACK_RELOAD_FAILED = PREFIX.append(Component.text("Failed to reload.", NamedTextColor.RED));
    private static Component FEEDBACK_CURRENT_VERSION = null;

    private static final Component MARK = Component.text("■ ", TextColor.color(0x94ffeb)); // ■ ⏹
    private static final TextColor COLOR_PINK = TextColor.color(0xfeabff);
    private static final TextColor COLOR_PINK_LIGHT = TextColor.color(0xffd4ff);

    private static final ThreadLocal<DecimalFormat> TWO_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.00"));
    private static final ThreadLocal<DecimalFormat> ONE_DECIMAL_PLACES = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.0"));

    public static void init() {
        LiteralCommandNode<CommandSourceStack> command = Commands.literal("fish")
            .requires(s -> s.getSender().hasPermission("bukkit.command.fish"))
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    MinecraftServer console = MinecraftServer.getServer();
                    try {
                        FishConfig.INSTANCE.load();
                    } catch (Exception e) {
                        sender.sendMessage(FEEDBACK_RELOAD_FAILED);
                        console.server.getLogger().severe(e.getMessage());
                        return Command.SINGLE_SUCCESS;
                    }
                    console.server.reloadCount++;
                    sender.sendMessage(FEEDBACK_RELOAD_SUCCESS);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(Commands.literal("health")
                .then(Commands.argument("worlds", IntegerArgumentType.integer(1))
                    .executes(ctx -> {
                        CommandSender sender = ctx.getSource().getSender();
                        int worlds = ctx.getArgument("worlds", int.class);

                        formatHealthMetrics(sender, worlds);

                        return Command.SINGLE_SUCCESS;
                    })
                )
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    formatHealthMetrics(sender, 3);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .then(Commands.literal("version")
                .executes(ctx -> {
                    if (FEEDBACK_CURRENT_VERSION == null) {
                        FEEDBACK_CURRENT_VERSION = PREFIX.append(Component.text("This server is running " + Bukkit.getName() + " version " + Bukkit.getVersion() + " (Implementing API version " + Bukkit.getBukkitVersion() + ")", NamedTextColor.WHITE));
                    }
                    ctx.getSource().getSender().sendMessage(FEEDBACK_CURRENT_VERSION);
                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();

        PaperCommands.INSTANCE.registerWithFlagsInternal(null, "biquaternions", "fish", command, "Fish related commands", List.of(), Set.of());
    }

    public static void formatHealthMetrics(CommandSender sender, int worlds) {
        final long now = System.nanoTime();
        final double loadRate = ChunkFullTask.fish$loadRate(now);
        final double genRate = ChunkFullTask.fish$genRate(now);
        MinecraftServer server = MinecraftServer.getServer();
        TickData.TickReportData reportGlobal = server.tickTimes15s.generateTickReport(null, now, server.tickRateManager().nanosecondsPerTick());

        int threads;
        double utilisationTotal = reportGlobal == null ? 0.0 : reportGlobal.utilisation();
        List<Component> extraMessages = Lists.newArrayList();

        if (FishConfig.ASYNC.WORLD_TICKING._ENABLED) {
            PriorityQueue<Triple<Double, Double, ServerLevel>> levelsQueue = new PriorityQueue<>((a, b) -> b.getLeft().compareTo(a.getLeft()));
            Iterable<ServerLevel> levels = server.getAllLevels();

            double highestUtilisation = 0.0;
            for (ServerLevel level : levels) {
                TickData.TickReportData reportWorld = level.fish$tickTimes15s.generateTickReport(null, now, server.tickRateManager().nanosecondsPerTick());
                if (reportWorld == null) {
                    continue;
                }

                double mspt = reportWorld.timePerTickData().segmentAll().average() / 1.0E6;
                double utilisation = reportWorld.utilisation();
                utilisationTotal += utilisation;
                levelsQueue.offer(Triple.of(utilisation, mspt, level));

                if (highestUtilisation < utilisation) {
                    highestUtilisation = utilisation;
                }
            }
            // Global util = world highest util + global tasks
            utilisationTotal -= highestUtilisation; // This avoids duplication of the highest util

            threads = Math.min(levelsQueue.size(), FishConfig.ASYNC.WORLD_TICKING._THREADS);
            List<Component> detailsFullMessages = Lists.newArrayList();
            Triple<Double, Double, ServerLevel> entry;

            double mspt = 0.0;
            int medianIndex = levelsQueue.size() / 2;
            for (int index = 0; (entry = levelsQueue.poll()) != null; ++index) {
                mspt = entry.getMiddle();
                if (index == 0) {
                    extraMessages.add(
                        MARK.append(Component.text("Highest World MSPT: ", NamedTextColor.WHITE)).append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt), getColorForMSPT(mspt)))
                    );
                }
                if (index == medianIndex) {
                    extraMessages.add(
                        MARK.append(Component.text("Median World MSPT: ", NamedTextColor.WHITE)).append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt), getColorForMSPT(mspt)))
                    );
                }
                if (index < worlds) {
                    ServerLevel level = entry.getRight();
                    int players = level.players().size();
                    int entities = level.entityTickList.entities.size();
                    int chunks = level.moonrise$getLoadedChunks().size();
                    detailsFullMessages.addAll(
                        List.of(
                            MARK.append(Component.text("World ", NamedTextColor.WHITE)).append(Component.text(String.format("[%s]", level.dimension().identifier()), COLOR_PINK_LIGHT)).append(Component.text(":", NamedTextColor.WHITE)),
                            Component.text("   ").append(Component.text(ONE_DECIMAL_PLACES.get().format(entry.getLeft() * 100.0), getUtilisationColorRegion(entry.getLeft()))).append(Component.text("% util at ", NamedTextColor.WHITE))
                                .append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt), getColorForMSPT(mspt))).append(Component.text(" MSPT", NamedTextColor.WHITE)),
                            Component.text("   ").append(Component.text("Chunks: ", NamedTextColor.WHITE)).append(Component.text(chunks, COLOR_PINK_LIGHT))
                                .append(Component.text(" Players: ", NamedTextColor.WHITE)).append(Component.text(players, COLOR_PINK_LIGHT))
                                .append(Component.text(" Entities: ", NamedTextColor.WHITE)).append(Component.text(entities, COLOR_PINK_LIGHT))
                        )
                    );
                }
            }
            extraMessages.add(
                MARK.append(Component.text("Lowest World MSPT: ", NamedTextColor.WHITE)).append(Component.text(TWO_DECIMAL_PLACES.get().format(mspt), getColorForMSPT(mspt)))
            );

            extraMessages.add(
                Component.text("Highest ", COLOR_PINK, TextDecoration.BOLD).append(Component.text(worlds, COLOR_PINK_LIGHT, TextDecoration.BOLD)).append(Component.text(" utilisation worlds", COLOR_PINK, TextDecoration.BOLD))
            );
            extraMessages.addAll(detailsFullMessages);

        } else {
            threads = 1;

            double tps = reportGlobal == null ? 20.0 : reportGlobal.tpsData().segmentAll().average();
            extraMessages.add(MARK.append(Component.text("Global TPS: ", NamedTextColor.WHITE).append(Component.text(TWO_DECIMAL_PLACES.get().format(tps), getColorForTPS(tps)))));
        }

        List<Component> messages = Lists.newArrayList(
            PREFIX.append(Component.text("Server Health Report", COLOR_PINK, TextDecoration.BOLD)),
            MARK.append(Component.text("Online Players: ", NamedTextColor.WHITE)).append(Component.text(Bukkit.getOnlinePlayers().size(), COLOR_PINK_LIGHT)),
            MARK.append(Component.text("Total Worlds: ", NamedTextColor.WHITE)).append(Component.text(Bukkit.getWorlds().size(), COLOR_PINK_LIGHT)),
            MARK.append(Component.text("Utilisation: ", NamedTextColor.WHITE)).append(Component.text(ONE_DECIMAL_PLACES.get().format(utilisationTotal * 100), getUtilisationColorRegion(utilisationTotal / (double) threads)))
                .append(Component.text("% / ", NamedTextColor.WHITE)).append(Component.text(ONE_DECIMAL_PLACES.get().format(threads * 100.0), COLOR_PINK_LIGHT)).append(Component.text("%", NamedTextColor.WHITE)),
            MARK.append(Component.text("Load rate: ", NamedTextColor.WHITE)).append(Component.text(TWO_DECIMAL_PLACES.get().format(loadRate), COLOR_PINK_LIGHT))
                .append(Component.text(", Gen rate: ", NamedTextColor.WHITE)).append(Component.text(TWO_DECIMAL_PLACES.get().format(genRate), COLOR_PINK_LIGHT))
        );

        messages.addAll(extraMessages);
        messages.forEach(sender::sendMessage);
    }

    public static TextColor getUtilisationColorRegion(final double util) {
        // assume 20TPS
        return getColorForMSPT(util * 50.0);
    }

    public static TextColor getColorForMSPT(final double mspt) {
        final double clamped = Math.min(Math.abs(mspt), 50.0);
        final double coordinate;
        if (clamped <= 15.0) {
            coordinate = 130.0 + ((140.0 - 130.0)/(0.0 - 15.0)) * (clamped - 15.0);
        } else if (clamped <= 25.0) {
            coordinate = 90.0 + ((130.0 - 90.0)/(15.0 - 25.0)) * (clamped - 25.0);
        } else if (clamped <= 35.0) {
            coordinate = 30.0 + ((90.0 - 30.0)/(25.0 - 35.0)) * (clamped - 35.0);
        } else if (clamped <= 40.0) {
            coordinate = 15.0 + ((30.0 - 15.0)/(35.0 - 40.0)) * (clamped - 40.0);
        } else {
            coordinate = 0.0 + ((15.0 - 0.0)/(40.0 - 50.0)) * (clamped - 50.0);
        }

        return TextColor.color(HSVLike.hsvLike((float)(coordinate / 360.0), 85.0f / 100.0f, 80.0f / 100.0f));
    }

    public static TextColor getColorForTPS(final double tps) {
        final double difference = Math.min(Math.abs(20.0 - tps), 20.0);
        final double coordinate;
        if (difference <= 2.0) {
            // >= 18 tps
            coordinate = 70.0 + ((140.0 - 70.0)/(0.0 - 2.0)) * (difference - 2.0);
        } else if (difference <= 5.0) {
            // >= 15 tps
            coordinate = 30.0 + ((70.0 - 30.0)/(2.0 - 5.0)) * (difference - 5.0);
        } else if (difference <= 10.0) {
            // >= 10 tps
            coordinate = 10.0 + ((30.0 - 10.0)/(5.0 - 10.0)) * (difference - 10.0);
        } else {
            // >= 0.0 tps
            coordinate = 0.0 + ((10.0 - 0.0)/(10.0 - 20.0)) * (difference - 20.0);
        }

        return TextColor.color(HSVLike.hsvLike((float)(coordinate / 360.0), 85.0f / 100.0f, 80.0f / 100.0f));
    }

}
