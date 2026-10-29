package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.ColorOption;
import gg.rago.config.IntOption;
import gg.rago.config.Option;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.gui.Theme;
import gg.rago.gui.ui.TtfFont;
import gg.rago.hud.Styled;
import gg.rago.render.HudText;
import gg.rago.render.LabelBatch;
import gg.rago.render.NameTags;
import gg.rago.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Players' name tags drawn Rago's way: a style preset (Vanilla, Rago, Minimal, Outline) and then
 * every detail on its own: font (Minecraft, Poppins, Inter), server colours or a custom colour,
 * shadow, background colour, round corners, border, and whether the name shows faintly through
 * walls. Size and opacity stay vanilla's: only colours change, never how visible a name is. Who gets a name tag, and when, follows vanilla's rules (distance,
 * sneaking, team visibility, invisibility); the below-name score line is kept. Your own name can be
 * shown in third person.
 */
public final class Nametags extends Module implements Styled {
	public static final String[] STYLES = {"vanilla", "rago", "minimal", "outline"};
	/** Sharpness of TrueType text on name tags: glyphs are rasterised at this many pixels per label unit. */
	private static final float WORLD_SHARPNESS = 4f;
	/** Vanilla moves a sneaking player's name down by this many label units. */
	private static final float SNEAK_DROP = 9.374999f;

	private final LabelBatch batch = new LabelBatch();
	private final BooleanOption showOwn = addOption(new BooleanOption("show_own", false));
	private final BooleanOption throughWalls = addOption(new BooleanOption("through_walls", true));
	/**
	 * How visible a name is through walls, as a share of its normal look: vanilla's faint text, and
	 * not a setting, since a brighter name behind walls would show where players hide.
	 */
	private static final float WALL_OPACITY = 0.5f;
	/** Names show up to this many blocks away, as vanilla's 64 (32 when sneaking). */
	private final IntOption maxDistance = addOption(new IntOption("nametag_distance", 64, 8, 64));
	private final ChoiceOption style;
	private final ChoiceOption font;
	private final BooleanOption shadow;
	private final ChoiceOption colorMode;
	private final ColorOption nameColor;
	private final BooleanOption background;
	private final ColorOption backgroundColor;
	private final IntOption radius;
	/** Vanilla's space around the name; not a setting, as the tag keeps vanilla's size. */
	private static final int PADDING = 1;
	/** Vanilla's name tag background: black at a quarter opacity; only its colour can change. */
	private static final int BACKGROUND_ALPHA = 0x40;
	private final BooleanOption border;
	private final ColorOption borderColor;

