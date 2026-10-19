package civil.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
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
 * <p>
 * {@code rebuild} requires permission level 4 ({@link Commands#LEVEL_OWNERS});
 * {@code ring} requires level 2 ({@link Commands#LEVEL_GAMEMASTERS}) or higher.
 */
public final class CivilAdminCommands {

    private CivilAdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("civil")
                .then(literal("rebuild")
                        .requires(Commands.hasPermission(Commands.LEVEL_OWNERS))
                        .executes(ctx -> executeRebuild(ctx.getSource())))
                .then(literal("ring")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> executeRing(ctx.getSource(), SonarType.STATIC.getRadius()))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(3, 64))
                                .executes(ctx -> executeRing(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius"))))));
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
    private static int executeRing(CommandSourceStack source, int radius) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("[Civil] This command can only be run by a player."));
            return 0;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("[Civil] Expected a server level."));
            return 0;
        }
        SonarScanManager.startScan(player, level, player.blockPosition(), SonarType.STATIC, radius);
        source.sendSuccess(() -> Component.literal(
                "[Civil] Static sonar triggered at your position (radius=" + radius + " VC)."), true);
        return 1;
    }
}
