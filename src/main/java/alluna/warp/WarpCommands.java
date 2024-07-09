package alluna.warp;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import static net.minecraft.text.Text.literal;

public class WarpCommands {
    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Set = CommandManager.literal("set").then(CommandManager.argument("label", StringArgumentType.word())
        .executes(context -> setWarpPoint(context, false))
            .then(CommandManager.literal("private")
            .executes(context -> setWarpPoint(context, true))
        )
    );

    private static int setWarpPoint(CommandContext<ServerCommandSource> context, boolean isPrivate) {
        var label = StringArgumentType.getString(context, "label");
        var source = context.getSource();
        var playerName = source.getName();

        var locations = Warp.getLocations(label, playerName);

        if (!locations.isEmpty()) {
            source.sendError(literal("Warp location already exists"));
            return 1;
        }

        var position = source.getPosition();
        var player = source.getPlayer();
        var dimension = source.getWorld().getRegistryKey().getValue().toString();

        var location = new Location();

        location.label = label;
        location.dimension = dimension;
        location.x = position.x;
        location.y = position.y;
        location.z = position.z;
        location.pitch = player.getPitch();
        location.yaw = player.getYaw();

        location.createdBy = playerName;
        location.isPrivate = isPrivate;

        location.allowedPlayers.add(playerName);

        Warp.Locations.add(location);

        var message = "warp location " + label + " created by " + playerName;

        Warp.LOGGER.info(message);
        source.sendFeedback(() -> literal(Warp.LOGGER_ARTIFACT + message), true);

        return 0;
    }

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    To = CommandManager.literal("to").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getLocationSuggestions)
        .executes(context -> {
            var label = StringArgumentType.getString(context, "label");
            var source = context.getSource();
            var playerName = source.getName();

            var oLocation = Warp.getLocation(label, playerName);

            if (oLocation.isEmpty()) {
                source.sendError(Warp.locationMissing);
                return 1;
            }

            var location = oLocation.get();

            if (location.isPrivate && !location.isAllowedBy(playerName)) {
                source.sendError(literal("You do not have access to " + label));
                return 1;
            }

            var message = playerName + " warped to " + label;

            var player = source.getPlayer();
            var world = source.getWorld();
            var dimension = world.getRegistryKey().getValue().toString();

            var dimensionsMatch = dimension.equals(location.dimension);

            if (!dimensionsMatch) {
                source.sendError(literal("Cannot warp to " + label + ". Dimensions do not match"));
                return 1;
            }

            player.teleport(world, location.x, location.y, location.z, location.yaw, location.pitch);

            Warp.LOGGER.info(message);
            source.sendFeedback(() -> literal(Warp.LOGGER_ARTIFACT + message), true);

            return 0;
        })
    );

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Remove = CommandManager.literal("remove").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getLocationSuggestions)
        .executes(context -> {
            var label = StringArgumentType.getString(context, "label");
            var source = context.getSource();

            var playerName = source.getName();
            var oLocation = Warp.getLocation(label, playerName);

            if (oLocation.isEmpty()) {
                source.sendError(Warp.locationMissing);
                return 1;
            }

            var location = oLocation.get();

            if (!location.isAllowedBy(playerName)) {
                source.sendError(literal("You do not have access to " + label));
                return 1;
            }

            Warp.Locations.remove(location);

            var message = playerName + " removed " + label;

            Warp.LOGGER.info(message);
            source.sendFeedback(() -> literal(Warp.LOGGER_ARTIFACT + message), true);
            return 0;
        })
    );

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Edit = CommandManager.literal("edit").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getLocationSuggestions)
        .executes(context -> {
            var label = StringArgumentType.getString(context, "label");
            var source = context.getSource();

            var playerName = source.getName();
            var oLocation = Warp.getLocation(label, playerName);

            if (oLocation.isEmpty()) {
                source.sendError(Warp.locationMissing);
                return 1;
            }

            var location = oLocation.get();

            if (!location.isAllowedBy(playerName)) {
                source.sendError(literal("You do not have access to " + label));
                return 1;
            }

            var position = source.getPosition();
            var player = source.getPlayer();
            var world = source.getWorld();

            location.dimension = world.getRegistryKey().getValue().toString();
            location.x = position.x;
            location.y = position.y;
            location.z = position.z;
            location.pitch = player.getPitch();
            location.yaw = player.getYaw();

            var message = playerName + " edited " + label;

            Warp.LOGGER.info(message);
            source.sendFeedback(() -> literal(Warp.LOGGER_ARTIFACT + message), true);
            return 0;
        })
    );

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Allow = CommandManager.literal("allow").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getLocationSuggestions)
        .then(CommandManager.argument("player", StringArgumentType.word())
            .suggests(Warp::getPlayerToAddSuggestions)
            .executes(context -> {
                var source = context.getSource();
                var targetName = StringArgumentType.getString(context, "player");
                var label = StringArgumentType.getString(context, "label");
                var playerName = source.getName();

                var oLocation = Warp.getLocationFromContext(context);

                if (oLocation.isEmpty()) {
                    source.sendError(Warp.locationMissing);
                    return 1;
                }

                var location = oLocation.get();

                if (!location.isAllowedBy(playerName)) {
                    source.sendError(literal("You do not have access to " + label));
                    return 1;
                }

                if (playerName.equals(targetName)) {
                    source.sendError(literal("Can not allow self to use " + label));
                    return 1;
                }

                if (location.isOwnedBy(targetName)) {
                    source.sendError(literal("Can not allow owner to use " + label));
                    return 1;
                }
                if (location.isAllowedBy(targetName)) {
                    source.sendError(literal("Could not allow " + targetName + " to use " + label));
                    return 1;
                }

                location.allow(playerName);

                var message = targetName + " was allowed to use " + label + " by " + playerName;
                source.sendFeedback(() -> literal(message), true);
                Warp.LOGGER.info(Warp.LOGGER_ARTIFACT + message);

                return 0;
            })
        )
    );

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Disallow = CommandManager.literal("disallow").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getLocationSuggestions)
        .then(CommandManager.argument("player", StringArgumentType.word())
            .suggests(Warp::getPlayerToRemoveSuggestions)
            .executes(context -> {
                var source = context.getSource();
                var targetName = StringArgumentType.getString(context, "player");
                var label = StringArgumentType.getString(context, "label");
                var playerName = source.getName();

                var oLocation = Warp.getLocationFromContext(context);

                if (oLocation.isEmpty()) {
                    source.sendError(Warp.locationMissing);
                    return 1;
                }

                var location = oLocation.get();

                if (!location.isAllowedBy(playerName)) {
                    source.sendError(literal("You do not have access to " + label));
                    return 1;
                }

                if (playerName.equals(targetName)) {
                    source.sendError(literal("Can not disallow self from using " + label));
                    return 1;
                }

                if (location.isOwnedBy(targetName)) {
                    source.sendError(literal("Can not disallow owner from using " + label));
                    return 1;
                }

                if (!location.isAllowedBy(targetName)) {
                    source.sendError(literal("Could not disallow " + targetName + " from using " + label));
                    return 1;
                }

                location.disallow(targetName);

                var message = targetName + " was disallowed from using " + label + " by " + playerName;
                source.sendFeedback(() -> literal(message), true);
                Warp.LOGGER.info(Warp.LOGGER_ARTIFACT + message);

                return 0;
            })
        )
    );
}