	public Nametags() {
		super("nametags", FeatureClass.VISUAL, false);
		section("style");
		style = addOption(new ChoiceOption("style", "vanilla", STYLES).onSelect(this::applyStyle));
		font = addOption(new ChoiceOption("font", HudText.MINECRAFT, HudText.FONTS));
		shadow = addOption(new BooleanOption("shadow", false));
		section("text");
		colorMode = addOption(new ChoiceOption("name_color_mode", "server", "server", "custom"));
		// any colour, but as opaque as vanilla's text
		nameColor = addOption(new ColorOption("name_color", 0xFFFFFFFF).fixedAlpha(0xFF));
		nameColor.showIf(() -> colorMode.is("custom"));
		section("background");
		background = addOption(new BooleanOption("background", true));
		backgroundColor = addOption(new ColorOption("background_color", 0x40000000).fixedAlpha(BACKGROUND_ALPHA));
		backgroundColor.showIf(background::get);
		radius = addOption(new IntOption("corner_radius", 0, 0, 6));
		radius.showIf(background::get);
		border = addOption(new BooleanOption("border", false));
		borderColor = addOption(new ColorOption("border_color", 0x60FFFFFF).fixedAlpha(0x60));
		borderColor.showIf(border::get);
		section(null);
		applyStyle("vanilla");
		for (Option<?> o : options()) {
			o.makeCurrentDefault();
		}
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	@Override
	public String defaultStyle() {
		return "vanilla";
	}

	@Override
	public void applyStyle(String preset) {
		style.set(preset);
		switch (preset) {
			case "rago":
				font.set(HudText.POPPINS);
				shadow.set(false);
				background.set(true);
				backgroundColor.set(0xA0101014);
				radius.set(3);
				border.set(true);
				borderColor.set(0x30FFFFFF);
				break;
			case "minimal":
				font.set(HudText.INTER);
				shadow.set(true);
				background.set(false);
				border.set(false);
				break;
			case "outline":
				font.set(HudText.MINECRAFT);
				shadow.set(true);
				background.set(true);
				backgroundColor.set(0x30000000);
				radius.set(2);
				border.set(true);
				borderColor.set(Theme.ACCENT);
				break;
			default: // vanilla
				font.set(HudText.MINECRAFT);
				shadow.set(false);
				background.set(true);
				backgroundColor.set(0x40000000);
				radius.set(0);
				border.set(false);
		}
	}

	/** Whether your own name is drawn right now (third person), so other labels can go above it. */
	public boolean ownShown() {
		Minecraft mc = Minecraft.getMinecraft();
		return isEnabled() && showOwn.get() && mc.gameSettings.thirdPersonView != 0 && Minecraft.isGuiEnabled();
	}

	public boolean throughWalls() {
		return throughWalls.get();
	}

	/** Height of one name tag in blocks, for labels stacked above it (Level Head). */
	public double lineHeight() {
		if (!isEnabled()) {
			return NameTags.LINE;
		}
		float units = Math.max(9 * 1.15f, 8 + PADDING * 2 + 2.35f);
		return units * WorldDraw.LABEL_SCALE;
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onNamePre(RenderLivingEvent.Specials.Pre<EntityLivingBase> event) {
		if (!isEnabled() || !(event.entity instanceof EntityPlayer)) {
			return;
		}
		EntityPlayer p = (EntityPlayer) event.entity;
		Minecraft mc = Minecraft.getMinecraft();
		boolean self = p == mc.getRenderViewEntity();
		if (self) {
			// vanilla never names the camera's own player; draw it when asked, then let others add lines
			if (p == mc.thePlayer && ownShown() && !p.isInvisible()) {
				drawName(p, event.x, event.y, event.z);
			}
			return;
		}
		if (!NameTags.visible(p)) {
			return; // vanilla would not draw it either
		}
		if (p.getDistanceSqToEntity(mc.getRenderViewEntity()) > maxDistance.get() * (double) maxDistance.get()) {
			event.setCanceled(true); // nearer than vanilla's limit: neither Rago nor vanilla draws it
			return;
		}
		event.setCanceled(true);
		drawName(p, event.x, event.y, event.z);
		// cancelling skips the Post event; send it so labels above the name (Level Head) still show
		MinecraftForge.EVENT_BUS.post(new RenderLivingEvent.Specials.Post<>(event.entity, event.renderer, event.x, event.y, event.z));
	}

	private void drawName(EntityPlayer p, double x, double y, double z) {
		Minecraft mc = Minecraft.getMinecraft();
		double base = y + p.height + 0.5 - (p.isChild() ? p.height / 2 : 0);
		String name = p.getDisplayName().getFormattedText();
		if (p.isSneaking()) {
			// vanilla: lower, only where the player is visible, faint text
			label(name, x, base, z, SNEAK_DROP, false, 0x20);
			return;
		}
		double top = base;
		double distSq = p.getDistanceSqToEntity(mc.getRenderViewEntity());
		Scoreboard board = p.getWorldScoreboard();
		ScoreObjective below = board.getObjectiveInDisplaySlot(2);
		if (below != null && distSq < 100) {
			Score score = board.getValueFromObjective(p.getName(), below);
			label(score.getScorePoints() + " " + below.getDisplayName(), x, top, z, 0, throughWalls.get(), 0xFF);
			top += lineHeight();
		}
		RagoBadge badge = gg.rago.hooks.Hooks.ragoBadge;
		boolean logo = p == mc.thePlayer && badge != null && badge.onNametag();
		label(name, x, top, z, 0, throughWalls.get(), 0xFF, logo ? badge : null);
	}

	/**
	 * Queues one label facing the camera at the height vanilla gives a name tag anchored at
	 * {@code anchorY} (the top of vanilla's text). {@code seeThrough} adds the faint copy visible
	 * through walls; {@code alpha} is the text opacity where the player is visible. Labels are drawn
	 * all together after the world (see {@link LabelBatch}).
	 */
	public void label(String text, double x, double anchorY, double z, float drop, boolean seeThrough, int alpha) {
		label(text, x, anchorY, z, drop, seeThrough, alpha, null);
	}

	/** {@link #label}, with the Rago logo before the text when {@code logo} is given. */
	private void label(String text, double x, double anchorY, double z, float drop, boolean seeThrough, int alpha, RagoBadge logo) {
		String shown = colorMode.is("custom") ? TtfFont.strip(text) : text;
		int color = colorMode.is("custom") ? nameColor.get() : 0xFFFFFFFF;
		float s = WorldDraw.LABEL_SCALE;
		int pad = background.get() ? PADDING : 0;
		TtfFont ttf = HudText.ttf(font.get());
		float em = HudText.em(font.get());
		float logoW = logo != null ? RagoBadge.LOGO_SIZE + 2 : 0;
		float w = LabelBatch.width(ttf, em, WORLD_SHARPNESS, shown) + logoW;
		float half = w / 2f;
		// keep the bottom of the text where vanilla's is, so a bigger tag grows upwards, away from the head
		batch.begin(x, anchorY, z, s, (drop + 8) * WorldDraw.LABEL_SCALE / s - 8 - pad);
		// as vanilla: the tag is drawn normally where the player can be seen, and only a faint copy
		// (background, border and text at vanilla's faintness) shows through walls, whatever the colours
		float wall = seeThrough ? WALL_OPACITY : 0;
		float bx = -half - 1 - pad;
		float by = -1 - pad;
		float bw = w + 2 + pad * 2;
		float bh = 9 + pad * 2;
		if (background.get()) {
			layered(bx, by, bw, bh, wall, backgroundColor.get(), false);
		}
		if (border.get()) {
			layered(bx, by, bw, bh, wall, borderColor.get(), true);
		}
		int a = Math.min(alpha, color >>> 24);
		boolean ok = true;
		int faint = Math.round(0x40 * wall * a / 255f);
		float tx = -half + logoW;
		if (faint > 0) {
			ok = batch.text(LabelBatch.SEE_THROUGH, ttf, em, WORLD_SHARPNESS, shown, tx, 0, faint << 24 | color & 0xFFFFFF, false);
		}
		if (ok) {
			ok = batch.text(seeThrough ? LabelBatch.SOLID : LabelBatch.DEPTH, ttf, em, WORLD_SHARPNESS, shown, tx, 0,
				a << 24 | color & 0xFFFFFF, shadow.get());
		}
		int logoTexture = logo != null ? logo.texture() : 0;
		if (ok && logoTexture != 0) {
			batch.image(seeThrough ? LabelBatch.SOLID : LabelBatch.DEPTH, logoTexture, -half, -0.5f, RagoBadge.LOGO_SIZE, RagoBadge.LOGO_SIZE,
				logo.color());
		}
		if (!ok) {
			// characters outside the Minecraft font's ASCII page (or the Unicode font): vanilla draws it
			WorldDraw.label(shown, x, anchorY - (drop + 8) * WorldDraw.LABEL_SCALE + (8 + pad) * s, z, false);
		}
	}

	/**
	 * A background or border: a faint copy at {@code wall} of its opacity that ignores depth, and the
	 * depth-tested part with the rest, so where the player is visible the two add up to the set colour.
	 */
	private void layered(float x, float y, float w, float h, float wall, int argb, boolean outline) {
		float a = (argb >>> 24) / 255f;
		float faint = a * wall;
		float rest = faint >= 1 ? 0 : (a - faint) / (1 - faint);
		int rgb = argb & 0xFFFFFF;
		if (faint > 0.004f) {
			shape(LabelBatch.SEE_THROUGH, x, y, w, h, Math.round(faint * 255) << 24 | rgb, outline);
		}
		if (rest > 0.004f) {
			shape(LabelBatch.DEPTH, x, y, w, h, Math.round(rest * 255) << 24 | rgb, outline);
		}
	}

	private void shape(int layer, float x, float y, float w, float h, int argb, boolean outline) {
		if (outline) {
			batch.outline(layer, x, y, w, h, radius.get(), 0.75f, argb);
		} else {
			batch.round(layer, x, y, w, h, radius.get(), argb);
		}
	}

	@SubscribeEvent
	public void onRenderStart(TickEvent.RenderTickEvent event) {
		if (event.phase == TickEvent.Phase.START) {
			batch.clear(); // labels queued outside the world (an entity in a menu) are dropped
		}
	}

	@SubscribeEvent
	public void onWorldDrawn(RenderWorldLastEvent event) {
		long t = gg.rago.debug.FrameProfile.ON ? System.nanoTime() : 0;
		batch.flush();
		if (gg.rago.debug.FrameProfile.ON) {
			gg.rago.debug.FrameProfile.add(gg.rago.debug.FrameProfile.NAMETAGS, System.nanoTime() - t);
		}
	}
}
