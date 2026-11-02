package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.TextColors;
import gg.rago.optimize.OptiFineFields;
import gg.rago.overlays.AtlasPaint;
import gg.rago.overlays.BlockArt;
import gg.rago.overlays.BuiltInSky;
import gg.rago.overlays.PackSky;
import gg.rago.overlays.SkyArt;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Locale;

/**
 * Overlays without hunting for packs: Rago's own wool and clay (The Bridge, Bed Wars) in a few clean
 * styles, following the colour-blind team colours if you use them; six skies painted by Rago; skies
 * from your packs shown even without OptiFine; and block-hit styles for the sword. Overlay packs in
 * your resourcepacks folder are listed too, to turn on over your main pack. Only what you see changes.
 */
public final class Overlays extends Module {
	public static final String[] WOOL = {"off", "flat", "outline", "bevel", "knit"};
	public static final String[] CLAY = {"off", "flat", "outline", "bevel"};
	public static final String[] SKIES = {"off", "day", "sunset", "night", "galaxy", "aurora", "pastel"};
	public static final String[] BLOCKHITS = {"off", "classic", "low", "slide", "thrust", "tilt"};

	/** A block-hit style: the sword's movement while you block and hit. */
	public enum BlockStyle {
		/** The 1.7 swing while blocking. */
		CLASSIC(true),
		/** The 1.7 swing, the sword lower and a little smaller: more of the screen to see. */
		LOW(true),
		/** No swing: the sword dips with each hit. */
		SLIDE(false),
		/** No swing: the sword pushes forward with each hit. */
		THRUST(false),
		/** No swing: the blade tips over with each hit. */
		TILT(false);

		/** Whether the arm's swing turns the sword (1.7) or the style moves it its own way. */
		final boolean swing;

		BlockStyle(boolean swing) {
			this.swing = swing;
		}
	}

	private final ChoiceOption wool = hidden(addOption(new ChoiceOption("overlay_wool", "off", WOOL)));
	private final ChoiceOption clay = hidden(addOption(new ChoiceOption("overlay_clay", "off", CLAY)));
	private final BooleanOption teamColors = hidden(addOption(new BooleanOption("overlay_team_colors", true)));
	private final ChoiceOption sky = hidden(addOption(new ChoiceOption("overlay_sky", "off", SKIES)));
	private final BooleanOption packSkies = hidden(addOption(new BooleanOption("overlay_pack_skies", true)));
	private final ChoiceOption blockhit = hidden(addOption(new ChoiceOption("overlay_blockhit", "off", BLOCKHITS)));

	private BuiltInSky builtIn;
	private PackSky packSky = PackSky.NONE;
	/** Counts resource reloads: a new atlas has the pack's pictures again. */
	private int reloads;
	/** What the atlas was last painted for (styles, team colours, palette, reload), compared every tick without making a string. */
	private BlockArt.Style paintedWool;
	private BlockArt.Style paintedClay;
	private int paintedKey = Integer.MIN_VALUE;
	/** Whether Rago's wool or clay is in the atlas now (so off must put the pack's back). */
	private boolean paintedAny;
	/** The swing passed to the block pose this frame, and whether the next held-item transform is it. */
	private float blockSwing;
	private boolean blockPose;

	public Overlays() {
		super("overlays", FeatureClass.VISUAL, true);
		Minecraft mc = Minecraft.getMinecraft();
		if (mc != null && mc.getResourceManager() instanceof IReloadableResourceManager) {
			// after the game's own listeners: the block atlas is made by then
			((IReloadableResourceManager) mc.getResourceManager()).registerReloadListener(m -> {
				reloads++;
				paintedAny = false;
				packSky = PackSky.load(m);
			});
		}
	}

	private static <O extends gg.rago.config.Option<?>> O hidden(O option) {
		// chosen in the overlays panel of the menu, with pictures, not as rows
		option.showIf(() -> false);
		return option;
	}

	@Override
	public Category category() {
		return Category.VISUAL;
	}

	public ChoiceOption woolOption() {
		return wool;
	}

	public ChoiceOption clayOption() {
		return clay;
	}

	public BooleanOption teamColorsOption() {
		return teamColors;
	}

	public ChoiceOption skyOption() {
		return sky;
	}

	public BooleanOption packSkiesOption() {
		return packSkies;
	}

	public ChoiceOption blockhitOption() {
		return blockhit;
	}

	/** How many times the resources were reloaded: the packs' pictures may have changed each time. */
	public int reloads() {
		return reloads;
	}

	/** The skies found in the packs in use (shown by Rago when OptiFine is not installed). */
	public PackSky packSky() {
		return packSky;
	}

	public static BlockArt.Style style(String id) {
		return "off".equals(id) ? null : BlockArt.Style.valueOf(id.toUpperCase(Locale.ROOT));
	}

	public static SkyArt.Kind skyKind(String id) {
		return "off".equals(id) ? null : SkyArt.Kind.valueOf(id.toUpperCase(Locale.ROOT));
	}

	/** The RGB a team dye takes from the colour-blind palette, or -1 for Rago's own colour. */
	public int teamRgb(int dye) {
		int code = BlockArt.teamCode(dye);
		return teamColors.get() && code >= 0 && TextColors.palette() != TextColors.NORMAL ? TextColors.rgb(code) : -1;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		paintBlocks(style(wool.get()), style(clay.get()));
		updateSky(mc, skyKind(sky.get()));
	}

	@Override
	protected void onDisable() {
		Minecraft mc = Minecraft.getMinecraft();
		paintBlocks(null, null);
		updateSky(mc, null);
	}

