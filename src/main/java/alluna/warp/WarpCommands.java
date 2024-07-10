package alluna.warp;

import alluna.warp.Locations.Location;
import alluna.warp.Locations.PrivateLocation;
import alluna.warp.Locations.PublicLocation;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import java.util.ArrayList;
import java.util.Optional;

import static net.minecraft.text.Text.literal;

public class WarpCommands {
    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Set = CommandManager.literal("set").then(CommandManager.argument("label", StringArgumentType.word())
        .executes(context -> setWarpPoint(context, false))
            .then(CommandManager.literal("public")
            .executes(context -> setWarpPoint(context, true))
        )
    );

    private static int setWarpPoint(CommandContext<ServerCommandSource> context, boolean isPublic) {
        var label = StringArgumentType.getString(context, "label");
        var source = context.getSource();
        var playerName = source.getName();

        var oPrivateLocation = Warp.getPrivateLocation(label, playerName);
        var oPublicLocation = Warp.getPublicLocation(label);

        if ((!isPublic && oPrivateLocation.isPresent()) || (isPublic && oPublicLocation.isPresent())) {
            source.sendError(literal("Warp location already exists"));
            return 1;
        }

        var message = isPublic ? createPublicLocation(source, label) : createPrivateLocation(source, label);

        Warp.LOGGER.info(message);
        source.sendFeedback(() -> literal(Warp.LOGGER_ARTIFACT + message), true);

        return 0;
    }

    private static String createPrivateLocation(ServerCommandSource source, String label) {
        var playerName = source.getName();

        var location = new PrivateLocation(source, label);
        Warp.PrivateLocations.get(playerName).add(location);

        return "Private warp location " + label + " created by " + playerName;
    }

    private static String createPublicLocation (ServerCommandSource source, String label) {
        var playerName = source.getName();

        var location = new PublicLocation(source, label);
        Warp.PublicLocations.add(location);

        return "Public warp location " + label + " created by " + playerName;
    }

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    To = CommandManager.literal("to").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getAllLocationSuggestions)
        .executes(context -> gotoWarpPoint(context, false))
            .then(CommandManager.literal("public")
            .executes(context -> gotoWarpPoint(context, true))
        )
    );

    private static int gotoWarpPoint(CommandContext<ServerCommandSource> context, boolean isPublic){
        var label = StringArgumentType.getString(context, "label");
        var source = context.getSource();
        var playerName = source.getName();

        var oPrivateLocation = Warp.getPrivateLocation(label, playerName);
        var oPublicLocation = Warp.getPublicLocation(label);

        if (isPublic || oPrivateLocation.isEmpty()) {
            return gotoPublicLocation(source, oPublicLocation, playerName);
        }

        return teleportPlayerToLocation(source, oPrivateLocation.get(), playerName);
    }

    private static int gotoPublicLocation(ServerCommandSource source, Optional<PublicLocation> oLocation, String playerName) {
        if (oLocation.isEmpty()) {
            source.sendError(Warp.locationMissing);
            return 1;
        }

        var location = oLocation.get();

        return teleportPlayerToLocation(source, location, playerName);
    }

    private static int teleportPlayerToLocation(ServerCommandSource source, Location location, String playerName) {
        var message = playerName + " warped to " + location.label;

        var player = source.getPlayer();
        var world = source.getWorld();
        var dimension = world.getRegistryKey().getValue().toString();

        var dimensionsMatch = dimension.equals(location.dimension);

        if (!dimensionsMatch) {
            source.sendError(literal("Cannot warp to " + location.label + ". Dimensions do not match"));
            return 1;
        }

        player.teleport(world, location.x, location.y, location.z, location.yaw, location.pitch);

        Warp.LOGGER.info(message);
        source.sendFeedback(() -> literal(Warp.LOGGER_ARTIFACT + message), true);

        return 0;
    }

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Remove = CommandManager.literal("remove").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getAllLocationSuggestions)
        .executes(context -> removeWarpPoint(context, false))
            .then(CommandManager.literal("public")
            .executes(context -> removeWarpPoint(context, true))
        )
    );

    private static int removeWarpPoint(CommandContext<ServerCommandSource> context, boolean isPublic){
        var label = StringArgumentType.getString(context, "label");
        var source = context.getSource();

        var playerName = source.getName();
        var oLocation = isPublic ? Warp.getPublicLocation(label) : Warp.getPrivateLocation(label, playerName);

        if (oLocation.isEmpty()) {
            source.sendError(Warp.locationMissing);
            return 1;
        }

        var location = oLocation.get();

        if (isPublic && !((PublicLocation) location).isOwnedBy(playerName)) {
            source.sendError(literal("You do not own " + label));
            return 1;
        }

        var success = isPublic ? Warp.PublicLocations.remove(location) : Warp.PrivateLocations.get(playerName).remove(location);

        if (!success) {
            source.sendError(literal("Could not remove " + label));
        }

        var message = playerName + " removed " + label;

        Warp.LOGGER.info(message);
        source.sendFeedback(() -> literal(Warp.LOGGER_ARTIFACT + message), true);
        return 0;
    }

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Edit = CommandManager.literal("edit").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getPrivateLocationSuggestions)
        .executes(context -> editWarpPoint(context, false))
            .then(CommandManager.literal("public")
            .executes(context -> editWarpPoint(context, true))
        )
    );

    private static int editWarpPoint(CommandContext<ServerCommandSource> context, boolean isPublic){
        var label = StringArgumentType.getString(context, "label");
        var source = context.getSource();

        var playerName = source.getName();
        var oLocation = isPublic ? Warp.getPublicLocation(label) : Warp.getPrivateLocation(label, playerName);

        if (oLocation.isEmpty()) {
            source.sendError(Warp.locationMissing);
            return 1;
        }

        var location = oLocation.get();

        if (isPublic && !((PublicLocation) location).isOwnedBy(playerName)) {
            source.sendError(literal("You do not own " + label));
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
    }

    public static ArgumentBuilder<ServerCommandSource, LiteralArgumentBuilder<ServerCommandSource>>
    Give = CommandManager.literal("give").then(CommandManager.argument("label", StringArgumentType.word())
        .suggests(Warp::getPrivateLocationSuggestions)
        .then(CommandManager.argument("player", StringArgumentType.word())
            .suggests(Warp::getPlayerSuggestions)
            .executes(context -> {
                var source = context.getSource();
                var targetName = StringArgumentType.getString(context, "player");
                var label = StringArgumentType.getString(context, "label");
                var playerName = source.getName();

                if (!Warp.PrivateLocations.containsKey(targetName)) {
                    Warp.PrivateLocations.put(targetName, new ArrayList<>());
                }

                var oLocation = Warp.getPrivateLocation(label, playerName);

                try {
                    var oTargetLocation = Warp.getPrivateLocation(label, targetName);

                if (oLocation.isEmpty()) {
                    source.sendError(Warp.locationMissing);
                    return 1;
                }

                var location = oLocation.get();

                if (playerName.equals(targetName)) {
                    source.sendError(literal("Can not give " + label + " to self"));
                    return 1;
                }

                if (oTargetLocation.isPresent()) {
                    source.sendError(literal(targetName + " already has a warped named " + label));
                    return 1;
                }

                Warp.PrivateLocations.get(targetName).add(location.clone());

                var message = playerName + " gave " + label + " to " + targetName;

                source.sendFeedback(() -> literal(message), true);
                Warp.LOGGER.info(Warp.LOGGER_ARTIFACT + message);

                }
                catch (Exception e) {
                    source.sendError(literal(e.getMessage()));
                    e.printStackTrace();

                }
                return 0;
            })
        )
    );
}
