package gg.rago.hud.elements;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.hud.Anchor;
import gg.rago.hud.HudElement;
import gg.rago.music.MediaSession;
import gg.rago.render.Paint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.io.File;

/**
 * Music: what plays on this PC (Spotify, Deezer, a browser...) with its cover, artist, time and a
 * progress bar, and keys for play/pause, next and previous, so nobody leaves the game for it.
 *
 * <p>Read from Windows' media controls by {@link MediaSession} in a separate low-priority process;
 * on the game's thread this only reads one field a frame, draws one cover, three lines and a bar,
 * keeps the cut titles until the track changes and the time text until its second changes, and
 * uploads a 128 x 128 cover once per track. The process only runs while the HUD is on.
 */
public final class MusicHud extends HudElement {
	private static final int PAD = 4;
	private static final int COVER = 30;
	private static final int GAP = 5;
	private static final int H = 2 * PAD + COVER;
	private static final int PANEL = 0x90000000;
	private static final int DIM = 0xFFAAAAAA;
	private static final int TRACK_BG = 0x40FFFFFF;
	private static final int BAR = 0xFF1ED760;
	private static final int NO_COVER = 0x30FFFFFF;

	private final IntOption width = addOption(new IntOption("music_width", 170, 120, 260));
	private final BooleanOption showCover = addOption(new BooleanOption("music_cover", true));
	private final BooleanOption hideIdle = addOption(new BooleanOption("music_hide_idle", true));

	private final KeyBinding toggleKey = new KeyBinding("key.rago.music_toggle", 0, "key.categories.rago");
	private final KeyBinding nextKey = new KeyBinding("key.rago.music_next", 0, "key.categories.rago");
	private final KeyBinding previousKey = new KeyBinding("key.rago.music_previous", 0, "key.categories.rago");

	private final MediaSession session = new MediaSession();

	private DynamicTexture coverTexture;
	private ResourceLocation coverLocation;
	private boolean hasCover;
	private final int[] coverId = new int[1];

	// what was cut to fit, kept until the track, the width or the font changes
	private MediaSession.Track cutFor;
	private int cutWidth = -1;
	private String cutFont;
	private String title = "";
	private String artist = "";
	private int timeSecond = -1;
	private int timeLength = -1;
	private String time = "";

	public MusicHud() {
		super("music", FeatureClass.INFORMATIONAL, false, Anchor.TOP_LEFT, 4, 60, 0xFFFFFFFF, true);
	}

	@Override
	public Category category() {
		return Category.HUD;
	}

	public KeyBinding[] keys() {
		return new KeyBinding[]{toggleKey, nextKey, previousKey};
	}

	@Override
	protected void onDisable() {
		session.stop();
	}

	@Override
	public void tick() {
		if (!MediaSession.supported()) {
			return;
		}
		session.ensureRunning(new File(Minecraft.getMinecraft().mcDataDir, "rago/music"));
		while (toggleKey.isPressed()) {
			session.send("toggle");
		}
		while (nextKey.isPressed()) {
			session.send("next");
		}
		while (previousKey.isPressed()) {
			session.send("previous");
		}
	}

	@Override
	public boolean hasContent() {
		if (!MediaSession.supported()) {
			return false;
		}
		MediaSession.Track t = session.track();
		return t != MediaSession.NONE && (t.playing || !hideIdle.get()) || session.unavailable() && !hideIdle.get();
	}

	@Override
	public int width(FontRenderer text, boolean placeholder) {
		return width.get();
	}

	@Override
	public int height(FontRenderer text, boolean placeholder) {
		return H;
	}

