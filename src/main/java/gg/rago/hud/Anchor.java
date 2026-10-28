package gg.rago.hud;

/** Which point of the screen a HUD element is attached to, so it stays put when the window size changes. */
public enum Anchor {
	TOP_LEFT(0, 0), TOP_CENTER(0.5f, 0), TOP_RIGHT(1, 0),
	MIDDLE_LEFT(0, 0.5f), CENTER(0.5f, 0.5f), MIDDLE_RIGHT(1, 0.5f),
	BOTTOM_LEFT(0, 1), BOTTOM_CENTER(0.5f, 1), BOTTOM_RIGHT(1, 1);

	public final float fx;
	public final float fy;

	Anchor(float fx, float fy) {
		this.fx = fx;
		this.fy = fy;
	}

	/** The anchor for the screen third (horizontal and vertical) that contains the given point. */
	public static Anchor forPoint(float x, float y, int screenW, int screenH) {
		int col = third(x, screenW);
		int row = third(y, screenH);
		return values()[row * 3 + col];
	}

	private static int third(float v, int size) {
		if (size <= 0) {
			return 0;
		}
		return Math.max(0, Math.min(2, (int) (v * 3 / size)));
	}
}
