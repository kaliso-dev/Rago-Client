package gg.rago.music;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaSessionTest {
	@Test
	void aLineOfTheScriptIsATrack() {
		MediaSession.Track t = MediaSession.parse("{\"title\":\"Blinding Lights\",\"artist\":\"The Weeknd\",\"playing\":true,"
			+ "\"position\":61.5,\"duration\":200.0,\"cover\":\"C:\\\\x\\\\cover-1.img\",\"coverId\":1,\"app\":\"Spotify.exe\"}", 0);
		assertEquals("Blinding Lights", t.title);
		assertEquals("The Weeknd", t.artist);
		assertTrue(t.playing);
		assertEquals(1, t.coverId);
		assertEquals("C:\\x\\cover-1.img", t.cover);
		// it moves on by itself while playing, and stops at the end
		assertEquals(63.5, t.positionAt(2_000_000_000L), 1e-9);
		assertEquals(200.0, t.positionAt(1_000_000_000_000L), 1e-9);
	}

	@Test
	void nothingPlayingAndBrokenLines() {
		assertSame(MediaSession.NONE, MediaSession.parse("{\"none\":true}", 0));
		assertSame(MediaSession.NONE, MediaSession.parse("{\"error\":true}", 0));
		assertSame(MediaSession.NONE, MediaSession.parse("{\"title\":\"\",\"playing\":true}", 0));
		assertNull(MediaSession.parse("not json {", 0));
		assertNull(MediaSession.parse("[1,2]", 0));
	}

	@Test
	void aPausedTrackDoesNotMove() {
		MediaSession.Track t = MediaSession.parse("{\"title\":\"x\",\"playing\":false,\"position\":10,\"duration\":0}", 0);
		assertFalse(t.playing);
		assertEquals(10, t.positionAt(5_000_000_000L), 1e-9);
	}

	@Test
	void aCoverIsCroppedToASquareAndScaled() throws Exception {
		BufferedImage wide = new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB);
		for (int x = 0; x < 300; x++) {
			for (int y = 0; y < 200; y++) {
				wide.setRGB(x, y, x < 50 || x >= 250 ? 0xFF0000 : 0x00FF00);
			}
		}
		File f = Files.createTempFile("cover", ".img").toFile();
		try {
			ImageIO.write(wide, "png", f);
			int[] px = MediaSession.decode(f);
			assertEquals(MediaSession.COVER * MediaSession.COVER, px.length);
			// the red side bands were cropped away
			assertEquals(0xFF00FF00, px[0]);
			assertEquals(0xFF00FF00, px[px.length - 1]);
			Files.write(f.toPath(), new byte[]{1, 2, 3});
			assertNull(MediaSession.decode(f));
		} finally {
			f.delete();
		}
	}
}
