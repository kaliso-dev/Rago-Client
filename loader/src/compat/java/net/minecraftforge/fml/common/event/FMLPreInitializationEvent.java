package net.minecraftforge.fml.common.event;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class FMLPreInitializationEvent extends FMLEvent {
	private final File configDir;
	private final File source;
	private final String modid;

	public FMLPreInitializationEvent(File configDir, File source, String modid) {
		this.configDir = configDir;
		this.source = source;
		this.modid = modid;
	}

	public File getModConfigurationDirectory() {
		return configDir;
	}

	public File getSuggestedConfigurationFile() {
		return new File(configDir, modid + ".cfg");
	}

	public File getSourceFile() {
		return source;
	}

	public Logger getModLog() {
		return LogManager.getLogger(modid);
	}
}
