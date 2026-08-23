package com.mccievent;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
		});
	}
	private static int executeCommandWithSuggestions(CommandContext<CommandSourceStack> context) {
		LOGGER.info("command_with_suggestions executed");
		return 1;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
