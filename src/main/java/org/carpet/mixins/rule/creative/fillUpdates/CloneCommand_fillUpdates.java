package org.carpet.mixins.rule.creative.fillUpdates;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CloneCommand;
import net.minecraft.server.command.source.CommandSource;
import org.carpet.settings.CarpetSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.carpet.settings.CarpetSettings.*;

@Mixin(CloneCommand.class)
public abstract class CloneCommand_fillUpdates {
	@Unique
	private static boolean[] recordedYeets = new boolean[5];

	@Inject(method = "run", at = @At("HEAD"))
	public void saveYeets(MinecraftServer server, CommandSource source, String[] args, CallbackInfo ci) {
		if (!CarpetSettings.fillUpdates) {
			recordedYeets[0] = yeetComparatorUpdates;
			recordedYeets[1] = yeetNeighborUpdates;
			recordedYeets[2] = yeetObserverUpdates;
			recordedYeets[3] = yeetInitialUpdates;
			recordedYeets[4] = yeetRemovalUpdates;
		}
		yeetComparatorUpdates = yeetNeighborUpdates = yeetObserverUpdates =
			yeetInitialUpdates = yeetRemovalUpdates = true;
	}

	@Inject(method = "run", at = @At("TAIL"))
	public void loadYeets(MinecraftServer server, CommandSource source, String[] args, CallbackInfo ci) {
		if (!CarpetSettings.fillUpdates) {
			yeetComparatorUpdates = recordedYeets[0];
			yeetNeighborUpdates = recordedYeets[1];
			yeetObserverUpdates = recordedYeets[2];
			yeetInitialUpdates = recordedYeets[3];
			yeetRemovalUpdates = recordedYeets[4];
		}
	}
}
