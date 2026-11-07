package gg.rago.fixes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.FrameTimer;
import net.minecraft.util.MathHelper;

import java.lang.ref.WeakReference;

/**
 * PolyPatcher's Use Vanilla Metrics Renderer: with F3 and ALT+F3, the frame time chart of the
 * game's later versions at the bottom left and, in singleplayer, the server's tick time chart at
 * the bottom right, in place of OptiFine's lagometer (left out even with its own Lagometer option).
 * <ul>
 *   <li>Each chart is half the screen wide, at most 240 bars (one per frame or tick, the newest on
 *   the right); the tick chart fills from the left after the server starts.</li>
 *   <li>A frame bar is 30 units high per 1/60 s, coloured green at 0, yellow at 50 and red from
 *   100; a tick bar 60 per 1/20 s, yellow at 30 and red from 60. The chart is 60 high, a longer
 *   bar goes past its top.</li>
 *   <li>Marks at 60 and 30 FPS (20 TPS for the ticks), the frame rate limit in cyan when it is
 *   at most 250, and above each chart the lowest, average and highest time in whole
 *   milliseconds over all its places (the empty ones count as 0).</li>
 * </ul>
 * Drawn with F3's text, so under the chat and with the chat box over it. Frames are timed from
 * each frame's start all along, and ticks as the server times them from its start, so the charts
 * show the past when they open.
 */
public final class FrameCharts {
	static final int BACKGROUND = 0x90505050;
	static final int TEXT = 0xE0E0E0;
	static final int MARK = 0xFFFFFFFF;
	static final int LIMIT = 0xFF00FFFF;
	static final int GREEN = 0xFF00FF00;
	static final int YELLOW = 0xFFFFFF00;
	static final int RED = 0xFFFF0000;
	/** The height of a chart: 60 units. */
	static final int HEIGHT = 60;

	private static final FrameTimer FRAMES = new FrameTimer();
	/** A chart with nothing yet, for a server that has not ticked. */
	private static final FrameTimer NONE = new FrameTimer();
	private static long frameStart;
	private static volatile ServerTicks ticks;

	private FrameCharts() {
	}

	/** The tick times of one singleplayer server, from its start (written on the server's thread). */
	private static final class ServerTicks {
		final WeakReference<MinecraftServer> server;
		final FrameTimer times = new FrameTimer();

		ServerTicks(MinecraftServer server) {
			this.server = new WeakReference<>(server);
		}
	}

	/** Each frame's start (RenderTickEvent START): the time since the last one. */
	public static void frameStart() {
		long now = System.nanoTime();
		if (frameStart != 0) {
			FRAMES.addFrame(now - frameStart);
		}
		frameStart = now;
	}

	/**
	 * Each tick of the singleplayer server, once done (ServerTickEvent END, on its thread): its time,
	 * the first tick of a server at the first place.
	 */
	public static void serverTick(MinecraftServer server) {
		if (server == null) {
			return;
		}
		ServerTicks t = ticks;
		if (t == null || t.server.get() != server) {
			t = new ServerTicks(server);
			ticks = t;
		}
		t.times.addFrame(server.tickTimeArray[server.getTickCounter() % 100]);
	}

	private static FrameTimer ticksOf(MinecraftServer server) {
		ServerTicks t = ticks;
		return t != null && t.server.get() == server ? t.times : NONE;
	}

	/** With F3 and ALT+F3: the frame chart, and the tick chart in singleplayer. */
	public static void draw(Minecraft mc, ScaledResolution resolution) {
		int width = resolution.getScaledWidth();
		int bottom = resolution.getScaledHeight();
		chart(mc, FRAMES, 0, width / 2, bottom, true);
		MinecraftServer server = mc.getIntegratedServer();
		if (server != null) {
			chart(mc, ticksOf(server), width - Math.min(width / 2, 240), width / 2, bottom, false);
		}
	}

