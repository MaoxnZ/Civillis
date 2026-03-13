package civil.command;

import com.mojang.brigadier.CommandDispatcher;
import civil.CivilMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.literal;

/**
 * Admin commands for Civil.
 */
public final class CivilAdminCommands {

    private CivilAdminCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("civil")
                .then(literal("rebuild")
                        .executes(ctx -> executeRebuild(ctx.getSource()))));
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
}
