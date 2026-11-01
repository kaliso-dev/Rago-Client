package gg.rago.gui;

import com.mojang.authlib.GameProfile;
import gg.rago.accounts.Skins;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.opengl.GL11;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The player in 3D on a menu (the locker, the home screen): the worn skin, the cape and the
 * cosmetics, with the head following the mouse. Works without a world loaded.
 */
final class PlayerPreview {
	/** A world for the preview player when no world is loaded; made once, it holds nothing. */
	private static WorldClient emptyWorld;

	private Model model;
	/** The account the model was made for: its UUID is what makes the cape and cosmetics yours. */
	private Session session;
	private boolean broken;

	private PlayerPreview(Model model, Session session) {
		this.model = model;
		this.session = session;
	}

	/** A preview of the account's player, or null when one cannot be made. */
	static PlayerPreview create() {
		Session session = Minecraft.getMinecraft().getSession();
		Model m = Model.create();
		return m == null ? null : new PlayerPreview(m, session);
	}

	/**
	 * The model for the signed-in account, made again after a switch of account: the old one keeps
	 * the old account's UUID, so the cape and cosmetics, which are drawn only on you, disappear.
	 */
	private Model model() {
		Session now = Minecraft.getMinecraft().getSession();
		if (now != session) {
			Model m = Model.create();
			if (m != null) {
				m.ticksExisted = model.ticksExisted;
				model = m;
			}
			session = now;
		}
		return model;
	}

	/** Moves the idle animation on; call once a tick. */
	void tick() {
		model().ticksExisted++;
	}

	UUID id() {
		return model().getUniqueID();
	}

	/**
	 * Draws the player standing at ({@code cx}, {@code feetY}), {@code size} units a block, turned
	 * by {@code yaw} degrees, in {@code skin} (null: the account's own, else Minecraft's default).
	 */
	void draw(float cx, float feetY, float size, float yaw, Skins.Skin skin, float partialTicks) {
		if (broken) {
			return;
		}
		Model e = model();
		if (skin != null && skin.texture() != null) {
			e.skin = skin.texture();
			e.slim = skin.slim;
		} else {
			Skins.Skin acc = Skins.account();
			if (acc != null && acc.texture() != null) {
				e.skin = acc.texture();
				e.slim = acc.slim;
			} else {
				e.skin = DefaultPlayerSkin.getDefaultSkin(e.getUniqueID());
				e.slim = "slim".equals(DefaultPlayerSkin.getSkinType(e.getUniqueID()));
			}
		}
		float s = Ui.scale();
		// the head follows the mouse, as in the inventory
		float lookX = (cx - Ui.mouseX()) / 40f;
		float lookY = (feetY - size * 1.6f - Ui.mouseY()) / 40f;
		e.renderYawOffset = e.prevRenderYawOffset = 0;
		e.rotationYaw = e.prevRotationYaw = 0;
		e.rotationYawHead = e.prevRotationYawHead = (float) Math.atan(lookX) * 30f;
		e.rotationPitch = e.prevRotationPitch = -(float) Math.atan(lookY) * 20f;

		GlStateManager.pushMatrix();
		GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
		GlStateManager.enableDepth();
		GlStateManager.depthMask(true);
		GlStateManager.enableAlpha();
		GlStateManager.enableColorMaterial();
		GlStateManager.color(1, 1, 1, Ui.opacity());
		GlStateManager.translate(cx * s, feetY * s, 100);
		GlStateManager.scale(-size * s, size * s, size * s);
		GlStateManager.rotate(180, 0, 0, 1);
		GlStateManager.rotate(135, 0, 1, 0);
		RenderHelper.enableStandardItemLighting();
		GlStateManager.rotate(-135, 0, 1, 0);
		GlStateManager.rotate(yaw, 0, 1, 0);
		try {
			render(e, partialTicks);
		} catch (RuntimeException ex) {
			RagoClient.LOG.warn("The player preview failed and is turned off", ex);
			broken = true;
		}
		GlStateManager.popMatrix();
		RenderHelper.disableStandardItemLighting();
		GlStateManager.disableRescaleNormal();
		GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
		GlStateManager.disableTexture2D();
		GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
		GlStateManager.enableTexture2D();
		// back to the state Ui draws in
		GlStateManager.disableLighting();
		GlStateManager.disableDepth();
		GlStateManager.enableBlend();
		GlStateManager.disableCull();
		GlStateManager.disableAlpha();
		GlStateManager.color(1, 1, 1, 1);
	}

