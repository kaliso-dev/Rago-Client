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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The patches on classes as another mod left them: run the game with
 * {@code -Drago.dumpClasses=all} (with OptiFine, say), which saves every class Rago patches as it
 * arrives in {@code rago-dump/}, then run this test with {@code RAGO_DUMPED_CLASSES} set to that
 * folder. Skipped otherwise.
 */
class DumpedClassesFitTest {
	@Test
	void patchesFitTheDumpedClasses() throws IOException {
		String dir = System.getenv("RAGO_DUMPED_CLASSES");
		assumeTrue(dir != null && new File(dir).isDirectory(), "RAGO_DUMPED_CLASSES not set");
		List<String> problems = new ArrayList<>();
		File[] files = new File(dir).listFiles((d, name) -> name.endsWith(".class"));
		for (File file : files == null ? new File[0] : files) {
			String name = file.getName().substring(0, file.getName().length() - ".class".length());
			if (!RagoTransformer.patchedClasses().contains(name)) {
				continue;
			}
			ClassNode node = new ClassNode();
			new ClassReader(Files.readAllBytes(file.toPath())).accept(node, 0);
			List<MethodNode> changed = RagoTransformer.patchForTest(name, node, problems);
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			ClassNode written = new ClassNode();
			new ClassReader(writer.toByteArray()).accept(written, 0);
			for (MethodNode m : changed) {
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
	}
}
