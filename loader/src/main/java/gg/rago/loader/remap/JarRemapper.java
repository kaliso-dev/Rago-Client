package gg.rago.loader.remap;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Renames the vanilla client jar with an {@link Srg} table, the way Forge renames each class as it
 * loads it, but once, ahead of time: the game then starts without any renaming at runtime.
 *
 * <p>A field or method used through a subclass ({@code Child.foo} declared in {@code Parent}) is
 * found by walking up the class's parents inside the jar. Mojang's signature files are dropped,
 * since the classes no longer match them.
 */
public final class JarRemapper extends Remapper {
	private record Info(String superName, String[] interfaces) { }

	private final Srg srg;
	private final Map<String, Info> infos = new HashMap<>();
	private final Map<String, String> methodCache = new HashMap<>();
	private final Map<String, String> fieldCache = new HashMap<>();

	public JarRemapper(Srg srg) {
		this.srg = srg;
	}

	@Override
	public String map(String internalName) {
		return srg.classes.getOrDefault(internalName, internalName);
	}

	@Override
	public String mapMethodName(String owner, String name, String descriptor) {
		if (name.startsWith("<")) {
			return name;
		}
		String key = owner + "/" + name + " " + descriptor;
		String cached = methodCache.get(key);
		if (cached == null) {
			cached = findMethod(owner, name, descriptor);
			methodCache.put(key, cached == null ? name : cached);
		}
		return cached == null ? name : cached;
	}

	private String findMethod(String owner, String name, String descriptor) {
		String found = srg.methods.get(owner + "/" + name + " " + descriptor);
		if (found != null) {
			return found;
		}
		Info info = infos.get(owner);
		if (info == null) {
			return null;
		}
		if (info.superName != null && (found = findMethod(info.superName, name, descriptor)) != null) {
			return found;
		}
		for (String i : info.interfaces) {
			if ((found = findMethod(i, name, descriptor)) != null) {
				return found;
			}
		}
		return null;
	}

	@Override
	public String mapFieldName(String owner, String name, String descriptor) {
		String key = owner + "/" + name;
		String cached = fieldCache.get(key);
		if (cached == null) {
			String found = findField(owner, name);
			cached = found == null ? name : found;
			fieldCache.put(key, cached);
		}
		return cached;
	}

	private String findField(String owner, String name) {
		String found = srg.fields.get(owner + "/" + name);
		if (found != null) {
			return found;
		}
		Info info = infos.get(owner);
		if (info == null) {
			return null;
		}
		if (info.superName != null && (found = findField(info.superName, name)) != null) {
			return found;
		}
		for (String i : info.interfaces) {
			if ((found = findField(i, name)) != null) {
				return found;
			}
		}
		return null;
	}

	/** Writes {@code in} renamed to {@code out} (through a temporary file, so a half-written jar is never left). */
	public void remap(Path in, Path out) throws IOException {
		Map<String, byte[]> classes = new LinkedHashMap<>();
		List<String> order = new ArrayList<>();
		Path tmp = out.resolveSibling(out.getFileName() + ".tmp");
		try (ZipFile zip = new ZipFile(in.toFile())) {
			for (ZipEntry e : java.util.Collections.list(zip.entries())) {
				if (e.isDirectory()) {
					continue;
				}
				order.add(e.getName());
				if (e.getName().endsWith(".class")) {
					byte[] bytes;
					try (InputStream s = zip.getInputStream(e)) {
						bytes = s.readAllBytes();
					}
					classes.put(e.getName(), bytes);
					ClassReader r = new ClassReader(bytes);
					infos.put(r.getClassName(), new Info(r.getSuperName(), r.getInterfaces()));
				}
			}
			Files.createDirectories(out.toAbsolutePath().getParent());
			try (OutputStream file = Files.newOutputStream(tmp); ZipOutputStream zout = new ZipOutputStream(file)) {
				for (String name : order) {
					if (name.startsWith("META-INF/") && (name.endsWith(".SF") || name.endsWith(".DSA") || name.endsWith(".RSA"))) {
						continue;
					}
					byte[] bytes = classes.get(name);
					String outName = name;
					if (bytes != null) {
						ClassReader r = new ClassReader(bytes);
						ClassWriter w = new ClassWriter(0);
						r.accept(new ClassRemapper(w, this), 0);
						bytes = w.toByteArray();
						outName = map(r.getClassName()) + ".class";
					} else {
						try (InputStream s = zip.getInputStream(zip.getEntry(name))) {
							bytes = s.readAllBytes();
						}
						if (name.equals("META-INF/MANIFEST.MF")) {
							bytes = "Manifest-Version: 1.0\r\n\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
						}
					}
					zout.putNextEntry(new ZipEntry(outName));
					zout.write(bytes);
					zout.closeEntry();
				}
			}
		}
		Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
