package com.treecapitator.simple;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("tree_capitator_simple")
public class TreeCapitatorSimple {

	/** Máximo de troncos que se talan de una sola vez (seguridad). */
	private static final int MAX_LOGS = 256;

	public TreeCapitatorSimple() {
		// Forge 26.x usa EventBus 7: cada evento tiene su propio BUS.
		// El valor devuelto es "cancelar": false = no cancelar (el bloque base se rompe normal).
		BlockEvent.BreakEvent.BUS.addListener(TreeCapitatorSimple::onBlockBreak);
	}

	private static boolean onBlockBreak(BlockEvent.BreakEvent event) {
		Player player = event.getPlayer();
		if (player == null || !player.isShiftKeyDown()) return false;       // sin agacharse: normal
		if (!(event.getLevel() instanceof ServerLevel level)) return false; // solo en el servidor

		BlockPos pos = event.getPos();
		BlockState state = event.getState();
		if (!state.is(BlockTags.LOGS)) return false;                        // solo troncos
		if (level.getBlockState(pos.below()).is(BlockTags.LOGS)) return false; // solo la base

		ItemStack tool = player.getMainHandItem();
		if (!tool.is(ItemTags.AXES)) return false;                          // solo con hacha

		chop(level, player, tool, pos);
		return false; // no cancelamos: la base la rompe el juego normalmente (cuesta 1 de durabilidad)
	}

	private static void chop(ServerLevel level, Player player, ItemStack tool, BlockPos origin) {
		// Durabilidad restante (el hacha no se romperá a mitad de la tala).
		int remaining = tool.getMaxDamage() - tool.getDamageValue();
		// Reservamos 1 para el bloque base, que el juego rompe después de este evento.
		int allowed = Math.min(MAX_LOGS, Math.max(0, remaining - 1));
		if (allowed <= 0) return;

		List<BlockPos> logs = new ArrayList<>();
		Set<BlockPos> visited = new HashSet<>();
		ArrayDeque<BlockPos> queue = new ArrayDeque<>();
		visited.add(origin);
		queue.add(origin);

		while (!queue.isEmpty() && logs.size() < allowed) {
			BlockPos current = queue.poll();
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
			if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;
			if (level.destroyBlock(p, true, player)) {
				broken++;
			}
		}

		if (broken > 0) {
			// Un punto de durabilidad por cada tronco extra.
			tool.hurtAndBreak(broken, player, EquipmentSlot.MAINHAND);
		}
	}
}
