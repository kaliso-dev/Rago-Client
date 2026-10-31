package gg.rago.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;

/**
 * Where a HUD element sits, in scaled GUI pixels. The element's top-left corner is
 * {@code anchor.fx * (screenW - w) + offsetX}, so offsets are relative to the anchored
 * edge and the layout survives window resizes and GUI-scale changes. Pure Java (no
 * Minecraft types) so the math is unit tested.
 */
public final class HudPosition {
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 3.0f;

	private final Anchor defaultAnchor;
	private final int defaultX;
	private final int defaultY;

	private Anchor anchor;
	private int offsetX;
	private int offsetY;
	private float scale = 1;

	public HudPosition(Anchor anchor, int offsetX, int offsetY) {
		this.defaultAnchor = anchor;
		this.defaultX = offsetX;
		this.defaultY = offsetY;
		reset();
	}

	public void reset() {
		anchor = defaultAnchor;
		offsetX = defaultX;
		offsetY = defaultY;
		scale = 1;
	}

	/** Still where the element starts out (scale aside), so the HUD may stack it with its neighbours. */
	public boolean isDefault() {
		return anchor == defaultAnchor && offsetX == defaultX && offsetY == defaultY;
	}

	public Anchor anchor() {
		return anchor;
	}

	public int offsetX() {
		return offsetX;
	}

	public int offsetY() {
		return offsetY;
	}

	public float scale() {
		return scale;
	}

	public void setScale(float scale) {
		float s = Math.round(scale * 10) / 10f;
		this.scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, s));
	}

	/** Left edge on screen for an element of scaled width {@code w}, kept inside the screen. */
	public int screenX(int w, int screenW) {
		return clamp(Math.round(anchor.fx * (screenW - w)) + offsetX, screenW - w);
	}

	public int screenY(int h, int screenH) {
		return clamp(Math.round(anchor.fy * (screenH - h)) + offsetY, screenH - h);
	}

	/** Stores a new top-left corner chosen in the editor, re-anchoring to the nearest screen third. */
	public void moveTo(int x, int y, int w, int h, int screenW, int screenH) {
		x = clamp(x, screenW - w);
		y = clamp(y, screenH - h);
		anchor = Anchor.forPoint(x + w / 2f, y + h / 2f, screenW, screenH);
		offsetX = x - Math.round(anchor.fx * (screenW - w));
		offsetY = y - Math.round(anchor.fy * (screenH - h));
	}

	private static int clamp(int v, int max) {
		return Math.max(0, Math.min(Math.max(0, max), v));
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("anchor", anchor.name());
		o.addProperty("x", offsetX);
		o.addProperty("y", offsetY);
		o.addProperty("scale", scale);
		return o;
	}

	/** Where the element starts out, as {@link #toJson} stores it. */
	public JsonObject defaultJson() {
		return new HudPosition(defaultAnchor, defaultX, defaultY).toJson();
	}

	/** Reads stored values; any missing or invalid field keeps the default. */
	public void fromJson(JsonObject o) {
		reset();
		try {
			JsonElement a = o.get("anchor");
			if (a != null) {
				anchor = Anchor.valueOf(a.getAsString().toUpperCase(Locale.ROOT));
			}
			if (o.has("x")) {
				offsetX = o.get("x").getAsInt();
			}
			if (o.has("y")) {
				offsetY = o.get("y").getAsInt();
			}
			if (o.has("scale")) {
				setScale(o.get("scale").getAsFloat());
			}
		} catch (RuntimeException e) {
			reset();
		}
	}
}
