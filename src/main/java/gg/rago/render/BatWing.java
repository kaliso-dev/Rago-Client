package gg.rago.render;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * The Dragon wings: a bat's wing, drawn as smooth shapes rather than pixels. Light, rounded-looking
 * bones (the arm along the top edge, a claw at the wrist, three fingers spreading down) hold a dark
 * membrane whose trailing edge curves in between the fingers. Rago's own design, in code.
 *
 * <p>The wing is drawn in its own plane: x out from the back, y down, z through the membrane, one unit
 * the length of the wing; the caller places it on the body (body space, as the other wings).
 */
public final class BatWing {
	/** The membrane's colour when the wings colour is left at white: near black. */
	public static final int MEMBRANE = 0xFF1C191A;
	private static final int BONE = 0xFFC7B8A1;

	/** Where the bones meet: the root on the back, the wrist, the tip of the top edge and the finger tips. */
	static final float[] ROOT = {0, 0};
	static final float[] WRIST = {0.40f, -0.09f};
	static final float[] TIP = {1.0f, 0.05f};
	static final float[][] FINGERS = {{0.90f, 0.47f}, {0.66f, 0.61f}, {0.38f, 0.63f}};
	/** Where the membrane meets the back, below the root. */
	static final float[] BACK = {0.03f, 0.40f};
	/** The membrane is drawn as a fan around this point, inside the wing. */
	static final float[] CENTRE = {0.45f, 0.20f};
	/** How far the trailing edge curves in between two points, towards the wrist. */
	static final float SCALLOP = 0.30f;
	private static final int CURVE_STEPS = 7;

	/** The membrane's outline, made once: root, wrist, tip, then the curved trailing edge to the back. */
	static final float[][] OUTLINE = outline();

	private BatWing() {
	}

	static float[][] outline() {
		List<float[]> out = new ArrayList<>();
		out.add(ROOT);
		out.add(WRIST);
		out.add(TIP);
		float[][] trailing = {TIP, FINGERS[0], FINGERS[1], FINGERS[2], BACK};
		for (int i = 0; i + 1 < trailing.length; i++) {
			float[] a = trailing[i];
			float[] b = trailing[i + 1];
			float mx = (a[0] + b[0]) / 2;
			float my = (a[1] + b[1]) / 2;
			float cx = mx + SCALLOP * (WRIST[0] - mx);
			float cy = my + SCALLOP * (WRIST[1] - my);
			for (int k = 1; k <= CURVE_STEPS; k++) {
				float t = k / (float) CURVE_STEPS;
				float u = 1 - t;
				out.add(new float[] {u * u * a[0] + 2 * u * t * cx + t * t * b[0], u * u * a[1] + 2 * u * t * cy + t * t * b[1]});
			}
		}
		return out.toArray(new float[0][]);
	}

	/**
	 * One wing on the body (body space, after {@code bipedBody.postRender}), {@code side} -1 or 1,
	 * {@code membrane} its colour, {@code size} 1 at 100 %, {@code beat} the flap in degrees.
	 */
	public static void draw(int membrane, float size, int side, float beat) {
		float span = 0.95f * size;
		GlStateManager.pushMatrix();
		// the root high on the back, between the shoulder blades
		GlStateManager.translate(side * 0.06f, 0.06f, 0.16f);
		// spread back from the body, beating
		GlStateManager.rotate(side * (-28 + beat), 0, 1, 0);
		// the top edge a little raised
		GlStateManager.rotate(side * -12, 0, 0, 1);
		GlStateManager.scale(side * span, span, span);
		Tessellator t = Tessellator.getInstance();
		WorldRenderer w = t.getWorldRenderer();
		// the membrane, seen from both sides (the layer draws without culling)
		w.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
		for (int i = 0; i < OUTLINE.length; i++) {
			float[] a = OUTLINE[i];
			float[] b = OUTLINE[(i + 1) % OUTLINE.length];
			membraneVertex(w, CENTRE, membrane);
			membraneVertex(w, a, membrane);
			membraneVertex(w, b, membrane);
		}
		t.draw();
		// the bones, thicker at the root, a little in front of and behind the membrane
		w.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
		bone(w, ROOT, WRIST, 0.060f, 0.046f);
		bone(w, WRIST, TIP, 0.040f, 0.014f);
		for (float[] f : FINGERS) {
			bone(w, WRIST, f, 0.032f, 0.010f);
		}
		// the claw on the wrist
		bone(w, WRIST, new float[] {WRIST[0] + 0.035f, WRIST[1] - 0.085f}, 0.030f, 0.004f);
		joint(w, WRIST, 0.036f);
		joint(w, ROOT, 0.040f);
		t.draw();
		GlStateManager.popMatrix();
	}

