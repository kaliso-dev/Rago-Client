package gg.rago.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.awt.image.BufferedImage;

/**
 * The bed in 3D, Rago's own model, drawn with the bed textures in use (whatever pack they come from):
 * four real legs at the corners, the wooden frame, the mattress set a little in from the frame's edge,
 * a raised pillow, the fold of the blanket below it, and the blanket over the foot's end. Each face
 * shows the pixels of the textures that vanilla's flat bed shows there, so a pack's colours are kept.
 *
 * <p>Laid out as vanilla's bed models: the head part has its end (the pillow) to the south, the foot
 * part to the north; the block state turns them. Rows of the side and end textures go with the height
 * as vanilla's do: row {@code 16 - y} at height {@code y}, the legs in the bottom rows, the frame
 * above them, the mattress and blanket above that. The hitbox stays vanilla's.
 */
public final class Bed3D {
	/** Vanilla's legs: 3 pixels square, 3 high. */
	static final int LEG = 3;
	/** How far the mattress sits in from the frame's edge. */
	static final double INSET = 0.5;
	/** Top of the frame and of the mattress. */
	static final double FRAME_TOP = 5;
	static final double TOP = 9;

	private Bed3D() {
	}

	/**
	 * The model of the head ({@code head}) or foot part, its legs as wide as the side texture draws
	 * them ({@code side} null: vanilla's).
	 */
	public static JsonObject model(boolean head, BufferedImage side) {
		int leg = side == null ? LEG : legWidth(side, head);
		String part = head ? "head" : "feet";
		JsonObject textures = new JsonObject();
		textures.addProperty("particle", "blocks/bed_" + part + "_top");
		textures.addProperty("top", "blocks/bed_" + part + "_top");
		textures.addProperty("end", "blocks/bed_" + part + "_end");
		textures.addProperty("side", "blocks/bed_" + part + "_side");
		textures.addProperty("bottom", "blocks/planks_oak");
		JsonArray elements = new JsonArray();
		if (head) {
			head(elements, leg);
		} else {
			foot(elements, leg);
		}
		JsonObject model = new JsonObject();
		model.add("textures", textures);
		model.add("elements", elements);
		return model;
	}

	/**
	 * The legs' width in the side texture: the opaque pixels in its bottom row, from the end where the
	 * leg is (the head's right, the foot's left). Vanilla's 3 when that is not between 1 and 6.
	 */
	static int legWidth(BufferedImage side, boolean head) {
		int w = side.getWidth();
		if (w < 16 || w % 16 != 0) {
			return LEG;
		}
		int scale = w / 16;
		int y = Math.min(side.getHeight(), w) - 1;
		int n = 0;
		for (int i = 0; i < w; i++) {
			int x = head ? w - 1 - i : i;
			if ((side.getRGB(x, y) >>> 24) <= 127) {
				break;
			}
			n++;
		}
		int px = Math.round(n / (float) scale);
		return px >= 1 && px <= 6 ? px : LEG;
	}

	// The head part: the outer end (pillow, legs) at z 16, the foot part beyond z 0.

	private static void head(JsonArray out, int leg) {
		int far = 16 - leg;
		double legTop = 3;
		// legs
		out.add(box(0, 0, far, leg, legTop, 16)
			.face("west", "#side", far, 13, 16, 16).face("east", "#side", 16, 13, far, 16)
			.face("south", "#end", 0, 13, leg, 16, "south").face("north", "#end", 0, 13, leg, 16)
			.face("down", "#bottom", 0, far, leg, 16, "down").json());
		out.add(box(far, 0, far, 16, legTop, 16)
			.face("east", "#side", 16, 13, far, 16).face("west", "#side", far, 13, 16, 16)
			.face("south", "#end", far, 13, 16, 16, "south").face("north", "#end", far, 13, 16, 16)
			.face("down", "#bottom", far, far, 16, 16, "down").json());
		// the frame, a lip of it showing around the mattress
		out.add(box(0, legTop, 0, 16, FRAME_TOP, 16)
			.face("west", "#side", 0, 11, 16, 13, "west").face("east", "#side", 16, 11, 0, 13, "east")
			.face("south", "#end", 0, 11, 16, 13, "south").face("down", "#bottom", 0, 0, 16, 16)
			.face("up", "#side", 0, 11, 16, 12).json());
		// the mattress and blanket
		double in = INSET;
		double end = 16 - in;
		out.add(box(in, FRAME_TOP, 0, end, TOP, end)
			.face("west", "#side", 0, 7, end, 11).face("east", "#side", end, 7, 0, 11)
			.face("south", "#end", in, 7, end, 11).top(0, end, in, end).json());
		// the pillow, on the pillow of the top texture
		out.add(box(2, TOP, 10, 14, TOP + 2, 15)
			.face("west", "#side", 10, 7, 15, 9).face("east", "#side", 15, 7, 10, 9)
			.face("north", "#end", 2, 7, 14, 9).face("south", "#end", 2, 7, 14, 9).top(10, 15, 2, 14).json());
		// the blanket folded back below the pillow
		out.add(box(in, TOP, 6.75, end, TOP + 0.75, 8.75)
			.face("north", "#side", 0, 7, 7, 8).face("south", "#side", 0, 7, 7, 8)
			.face("west", "#side", 5, 7, 7, 8).face("east", "#side", 7, 7, 5, 8)
			.faceRotated("up", "#top", 5, end, 7, in, 90).json());
	}

