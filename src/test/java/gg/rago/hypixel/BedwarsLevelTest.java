package gg.rago.hypixel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BedwarsLevelTest {
	@Test
	void theFirstStarsOfAPrestigeCostLess() {
		assertEquals(0, BedwarsLevel.star(0));
		assertEquals(0, BedwarsLevel.star(499));
		assertEquals(1, BedwarsLevel.star(500));
		assertEquals(2, BedwarsLevel.star(1500));
		assertEquals(3, BedwarsLevel.star(3500));
		assertEquals(4, BedwarsLevel.star(7000));
		assertEquals(5, BedwarsLevel.star(12_000));
		assertEquals(99, BedwarsLevel.star(BedwarsLevel.PRESTIGE_XP - 1));
		assertEquals(100, BedwarsLevel.star(BedwarsLevel.PRESTIGE_XP));
		assertEquals(101, BedwarsLevel.star(BedwarsLevel.PRESTIGE_XP + 500), "a new prestige starts cheap again");
	}

	@Test
	void experienceToTheNextStar() {
		// star 312: three prestiges, the four cheap stars and eight more, then 4,800 into the 13th
		long xp = 3 * BedwarsLevel.PRESTIGE_XP + 7000 + 8 * 5000 + 4800;
		assertEquals(312, BedwarsLevel.star(xp));
		assertEquals(4800, BedwarsLevel.into(xp));
		assertEquals(200, BedwarsLevel.toNext(xp));
		assertEquals(0.96, BedwarsLevel.progress(xp), 1e-9);
		assertEquals(500, BedwarsLevel.toNext(BedwarsLevel.PRESTIGE_XP));
		assertEquals(5000, BedwarsLevel.cost(99));
		assertEquals(500, BedwarsLevel.cost(100));
		assertEquals(3500, BedwarsLevel.cost(203));
		assertEquals(0, BedwarsLevel.star(-5), "nothing below zero");
	}
}
