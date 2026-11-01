package gg.rago.render;

import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Parts of a player model that bend: an arm at its elbow, a leg at its knee, the body at its waist.
 * The part is drawn as one piece whose middle curves over a few pixels, cut in thin slices, so its two
 * halves stay joined like a real limb and the skin stretches over the bend. A part given a
 * {@link Bend} for the frame (by the emotes, on your own player) is drawn here instead of by vanilla,
 * with its own boxes, skin and light; what hangs from its end (the held item) follows the bend.
 */
public final class BentModel {
	private static final Logger LOG = LogManager.getLogger("Rago");
	/** How long the curved part of a bent limb is, in model pixels, centred on the joint. */
	static final float ZONE = 4;
	/** Slices across the curved part: more is rounder. */
	private static final int SLICES = 6;
	private static final float DEG = (float) (180 / Math.PI);

	/** A bend: how far, and which way. */
	public static final class Bend {
		/** The angle, in radians; 0 is straight. */
		public float angle;
		/** Which way the end swings, around the limb: 0 forward (toward -z), PI/2 toward +x, PI back. */
		public float toward;
		/** True when the bottom stays and the top bends (the body at its waist); false for arms and legs. */
		public final boolean fromBottom;

		public Bend(boolean fromBottom) {
			this.fromBottom = fromBottom;
		}

		public Bend set(float angle, float toward) {
			this.angle = angle;
			this.toward = toward;
			return this;
		}
	}

	private static final Map<ModelRenderer, Bend> BENT = new IdentityHashMap<>();
	/** Each box's faces: per face 4 corners of x, y, z, u, v. */
	private static final Map<ModelBox, float[][]> FACES = new WeakHashMap<>();
	private static Field quads;
	private static boolean broken;

	private BentModel() {
	}

	/** Draws {@code part} bent this way from now on; null or straight puts it back to vanilla. */
	public static void bend(ModelRenderer part, Bend b) {
		if (part == null) {
			return;
		}
		if (b == null || Math.abs(b.angle) < 1e-3f || broken) {
			BENT.remove(part);
		} else {
			BENT.put(part, b);
		}
	}