	@Override
	public void render(FontRenderer text, boolean placeholder) {
		MediaSession.Track t = session.track();
		boolean sample = placeholder && t == MediaSession.NONE;
		long now = System.nanoTime();
		int w = width.get();
		int textX = showCover.get() ? PAD + COVER + GAP : PAD;
		int textW = w - textX - PAD;

		double position;
		double duration;
		boolean playing;
		if (sample) {
			position = 83;
			duration = 225;
			playing = true;
			cut(I18n.format("rago.music.sample_title"), I18n.format("rago.music.sample_artist"), textW, null);
		} else if (t == MediaSession.NONE) {
			position = duration = 0;
			playing = false;
			cut(I18n.format(session.unavailable() ? "rago.music.unavailable" : "rago.music.nothing"), "", textW, null);
		} else {
			position = t.positionAt(now);
			duration = t.duration;
			playing = t.playing;
			if (t != cutFor || textW != cutWidth || !font.get().equals(cutFont)) {
				cut(t.title, t.artist, textW, t);
			}
		}
		updateTime(position, duration);
		if (showCover.get() && !sample) {
			uploadCover();
		}

		// the panel, the cover's frame, the bar: one batch
		WorldRenderer shapes = Paint.beginShapes();
		if (background.get()) {
			Paint.roundInto(shapes, 0, 0, w, H, 4, PANEL);
		}
		boolean coverShown = showCover.get() && hasCover && !sample && t != MediaSession.NONE;
		if (showCover.get() && !coverShown) {
			Paint.roundInto(shapes, PAD, PAD, COVER, COVER, 3, NO_COVER);
		}
		int barY = PAD + COVER - 3;
		if (duration > 0) {
			float k = (float) Math.max(0, Math.min(1, position / duration));
			Paint.roundInto(shapes, textX, barY, textW, 3, 1.5f, TRACK_BG);
			Paint.roundInto(shapes, textX, barY, Math.max(3, textW * k), 3, 1.5f, playing ? BAR : DIM);
		}
		Paint.endShapes();

		if (coverShown) {
			Minecraft.getMinecraft().getTextureManager().bindTexture(coverLocation);
			GlStateManager.enableTexture2D();
			GlStateManager.enableBlend();
			GlStateManager.color(1, 1, 1, 1);
			Gui.drawScaledCustomSizeModalRect(PAD, PAD, 0, 0, 1, 1, COVER, COVER, 1, 1);
		}
		if (showCover.get() && !playing && t != MediaSession.NONE && !sample) {
			// paused: two bars over the cover
			WorldRenderer pause = Paint.beginShapes();
			Paint.roundInto(pause, PAD, PAD, COVER, COVER, 3, 0x80000000);
			Paint.roundInto(pause, PAD + COVER / 2f - 5, PAD + COVER / 2f - 6, 3.5f, 12, 1, 0xFFFFFFFF);
			Paint.roundInto(pause, PAD + COVER / 2f + 1.5f, PAD + COVER / 2f - 6, 3.5f, 12, 1, 0xFFFFFFFF);
			Paint.endShapes();
		}

		drawText(text, title, textX, PAD + 1, textColor.get());
		if (!artist.isEmpty()) {
			drawText(text, artist, textX, PAD + 11, DIM);
		}
		if (duration > 0) {
			drawText(text, time, w - PAD - textWidth(time), PAD + 11, DIM);
		}
	}

	/** Cuts the title and artist to the width (the artist leaves room for the time). */
	private void cut(String fullTitle, String fullArtist, int textW, MediaSession.Track forTrack) {
		cutFor = forTrack;
		cutWidth = textW;
		cutFont = font.get();
		title = fit(fullTitle, textW);
		artist = fit(fullArtist, textW - textWidth("00:00 / 00:00") - 4);
	}

	private String fit(String s, int maxW) {
		if (textWidth(s) <= maxW) {
			return s;
		}
		int dots = textWidth("...");
		int n = s.length();
		while (n > 1 && textWidth(s.substring(0, n)) + dots > maxW) {
			n--;
		}
		return s.substring(0, n).trim() + "...";
	}

	private void updateTime(double position, double duration) {
		int second = (int) position;
		int length = (int) duration;
		if (second != timeSecond || length != timeLength) {
			timeSecond = second;
			timeLength = length;
			time = clock(second) + " / " + clock(length);
		}
	}

	static String clock(int seconds) {
		int m = seconds / 60;
		int s = seconds % 60;
		return m + ":" + (s < 10 ? "0" : "") + s;
	}

	/** Puts a newly read cover into the texture (once per track). */
	private void uploadCover() {
		int[] pixels = session.takeCover(coverId);
		if (pixels == null) {
			return;
		}
		if (pixels.length != MediaSession.COVER * MediaSession.COVER) {
			hasCover = false;
			return;
		}
		if (coverTexture == null) {
			coverTexture = new DynamicTexture(MediaSession.COVER, MediaSession.COVER);
			coverLocation = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("rago_music_cover", coverTexture);
			// smooth when drawn smaller or larger than 128 pixels
			GlStateManager.bindTexture(coverTexture.getGlTextureId());
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		}
		System.arraycopy(pixels, 0, coverTexture.getTextureData(), 0, pixels.length);
		coverTexture.updateDynamicTexture();
		hasCover = true;
	}
}
