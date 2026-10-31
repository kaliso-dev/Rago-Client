package gg.rago.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * A 3D block model for the ladder, worked out from the ladder texture in use (whatever pack it
 * comes from): the columns opaque from top to bottom are the rails, the rows opaque between them
 * the rungs. The rails stand 2/16 off the wall, the depth of the ladder's hitbox; the rungs, 1/16
 * thick, sit between them. Each part shows its own pixels of the texture, so a pack's colours and
 * shapes are kept.
 */
public final class Ladder3D {
	/** A column opaque on this share of its pixels or more is a rail. */
	static final double RAIL = 0.85;
	/** A row opaque on this share of its pixels between the rails or more is a rung. */
	static final double RUNG = 0.6;
	static final double RAIL_DEPTH = 2;
	static final double RUNG_FRONT = 14.5;
	static final double RUNG_BACK = 15.5;
	private static final int MAX_PARTS = 48;

	private Ladder3D() {
	}

	/** The model as JSON, or null when the texture has no rails (it is then left flat). */
	public static JsonObject model(BufferedImage texture) {
		int w = texture.getWidth();
		// an animated texture is frames one under the other: the first one
		int h = Math.min(texture.getHeight(), w);
		if (w < 4 || h < 4) {
			return null;
		}
		boolean[][] solid = new boolean[w][h];
		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				solid[x][y] = (texture.getRGB(x, y) >>> 24) > 127;
			}
		}
		boolean[] railColumn = new boolean[w];
		for (int x = 0; x < w; x++) {
			int n = 0;
			for (int y = 0; y < h; y++) {
				n += solid[x][y] ? 1 : 0;
			}
			railColumn[x] = n >= RAIL * h;
		}
		List<int[]> rails = runs(railColumn);
		if (rails.isEmpty() || rails.size() > MAX_PARTS) {
			return null;
		}
		// rungs: rows mostly opaque between the outer rails (or across, with one rail), rails left out
		int from = rails.size() > 1 ? rails.get(0)[1] + 1 : 0;
		int to = rails.size() > 1 ? rails.get(rails.size() - 1)[0] - 1 : w - 1;
		boolean[] rungRow = new boolean[h];
		for (int y = 0; y < h; y++) {
			int n = 0;
			int all = 0;
			for (int x = from; x <= to; x++) {
				if (!railColumn[x]) {
					all++;
					n += solid[x][y] ? 1 : 0;
				}
			}
			rungRow[y] = all > 0 && n >= RUNG * all;
		}
		List<int[]> rungs = runs(rungRow);
		if (rails.size() + rungs.size() > MAX_PARTS) {
			rungs = rungs.subList(0, MAX_PARTS - rails.size());
		}

		double px = 16.0 / w;
		JsonArray elements = new JsonArray();
		for (int[] r : rails) {
			elements.add(rail(r[0] * px, (r[1] + 1) * px));
		}
		for (int[] r : rungs) {
			// as wide as the rung's pixels go, past the rails when the texture says so
			int left = w;
			int right = -1;
			for (int y = r[0]; y <= r[1]; y++) {
				for (int x = 0; x < w; x++) {
					if (solid[x][y]) {
						left = Math.min(left, x);
						right = Math.max(right, x);
					}
				}
			}
			elements.add(rung(left * px, (right + 1) * px, r[0] * px, (r[1] + 1) * px));
		}

		JsonObject model = new JsonObject();
		model.addProperty("ambientocclusion", false);
		model.add("textures", textures());
		model.add("elements", elements);
		return model;
	}

	/** The flat ladder, as vanilla draws it: one plane just off the wall, seen from both sides. */
	public static JsonObject flat() {
		JsonObject faces = new JsonObject();
		faces.add("north", face(0, 0, 16, 16, null));
		faces.add("south", face(0, 0, 16, 16, null));
		JsonObject plane = element(0, 0, 15.2, 16, 16, 15.2, faces);
		plane.addProperty("shade", false);
		JsonArray elements = new JsonArray();
		elements.add(plane);
		JsonObject model = new JsonObject();
		model.addProperty("ambientocclusion", false);
		model.add("textures", textures());
		model.add("elements", elements);
		return model;
	}

	private static JsonObject textures() {
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "blocks/ladder");
		textures.addProperty("texture", "blocks/ladder");
		return textures;
	}

	/** Runs of true values, as {first, last} indices. */
	static List<int[]> runs(boolean[] values) {
		List<int[]> out = new ArrayList<>();
		int start = -1;
		for (int i = 0; i <= values.length; i++) {
			boolean on = i < values.length && values[i];
			if (on && start < 0) {
				start = i;
			} else if (!on && start >= 0) {
				out.add(new int[] {start, i - 1});
				start = -1;
			}
		}
		return out;
	}

	/**
	 * A rail from texture column u0 to u1, full height. The front faces north, as the vanilla
	 * ladder's does (the block state turns it to the wall): there x runs the other way to u.
	 */
	private static JsonObject rail(double u0, double u1) {
		double height = 16;
		double z0 = 16 - RAIL_DEPTH;
		double side = Math.min(RAIL_DEPTH, u1 - u0);
		JsonObject faces = new JsonObject();
		faces.add("north", face(u0, 0, u1, height, null));
		faces.add("south", face(u0, 0, u1, height, "south"));
		faces.add("east", face(u0, 0, u0 + side, height, null));
		faces.add("west", face(u1 - side, 0, u1, height, null));
		faces.add("up", face(u0, 0, u1, RAIL_DEPTH, null));
		faces.add("down", face(u0, height - RAIL_DEPTH, u1, height, null));
		return element(16 - u1, 0, z0, 16 - u0, 16, 16, faces);
	}

	/** A rung over texture columns u0 to u1 and rows v0 to v1, between the rails' front and the wall. */
	private static JsonObject rung(double u0, double u1, double v0, double v1) {
		double depth = RUNG_BACK - RUNG_FRONT;
		JsonObject faces = new JsonObject();
		faces.add("north", face(u0, v0, u1, v1, null));
		faces.add("up", face(u0, v0, u1, Math.min(v1, v0 + depth), null));
		faces.add("down", face(u0, Math.max(v0, v1 - depth), u1, v1, null));
		faces.add("east", face(u0, v0, Math.min(u1, u0 + depth), v1, null));
		faces.add("west", face(Math.max(u0, u1 - depth), v0, u1, v1, null));
		return element(16 - u1, 16 - v1, RUNG_FRONT, 16 - u0, 16 - v0, RUNG_BACK, faces);
	}

	private static JsonObject face(double u0, double v0, double u1, double v1, String cull) {
		JsonObject f = new JsonObject();
		JsonArray uv = new JsonArray();
		for (double d : new double[] {u0, v0, u1, v1}) {
			uv.add(new JsonPrimitive(round(d)));
		}
		f.add("uv", uv);
		f.addProperty("texture", "#texture");
		if (cull != null) {
			f.addProperty("cullface", cull);
		}
		return f;
	}

	private static JsonObject element(double x0, double y0, double z0, double x1, double y1, double z1, JsonObject faces) {
		JsonObject e = new JsonObject();
		e.add("from", vec(x0, y0, z0));
		e.add("to", vec(x1, y1, z1));
		e.add("faces", faces);
		return e;
	}

	private static JsonArray vec(double x, double y, double z) {
		JsonArray a = new JsonArray();
		a.add(new JsonPrimitive(round(x)));
		a.add(new JsonPrimitive(round(y)));
		a.add(new JsonPrimitive(round(z)));
		return a;
	}

	private static double round(double d) {
		return Math.round(d * 1000) / 1000.0;
	}
}
