package com.livelyvillagers;

import com.livelyvillagers.Lines.Topic;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Map;

/** Gifts thrown by players: one at a time, held for a moment, the first each day raises reputation. */
public final class Gifts {
	/** Ticks a villager ignores further gifts after taking one, so a thrown stack isn't eaten whole. */
	private static final int COOLDOWN = 60;
	/** Ticks the villager shows the gift off in its hand. */
	private static final int HOLD = 100;

	public static boolean isGift(ItemStack stack) {
		return isLoved(stack) || (stack.is(ItemTags.FLOWERS) && !stack.is(Items.WITHER_ROSE))
			|| stack.is(Items.COOKIE) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES);
	}

	private static boolean isLoved(ItemStack stack) {
		return stack.is(Items.CAKE) || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.HONEY_BOTTLE);
	}

	/** Gifts only count when a player threw them; dropped flowers from farms are left alone. */
	public static boolean isPlayerGift(ItemEntity item) {
		return isGift(item.getItem()) && item.getOwner() instanceof Player;
	}

	public static boolean acceptsGiftNow(Villager v) {
		return !v.isSleeping() && v.level().getGameTime() >= LivelyVillagers.state(v).nextGiftAt;
	}

	/** Called instead of the vanilla pickup for a player's gift: take one, hold it, react. */
	public static void receive(Villager v, ItemEntity itemEntity) {
		if (!(v.level() instanceof ServerLevel level) || !(itemEntity.getOwner() instanceof ServerPlayer player)) {
			return;
		}
		ItemStack stack = itemEntity.getItem();
		Item item = stack.getItem();
		boolean loved = isLoved(stack);
		VillagerState state = LivelyVillagers.state(v);
		state.nextGiftAt = level.getGameTime() + COOLDOWN;
		v.take(itemEntity, 1);
		stack.shrink(1);
		if (stack.isEmpty()) {
			itemEntity.discard();
		}
		if (!v.isTrading() && (v.getMainHandItem().isEmpty() || state.heldGift != null)) {
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(item));
			state.heldGift = item;
			state.clearHandAt = level.getGameTime() + HOLD;
		}

		Speech.lookAt(v, player);
		String itemName = new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT);
		Map<String, String> vars = Map.of("item", itemName, "player", player.getScoreboardName());
		VillagerMind mind = LivelyVillagers.mind(v);
		long day = level.getDayTime() / 24000L;
		if (mind.lastGiftDay().getOrDefault(player.getUUID(), -1L) == day) {
			level.broadcastEntityEvent(v, Speech.HAPPY);
			Speech.say(v, Topic.GIFT_AGAIN, SoundEvents.VILLAGER_YES, vars);
			return;
		}
		int rep = loved ? LivelyConfig.get().lovedGiftReputation : LivelyConfig.get().giftReputation;
		if (rep > 0) {
			v.getGossips().add(player.getUUID(), GossipType.MINOR_POSITIVE, rep);
		}
		LivelyVillagers.setMind(v, mind.withGift(player.getUUID(), day));
		level.broadcastEntityEvent(v, Speech.HEARTS);
		Speech.say(v, loved ? Topic.GIFT_LOVE : Topic.GIFT_LIKE, SoundEvents.VILLAGER_CELEBRATE, vars);
	}

	/**
	 * Keeps a received gift in the villager's hand for a few seconds. ShowTradesToPlayer empties the
	 * hand every tick while a player is near; this runs after the brain, so the gift is what gets
	 * synced. Trade previews still win because they fill the hand.
	 */
	static void tickHeldGift(Villager v, VillagerState state, long now) {
		if (state.heldGift == null) {
			return;
		}
		if (now >= state.clearHandAt) {
			if (!v.isTrading() && v.getMainHandItem().is(state.heldGift)) {
				v.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
			state.heldGift = null;
		} else if (!v.isTrading() && v.getMainHandItem().isEmpty()) {
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(state.heldGift));
		}
	}

	private Gifts() {
	}
}
