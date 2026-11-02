package gg.rago.gui.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoBackdropTest {
	@Test
	void everyBackgroundHasAClipAndAPlaceInTheMenu() throws IOException {
		assertEquals(Background.KEYS.length, Background.CLIPS.length);
		int[] sorted = Background.ORDER.clone();
		Arrays.sort(sorted);
		int[] all = new int[Background.KEYS.length];
		for (int i = 0; i < all.length; i++) {
			all[i] = i;
		}
		assertArrayEquals(all, sorted, "the menu shows every background once");
		assertEquals(Background.OWN_IMAGE, Background.ORDER[Background.ORDER.length - 1], "the player's own comes last");
		assertNull(Background.CLIPS[Background.OWN_IMAGE]);
		for (int id = 0; id < Background.CLIPS.length; id++) {
			String clip = Background.CLIPS[id];
			if (clip == null) {
				continue;
			}
			try (InputStream in = VideoBackdrop.class.getResourceAsStream("/assets/rago/video/" + clip + ".bin")) {
				assertNotNull(in, clip);
				VideoBackdrop.Clip c = VideoBackdrop.read(in);
				assertEquals(960, c.width, clip);
				assertEquals(540, c.height, clip);
				assertTrue(c.fps == 24 || c.fps == 30, clip);
				assertTrue(c.jpegs.length >= 90, clip + " loops at least a few seconds");
				BufferedImage first = ImageIO.read(new ByteArrayInputStream(c.jpegs[0]));
				assertEquals(960, first.getWidth(), clip);
				BufferedImage last = ImageIO.read(new ByteArrayInputStream(c.jpegs[c.jpegs.length - 1]));
				assertEquals(540, last.getHeight(), clip);
			}
			try (InputStream in = VideoBackdrop.class.getResourceAsStream("/assets/rago/video/" + clip + ".jpg")) {
				assertNotNull(in, clip + " still picture");
				assertEquals(320, ImageIO.read(in).getWidth(), clip);
			}
		}
	}

	@Test
	void aBrokenClipFileIsRefused() {
		assertThrows(IOException.class, () -> VideoBackdrop.read(new ByteArrayInputStream("NOPE....".getBytes(StandardCharsets.US_ASCII))));
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (DataOutputStream out = new DataOutputStream(bytes)) {
			out.writeBytes("RGV1");
			out.writeInt(960);
			out.writeInt(540);
			out.writeInt(24);
			out.writeInt(2);
			out.writeInt(10);
			out.writeInt(-5);
		} catch (IOException e) {
			throw new AssertionError(e);
		}
		assertThrows(IOException.class, () -> VideoBackdrop.read(new ByteArrayInputStream(bytes.toByteArray())));
	}

	@Test
	void theOwnVideoIsReadFromAFolderOfFrames(@TempDir Path dir) throws IOException {
		frame(dir.resolve("0002.jpg"), 2000, 1000);
		frame(dir.resolve("0001.jpg"), 2000, 1000);
		frame(dir.resolve("0003.jpg"), 640, 360); // another size: left out
		Files.write(dir.resolve("notes.txt"), "not a frame".getBytes(StandardCharsets.UTF_8));
		Files.write(dir.resolve("0004.jpg"), "not a JPEG".getBytes(StandardCharsets.UTF_8));
		VideoBackdrop.Clip c = VideoBackdrop.readFolder(dir.toFile());
		assertEquals(2, c.jpegs.length);
		assertEquals(2, c.subsample, "2000 wide is read at half size");
		assertEquals(1000, c.width);
		assertEquals(500, c.height);
		assertEquals(24, c.fps);
		Files.write(dir.resolve("fps.txt"), " 30\n".getBytes(StandardCharsets.UTF_8));
		assertEquals(30, VideoBackdrop.ownFps(dir.toFile()));
		Files.write(dir.resolve("fps.txt"), "999".getBytes(StandardCharsets.UTF_8));
		assertEquals(24, VideoBackdrop.ownFps(dir.toFile()));
	}

	@Test
	void largeFramesAreReadAtMost1280Wide() {
		assertEquals(1, VideoBackdrop.subsample(1280));
		assertEquals(2, VideoBackdrop.subsample(1920));
		assertEquals(2, VideoBackdrop.subsample(2560));
		assertEquals(3, VideoBackdrop.subsample(3840));
	}

	@Test
	void anEmptyFolderIsNoVideo(@TempDir Path dir) {
		File empty = dir.toFile();
		assertThrows(IOException.class, () -> VideoBackdrop.readFolder(empty));
	}

	private static void frame(Path file, int w, int h) throws IOException {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
		ImageIO.write(img, "jpg", file.toFile());
	}
}
