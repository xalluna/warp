package alluna.warp;

import alluna.warp.Locations.Location;
import alluna.warp.Locations.PrivateLocation;
import alluna.warp.Locations.PublicLocation;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT;
import static net.minecraft.text.Text.literal;

public class Warp implements ModInitializer {
    public static final String MOD_ID = "Warp";
    public static final String LOGGER_ARTIFACT = "[Warp] ";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final File CONFIG_DIR = new File("warp");
    private static File worldPublicLocationFile;
    private static File worldPrivateLocationFile;

    public static ArrayList<PublicLocation> PublicLocations = new ArrayList<>();
    public static HashMap<String, ArrayList<PrivateLocation>> PrivateLocations = new HashMap<>();

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(Warp::loadLocations);
        ServerLifecycleEvents.SERVER_STOPPING.register(Warp::saveLocations);

        ServerPlayConnectionEvents.JOIN.register(Warp::onPlayerJoin);

        EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("warp")
                .then(WarpCommands.Set)
                .then(WarpCommands.To)
                .then(WarpCommands.Remove)
                .then(WarpCommands.Edit)
                .then(WarpCommands.Give)
            );
        });
    }

    public static Text locationMissing = literal("Warp location does not exist");

    private static void loadLocations(MinecraftServer server) {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        if (worldPublicLocationFile == null) {
            var seed = Long.toString(server.getOverworld().getSeed());
            worldPublicLocationFile = new File(CONFIG_DIR, seed + "_public.json");
        }

        if (worldPublicLocationFile.exists()) {
            var type = new TypeToken<ArrayList<PublicLocation>>() {}.getType();
            PublicLocations = JsonUtils.readFromJson(worldPublicLocationFile, type);
        }

        if (worldPrivateLocationFile == null) {
            var seed = Long.toString(server.getOverworld().getSeed());
            worldPrivateLocationFile = new File(CONFIG_DIR, seed + "_private.json");
        }

        if (worldPrivateLocationFile.exists()) {
            var type = new TypeToken<HashMap<String, ArrayList<PrivateLocation>>>() {}.getType();
            PrivateLocations = JsonUtils.readFromJson(worldPrivateLocationFile, type);
        }

        if (PublicLocations == null) {
            PublicLocations = new ArrayList<>();
        }

        if (PrivateLocations == null) {
            PrivateLocations = new HashMap<>();
        }

        LOGGER.info(LOGGER_ARTIFACT + " Locations loaded");
    }

    private static void saveLocations(MinecraftServer server) {
        if (!CONFIG_DIR.exists()) {
            CONFIG_DIR.mkdirs();
        }

        var seed = Long.toString(server.getOverworld().getSeed());

        if (worldPublicLocationFile == null) {
            worldPublicLocationFile = new File(CONFIG_DIR, seed + "_public.json");
        }

        JsonUtils.writeToJson(worldPublicLocationFile, PublicLocations);

        if (worldPrivateLocationFile == null) {
            worldPrivateLocationFile = new File(CONFIG_DIR, seed + "_private.json");
        }

        JsonUtils.writeToJson(worldPrivateLocationFile, PrivateLocations);

        LOGGER.info(LOGGER_ARTIFACT + " Locations saved");
    }

    private static void onPlayerJoin(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        var playerName = handler.getPlayer().getName().getString();

        if (PrivateLocations.containsKey(playerName)) {
            return;
        }

        PrivateLocations.put(playerName, new ArrayList<>());
    }

    public static CompletableFuture<Suggestions> getAllLocationSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var playerName = context.getSource().getName();

        var labels = PublicLocations.stream().map(Location::getLabel);

        var playerLocations = PrivateLocations.get(playerName);

        if (playerLocations != null) {
            labels = Stream.concat(labels, playerLocations.stream().map(Location::getLabel));
        }

        labels.forEach(builder::suggest);

        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> getPublicLocationSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        PublicLocations.stream().map(Location::getLabel).forEach(builder::suggest);
        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> getPrivateLocationSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var playerName = context.getSource().getName();
        var playerLocations = PrivateLocations.get(playerName);

        PublicLocations.stream()
          .filter(location -> location.isOwnedBy(playerName))
          .map(Location::getLabel)
          .forEach(builder::suggest);

        if (playerLocations != null) {
            playerLocations.stream().map(Location::getLabel).forEach(builder::suggest);
        }

        return builder.buildFuture();
    }

    public static CompletableFuture<Suggestions> getPlayerSuggestions(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        var playerName = context.getSource().getName();

        PrivateLocations
          .keySet()
          .stream()
          .filter(name -> !name.equals(playerName))
          .forEach(builder::suggest);
        return builder.buildFuture();
    }

    public static Optional<PublicLocation> getPublicLocation(String label) {
        return PublicLocations
          .stream()
          .filter((publicLocation) -> publicLocation.label.equals(label))
          .findFirst();
    }

    public static Optional<PrivateLocation> getPrivateLocation(String label, String playerName) {
        var playerLocations = PrivateLocations.get(playerName);

        if (playerLocations == null)
        {
            return Optional.empty();
        }

        return playerLocations
          .stream()
          .filter(location -> location.label.equals(label))
          .findFirst();
    }
}