package gg.rago.loader.tools;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Lists what a mod uses from Minecraft that vanilla does not have: the classes, fields and methods
 * Forge adds to the game. That is what Rago's loader has to provide (or Rago has to stop using)
 * to run without Forge.
 */
public final class ForgeOnly {
	private record Members(String superName, String[] interfaces, Set<String> fields, Set<String> methods) { }

	public static Map<String, Set<String>> scan(Path game, Path mod) throws IOException {
		Map<String, Members> vanilla = new HashMap<>();
		try (ZipFile zip = new ZipFile(game.toFile())) {
			for (ZipEntry e : Collections.list(zip.entries())) {
				if (!e.getName().endsWith(".class")) {
					continue;
				}
				ClassNode c = new ClassNode();
				try (InputStream in = zip.getInputStream(e)) {
					new ClassReader(in).accept(c, ClassReader.SKIP_CODE);
				}
				Set<String> f = new TreeSet<>(), m = new TreeSet<>();
				for (FieldNode x : c.fields) {
					f.add(x.name);
				}
				for (MethodNode x : c.methods) {
					m.add(x.name + x.desc);
				}
				vanilla.put(c.name, new Members(c.superName, c.interfaces.toArray(new String[0]), f, m));
			}
		}
		Map<String, Set<String>> missing = new TreeMap<>();
		try (ZipFile zip = new ZipFile(mod.toFile())) {
			for (ZipEntry e : Collections.list(zip.entries())) {
				if (!e.getName().endsWith(".class")) {
					continue;
				}
				ClassNode c = new ClassNode();
				try (InputStream in = zip.getInputStream(e)) {
					new ClassReader(in).accept(c, 0);
				}
				for (MethodNode m : c.methods) {
					for (AbstractInsnNode i : m.instructions) {
						if (i instanceof MethodInsnNode mi && mi.owner.startsWith("net/minecraft/")) {
							if (!has(vanilla, mi.owner, mi.name + mi.desc, false)) {
								missing.computeIfAbsent(c.name, k -> new TreeSet<>()).add(mi.owner + "." + mi.name + mi.desc);
							}
						} else if (i instanceof FieldInsnNode fi && fi.owner.startsWith("net/minecraft/")) {
							if (!has(vanilla, fi.owner, fi.name, true)) {
								missing.computeIfAbsent(c.name, k -> new TreeSet<>()).add(fi.owner + "." + fi.name);
							}
						}
					}
				}
				if (c.superName != null && c.superName.startsWith("net/minecraft/") && !vanilla.containsKey(c.superName)) {
					missing.computeIfAbsent(c.name, k -> new TreeSet<>()).add("extends " + c.superName);
				}
			}
		}
		return missing;
	}

	private static boolean has(Map<String, Members> vanilla, String owner, String member, boolean field) {
		Members m = vanilla.get(owner);
		if (m == null) {
			return false;
		}
		if ((field ? m.fields : m.methods).contains(member)) {
			return true;
		}
		if (m.superName != null && vanilla.containsKey(m.superName) && has(vanilla, m.superName, member, field)) {
			return true;
		}
		for (String i : m.interfaces) {
			if (vanilla.containsKey(i) && has(vanilla, i, member, field)) {
				return true;
			}
		}
		if (field) {
			return false;
		}
		// declared in a JDK parent (Object.equals, Comparable.compareTo): not Forge's
		for (String parent : parents(m)) {
			if (!vanilla.containsKey(parent) && (!parent.startsWith("java/") || jdkHas(parent, member))) {
				return true;
			}
		}
		return false;
	}

	private static java.util.List<String> parents(Members m) {
		java.util.List<String> out = new java.util.ArrayList<>(java.util.Arrays.asList(m.interfaces));
		if (m.superName != null) {
			out.add(m.superName);
		}
		return out;
	}

	private static boolean jdkHas(String owner, String member) {
		try {
			for (Class<?> c = Class.forName(owner.replace('/', '.')); c != null; c = c.getSuperclass()) {
				for (java.lang.reflect.Method x : c.getDeclaredMethods()) {
					if (member.equals(x.getName() + org.objectweb.asm.Type.getMethodDescriptor(x))) {
						return true;
					}
				}
				for (Class<?> i : c.getInterfaces()) {
					if (jdkHas(i.getName().replace('.', '/'), member)) {
						return true;
					}
				}
			}
		} catch (ClassNotFoundException e) {
			return true;
		}
		return false;
	}

	private ForgeOnly() {
	}
}
