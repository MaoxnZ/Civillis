package civil.command;

import com.mojang.brigadier.CommandDispatcher;
import civil.CivilMod;
import civil.aura.SonarScanManager;
import civil.aura.SonarType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.literal;

/**
 * Admin commands for Civil.
 *
 * <p>All subcommands require permission level {@link Commands#LEVEL_GAMEMASTERS} (2) or higher
 * ({@link Commands#hasPermission(int)}): dedicated-server operators, integrated single-player with
 * cheats enabled (host is typically level 4), and the dedicated console / RCON when configured with
 * sufficient permission.
 */
public final class CivilAdminCommands {

    private CivilAdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("civil")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(literal("rebuild")
                        .executes(ctx -> executeRebuild(ctx.getSource())))
                .then(literal("ring")
                        .executes(ctx -> executeRing(ctx.getSource()))));
    }

    private static int executeRebuild(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("[Civil] Rebuild started..."), true);
        boolean ok = CivilMod.rebuildCivilData(source.getServer());
        if (ok) {
            source.sendSuccess(() -> Component.literal("[Civil] Rebuild completed."), true);
            return 1;
        }
        source.sendFailure(Component.literal("[Civil] Rebuild failed. Check server logs."));
        return 0;
    }

    /**
     * Triggers one STATIC (bell + lodestone) sonar at the executing player's feet.
     * Does not apply bell cooldown (admin/debug). Ignores {@code auraEffectEnabled} so admins can
     * preview or debug when the global aura toggle is off.
     */
    private static int executeRing(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[Civil] This command can only be run by a player."));
            return 0;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("[Civil] Expected a server level."));
            return 0;
        }
        SonarScanManager.startScan(player, level, player.blockPosition(), SonarType.STATIC);
        source.sendSuccess(() -> Component.literal("[Civil] Static sonar triggered at your position."), true);
        return 1;
    }
}
