package gg.rago.hud;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudPositionTest {
	@Test
	void topLeftOffsets() {
		HudPosition p = new HudPosition(Anchor.TOP_LEFT, 4, 6);
		assertEquals(4, p.screenX(50, 400));
		assertEquals(6, p.screenY(10, 300));
	}

	@Test
	void rightAnchorFollowsScreenWidth() {
		HudPosition p = new HudPosition(Anchor.TOP_RIGHT, -4, 4);
		assertEquals(400 - 50 - 4, p.screenX(50, 400));
		assertEquals(800 - 50 - 4, p.screenX(50, 800));
	}

	@Test
	void positionIsClampedOnScreen() {
		HudPosition p = new HudPosition(Anchor.TOP_LEFT, 1000, -20);
		assertEquals(350, p.screenX(50, 400));
		assertEquals(0, p.screenY(10, 300));
	}

	@Test
	void moveToReanchorsAndRoundTrips() {
		HudPosition p = new HudPosition(Anchor.TOP_LEFT, 0, 0);
		p.moveTo(330, 270, 60, 20, 400, 300);
		assertEquals(Anchor.BOTTOM_RIGHT, p.anchor());
		assertEquals(330, p.screenX(60, 400));
		assertEquals(270, p.screenY(20, 300));
		// after a resize it stays attached to the bottom-right corner
		assertEquals(330 + 400, p.screenX(60, 800));
	}

	@Test
	void centreAnchor() {
		HudPosition p = new HudPosition(Anchor.TOP_LEFT, 0, 0);
		p.moveTo(170, 140, 60, 20, 400, 300);
		assertEquals(Anchor.CENTER, p.anchor());
		assertEquals(0, p.offsetX());
		assertEquals(0, p.offsetY());
	}

	@Test
	void scaleIsClampedAndRounded() {
		HudPosition p = new HudPosition(Anchor.TOP_LEFT, 0, 0);
		p.setScale(9);
		assertEquals(HudPosition.MAX_SCALE, p.scale());
		p.setScale(0.1f);
		assertEquals(HudPosition.MIN_SCALE, p.scale());
		p.setScale(1.04f);
		assertEquals(1.0f, p.scale());
	}

	@Test
	void jsonRoundTripAndBadValues() {
		HudPosition p = new HudPosition(Anchor.TOP_LEFT, 4, 4);
		p.moveTo(330, 270, 60, 20, 400, 300);
		p.setScale(1.5f);
		HudPosition q = new HudPosition(Anchor.TOP_LEFT, 4, 4);
		q.fromJson(p.toJson());
		assertEquals(p.anchor(), q.anchor());
		assertEquals(p.offsetX(), q.offsetX());
		assertEquals(1.5f, q.scale());

		JsonObject bad = new JsonObject();
		bad.addProperty("anchor", "NOWHERE");
		q.fromJson(bad);
		assertEquals(Anchor.TOP_LEFT, q.anchor());
		assertEquals(4, q.offsetX());
	}

	@Test
	void defaultUntilMoved() {
		HudPosition p = new HudPosition(Anchor.TOP_LEFT, 4, 26);
		assertTrue(p.isDefault());
		p.setScale(1.5f);
		assertTrue(p.isDefault());
		p.moveTo(100, 100, 40, 10, 400, 300);
		assertFalse(p.isDefault());
		p.reset();
		assertTrue(p.isDefault());
	}
}