	private static void chart(Minecraft mc, FrameTimer timer, int x, int width, int bottom, boolean frames) {
		GlStateManager.disableDepth();
		long[] times = timer.getFrames();
		int places = places(times.length, width);
		int first = first(timer, times.length, width);
		int end = timer.getIndex();
		int[] stats = stats(timer, first, places);
		Gui.drawRect(x, bottom - HEIGHT, x + places, bottom, BACKGROUND);

		Tessellator tessellator = Tessellator.getInstance();
		WorldRenderer renderer = tessellator.getWorldRenderer();
		GlStateManager.enableBlend();
		GlStateManager.disableTexture2D();
		GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
		renderer.begin(7, DefaultVertexFormats.POSITION_COLOR);
		int column = x;
		for (int i = first; i != end; i = timer.parseIndex(i + 1)) {
			int height = frames ? frameHeight(times[i]) : tickHeight(times[i]);
			int color = frames ? frameColor(height) : tickColor(height);
			int r = color >> 16 & 255;
			int g = color >> 8 & 255;
			int b = color & 255;
			int a = color >>> 24;
			renderer.pos(column + 1, bottom, 0).color(r, g, b, a).endVertex();
			renderer.pos(column + 1, bottom - height + 1, 0).color(r, g, b, a).endVertex();
			renderer.pos(column, bottom - height + 1, 0).color(r, g, b, a).endVertex();
			renderer.pos(column, bottom, 0).color(r, g, b, a).endVertex();
			column++;
		}
		tessellator.draw();
		GlStateManager.enableTexture2D();
		GlStateManager.disableBlend();

		FontRenderer font = mc.fontRendererObj;
		int right = x + places - 1;
		if (frames) {
			label(font, "60 FPS", x, bottom - 30);
			horizontal(x, right, bottom - 30, MARK);
			label(font, "30 FPS", x, bottom - 60);
			horizontal(x, right, bottom - 60, MARK);
		} else {
			label(font, "20 TPS", x, bottom - 60);
			horizontal(x, right, bottom - 60, MARK);
		}
		horizontal(x, right, bottom - 1, MARK);
		vertical(x, bottom - HEIGHT, bottom, MARK);
		vertical(right, bottom - HEIGHT, bottom, MARK);
		int limit = mc.gameSettings.limitFramerate;
		if (frames && limit > 0 && limit <= 250) {
			horizontal(x, right, bottom - 1 - (int) (1800.0D / limit), LIMIT);
		}
		String low = stats[0] + " ms min";
		String average = stats[1] + " ms avg";
		String high = stats[2] + " ms max";
		int y = bottom - HEIGHT - 9;
		font.drawStringWithShadow(low, x + 2, y, TEXT);
		font.drawStringWithShadow(average, x + places / 2 - font.getStringWidth(average) / 2, y, TEXT);
		font.drawStringWithShadow(high, x + places - font.getStringWidth(high), y, TEXT);
		GlStateManager.enableDepth();
	}

	/** A mark's name on its own small shade, the text running past it. */
	private static void label(FontRenderer font, String text, int x, int line) {
		Gui.drawRect(x + 1, line + 1, x + 14, line + 10, BACKGROUND);
		font.drawString(text, x + 2, line + 2, TEXT);
	}

	private static void horizontal(int from, int to, int y, int color) {
		Gui.drawRect(Math.min(from, to), y, Math.max(from, to) + 1, y + 1, color);
	}

	private static void vertical(int x, int from, int to, int color) {
		Gui.drawRect(x, Math.min(from, to) + 1, x + 1, Math.max(from, to), color);
	}

	/** How many places a chart this wide shows (the newest ones, at most all of them). */
	static int places(int length, int width) {
		return length - Math.max(0, length - width);
	}

	/** Where the bars start: the oldest place kept, past those that do not fit. */
	static int first(FrameTimer timer, int length, int width) {
		return timer.parseIndex(timer.getLastIndex() + Math.max(0, length - width));
	}

	/** The lowest, average and highest time over the chart's places, in whole milliseconds. */
	static int[] stats(FrameTimer timer, int first, int places) {
		long[] times = timer.getFrames();
		long sum = 0;
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int i = 0; i < places; i++) {
			int ms = (int) (times[timer.parseIndex(first + i)] / 1000000L);
			min = Math.min(min, ms);
			max = Math.max(max, ms);
			sum += ms;
		}
		return new int[] {min, (int) (sum / places), max};
	}

	/** A frame bar: 30 units per 1/60 of a second. */
	static int frameHeight(long nanos) {
		return height(nanos, 30, 60);
	}

	/** A tick bar: 60 units per 1/20 of a second. */
	static int tickHeight(long nanos) {
		return height(nanos, 60, 20);
	}

	private static int height(long nanos, int units, int perSecond) {
		return (int) ((double) nanos / (double) (1000000000L / perSecond) * units);
	}

	/** Green at 0, yellow at 50, red from 100. */
	static int frameColor(int height) {
		return color(MathHelper.clamp_int(height, 0, 100), 100);
	}

	/** Green at 0, yellow at 30, red from 60. */
	static int tickColor(int height) {
		return color(MathHelper.clamp_int(height, 0, 60), 60);
	}

	private static int color(int height, int top) {
		int middle = top / 2;
		return height < middle ? blend(GREEN, YELLOW, (float) height / middle)
			: blend(YELLOW, RED, (float) (height - middle) / (top - middle));
	}

	/** Each of alpha, red, green and blue from {@code from} to {@code to}, cut to whole values. */
	static int blend(int from, int to, float part) {
		int color = 0;
		for (int shift = 24; shift >= 0; shift -= 8) {
			int a = from >> shift & 255;
			int b = to >> shift & 255;
			color |= MathHelper.clamp_int((int) (a + part * (b - a)), 0, 255) << shift;
		}
		return color;
	}
}
