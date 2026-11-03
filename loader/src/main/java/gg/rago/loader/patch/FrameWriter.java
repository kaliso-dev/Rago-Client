package gg.rago.loader.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A ClassWriter that recomputes stack map frames without loading any game class: the class
 * hierarchy is read from the class files themselves (the game's through {@code classes}, the JDK's
 * and the libraries' through the parent loader). Loading a game class here would define it before
 * its own transformation.
 */
public final class FrameWriter extends ClassWriter {
	private record Node(String superName, boolean isInterface) { }

	private static final Map<String, Node> NODES = new ConcurrentHashMap<>();
	private final ClassLoader classes;

	public FrameWriter(ClassLoader classes) {
		super(COMPUTE_FRAMES);
		this.classes = classes;
	}

	@Override
	protected ClassLoader getClassLoader() {
		return classes;
	}

	private Node node(String name) {
		return NODES.computeIfAbsent(name, n -> {
			try (InputStream in = classes.getResourceAsStream(n + ".class")) {
				if (in == null) {
					throw new TypeNotPresentException(n.replace('/', '.'), null);
				}
				ClassReader r = new ClassReader(in);
				return new Node(r.getSuperName(), (r.getAccess() & Opcodes.ACC_INTERFACE) != 0);
			} catch (IOException e) {
				throw new TypeNotPresentException(n.replace('/', '.'), e);
			}
		});
	}

	private List<String> chain(String name) {
		List<String> out = new ArrayList<>();
		for (String c = name; c != null; c = node(c).superName) {
			out.add(c);
		}
		return out;
	}

	private boolean assignable(String to, String from) {
		if (to.equals(from)) {
			return true;
		}
		Node n = node(from);
		if (n.superName != null && assignable(to, n.superName)) {
			return true;
		}
		try (InputStream in = classes.getResourceAsStream(from + ".class")) {
			if (in != null) {
				for (String i : new ClassReader(in).getInterfaces()) {
					if (assignable(to, i)) {
						return true;
					}
				}
			}
		} catch (IOException e) {
			throw new TypeNotPresentException(from.replace('/', '.'), e);
		}
		return false;
	}

	@Override
	protected String getCommonSuperClass(String a, String b) {
		if (a.equals(b)) {
			return a;
		}
		if (node(a).isInterface || node(b).isInterface) {
			if (assignable(a, b)) {
				return a;
			}
			if (assignable(b, a)) {
				return b;
			}
			return "java/lang/Object";
		}
		List<String> up = chain(a);
		for (String c = b; c != null; c = node(c).superName) {
			if (up.contains(c)) {
				return c;
			}
		}
		return "java/lang/Object";
	}
}