	/**
	 * Draws the player model and its layers (cape, cosmetics) the way RendererLivingEntity does,
	 * but without its render events: mods listening to them (Patcher, for one) expect a world with
	 * a player in it, which the home screen does not have.
	 */
	private static void render(Model e, float partialTicks) {
		RenderPlayer render = Minecraft.getMinecraft().getRenderManager().getSkinMap().get(e.getSkinType());
		if (render == null) {
			return;
		}
		ModelPlayer model = render.getMainModel();
		model.setInvisible(true);
		model.swingProgress = 0;
		model.isRiding = false;
		model.isChild = false;
		model.isSneak = false;
		model.heldItemLeft = 0;
		model.heldItemRight = 0;
		model.aimedBow = false;
		float age = e.ticksExisted + partialTicks;
		float headYaw = e.rotationYawHead - e.renderYawOffset;
		float pitch = e.rotationPitch;
		float scale = 0.0625f;
		GlStateManager.pushMatrix();
		GlStateManager.disableCull();
		GlStateManager.enableRescaleNormal();
		GlStateManager.rotate(180, 0, 1, 0);
		GlStateManager.scale(-1, -1, 1);
		GlStateManager.scale(0.9375f, 0.9375f, 0.9375f);
		GlStateManager.translate(0, -1.5078125f, 0);
		model.setLivingAnimations(e, 0, 0, partialTicks);
		model.setRotationAngles(0, 0, age, headYaw, pitch, scale, e);
		Minecraft.getMinecraft().getTextureManager().bindTexture(e.getLocationSkin());
		model.render(e, 0, 0, age, headYaw, pitch, scale);
		for (LayerRenderer<AbstractClientPlayer> layer : layers(render)) {
			GlStateManager.color(1, 1, 1, 1);
			layer.doRenderLayer(e, 0, 0, partialTicks, age, headYaw, pitch, scale);
		}
		GlStateManager.disableRescaleNormal();
		GlStateManager.popMatrix();
	}

	/** The renderers' layer lists, found once (by reflection) for each of the two player models. */
	private static final java.util.Map<RenderPlayer, List<LayerRenderer<AbstractClientPlayer>>> LAYERS = new java.util.IdentityHashMap<>();

	@SuppressWarnings("unchecked")
	private static List<LayerRenderer<AbstractClientPlayer>> layers(RenderPlayer render) {
		return LAYERS.computeIfAbsent(render, r -> (List<LayerRenderer<AbstractClientPlayer>>) ReflectionHelper.getPrivateValue(
			RendererLivingEntity.class, r, "layerRenderers", "field_177097_h"));
	}

	/** A player with no connection behind it, wearing whatever skin the preview shows. */
	private static final class Model extends EntityOtherPlayerMP {
		ResourceLocation skin;
		boolean slim;

		private Model(World world, GameProfile profile) {
			super(world, profile);
			// every skin layer (hat, jacket, sleeves, trousers) and the cape shown
			getDataWatcher().updateObject(10, (byte) 0x7F);
			chasingPosX = prevChasingPosX = posX;
			chasingPosY = prevChasingPosY = posY;
			chasingPosZ = prevChasingPosZ = posZ;
		}

		static Model create() {
			Minecraft mc = Minecraft.getMinecraft();
			try {
				World world = mc.theWorld;
				if (world == null) {
					if (emptyWorld == null) {
						emptyWorld = new WorldClient(null, new WorldSettings(0L, WorldSettings.GameType.SURVIVAL, false, false, WorldType.FLAT),
							0, EnumDifficulty.PEACEFUL, mc.mcProfiler);
					}
					world = emptyWorld;
				}
				GameProfile own = mc.getSession().getProfile();
				UUID id = own.getId() != null ? own.getId() : UUID.nameUUIDFromBytes(own.getName().getBytes(StandardCharsets.UTF_8));
				return new Model(world, new GameProfile(id, own.getName()));
			} catch (RuntimeException e) {
				RagoClient.LOG.warn("No player preview", e);
				return null;
			}
		}

		@Override
		public ResourceLocation getLocationSkin() {
			return skin != null ? skin : DefaultPlayerSkin.getDefaultSkin(getUniqueID());
		}

		@Override
		public String getSkinType() {
			return slim ? "slim" : "default";
		}

		@Override
		public boolean hasPlayerInfo() {
			return true;
		}

		@Override
		protected NetworkPlayerInfo getPlayerInfo() {
			return null;
		}

		@Override
		public boolean isSpectator() {
			return false;
		}

		@Override
		public boolean isInvisibleToPlayer(net.minecraft.entity.player.EntityPlayer player) {
			return false;
		}
	}
}
