package gg.rago.render;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import gg.rago.core.RagoClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A built-in resource pack for 3D Items: the ladder's block model in 3D ({@link Ladder3D}), made
 * from the ladder texture of the packs in use, and the bed's ({@link Bed3D}), drawn with the bed
 * textures in use. It sits under every other pack, so a pack with a ladder or bed model of its own
 * keeps it. Added before the first resource load; it serves each model only while 3D Items wants
 * it, and asks for a resource reload when that changes.
 */
public final class Pack3D implements IResourcePack {
	private static final ResourceLocation LADDER_MODEL = new ResourceLocation("minecraft", "models/block/ladder.json");
	private static final ResourceLocation LADDER_TEXTURE = new ResourceLocation("minecraft", "textures/blocks/ladder.png");
	private static final ResourceLocation BED_HEAD_MODEL = new ResourceLocation("minecraft", "models/block/bed_head.json");
	private static final ResourceLocation BED_FOOT_MODEL = new ResourceLocation("minecraft", "models/block/bed_foot.json");
	private static Pack3D installed;

	private BooleanSupplier ladder = () -> false;
	private BooleanSupplier bed = () -> false;
	/** What the last resource load was made with, and whether a reload is on its way. */
	private boolean loadedLadder;
	private boolean loadedBed;
	private boolean reloading;

	private Pack3D() {
	}

	/** Adds the pack under every other one; call it before the game first loads its resources. */
	public static void install() {
		Minecraft mc = Minecraft.getMinecraft();
		try {
			List<IResourcePack> packs = ObfuscationReflectionHelper.getPrivateValue(Minecraft.class, mc, "defaultResourcePacks", "field_110449_ao");
			Pack3D pack = new Pack3D();
			packs.add(pack);
			installed = pack;
		} catch (RuntimeException e) {
			RagoClient.LOG.warn("3D Items: could not add the built-in pack", e);
		}
	}

	/** Whether the 3D ladder and bed are wanted, read at each resource load; then follows them ({@link #sync}). */
	public static void wants(BooleanSupplier ladder, BooleanSupplier bed) {
		Pack3D p = installed;
		if (p == null) {
			return;
		}
		p.ladder = ladder;
		p.bed = bed;
		if (Minecraft.getMinecraft().getResourceManager() instanceof IReloadableResourceManager) {
			// told of every resource load, after the models
			((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(m -> {
				p.loadedLadder = p.ladder.getAsBoolean();
				p.loadedBed = p.bed.getAsBoolean();
				p.reloading = false;
			});
		}
	}

	/**
	 * Reloads the resources when the 3D ladder or bed was turned on or off since the last load; not
	 * while the game starts (the settings being read), as it loads them then.
	 */
	public static void sync() {
		Pack3D p = installed;
		if (p != null && !p.reloading && (p.ladder.getAsBoolean() != p.loadedLadder || p.bed.getAsBoolean() != p.loadedBed)
			&& Loader.instance().hasReachedState(LoaderState.AVAILABLE)) {
			p.reloading = true;
			Minecraft.getMinecraft().scheduleResourcesRefresh();
		}
	}

	@Override
	public boolean resourceExists(ResourceLocation rl) {
		return LADDER_MODEL.equals(rl) && ladder.getAsBoolean() || (BED_HEAD_MODEL.equals(rl) || BED_FOOT_MODEL.equals(rl)) && bed.getAsBoolean();
	}

	@Override
	public InputStream getInputStream(ResourceLocation rl) throws IOException {
		if (!resourceExists(rl)) {
			throw new FileNotFoundException(rl.toString());
		}
		if (!LADDER_MODEL.equals(rl)) {
			return bed(BED_HEAD_MODEL.equals(rl));
		}
		JsonObject model = null;
		try {
			IResource texture = Minecraft.getMinecraft().getResourceManager().getResource(LADDER_TEXTURE);
			try (InputStream in = texture.getInputStream()) {
				BufferedImage image = ImageIO.read(in);
				model = image == null ? null : Ladder3D.model(image);
			}
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.warn("3D Items: could not read the ladder texture", e);
		}
		// past this point the model must exist (a missing one shows as a black and purple cube)
		if (model == null) {
			RagoClient.LOG.info("3D Items: the ladder texture has no rails to make 3D; the ladder stays flat");
			model = Ladder3D.flat();
		} else {
			RagoClient.LOG.info("3D Items: 3D ladder made of {} parts", model.getAsJsonArray("elements").size());
		}
		return new ByteArrayInputStream(model.toString().getBytes(StandardCharsets.UTF_8));
	}

	/** The 3D bed's head or foot, its legs as wide as the pack's side texture draws them. */
	private static InputStream bed(boolean head) {
		BufferedImage side = null;
		ResourceLocation texture = new ResourceLocation("minecraft", "textures/blocks/bed_" + (head ? "head" : "feet") + "_side.png");
		try (InputStream in = Minecraft.getMinecraft().getResourceManager().getResource(texture).getInputStream()) {
			side = ImageIO.read(in);
		} catch (IOException | RuntimeException e) {
			RagoClient.LOG.warn("3D Items: could not read {}, the bed keeps vanilla's legs", texture, e);
		}
		JsonObject model = Bed3D.model(head, side);
		return new ByteArrayInputStream(model.toString().getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public Set<String> getResourceDomains() {
		return ImmutableSet.of("minecraft");
	}

	@Override
	public <T extends IMetadataSection> T getPackMetadata(IMetadataSerializer serializer, String section) {
		return null;
	}

	@Override
	public BufferedImage getPackImage() throws IOException {
		throw new FileNotFoundException("pack.png");
	}

	@Override
	public String getPackName() {
		return "Rago 3D items";
	}
}
