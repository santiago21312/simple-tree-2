package com.treecapitator.simple;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class TreeCapitatorSimple implements ModInitializer {

	/** Máximo de troncos que se talan de una sola vez (seguridad). */
	private static final int MAX_LOGS = 256;

	@Override
	public void onInitialize() {
		// AFTER: el bloque base ya se rompió de forma normal (con su desgaste de 1 y su drop).
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide()) return;
			if (!player.isShiftKeyDown()) return;          // sin agacharse: comportamiento normal
			if (!state.is(BlockTags.LOGS)) return;         // solo troncos
			if (level.getBlockState(pos.below()).is(BlockTags.LOGS)) return; // solo la base del árbol

			ItemStack tool = player.getMainHandItem();
			if (!tool.is(ItemTags.AXES)) return;           // solo con hacha

			chop(level, player, tool, pos);
		});
	}

	private static void chop(Level level, Player player, ItemStack tool, BlockPos origin) {
		// Durabilidad restante (el hacha no se romperá a mitad de la tala).
		int remaining = tool.getMaxDamage() - tool.getDamageValue();
		// Reservamos 1 por si el hacha ya quedó al límite tras romper la base.
		int allowed = Math.min(MAX_LOGS, Math.max(0, remaining - 1));
		if (allowed <= 0) return;

		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		queue.add(origin);

		while (!queue.isEmpty() && logs.size() < allowed) {
			BlockPos current = queue.poll();
			// Vecinos en 3x3x3, solo a la misma altura o por encima de la base.
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = 0; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) continue;
						BlockPos next = current.offset(dx, dy, dz);
						if (next.getY() < origin.getY()) continue;
						if (!visited.add(next)) continue;
						if (!level.getBlockState(next).is(BlockTags.LOGS)) continue;
						logs.add(next);
						queue.add(next);
						if (logs.size() >= allowed) break;
					}
					if (logs.size() >= allowed) break;
				}
				if (logs.size() >= allowed) break;
			}
		}

		if (logs.isEmpty()) return;

		int broken = 0;
		for (BlockPos p : logs) {
			BlockState s = level.getBlockState(p);
			if (!s.is(BlockTags.LOGS)) continue;
			// true = suelta ítems; player = quien lo rompe
			if (level.destroyBlock(p, true, player)) {
				broken++;
			}
		}

		if (broken > 0) {
			// Un punto de durabilidad por cada tronco extra (la base ya costó 1 al romperla).
			tool.hurtAndBreak(broken, player, EquipmentSlot.MAINHAND);
		}
	}
}
