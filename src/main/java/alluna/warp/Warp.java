package alluna.warp;

import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT;
import static net.minecraft.text.Text.literal;

public class Warp implements ModInitializer {
    public static final String MOD_ID = "Warp";
    public static final String LOGGER_ARTIFACT = "[Warp] ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static ArrayList<Location> Locations = new ArrayList<>();
    private static final File CONFIG_DIR = new File("warp");
    private static File worldLocationFile;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(Warp::loadLocations);

        ServerLifecycleEvents.SERVER_STOPPING.register(Warp::saveLocations);

        EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("warp")
                .then(WarpCommands.Set)
                .then(WarpCommands.To)
                .then(WarpCommands.Remove)
                .then(WarpCommands.Edit)
                .then(WarpCommands.Allow)
                .then(WarpCommands.Disallow)
            );
        });
    }

    public static Text locationMissing = literal("Warp location does not exist");

    private static void loadLocations(MinecraftServer server) {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        if (worldLocationFile == null) {
            var seed = Long.toString(server.getOverworld().getSeed());
            worldLocationFile = new File(CONFIG_DIR, seed + ".json");
        }

        if (worldLocationFile.exists()) {
            var type = new TypeToken<ArrayList<Location>>() {}.getType();
            Locations = JsonUtils.readFromJson(worldLocationFile, type);
        }

        if (Locations == null) {
            Locations = new ArrayList<>();
        }

        LOGGER.info("Locations loaded");
    }

    private static void saveLocations(MinecraftServer server) {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        if (worldLocationFile == null) {
            var seed = Long.toString(server.getOverworld().getSeed());
            worldLocationFile = new File(CONFIG_DIR, seed + ".json");
        }

        JsonUtils.writeToJson(worldLocationFile, Locations);
        LOGGER.info("Locations saved");
    }

    public static CompletableFuture<Suggestions> getLocationSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var playerName = context.getSource().getName();

        Locations.stream()
            .filter(location -> location.isAllowedBy(playerName) || !location.isPrivate)
            .map(Location::getLabel)
            .collect(Collectors.toCollection(ArrayList::new))
            .forEach(builder::suggest);

        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> getPlayerToRemoveSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var oLocation = getLocationFromContext(context);
        var source = context.getSource();

        if (oLocation.isEmpty()) {
            source.sendError(locationMissing);
        }

        var location = oLocation.orElseGet(Location::new);
        var players = source.getPlayerNames().stream().filter(location::isAllowedBy);

        players.forEach(builder::suggest);
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> getPlayerToAddSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var oLocation = getLocationFromContext(context);
        var source = context.getSource();

        if (oLocation.isEmpty()) {
            source.sendError(locationMissing);
        }

        var location = oLocation.orElseGet(Location::new);
        var players = source.getPlayerNames().stream().filter(name -> !location.isAllowedBy(name));

        players.forEach(builder::suggest);
        return builder.buildFuture();
    }

    public static Optional<Location> getLocation(String label, String name) {
        return Locations
          .stream()
          .filter((location) -> location.label.equals(label) && (location.isAllowedBy(name) || !location.isPrivate)).findFirst();
    }

    public static Optional<Location> getLocationFromContext(CommandContext<ServerCommandSource> context) {
        var label = StringArgumentType.getString(context, "label");
        var playerName = context.getSource().getName();

        return getLocation(label, playerName);
    }

    public static ArrayList<Location> getLocations(String label, String name) {
        return Locations
            .stream()
            .filter(location -> location.label.equals(label) && ( !location.isPrivate || location.isOwnedBy(name) || location.isAllowedBy(name)))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}