package com.mccievent;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.ChatFormatting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class McciEvent implements ModInitializer {
	public static final String MOD_ID = "mcci-event";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(Commands.literal("command_with_suggestions").then(
					Commands.argument("entity", ResourceArgument.resource(registryAccess, Registries.ENTITY_TYPE))
							.suggests(SuggestionProviders.cast(SuggestionProviders.SUMMONABLE_ENTITIES))
							.executes(McciEvent::executeCommandWithSuggestions)
			));

			dispatcher.register(Commands.literal("mcci_event")
					.then(Commands.argument("coordinates", Vec3Argument.vec3(false))
							.then(Commands.literal("uncommon")
									.executes(context -> logEvent(context, "uncommon"))
									.then(commentArgument("uncommon")))
							.then(Commands.literal("rare")
									.executes(context -> logEvent(context, "rare"))
									.then(commentArgument("rare")))
							.then(Commands.literal("epic")
									.executes(context -> logEvent(context, "epic"))
									.then(commentArgument("epic")))
							.then(Commands.literal("legendary")
									.executes(context -> logEvent(context, "legendary"))
									.then(commentArgument("legendary")))
							.then(Commands.literal("mythic")
									.executes(context -> logEvent(context, "mythic"))
									.then(commentArgument("mythic")))
					)
			);
		});
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> commentArgument(
			String rarity) {
		return Commands.argument("comment", StringArgumentType.greedyString())
				.executes(context -> logEvent(context, rarity,
						StringArgumentType.getString(context, "comment")));
	}

	private static int logEvent(CommandContext<CommandSourceStack> context, String rarity) {
		return logEvent(context, rarity, null);
	}

	private static int logEvent(CommandContext<CommandSourceStack> context, String rarity, String comment) {
		Vec3 coordinates = Vec3Argument.getVec3(context, "coordinates");
		LOGGER.info("MCCI event command executed: coordinates:{}, rarity:{}, comment:{}", coordinates, rarity, comment);

		CommandSourceStack source = context.getSource();

		source.sendSuccess(() -> Component.literal(
				"Stash found infos: rarity:" + rarity + ", coords:" + coordinates
						+ (comment == null ? "" : ", comment:" + comment)
		), false);

		sendEventToFlask(source, rarity, coordinates, comment);

		return 1;
	}
	private static final String API_KEY = "61ed8c056ed89e6e3b00d7c4f9a791a3d55a9a3e62e2f6174bc3107003d8fb6d";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private static final Gson GSON = new Gson();

	private static void sendEventToFlask(CommandSourceStack source, String rarity, Vec3 coordinates, String comment) {
		String json = String.format(
				Locale.US,
				"{\"rarity\":\"%s\",\"x\":%f,\"y\":%f,\"z\":%f%s}",
				rarity, coordinates.x, coordinates.y, coordinates.z,
				comment == null ? "" : ",\"comment\":\"" + escapeJson(comment) + "\""
		);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create("https://mcci-event.vercel.app/event"))
				.header("Content-Type", "application/json")
				.header("X-API-Key", API_KEY)
				.POST(HttpRequest.BodyPublishers.ofString(json))
				.build();

	HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
			.thenAccept(response -> {

				source.getServer().execute(() -> {
					String messageText = null;
					String status = null;
					try {
						JsonElement root = GSON.fromJson(response.body(), JsonElement.class);
						JsonObject obj = root.isJsonArray()
								? root.getAsJsonArray().get(0).getAsJsonObject()
								: root.getAsJsonObject();
						if (obj.has("message")) {
							messageText = obj.get("message").getAsString();
						}
						if (obj.has("status")) {
							status = obj.get("status").getAsString();
						}
					} catch (Exception e) {
					}

					if (messageText != null) {
						String finalMessage = messageText;
						ChatFormatting color = "error".equalsIgnoreCase(status)
								? ChatFormatting.RED
								: ChatFormatting.GREEN;
						source.sendSuccess(() -> Component.literal(finalMessage).withStyle(color), false);
					} else {
						source.sendSuccess(() -> Component.literal(
								"Flask (" + response.statusCode() + "): " + response.body()
						), false);
					}
				});})
			.exceptionally(ex -> {
				LOGGER.error("Error, try to redo the command", ex);
				source.getServer().execute(() -> source.sendFailure(
						Component.literal("Error: can't contact api")
				));
				return null;
			});}

	private static int executeCommandWithSuggestions(CommandContext<CommandSourceStack> context) {
		LOGGER.info("command_with_suggestions executed");
		return 1;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
