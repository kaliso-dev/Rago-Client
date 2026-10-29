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
 * every detail on its own: font (Minecraft, Poppins, Inter), size, server colours or a custom
 * colour, shadow, background colour and opacity, round corners, border, and whether the name
 * shows faintly through walls. Who gets a name tag, and when, follows vanilla's rules (distance,
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
	private final IntOption scale = addOption(new IntOption("nametag_scale", 100, 50, 200));
	private final ChoiceOption style;
	private final ChoiceOption font;
	private final BooleanOption shadow;
	private final ChoiceOption colorMode;
	private final ColorOption nameColor;
	private final BooleanOption background;
	private final ColorOption backgroundColor;
	private final IntOption radius;
	private final IntOption padding;
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
		nameColor = addOption(new ColorOption("name_color", 0xFFFFFFFF));
		nameColor.showIf(() -> colorMode.is("custom"));
		section("background");
		background = addOption(new BooleanOption("background", true));
		backgroundColor = addOption(new ColorOption("background_color", 0x40000000));
		backgroundColor.showIf(background::get);
		radius = addOption(new IntOption("corner_radius", 0, 0, 6));
		radius.showIf(background::get);
		padding = addOption(new IntOption("padding", 1, 0, 6));
		padding.showIf(background::get);
		border = addOption(new BooleanOption("border", false));
		borderColor = addOption(new ColorOption("border_color", 0x60FFFFFF));
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
				padding.set(2);
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
				padding.set(2);
				border.set(true);
				borderColor.set(Theme.ACCENT);
				break;
			default: // vanilla
				font.set(HudText.MINECRAFT);
				shadow.set(false);
				background.set(true);
				backgroundColor.set(0x40000000);
				radius.set(0);
				padding.set(1);
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
		float units = Math.max(9 * 1.15f, 8 + padding.get() * 2 + 2.35f);
		return units * WorldDraw.LABEL_SCALE * scale.get() / 100.0;
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
		label(name, x, top, z, 0, throughWalls.get(), 0xFF);
	}

	/**
	 * Queues one label facing the camera at the height vanilla gives a name tag anchored at
	 * {@code anchorY} (the top of vanilla's text). {@code seeThrough} adds the faint copy visible
	 * through walls; {@code alpha} is the text opacity where the player is visible. Labels are drawn
	 * all together after the world (see {@link LabelBatch}).
	 */
	public void label(String text, double x, double anchorY, double z, float drop, boolean seeThrough, int alpha) {
		String shown = colorMode.is("custom") ? TtfFont.strip(text) : text;
		int color = colorMode.is("custom") ? nameColor.get() : 0xFFFFFFFF;
		float s = WorldDraw.LABEL_SCALE * scale.get() / 100f;
		int pad = background.get() ? padding.get() : 0;
		TtfFont ttf = HudText.ttf(font.get());
		float em = HudText.em(font.get());
		float w = LabelBatch.width(ttf, em, WORLD_SHARPNESS, shown);
		float half = w / 2f;
		// keep the bottom of the text where vanilla's is, so a bigger tag grows upwards, away from the head
		batch.begin(x, anchorY, z, s, (drop + 8) * WorldDraw.LABEL_SCALE / s - 8 - pad);
		int back = seeThrough ? LabelBatch.SEE_THROUGH : LabelBatch.DEPTH;
		float bx = -half - 1 - pad;
		float by = -1 - pad;
		float bw = w + 2 + pad * 2;
		float bh = 9 + pad * 2;
		if (background.get()) {
			batch.round(back, bx, by, bw, bh, radius.get(), backgroundColor.get());
		}
		if (border.get()) {
			batch.outline(back, bx, by, bw, bh, radius.get(), 0.75f, borderColor.get());
		}
		int a = Math.min(alpha, color >>> 24);
		boolean ok = true;
		if (seeThrough) {
			ok = batch.text(LabelBatch.SEE_THROUGH, ttf, em, WORLD_SHARPNESS, shown, -half, 0, 0x20000000 | color & 0xFFFFFF, false);
		}
		if (ok) {
			ok = batch.text(seeThrough ? LabelBatch.SOLID : LabelBatch.DEPTH, ttf, em, WORLD_SHARPNESS, shown, -half, 0,
				a << 24 | color & 0xFFFFFF, shadow.get());
		}
		if (!ok) {
			// characters outside the Minecraft font's ASCII page (or the Unicode font): vanilla draws it
			WorldDraw.label(shown, x, anchorY - (drop + 8) * WorldDraw.LABEL_SCALE + (8 + pad) * s, z, false);
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
		batch.flush();
	}
}
