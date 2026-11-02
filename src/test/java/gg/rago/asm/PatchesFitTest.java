package gg.rago.asm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.BasicVerifier;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every patch, applied to the game's own classes (Minecraft and Forge with their readable names, as
 * in development): it must find what it looks for, and the methods it changes must still be valid
 * bytecode (each instruction gets the values it expects from the stack).
 */
class PatchesFitTest {
	@Test
	void everyPatchFitsTheGameAndKeepsValidCode() throws IOException {
		List<String> problems = new ArrayList<>();
		int methods = 0;
		for (String name : new TreeSet<>(RagoTransformer.patchedClasses())) {
			byte[] bytes = read(name);
			if (bytes == null) {
				problems.add(name + ": class not found");
				continue;
			}
			ClassNode node = new ClassNode();
			new ClassReader(bytes).accept(node, 0);
			List<MethodNode> changed = RagoTransformer.patchForTest(name, node, problems);
			// written as the game gets it (the stack size worked out again), then read back
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			ClassNode written = new ClassNode();
			new ClassReader(writer.toByteArray()).accept(written, 0);
			for (MethodNode m : changed) {
				methods++;
				for (MethodNode w : written.methods) {
					if (w.name.equals(m.name) && w.desc.equals(m.desc)) {
						try {
							new Analyzer<BasicValue>(new BasicVerifier()).analyze(written.name, w);
						} catch (AnalyzerException e) {
							problems.add(name + "." + m.name + ": " + e.getMessage());
						}
					}
				}
			}
		}
		assertEquals(Collections.emptyList(), problems);
		assertFalse(methods == 0);
	}

	private static byte[] read(String className) throws IOException {
		try (InputStream in = PatchesFitTest.class.getClassLoader().getResourceAsStream(className.replace('.', '/') + ".class")) {
			if (in == null) {
				return null;
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			for (int n; (n = in.read(buffer)) > 0; ) {
				out.write(buffer, 0, n);
			}
			return out.toByteArray();
		}
	}
}
