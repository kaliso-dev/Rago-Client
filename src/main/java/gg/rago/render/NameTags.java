package gg.rago.render;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Team;

/** Where and when vanilla draws a player's name, so extra lines can go right above it. */
public final class NameTags {
	/** Vanilla's distance for name tags: 64 blocks, 32 while sneaking. */
	private static final double RANGE = 64;
	private static final double RANGE_SNEAK = 32;
	/** One line of text in blocks, as vanilla spaces the below-name score and the name. */
	public static final double LINE = 9 * 1.15 * WorldDraw.LABEL_SCALE;

	private NameTags() {
	}

	/** Whether vanilla draws {@code e}'s name tag (for another player), following its rules. */
	public static boolean visible(EntityPlayer e) {
		Minecraft mc = Minecraft.getMinecraft();
		EntityPlayer me = mc.thePlayer;
		if (me == null || e == mc.getRenderViewEntity()) {
			return false;
		}
		double range = e.isSneaking() ? RANGE_SNEAK : RANGE;
		return e.getDistanceSqToEntity(mc.getRenderViewEntity()) < range * range && allowed(e, me);
	}

	/** RendererLivingEntity.canRenderName: a team's name tag rule decides on its own. */
	private static boolean allowed(EntityPlayer e, EntityPlayer me) {
		Team team = e.getTeam();
		Team mine = me.getTeam();
		if (team != null) {
			switch (team.getNameTagVisibility()) {
				case NEVER:
					return false;
				case HIDE_FOR_OTHER_TEAMS:
					return mine == null || team.isSameTeam(mine);
				case HIDE_FOR_OWN_TEAM:
					return mine == null || !team.isSameTeam(mine);
				default:
					return true;
			}
		}
		return Minecraft.isGuiEnabled() && !e.isInvisibleToPlayer(me) && e.riddenByEntity == null;
	}

	/** Top of the name tag above an entity drawn at camera-relative y, including the below-name score line. */
	public static double nameTop(EntityLivingBase e, double y) {
		return nameTop(e, y, LINE);
	}

	/** {@link #nameTop(EntityLivingBase, double)} with name tags {@code line} blocks tall. */
	public static double nameTop(EntityLivingBase e, double y, double line) {
		double top = y + e.height + 0.5 - (e.isChild() ? e.height / 2 : 0);
		Minecraft mc = Minecraft.getMinecraft();
		if (!e.isSneaking() && e instanceof EntityPlayer && e.getDistanceSqToEntity(mc.getRenderViewEntity()) < 100) {
			ScoreObjective below = ((EntityPlayer) e).getWorldScoreboard().getObjectiveInDisplaySlot(2);
			if (below != null) {
				top += line;
			}
		}
		return top;
	}
}
