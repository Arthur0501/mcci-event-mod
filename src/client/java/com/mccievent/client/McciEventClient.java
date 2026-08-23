package com.mccievent.client;

import com.mccievent.McciEvent;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class McciEventClient implements ClientModInitializer {

	private static final String ALLOWED_SERVER = "mccisland.net";
	private static final String API_KEY = "61ed8c056ed89e6e3b00d7c4f9a791a3d55a9a3e62e2f6174bc3107003d8fb6d";
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private static final Gson GSON = new Gson();

	@Override
	public void onInitializeClient() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(literal("mcci_event")
					.then(argument("x", DoubleArgumentType.doubleArg())
							.then(argument("y", DoubleArgumentType.doubleArg())
									.then(argument("z", DoubleArgumentType.doubleArg())
											.then(literal("uncommon")
													.executes(context -> logEvent(context, "uncommon"))
													.then(commentArgument("uncommon")))
											.then(literal("rare")
													.executes(context -> logEvent(context, "rare"))
													.then(commentArgument("rare")))
											.then(literal("epic")
													.executes(context -> logEvent(context, "epic"))
													.then(commentArgument("epic")))
											.then(literal("legendary")
													.executes(context -> logEvent(context, "legendary"))
													.then(commentArgument("legendary")))
											.then(literal("mythic")
													.executes(context -> logEvent(context, "mythic"))
													.then(commentArgument("mythic")))
									)
							)
					)
			);
		});
	}

	private static com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, String> commentArgument(
			String rarity) {
		return argument("comment", StringArgumentType.greedyString())
				.executes(context -> logEvent(context, rarity,
						StringArgumentType.getString(context, "comment")));
	}

	private static int logEvent(CommandContext<FabricClientCommandSource> context, String rarity) {
		return logEvent(context, rarity, null);
	}

	private static int logEvent(CommandContext<FabricClientCommandSource> context, String rarity, String comment) {
		FabricClientCommandSource source = context.getSource();

		if (!isOnAllowedServer()) {
			source.sendError(Component.literal("You're not connected to " + ALLOWED_SERVER));
			return 0;
		}

		double x = DoubleArgumentType.getDouble(context, "x");
		double y = DoubleArgumentType.getDouble(context, "y");
		double z = DoubleArgumentType.getDouble(context, "z");
		Vec3 coordinates = new Vec3(x, y, z);

		McciEvent.LOGGER.info("MCCI event command executed: coordinates={}, rarity={}, comment={}", coordinates, rarity, comment);

		source.sendFeedback(Component.literal(
				"Stash found infos: rarity=" + rarity + ", coords=" + coordinates
						+ (comment == null ? "" : ", comment=" + comment)
		));

		sendEventToFlask(source, rarity, coordinates, comment);

		return 1;
	}

	private static boolean isOnAllowedServer() {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		if (server == null || server.ip == null) {
			return false;
		}
		return server.ip.toLowerCase(Locale.ROOT).contains(ALLOWED_SERVER);
	}

	private static String escapeJson(String value) {
		return value.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	private static void sendEventToFlask(FabricClientCommandSource source, String rarity, Vec3 coordinates, String comment) {
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
					Minecraft.getInstance().execute(() -> {
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
							// ignoré
						}

						if (messageText != null) {
							String finalMessage = messageText;
							ChatFormatting color = "error".equalsIgnoreCase(status)
									? ChatFormatting.RED
									: ChatFormatting.GREEN;
							source.sendFeedback(Component.literal(finalMessage).withStyle(color));
						} else {
							source.sendFeedback(Component.literal(
									"Flask (" + response.statusCode() + "): " + response.body()
							));
						}
					});
				})
				.exceptionally(ex -> {
					McciEvent.LOGGER.error("Error, try to redo the command", ex);
					Minecraft.getInstance().execute(() ->
							source.sendError(Component.literal("Error: can't contact api")));
					return null;
				});
	}
}