	private void paintBlocks(BlockArt.Style w, BlockArt.Style c) {
		boolean colors = teamColors.get();
		int key = (reloads * 31 + TextColors.palette()) * 2 + (colors ? 1 : 0);
		if (w == paintedWool && c == paintedClay && key == paintedKey) {
			return;
		}
		paintedWool = w;
		paintedClay = c;
		paintedKey = key;
		if (w == null && c == null && !paintedAny) {
			return; // the atlas already holds the packs' pictures
		}
		try {
			int n = AtlasPaint.paint(w, c, this::teamRgb);
			paintedAny = n > 0;
			RagoClient.LOG.info("Overlays: wool {}, clay {} ({} blocks painted)", w, c, n);
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("Overlays: could not paint wool and clay", e);
		}
	}

	private void updateSky(Minecraft mc, SkyArt.Kind kind) {
		if (builtIn != null && builtIn.kind != kind) {
			uninstall(mc);
			builtIn.delete();
			builtIn = null;
		}
		if (kind != null && builtIn == null) {
			builtIn = new BuiltInSky(kind);
		}
		WorldClient world = mc.theWorld;
		if (world == null) {
			return;
		}
		WorldProvider provider = world.provider;
		if (builtIn != null && builtIn.ready() && provider.getDimensionId() == 0 && provider.getSkyRenderer() != builtIn) {
			provider.setSkyRenderer(builtIn);
		}
	}

	private void uninstall(Minecraft mc) {
		if (mc.theWorld != null && builtIn != null && mc.theWorld.provider.getSkyRenderer() == builtIn) {
			mc.theWorld.provider.setSkyRenderer(null);
		}
	}

	/** The fog takes the colour of Rago's sky at the horizon, so far land fades into the sky. */
	@SubscribeEvent
	public void onFogColors(EntityViewRenderEvent.FogColors event) {
		Minecraft mc = Minecraft.getMinecraft();
		BuiltInSky s = builtIn;
		if (!isEnabled() || s == null || mc.theWorld == null || mc.theWorld.provider.getSkyRenderer() != s
			|| event.block.getMaterial() != Material.air) {
			return;
		}
		Entity e = event.entity;
		if (e instanceof EntityLivingBase && ((EntityLivingBase) e).isPotionActive(Potion.blindness)) {
			return;
		}
		float k = 1 - mc.theWorld.getRainStrength((float) event.renderPartialTicks) * 0.45f;
		// the game's darkening near the void
		double y = (e.lastTickPosY + (e.posY - e.lastTickPosY) * event.renderPartialTicks) * mc.theWorld.provider.getVoidFogYFactor();
		if (y < 1) {
			k *= (float) Math.max(0, y) * (float) Math.max(0, y);
		}
		event.red = (s.horizon >> 16 & 0xFF) / 255f * k;
		event.green = (s.horizon >> 8 & 0xFF) / 255f * k;
		event.blue = (s.horizon & 0xFF) / 255f * k;
	}

	/** RenderGlobal.renderSky, after it turns for the sun: the packs' skies when OptiFine is not there to draw them. */
	public void packSky(float partialTicks) {
		Minecraft mc = Minecraft.getMinecraft();
		if (packSkies.get() && !packSky.isEmpty() && !OptiFineFields.installed() && mc.theWorld != null
			&& mc.theWorld.provider.getDimensionId() == 0) {
			packSky.render(mc.theWorld, partialTicks);
		}
	}

	/** Whether Rago draws the packs' skies now (none with OptiFine, which draws them itself). */
	public boolean drawsPackSky() {
		return packSkies.get() && !packSky.isEmpty() && !OptiFineFields.installed();
	}

	// ---- block-hit styles -----------------------------------------------------------------------

	/** The style chosen, or null to leave the sword to 1.7 Animations. */
	public BlockStyle blockStyle() {
		return isEnabled() && !blockhit.is("off") ? BlockStyle.valueOf(blockhit.get().toUpperCase(Locale.ROOT)) : null;
	}

	/** ItemRenderer, blocking: the swing given to the held-item transform; remembers it for the pose. */
	public float blockSwing(BlockStyle style, float swing) {
		blockSwing = swing;
		blockPose = true;
		return style.swing ? swing : 0;
	}

	/** Start of the held-item transform: when it is the block pose, moves the sword as the style does. */
	public void blockOffset() {
		if (!blockPose) {
			return;
		}
		blockPose = false;
		BlockStyle style = blockStyle();
		if (style == null) {
			return;
		}
		float s = MathHelper.sin(MathHelper.sqrt_float(blockSwing) * (float) Math.PI);
		switch (style) {
			case LOW:
				GlStateManager.translate(0.02f, -0.06f, 0.04f);
				break;
			case SLIDE:
				GlStateManager.translate(-0.08f * s, -0.24f * s, 0);
				break;
			case THRUST:
				GlStateManager.translate(0, 0.04f * s, -0.3f * s);
				break;
			default:
				break;
		}
	}

	/** Start of the block pose (after the held-item transform): turns or sizes the sword as the style does. */
	public void blockTransform() {
		BlockStyle style = blockStyle();
		if (style == null) {
			return;
		}
		float s = MathHelper.sin(MathHelper.sqrt_float(blockSwing) * (float) Math.PI);
		switch (style) {
			case LOW:
				GlStateManager.scale(0.88f, 0.88f, 0.88f);
				break;
			case SLIDE:
				GlStateManager.rotate(-14 * s, 0, 0, 1);
				break;
			case TILT:
				GlStateManager.rotate(-38 * s, 1, 0, 0);
				GlStateManager.rotate(-12 * s, 0, 1, 0);
				break;
			default:
				break;
		}
	}
}
