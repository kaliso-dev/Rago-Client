package gg.rago.loader;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.objectweb.asm.Opcodes.*;

class StartTest {
	private static byte[] type(String name, String annotation) {
		ClassWriter w = new ClassWriter(0);
		w.visit(V1_8, ACC_PUBLIC | ACC_SUPER, name, null, "java/lang/Object", null);
		if (annotation != null) {
			w.visitAnnotation(annotation, true).visitEnd();
		}
		w.visitEnd();
		return w.toByteArray();
	}

	@Test
	void findsModClasses() {
		assertEquals("gg/rago/core/RagoClient", Start.modClass(type("gg/rago/core/RagoClient", "Lnet/minecraftforge/fml/common/Mod;")));
		assertNull(Start.modClass(type("gg/rago/core/Other", "Ljava/lang/Deprecated;")));
		assertNull(Start.modClass(type("gg/rago/core/Plain", null)));
	}

	@Test
	void parsesArguments() {
		Start.Args a = Start.Args.parse(new String[] {"launch", "--game", "g.jar", "--mod", "a.jar", "--mod", "b.jar", "--",
			"--username", "Rago", "--gameDir", "/games/rago"});
		assertEquals(Path.of("g.jar"), a.game());
		assertEquals(List.of(Path.of("a.jar"), Path.of("b.jar")), a.mods());
		assertArrayEquals(new String[] {"--username", "Rago", "--gameDir", "/games/rago"}, a.minecraft());
		assertEquals("/games/rago", a.option("--gameDir", "."));
		assertEquals("assets", a.option("--assetsDir", "assets"));
	}

	@Test
	void needsTheGame() {
		assertThrows(IllegalArgumentException.class, () -> Start.Args.parse(new String[] {"launch", "--mod", "a.jar"}));
		assertThrows(IllegalArgumentException.class, () -> Start.Args.parse(new String[] {"launch", "--game", "g.jar", "--what"}));
	}
}
