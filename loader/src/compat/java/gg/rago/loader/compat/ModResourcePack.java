package gg.rago.loader.compat;

import net.minecraft.client.resources.FileResourcePack;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** A mod's jar as a resource pack, named after the mod; mod jars have no pack.mcmeta, so one is made up, as Forge does. */
final class ModResourcePack extends FileResourcePack {
	private final String name;

	ModResourcePack(File jar, String name) {
		super(jar);
		this.name = name;
	}

	@Override
	protected InputStream func_110591_a(String path) throws IOException {
		if (path.equals("pack.mcmeta") && !func_110593_b(path)) {
			String meta = "{\"pack\":{\"description\":\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"pack_format\":1}}";
			return new ByteArrayInputStream(meta.getBytes(StandardCharsets.UTF_8));
		}
		return super.func_110591_a(path);
	}

	@Override
	public String func_130077_b() {
		return name;
	}
}
