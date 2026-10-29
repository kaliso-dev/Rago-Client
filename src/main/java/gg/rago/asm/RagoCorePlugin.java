package gg.rago.asm;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

/**
 * Registers {@link RagoTransformer}. It makes one change to the game: the frame-rate cap used by
 * menus and unfocused windows comes from Rago's settings instead of vanilla's fixed 30 FPS. It also
 * starts the OptiFine bundled in the jar, if there is one ({@link BundledOptiFine}).
 */
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.Name("Rago")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("gg.rago.asm.")
public final class RagoCorePlugin implements IFMLLoadingPlugin {
	public RagoCorePlugin() {
		BundledOptiFine.start();
	}

	@Override
	public String[] getASMTransformerClass() {
		return new String[] {RagoTransformer.class.getName()};
	}

	@Override
	public String getModContainerClass() {
		return null;
	}

	@Override
	public String getSetupClass() {
		return null;
	}

	@Override
	public void injectData(Map<String, Object> data) {
	}

	@Override
	public String getAccessTransformerClass() {
		return null;
	}
}
