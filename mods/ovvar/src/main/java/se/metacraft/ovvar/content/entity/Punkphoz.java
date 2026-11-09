package se.metacraft.ovvar.content.entity;

import de.tomalbrc.bil.api.AnimatedEntity;
import de.tomalbrc.bil.core.holder.entity.living.LivingEntityHolder;
import eu.pb4.polymer.virtualentity.api.attachment.EntityAttachment;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.UseItemGoal;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.Level;

/**
 * Punkphöz: a wandering trader in the traders' model (BIL). He wanders, trades and leaves like the vanilla one, but
 * drinks no invisibility potion (or milk) at dusk: his model has no invisible look to show.
 */
public class Punkphoz extends WanderingTrader implements AnimatedEntity {

	private final LivingEntityHolder<Punkphoz> holder;

	public Punkphoz(EntityType<? extends Punkphoz> type, Level level) {
		super(type, level);
		this.holder = new LivingEntityHolder<>(this, PunkphozEntities.model());
		EntityAttachment.ofTicking(this.holder, this);
	}

	@Override
	public LivingEntityHolder<Punkphoz> getHolder() {
		return holder;
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		goalSelector.removeAllGoals(g -> g instanceof UseItemGoal);
	}

	/** No stock yet: he offers nothing. */
	@Override
	protected void updateTrades(ServerLevel level) {
	}
}