	// The foot part: the outer end (legs, the blanket over it) at z 0, the head part beyond z 16.

	private static void foot(JsonArray out, int leg) {
		int far = 16 - leg;
		double legTop = 3;
		out.add(box(0, 0, 0, leg, legTop, leg)
			.face("west", "#side", 0, 13, leg, 16).face("east", "#side", leg, 13, 0, 16)
			.face("north", "#end", far, 13, 16, 16, "north").face("south", "#end", far, 13, 16, 16)
			.face("down", "#bottom", 0, 0, leg, leg, "down").json());
		out.add(box(far, 0, 0, 16, legTop, leg)
			.face("east", "#side", leg, 13, 0, 16).face("west", "#side", 0, 13, leg, 16)
			.face("north", "#end", 0, 13, leg, 16, "north").face("south", "#end", 0, 13, leg, 16)
			.face("down", "#bottom", far, 0, 16, leg, "down").json());
		out.add(box(0, legTop, 0, 16, FRAME_TOP, 16)
			.face("west", "#side", 0, 11, 16, 13, "west").face("east", "#side", 16, 11, 0, 13, "east")
			.face("north", "#end", 0, 11, 16, 13, "north").face("down", "#bottom", 0, 0, 16, 16)
			.face("up", "#side", 0, 11, 16, 12).json());
		double in = INSET;
		double end = 16 - in;
		out.add(box(in, FRAME_TOP, in, end, TOP, 16)
			.face("west", "#side", in, 7, 16, 11).face("east", "#side", 16, 7, in, 11)
			.face("north", "#end", in, 7, end, 11).top(in, 16, in, end).json());
		// the blanket over the foot's end, a little proud of the mattress, its edge rolled
		double drape = 0.25;
		out.add(box(drape, FRAME_TOP + 0.5, drape, 16 - drape, TOP + 0.25, in)
			.face("north", "#end", drape, 7, 16 - drape, 10.5).face("up", "#side", 0, 7, 16, 8)
			.face("west", "#side", 0, 7, 0.5, 10.5).face("east", "#side", 0.5, 7, 0, 10.5)
			.face("down", "#side", 0, 10, 16, 10.5).json());
	}

	private static Box box(double x0, double y0, double z0, double x1, double y1, double z1) {
		return new Box(x0, y0, z0, x1, y1, z1);
	}

	/** One element of the model, its faces added one by one. */
	static final class Box {
		private final JsonObject element = new JsonObject();
		private final JsonObject faces = new JsonObject();
		private final double x0;
		private final double x1;

		Box(double x0, double y0, double z0, double x1, double y1, double z1) {
			this.x0 = x0;
			this.x1 = x1;
			element.add("from", vec(x0, y0, z0));
			element.add("to", vec(x1, y1, z1));
			element.add("faces", faces);
		}

		Box face(String side, String texture, double u0, double v0, double u1, double v1) {
			return face(side, texture, u0, v0, u1, v1, null);
		}

		Box face(String side, String texture, double u0, double v0, double u1, double v1, String cull) {
			JsonObject f = new JsonObject();
			f.add("uv", uv(u0, v0, u1, v1));
			f.addProperty("texture", texture);
			if (cull != null) {
				f.addProperty("cullface", cull);
			}
			faces.add(side, f);
			return this;
		}

		Box faceRotated(String side, String texture, double u0, double v0, double u1, double v1, int rotation) {
			face(side, texture, u0, v0, u1, v1);
			faces.getAsJsonObject(side).addProperty("rotation", rotation);
			return this;
		}

		/**
		 * The top, showing the top texture's pixels from z0 to z1 (its columns) and over this box's
		 * width (its rows), turned as vanilla's top is.
		 */
		Box top(double z0, double z1, double xa, double xb) {
			return faceRotated("up", "#top", z0, xb, z1, xa, 90);
		}

		JsonObject json() {
			return element;
		}
	}

	private static JsonArray uv(double u0, double v0, double u1, double v1) {
		JsonArray a = new JsonArray();
		for (double d : new double[] {u0, v0, u1, v1}) {
			a.add(new JsonPrimitive(round(d)));
		}
		return a;
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