	/** The membrane a little lighter near the arm, darker towards its edge. */
	private static void membraneVertex(WorldRenderer w, float[] p, int argb) {
		float k = 1.18f - 0.38f * Math.max(0, Math.min(1, (p[1] + 0.05f) / 0.65f));
		color(w.pos(p[0], p[1], 0), argb, k).endVertex();
	}

	/** A bone from {@code a} to {@code b}: a square prism, {@code ta} thick at a and {@code tb} at b, lit from above. */
	private static void bone(WorldRenderer w, float[] a, float[] b, float ta, float tb) {
		float dx = b[0] - a[0];
		float dy = b[1] - a[1];
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len < 1e-5f) {
			return;
		}
		// across the bone in the wing's plane
		float nx = -dy / len;
		float ny = dx / len;
		float ha = ta / 2;
		float hb = tb / 2;
		// the edge towards the top of the wing is the lit one
		float up = ny < 0 ? 1 : -1;
		float[][] ca = corners(a, nx * up, ny * up, ha);
		float[][] cb = corners(b, nx * up, ny * up, hb);
		// faces: lit edge, back (z+), dark edge, front (z-)
		float[] shades = {1.0f, 0.86f, 0.62f, 0.86f};
		for (int f = 0; f < 4; f++) {
			int g = (f + 1) % 4;
			quad(w, ca[f], ca[g], cb[g], cb[f], BONE, shades[f]);
		}
		quad(w, cb[0], cb[1], cb[2], cb[3], BONE, 0.8f);
	}

	/** The four corners around {@code p}: lit edge front and back, then dark edge back and front. */
	private static float[][] corners(float[] p, float nx, float ny, float h) {
		return new float[][] {
			{p[0] + nx * h, p[1] + ny * h, -h},
			{p[0] + nx * h, p[1] + ny * h, h},
			{p[0] - nx * h, p[1] - ny * h, h},
			{p[0] - nx * h, p[1] - ny * h, -h}};
	}

	/** A small cube where bones meet. */
	private static void joint(WorldRenderer w, float[] p, float size) {
		float h = size / 2;
		float x0 = p[0] - h;
		float x1 = p[0] + h;
		float y0 = p[1] - h;
		float y1 = p[1] + h;
		quad(w, new float[] {x0, y0, -h}, new float[] {x1, y0, -h}, new float[] {x1, y0, h}, new float[] {x0, y0, h}, BONE, 1.0f);
		quad(w, new float[] {x0, y1, -h}, new float[] {x0, y1, h}, new float[] {x1, y1, h}, new float[] {x1, y1, -h}, BONE, 0.6f);
		quad(w, new float[] {x0, y0, -h}, new float[] {x0, y1, -h}, new float[] {x1, y1, -h}, new float[] {x1, y0, -h}, BONE, 0.86f);
		quad(w, new float[] {x0, y0, h}, new float[] {x1, y0, h}, new float[] {x1, y1, h}, new float[] {x0, y1, h}, BONE, 0.86f);
		quad(w, new float[] {x0, y0, -h}, new float[] {x0, y0, h}, new float[] {x0, y1, h}, new float[] {x0, y1, -h}, BONE, 0.74f);
		quad(w, new float[] {x1, y0, -h}, new float[] {x1, y1, -h}, new float[] {x1, y1, h}, new float[] {x1, y0, h}, BONE, 0.74f);
	}

	private static void quad(WorldRenderer w, float[] a, float[] b, float[] c, float[] d, int argb, float shade) {
		color(w.pos(a[0], a[1], a[2]), argb, shade).endVertex();
		color(w.pos(b[0], b[1], b[2]), argb, shade).endVertex();
		color(w.pos(c[0], c[1], c[2]), argb, shade).endVertex();
		color(w.pos(d[0], d[1], d[2]), argb, shade).endVertex();
	}

	private static WorldRenderer color(WorldRenderer w, int argb, float k) {
		return w.color(Math.min(255, Math.round((argb >> 16 & 0xFF) * k)), Math.min(255, Math.round((argb >> 8 & 0xFF) * k)),
			Math.min(255, Math.round((argb & 0xFF) * k)), 255);
	}
}