	/** ModelRenderer.render, first: true when the part was drawn bent here, so vanilla must not draw it. */
	public static boolean render(ModelRenderer r, float scale) {
		if (BENT.isEmpty()) {
			return false;
		}
		Bend b = BENT.get(r);
		if (b == null || r.cubeList.isEmpty()) {
			return false;
		}
		if (r.isHidden || !r.showModel) {
			return true;
		}
		for (ModelBox box : r.cubeList) {
			if (faces(box) == null) {
				return false;
			}
		}
		Frame frame = FRAME.on(r.cubeList.get(0), b);
		GlStateManager.translate(r.offsetX, r.offsetY, r.offsetZ);
		GlStateManager.pushMatrix();
		GlStateManager.translate(r.rotationPointX * scale, r.rotationPointY * scale, r.rotationPointZ * scale);
		if (r.rotateAngleZ != 0) {
			GlStateManager.rotate(r.rotateAngleZ * DEG, 0, 0, 1);
		}
		if (r.rotateAngleY != 0) {
			GlStateManager.rotate(r.rotateAngleY * DEG, 0, 1, 0);
		}
		if (r.rotateAngleX != 0) {
			GlStateManager.rotate(r.rotateAngleX * DEG, 1, 0, 0);
		}
		WorldRenderer w = Tessellator.getInstance().getWorldRenderer();
		w.begin(7, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
		for (ModelBox box : r.cubeList) {
			draw(w, faces(box), box.posY1, box.posY2, frame, scale);
		}
		Tessellator.getInstance().draw();
		if (r.childModels != null) {
			for (ModelRenderer child : r.childModels) {
				child.render(scale);
			}
		}
		GlStateManager.popMatrix();
		GlStateManager.translate(-r.offsetX, -r.offsetY, -r.offsetZ);
		return true;
	}

	/** ModelRenderer.postRender, at its end: what hangs from a bent part's end (the hand) follows the bend. */
	public static void postRender(ModelRenderer r, float scale) {
		if (BENT.isEmpty()) {
			return;
		}
		Bend b = BENT.get(r);
		if (b == null || r.cubeList.isEmpty() || r.isHidden || !r.showModel) {
			return;
		}
		Frame f = FRAME.on(r.cubeList.get(0), b);
		float[] end = f.centre(ZONE, END);
		float[] k = f.axis;
		GlStateManager.translate(end[0] * scale, end[1] * scale, end[2] * scale);
		GlStateManager.rotate(f.angle * DEG, k[0], k[1], k[2]);
		GlStateManager.translate(-f.cx * scale, -(f.y0 + f.sign * ZONE) * scale, -f.cz * scale);
	}

	/**
	 * Where a point of a part (in its own pixels, before its rotation) goes when the part bends:
	 * the joint and the far end move, the near end stays. Used to hang the head and arms on a bent body.
	 */
	public static float[] move(ModelRenderer part, Bend b, float x, float y, float z) {
		return move(part, b, x, y, z, new float[3]);
	}

	/** {@link #move} into {@code out} (returned), with no allocation: on the render thread only. */
	public static float[] move(ModelRenderer part, Bend b, float x, float y, float z, float[] out) {
		if (part.cubeList.isEmpty() || b == null || Math.abs(b.angle) < 1e-3f) {
			out[0] = x;
			out[1] = y;
			out[2] = z;
			return out;
		}
		return MOVE_FRAME.on(part.cubeList.get(0), b).apply(x, y, z, out);
	}

	/** The frame {@link #move} works in, apart from the one parts are drawn with. */
	private static final Frame MOVE_FRAME = new Frame();
	/** Where a bent part's end goes, worked out on the render thread. */
	private static final float[] END = new float[3];

	/** The frame, the corners and the cut heights of the part being drawn: one at a time, on the render thread. */
	private static final Frame FRAME = new Frame();
	private static final float[][] CORNER = new float[4][5];
	private static final float[] LEVELS = new float[SLICES + 3];
	private static final int[] PARTNER = new int[4];

	/** One box's quads, cut across the bend into slices, each corner moved by the frame. */
	private static void draw(WorldRenderer w, float[][] faces, float minY, float maxY, Frame frame, float scale) {
		int count = frame.levels(minY, maxY, LEVELS);
		float[][] corner = CORNER;
		for (float[] q : faces) {
			boolean side = false;
			for (int i = 0; i < 4; i++) {
				if (Math.abs(q[i * 5 + 1] - q[1]) > 1e-4f) {
					side = true;
				}
			}
			if (!side) {
				for (int i = 0; i < 4; i++) {
					frame.apply(q[i * 5], q[i * 5 + 1], q[i * 5 + 2], corner[i]);
					corner[i][3] = q[i * 5 + 3];
					corner[i][4] = q[i * 5 + 4];
				}
				emit(w, corner, scale);
				continue;
			}
			// a side: two corners on top, two below, each on an edge running down the part
			int[] partner = PARTNER;
			for (int i = 0; i < 4; i++) {
				partner[i] = -1;
				for (int j = 0; j < 4; j++) {
					if (j != i && Math.abs(q[i * 5] - q[j * 5]) < 1e-4f && Math.abs(q[i * 5 + 2] - q[j * 5 + 2]) < 1e-4f
						&& Math.abs(q[i * 5 + 1] - q[j * 5 + 1]) > 1e-4f) {
						partner[i] = j;
					}
				}
				if (partner[i] < 0) {
					return;
				}
			}
			for (int l = 0; l + 1 < count; l++) {
				for (int i = 0; i < 4; i++) {
					int j = partner[i];
					boolean top = q[i * 5 + 1] < q[j * 5 + 1];
					int a = top ? i : j;
					int c = top ? j : i;
					float ya = q[a * 5 + 1];
					float yc = q[c * 5 + 1];
					float y = top ? LEVELS[l] : LEVELS[l + 1];
					float t = (y - ya) / (yc - ya);
					frame.apply(lerp(q[a * 5], q[c * 5], t), y, lerp(q[a * 5 + 2], q[c * 5 + 2], t), corner[i]);
					corner[i][3] = lerp(q[a * 5 + 3], q[c * 5 + 3], t);
					corner[i][4] = lerp(q[a * 5 + 4], q[c * 5 + 4], t);
				}
				emit(w, corner, scale);
			}
		}
	}

	/** One quad, its normal worked out as TexturedQuad.draw does: (c2 - c1) x (c0 - c1). */
	private static void emit(WorldRenderer w, float[][] c, float scale) {
		float ax = c[0][0] - c[1][0];
		float ay = c[0][1] - c[1][1];
		float az = c[0][2] - c[1][2];
		float bx = c[2][0] - c[1][0];
		float by = c[2][1] - c[1][1];
		float bz = c[2][2] - c[1][2];
		float nx = by * az - bz * ay;
		float ny = bz * ax - bx * az;
		float nz = bx * ay - by * ax;
		float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		if (len < 1e-6f) {
			nx = 0;
			ny = 1;
			nz = 0;
		} else {
			nx /= len;
			ny /= len;
			nz /= len;
		}
		for (float[] v : c) {
			w.pos(v[0] * scale, v[1] * scale, v[2] * scale).tex(v[3], v[4]).normal(nx, ny, nz).endVertex();
		}
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	/** The quads of a box as vanilla built them (with its mirroring and inflation), read once. */
	private static float[][] faces(ModelBox box) {
		float[][] f = FACES.get(box);
		if (f != null || broken) {
			return f;
		}
		try {
			if (quads == null) {
				quads = ReflectionHelper.findField(ModelBox.class, "quadList", "field_78254_i");
			}
			TexturedQuad[] list = (TexturedQuad[]) quads.get(box);
			f = new float[list.length][];
			for (int i = 0; i < list.length; i++) {
				float[] q = new float[20];
				for (int v = 0; v < 4; v++) {
					PositionTextureVertex p = list[i].vertexPositions[v];
					q[v * 5] = (float) p.vector3D.xCoord;
					q[v * 5 + 1] = (float) p.vector3D.yCoord;
					q[v * 5 + 2] = (float) p.vector3D.zCoord;
					q[v * 5 + 3] = p.texturePositionX;
					q[v * 5 + 4] = p.texturePositionY;
				}
				f[i] = q;
			}
			FACES.put(box, f);
			return f;
		} catch (ReflectiveOperationException | RuntimeException e) {
			broken = true;
			BENT.clear();
			LOG.warn("Bending limbs is off: {}", e.toString());
			return null;
		}
	}

	/**
	 * A bend laid on a box: the joint halfway down it, the curve {@link #ZONE} pixels long around the
	 * joint, the cross-section turning with the curve so the limb keeps its thickness.
	 */
	static final class Frame {
		float cx;
		float cz;
		/** Where the curve starts (the side that stays), and which way along y it goes from there. */
		float y0;
		float sign;
		float angle;
		/** The way the end swings, and the axis it turns around (y-direction x swing). */
		final float[] swing = new float[3];
		final float[] axis = new float[3];

		Frame() {
		}

		Frame(float cx, float cz, float yJoint, Bend b) {
			set(cx, cz, yJoint, b);
		}

		Frame on(ModelBox box, Bend b) {
			return set((box.posX1 + box.posX2) / 2, (box.posZ1 + box.posZ2) / 2, (box.posY1 + box.posY2) / 2, b);
		}

		Frame set(float cx, float cz, float yJoint, Bend b) {
			this.cx = cx;
			this.cz = cz;
			this.sign = b.fromBottom ? -1 : 1;
			this.y0 = yJoint - sign * ZONE / 2;
			this.angle = b.angle;
			float ux = (float) Math.sin(b.toward);
			float uz = (float) -Math.cos(b.toward);
			swing[0] = ux;
			swing[2] = uz;
			// (0, sign, 0) x (ux, 0, uz)
			axis[0] = sign * uz;
			axis[2] = -sign * ux;
			return this;
		}

		/** The heights a side is cut at, into {@code out}: its ends and the slices of the curve inside it. */
		int levels(float minY, float maxY, float[] out) {
			int n = 0;
			out[n++] = minY;
			for (int i = 0; i <= SLICES; i++) {
				// from the top down whichever end the curve starts at
				float y = sign > 0 ? y0 + ZONE * i / SLICES : y0 - ZONE * (SLICES - i) / SLICES;
				if (y > minY + 1e-4f && y < maxY - 1e-4f) {
					out[n++] = y;
				}
			}
			out[n++] = maxY;
			return n;
		}

		/** The centre of the limb at distance s past the start of the curve, into {@code c}. */
		float[] centre(float s, float[] c) {
			float a = Math.min(s, ZONE);
			float along;
			float out;
			if (Math.abs(angle) < 1e-4f) {
				along = a;
				out = 0;
			} else {
				float r = ZONE / angle;
				along = r * (float) Math.sin(angle * a / ZONE);
				out = r * (1 - (float) Math.cos(angle * a / ZONE));
			}
			c[0] = cx + swing[0] * out;
			c[1] = y0 + sign * along;
			c[2] = cz + swing[2] * out;
			if (s > ZONE) {
				float d = s - ZONE;
				float cos = (float) Math.cos(angle);
				float sin = (float) Math.sin(angle);
				c[0] += d * swing[0] * sin;
				c[1] += d * sign * cos;
				c[2] += d * swing[2] * sin;
			}
			return c;
		}

		/** Where (x, y, z) goes, into the first three places of {@code out}. */
		float[] apply(float x, float y, float z, float[] out) {
			float s = sign * (y - y0);
			if (s <= 0) {
				out[0] = x;
				out[1] = y;
				out[2] = z;
				return out;
			}
			float phi = angle * Math.min(s, ZONE) / ZONE;
			// the cross-section's offset from the centre, turned with the limb (Rodrigues)
			float ox = x - cx;
			float oz = z - cz;
			float cos = (float) Math.cos(phi);
			float sin = (float) Math.sin(phi);
			float kx = axis[0];
			float kz = axis[2];
			float dot = kx * ox + kz * oz;
			// k x o, with k and o both flat: only y is left
			float crossY = kz * ox - kx * oz;
			centre(s, out);
			out[0] += ox * cos + kx * dot * (1 - cos);
			out[1] += crossY * sin;
			out[2] += oz * cos + kz * dot * (1 - cos);
			return out;
		}
	}
}
