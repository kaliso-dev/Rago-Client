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
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Players' name tags drawn Rago's way: a style preset (Vanilla, Rago, Minimal, Outline) and then
 * every detail on its own: font (Minecraft, Poppins, Inter), server colours or a custom colour,
 * shadow, background colour and opacity, round corners, border, and whether the name shows faintly
 * through walls. The size can go from half of vanilla's to a quarter more, in the world like
 * vanilla's (a far name still shrinks with distance, never a fixed size on screen): bigger would
 * make players easier to spot from afar and through walls. For the same reason the background can
 * go from clear to solid only where the player is in sight: through walls it never shows more than
 * vanilla's, and the tag can be moved down (by a fifth of a block at most), never up above cover.
 * Who gets a name tag, and when, follows vanilla's rules (distance, sneaking, team visibility,
 * invisibility); the below-name score line is kept. Your own name can be shown in third person. A
 * tag faces the camera from any angle: in 3D turned to its yaw and pitch like vanilla's (with the
 * front view, F5 twice, done right), in 2D flat and level on the screen.
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
	/** Percent of vanilla's size, in the world: at most a quarter bigger (see the class comment). */
	private final IntOption scale = addOption(new IntOption("nametag_scale", 100, 50, 125));
	/** How far below vanilla's place the tag sits, in hundredths of a block: down only, a fifth at most. */
	private final IntOption lower = addOption(new IntOption("nametag_lower", 0, 0, 20).decimals(2));
	/** 3D: turned to the camera's yaw and pitch, as vanilla's; 2D: flat on the screen, always level. */
	private final ChoiceOption mode = addOption(new ChoiceOption("nametag_mode", "3d", "3d", "2d"));
	private final ChoiceOption style;
	private final ChoiceOption font;
	private final BooleanOption shadow;
	private final ChoiceOption colorMode;
	private final ColorOption nameColor;
	private final BooleanOption background;
	private final ColorOption backgroundColor;
	/** Opacity of the background in percent, where the player is in sight; vanilla's is 25. */
	private final IntOption backgroundOpacity;
	private final IntOption radius;
	/** Vanilla's space around the name; not a setting, as the tag keeps vanilla's size. */
	private static final int PADDING = 1;
	/**
	 * Vanilla's name tag background: black at a quarter opacity. The colour's own opacity is fixed at
	 * it (the opacity is its own setting), and the copy seen through walls never goes beyond it.
	 */
	private static final int BACKGROUND_ALPHA = 0x40;
	/** {@link #BACKGROUND_ALPHA} in percent, the background opacity's default and the styles' value. */
	private static final int VANILLA_OPACITY = 25;
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
		backgroundOpacity = addOption(new IntOption("tag_background_opacity", VANILLA_OPACITY, 0, 100));
		backgroundOpacity.showIf(background::get);
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
		// every style has vanilla's background opacity, as they had before it could be set
		backgroundOpacity.set(VANILLA_OPACITY);
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

	/** How far below vanilla's place the name tags are drawn, in blocks, for labels stacked above them. */
	public double lowerBy() {
		return isEnabled() ? lower.get() / 100.0 : 0;
	}

	/** Height of one name tag in blocks, for labels stacked above it (Level Head). */
	public double lineHeight() {
		if (!isEnabled()) {
			return NameTags.LINE;
		}
		float units = Math.max(9 * 1.15f, 8 + PADDING * 2 + 2.35f);
		return units * WorldDraw.LABEL_SCALE * scale.get() / 100f;
	}

	@SubscribeEvent(priority = EventPriority.HIGH)
	public void onNamePre(RenderLivingEvent.Specials.Pre<EntityLivingBase> event) {
		if (!isEnabled() || !(event.entity instanceof EntityPlayer)) {
			return;
		}
		long t = gg.rago.debug.FrameProfile.ON ? gg.rago.debug.FrameProfile.start() : 0;
		namePre(event);
		if (gg.rago.debug.FrameProfile.ON) {
			gg.rago.debug.FrameProfile.add(gg.rago.debug.FrameProfile.NAMES_QUEUED, System.nanoTime() - t);
		}
	}

	private void namePre(RenderLivingEvent.Specials.Pre<EntityLivingBase> event) {
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
		double base = y + p.height + 0.5 - (p.isChild() ? p.height / 2 : 0) - lower.get() / 100.0;
		Name n = name(p);
		String name = n.text;
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
			int points = score.getScorePoints();
			String objective = below.getDisplayName();
			if (n.belowText == null || n.belowPoints != points || !objective.equals(n.belowObjective)) {
				// the line under the name (health in Bed Wars) is made when it changes, not every frame
				n.belowPoints = points;
				n.belowObjective = objective;
				n.belowText = points + " " + objective;
			}
			label(n.belowText, x, top, z, 0, throughWalls.get(), 0xFF);
			top += lineHeight();
		}
		RagoBadge badge = gg.rago.hooks.Hooks.ragoBadge;
		int logo = badge != null && badge.onNametag() ? badge.logoColor(p.getUniqueID()) : 0;
		if (logo == 0) {
			label(name, x, top, z, 0, throughWalls.get(), 0xFF);
			return;
		}
		boolean creator = badge.creator(p.getUniqueID());
		label(name, x, top, z, 0, throughWalls.get(), 0xFF, badge.texture(p.getUniqueID()), logo, creator ? p.getName() : null);
	}

	/** A player's name as the game shows it, what it was made from, and the tick it was made. */
	private static final class Name {
		int tick = -1;
		Object team;
		String prefix;
		String suffix;
		String base;
		String text;
		/** The line under the name (below-name objective), with what it was made from. */
		int belowPoints;
		String belowObjective;
		String belowText;
	}

	/** Names are made again at least this often, for mods that change them without a team change. */
	private static final int NAME_REFRESH_TICKS = 20;

	private final Map<EntityPlayer, Name> names = new WeakHashMap<>();
	private World namesWorld;

	/**
	 * The player's name with its team colours. The game builds a chat message with a click and a
	 * hover action (an NBT tag turned to text) each time it is asked, so the name is made again only
	 * when the team, its prefix or suffix or the name change, and once a second whatever happens.
	 */
	private Name name(EntityPlayer p) {
		if (p.worldObj != namesWorld) {
			namesWorld = p.worldObj;
			names.clear();
		}
		Name n = names.get(p);
		if (n == null) {
			n = new Name();
			names.put(p, n);
		}
		ScorePlayerTeam team = p.getWorldScoreboard().getPlayersTeam(p.getName());
		String prefix = team == null ? null : team.getColorPrefix();
		String suffix = team == null ? null : team.getColorSuffix();
		String base = p.getDisplayNameString();
		int age = p.ticksExisted - n.tick;
		if (n.text == null || age < 0 || age >= NAME_REFRESH_TICKS || n.team != team || n.prefix != prefix || n.suffix != suffix || n.base != base) {
			n.tick = p.ticksExisted;
			n.team = team;
			n.prefix = prefix;
			n.suffix = suffix;
			n.base = base;
			n.text = p.getDisplayName().getFormattedText();
		}
		return n;
	}

	/**
	 * Queues one label facing the camera at the height vanilla gives a name tag anchored at
	 * {@code anchorY} (the top of vanilla's text). {@code seeThrough} adds the faint copy visible
	 * through walls; {@code alpha} is the text opacity where the player is visible. Labels are drawn
	 * all together after the world (see {@link LabelBatch}).
	 */
	public void label(String text, double x, double anchorY, double z, float drop, boolean seeThrough, int alpha) {
		label(text, x, anchorY, z, drop, seeThrough, alpha, 0, 0, null);
	}

	/**
	 * {@link #label}, with the Rago logo (GL texture {@code logoTexture}, none for 0) before the text in
	 * colour {@code logo}; for the creator ({@code creator}: their name), the name in pink and the
	 * crown and sparkle after it.
	 */
	private void label(String text, double x, double anchorY, double z, float drop, boolean seeThrough, int alpha,
		int logoTexture, int logo, String creator) {
		String shown = colorMode.is("custom") ? stripped(text) : text;
		int color = colorMode.is("custom") ? nameColor.get() : 0xFFFFFFFF;
		float s = WorldDraw.LABEL_SCALE * scale.get() / 100f;
		int pad = background.get() ? PADDING : 0;
		TtfFont ttf = HudText.ttf(font.get());
		float em = HudText.em(font.get());
		float logoW = logoTexture != 0 ? RagoBadge.LOGO_SIZE + 2 : 0;
		RagoBadge badge = creator != null ? gg.rago.hooks.Hooks.ragoBadge : null;
		String nicked = badge != null ? gg.rago.hooks.Hooks.nick(shown) : shown;
		// the crown and sparkle, unless the vanilla font draws the label (characters outside its ASCII page)
		int icons = badge != null && LabelBatch.canDraw(ttf, nicked) ? badge.iconsTexture() : 0;
		// the creator's name split from the text before and after it, the icons in between
		String before = shown;
		String name = "";
		String after = "";
		if (icons != 0) {
			int at = RagoBadge.nameAt(nicked, creator) >= 0 ? RagoBadge.nameAt(shown, creator) : -1;
			if (at >= 0) {
				before = shown.substring(0, at);
				name = shown.substring(at, at + creator.length());
				after = RagoBadge.codesAt(shown.substring(0, at)) + shown.substring(at + creator.length());
			}
		}
		// the styles in force before the creator's name; worked out only when there is one (this runs
		// for every name tag, every frame)
		String style = name.isEmpty() ? "" : RagoBadge.stylesAt(before);
		String whole = name.isEmpty() ? before : before + name;
		float beforeW = LabelBatch.width(ttf, em, WORLD_SHARPNESS, before);
		float nameW = name.isEmpty() ? 0 : LabelBatch.width(ttf, em, WORLD_SHARPNESS, style + name);
		float iconsW = icons != 0 ? RagoBadge.ICONS_SPACE : 0;
		float afterW = after.isEmpty() ? 0 : LabelBatch.width(ttf, em, WORLD_SHARPNESS, after);
		float w = logoW + beforeW + nameW + iconsW + afterW;
		float half = w / 2f;
		// keep the bottom of the text where vanilla's is, so a bigger tag grows upwards, away from the head
		batch.flat(mode.is("2d"));
		batch.begin(x, anchorY, z, s, (drop + 8) * WorldDraw.LABEL_SCALE / s - 8 - pad);
		// as vanilla: the tag is drawn normally where the player can be seen, and only a faint copy
		// (background, border and text at vanilla's faintness) shows through walls, whatever the colours
		float wall = seeThrough ? WALL_OPACITY : 0;
		float bx = -half - 1 - pad;
		float by = -1 - pad;
		float bw = w + 2 + pad * 2;
		float bh = 9 + pad * 2;
		if (background.get()) {
			// any opacity where the player is in sight, never more than vanilla's through walls
			int bg = backgroundAlpha(backgroundOpacity.get());
			layered(bx, by, bw, bh, wallAlpha(bg / 255f, wall), bg << 24 | backgroundColor.get() & 0xFFFFFF, false);
		}
		if (border.get()) {
			int line = borderColor.get();
			layered(bx, by, bw, bh, (line >>> 24) / 255f * wall, line, true);
		}
		int a = Math.min(alpha, color >>> 24);
		boolean ok = true;
		int faint = Math.round(0x40 * wall * a / 255f);
		float tx = -half + logoW;
		float afterX = tx + beforeW + nameW + iconsW;
		int layer = seeThrough ? LabelBatch.SOLID : LabelBatch.DEPTH;
		if (faint > 0) {
			ok = batch.text(LabelBatch.SEE_THROUGH, ttf, em, WORLD_SHARPNESS, whole, tx, 0, faint << 24 | color & 0xFFFFFF, false);
			if (ok && !after.isEmpty()) {
				ok = batch.text(LabelBatch.SEE_THROUGH, ttf, em, WORLD_SHARPNESS, after, afterX, 0, faint << 24 | color & 0xFFFFFF, false);
			}
		}
		if (ok && !name.isEmpty() && RagoBadge.pinkName()) {
			// the creator's name, letter by letter in its shades of pink
			if (!before.isEmpty()) {
				ok = batch.text(layer, ttf, em, WORLD_SHARPNESS, before, tx, 0, a << 24 | color & 0xFFFFFF, shadow.get());
			}
			float cx = tx + beforeW;
			long now = Minecraft.getSystemTime();
			for (int i = 0; ok && i < name.length(); i++) {
				String ch = style + name.charAt(i);
				ok = batch.text(layer, ttf, em, WORLD_SHARPNESS, ch, cx, 0, a << 24 | RagoBadge.creatorShade(i, name.length(), now), shadow.get());
				cx += LabelBatch.width(ttf, em, WORLD_SHARPNESS, ch);
			}
		} else if (ok) {
			ok = batch.text(layer, ttf, em, WORLD_SHARPNESS, whole, tx, 0, a << 24 | color & 0xFFFFFF, shadow.get());
		}
		if (ok && !after.isEmpty()) {
			ok = batch.text(layer, ttf, em, WORLD_SHARPNESS, after, afterX, 0, a << 24 | color & 0xFFFFFF, shadow.get());
		}
		if (ok && logoTexture != 0) {
			batch.image(layer, logoTexture, -half, -0.5f, RagoBadge.LOGO_SIZE, RagoBadge.LOGO_SIZE, logo);
		}
		if (ok && icons != 0) {
			batch.image(layer, icons, tx + beforeW + nameW + 1.5f, -0.5f, RagoBadge.ICONS_W, RagoBadge.ICONS_H, a << 24 | 0xFFFFFF);
		}
		if (!ok) {
			// characters outside the Minecraft font's ASCII page (or the Unicode font): vanilla draws it
			WorldDraw.label(shown, x, anchorY - (drop + 8) * WorldDraw.LABEL_SCALE + (8 + pad) * s, z, false);
		}
	}

	/** Texts without their § codes, for the custom colour: the same names are drawn every frame. */
	private final Map<String, String> stripped = new HashMap<>();

	private String stripped(String text) {
		String s = stripped.get(text);
		if (s == null) {
			if (stripped.size() >= 512) {
				stripped.clear();
			}
			s = TtfFont.strip(text);
			stripped.put(text, s);
		}
		return s;
	}

	/** The background's opacity setting (percent) as an alpha from 0 to 255; vanilla's 25 % gives its 0x40. */
	static int backgroundAlpha(int percent) {
		return Math.round(Math.max(0, Math.min(100, percent)) * 255 / 100f);
	}

	/**
	 * Opacity (0 to 1) of the copy of a background seen through walls, for a background of opacity
	 * {@code alpha} (0 to 1) and a see-through share {@code wall}: never more than vanilla's background
	 * would give, so a darker background cannot show players hiding behind blocks better.
	 */
	static float wallAlpha(float alpha, float wall) {
		return Math.min(alpha, BACKGROUND_ALPHA / 255f) * wall;
	}

	/**
	 * Opacity of the depth-tested part drawn over a see-through copy of opacity {@code faint}, so
	 * that where the player is in sight the two together have opacity {@code alpha}.
	 */
	static float restAlpha(float alpha, float faint) {
		return faint >= 1 ? 0 : Math.max(0, (alpha - faint) / (1 - faint));
	}

	/**
	 * A background or border: a copy at opacity {@code faint} that ignores depth (seen through
	 * walls), and the depth-tested part with the rest, so where the player is visible the two add up
	 * to the set colour.
	 */
	private void layered(float x, float y, float w, float h, float faint, int argb, boolean outline) {
		float a = (argb >>> 24) / 255f;
		float rest = restAlpha(a, faint);
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
		long t = gg.rago.debug.FrameProfile.ON ? gg.rago.debug.FrameProfile.start() : 0;
		batch.flush();
		if (gg.rago.debug.FrameProfile.ON) {
			gg.rago.debug.FrameProfile.add(gg.rago.debug.FrameProfile.NAMETAGS, System.nanoTime() - t);
		}
	}
}
