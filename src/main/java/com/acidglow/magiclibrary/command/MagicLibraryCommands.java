package com.acidglow.magiclibrary.command;

import com.acidglow.magiclibrary.MagicLibrary;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.acidglow.magiclibrary.server.MagicLibraryAdminData;
import com.mojang.brigadier.context.CommandContext;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class MagicLibraryCommands {
    private MagicLibraryCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal(MagicLibrary.MODID)
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .then(Commands.literal("make")
                    .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("supreme")
                            .executes(MagicLibraryCommands::executeMakeSupreme))))
        );
    }

    private static int executeMakeSupreme(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer target : targets) {
            MagicLibraryAdminData.setPendingSupreme(target, true);
        }

        int affected = targets.size();
        if (affected == 1) {
            ServerPlayer onlyTarget = targets.iterator().next();
            context.getSource().sendSuccess(
                () -> Component.literal("Pending supreme upgrade set for " + onlyTarget.getName().getString() + "."),
                true
            );
        } else {
            context.getSource().sendSuccess(
                () -> Component.literal("Pending supreme upgrade set for " + affected + " players."),
                true
            );
        }

        return affected;
    }
}
