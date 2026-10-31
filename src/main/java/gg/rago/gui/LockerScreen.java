package gg.rago.gui;

import com.mojang.authlib.GameProfile;
import gg.rago.hooks.Hooks;
import gg.rago.modules.NickHider;
import gg.rago.accounts.Skins;
import gg.rago.config.BooleanOption;
import gg.rago.config.ChoiceOption;
import gg.rago.config.Option;
import gg.rago.core.Module;
import gg.rago.core.RagoClient;
import gg.rago.gui.ui.Anim;
import gg.rago.gui.ui.Ui;
import gg.rago.gui.ui.Widgets;
import gg.rago.render.NeonColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * The locker, opened from the home screen: your player in 3D on the left, turned by dragging, and
 * on the right the skins you collected (added by player name, worn with a click) and the
 * cosmetics (cape, hat, wings, shield). A worn skin shows in this client only, unless you also set
 * it on your Minecraft account.
 */
public final class LockerScreen extends GuiScreen {
	private static final float CARD_W = 96;
	private static final float CARD_H = 128;
	private static final float GAP = 10;
	/** A world for the preview player when no world is loaded; made once, it holds nothing. */
	private static WorldClient emptyWorld;

	private final GuiScreen parent;
	private long openedNs;
	private boolean cosmeticsTab;
	private String name = "";
	private float scroll;
	private float yaw = -28;
	private boolean dragging;
	private float lastMouseX;
	private float stageX;
	private float stageY;
	private float stageW;
	private float stageH;
	private Preview preview;
	/** The skin under the mouse, shown on the preview instead of the worn one. */
	private Skins.Skin hovered;
	private boolean hoveredAccount;
	private volatile boolean busy;
	private volatile String message;
	private volatile boolean messageError;
	private Skins.Skin confirmApply;

	public LockerScreen(GuiScreen parent) {
		this.parent = parent;
	}

	private static String t(String key, Object... args) {
		return I18n.format(key, args);
	}

	@Override
	public void initGui() {
		openedNs = System.nanoTime();
		Keyboard.enableRepeatEvents(true);
		if (preview == null) {
			preview = Preview.create();
		}
	}

	@Override
	public void updateScreen() {
		if (preview != null) {
			preview.ticksExisted++;
		}
	}

	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		Ui.begin();
		float w = Ui.width();
		float h = Ui.height();
		Ui.backdrop(0.5f);
		Ui.captureGlass();
		float p = Anim.progress(openedNs, 260);
		Ui.opacity(p);

		float pw = Math.min(800, w - 40);
		float ph = Math.min(480, h - 40);
		float x = (w - pw) / 2;
		float y = (h - ph) / 2 + (1 - p) * 12;
		Ui.glassBorder(x, y, pw, ph, 14, 0xF2121114, Theme.BORDER_STRONG);

		// header
		Ui.round(x + 20, y + 20, 32, 32, 9, Theme.ACCENT_SOFT);
		Ui.icon("shirt", x + 28, y + 28, 16, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, t("rago.locker.title"), x + 64, y + 20, 15, Theme.TEXT);
		Ui.text(Ui.REGULAR, t("rago.locker.subtitle"), x + 64, y + 40, 9.5f, Theme.MUTED);
		Widgets.iconButton(x + pw - 46, y + 20, 26, "x");
		Ui.click(x + pw - 46, y + 20, 26, 26, () -> mc.displayGuiScreen(parent));

		hovered = null;
		hoveredAccount = false;
		float rx = x + 20 + 250 + 20;
		float rw = x + pw - 20 - rx;
		float top = y + 72;
		tabs(rx, top, rw);
		if (cosmeticsTab) {
			cosmetics(rx, top + 40, rw, y + ph - 20 - (top + 40));
		} else {
			skins(rx, top + 40, rw, y + ph - 20 - (top + 40));
		}
		stage(x + 20, top, 250, y + ph - 20 - top, p, partialTicks);

		if (confirmApply != null) {
			confirm(w, h);
		}
		Ui.opacity(1);
		Ui.end();
	}

	// ---- preview --------------------------------------------------------------------------------

	private void stage(float x, float y, float w, float h, float p, float partialTicks) {
		stageX = x;
		stageY = y;
		stageW = w;
		stageH = h;
		Ui.glassBorder(x, y, w, h, 12, 0xFF0F0E11, 0x14FFFFFF);
		Ui.glow(x + w / 2, y + h * 0.55f, w * 0.55f, Ui.alpha(Theme.ACCENT, 0.16f));
		// a floor ellipse under the feet
		float floorY = y + h - 70;
		Ui.round(x + w / 2 - 60, floorY - 6, 120, 12, 6, 0x40000000);

		Skins.Skin shown = shownSkin();
		String label = shown == null ? t("rago.locker.my_skin") : shown.name;
		Ui.textCentered(Ui.SEMIBOLD, label, x + w / 2, y + 14, 12, Theme.TEXT);
		Ui.textCentered(Ui.REGULAR, t("rago.locker.drag"), x + w / 2, y + 31, 8.5f, 0xFF7C7A82);

		if (dragging) {
			if (Mouse.isButtonDown(0)) {
				yaw += (Ui.mouseX() - lastMouseX) * 0.9f;
			} else {
				dragging = false;
			}
		}
		lastMouseX = Ui.mouseX();
		if (preview != null && p > 0.05f) {
			drawPlayer(x + w / 2, floorY, Math.min(w * 0.42f, (floorY - y - 60) / 2.1f), shown, partialTicks);
		}

		// what wearing means, and the account button
		Skins.Skin worn = Skins.worn();
		float by = y + h - 46;
		if (worn != null) {
			Widgets.button(x + 14, by, w - 28, 28, "user-round-check", t("rago.locker.apply"), false, 9.5f);
			if (!busy && confirmApply == null) {
				Ui.click(x + 14, by, w - 28, 28, () -> confirmApply = worn);
			}
		} else {
			for (String line : Ui.wrap(Ui.REGULAR, t("rago.locker.account_note"), 8.5f, w - 32)) {
				Ui.textCentered(Ui.REGULAR, line, x + w / 2, by + 4, 8.5f, 0xFF7C7A82);
				by += 12;
			}
		}
	}

	/** The skin the preview wears: the one under the mouse, else the worn one (null: the account's). */
	private Skins.Skin shownSkin() {
		if (hovered != null) {
			return hovered;
		}
		return hoveredAccount ? null : Skins.worn();
	}

	private void drawPlayer(float cx, float feetY, float size, Skins.Skin skin, float partialTicks) {
		Preview e = preview;
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
			renderModel(e, partialTicks);
		} catch (RuntimeException ex) {
			RagoClient.LOG.warn("The locker preview failed and is turned off", ex);
			preview = null;
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
	private void renderModel(Preview e, float partialTicks) {
		RenderPlayer render = mc.getRenderManager().getSkinMap().get(e.getSkinType());
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
		mc.getTextureManager().bindTexture(e.getLocationSkin());
		model.render(e, 0, 0, age, headYaw, pitch, scale);
		for (LayerRenderer<AbstractClientPlayer> layer : layers(render)) {
			GlStateManager.color(1, 1, 1, 1);
			layer.doRenderLayer(e, 0, 0, partialTicks, age, headYaw, pitch, scale);
		}
		GlStateManager.disableRescaleNormal();
		GlStateManager.popMatrix();
	}

	@SuppressWarnings("unchecked")
	private static List<LayerRenderer<AbstractClientPlayer>> layers(RenderPlayer render) {
		return (List<LayerRenderer<AbstractClientPlayer>>) ReflectionHelper.getPrivateValue(RendererLivingEntity.class, render,
			"layerRenderers", "field_177097_h");
	}

	/** A player with no connection behind it, wearing whatever skin the locker shows. */
	static final class Preview extends EntityOtherPlayerMP {
		ResourceLocation skin;
		boolean slim;

		private Preview(World world, GameProfile profile) {
			super(world, profile);
			// every skin layer (hat, jacket, sleeves, trousers) and the cape shown
			getDataWatcher().updateObject(10, (byte) 0x7F);
			chasingPosX = prevChasingPosX = posX;
			chasingPosY = prevChasingPosY = posY;
			chasingPosZ = prevChasingPosZ = posZ;
		}

		static Preview create() {
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
				UUID id = own.getId() != null ? own.getId() : UUID.nameUUIDFromBytes(own.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
				return new Preview(world, new GameProfile(id, own.getName()));
			} catch (RuntimeException e) {
				RagoClient.LOG.warn("No player preview in the locker", e);
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

	// ---- tabs -----------------------------------------------------------------------------------

	private void tabs(float x, float y, float w) {
		String[] labels = {t("rago.locker.tab.skins"), t("rago.locker.tab.cosmetics")};
		String[] icons = {"user", "sparkles"};
		float tw = (w - 8) / 2;
		Ui.glassBorder(x, y, w, 30, 15, 0xFF141316, 0x14FFFFFF);
		float knob = Anim.to("locker:tab", cosmeticsTab ? 1 : 0, 180);
		Ui.round(x + 3 + knob * (tw + 2), y + 3, tw, 24, 12, Theme.ACCENT);
		for (int i = 0; i < 2; i++) {
			float tx = x + 3 + i * (tw + 2);
			boolean on = cosmeticsTab == (i == 1);
			float lw = Ui.textWidth(Ui.SEMIBOLD, labels[i], 10) + 18;
			float lx = tx + (tw - lw) / 2;
			int c = on ? 0xFF1A0605 : Ui.mix(0xFF9A98A0, Theme.TEXT, Widgets.hoverAmount("locker:tabh" + i, tx, y, tw, 30));
			Ui.icon(icons[i], lx, y + 9, 12, c);
			Ui.text(Ui.SEMIBOLD, labels[i], lx + 18, y + (30 - 10 * 1.21f) / 2, 10, c);
			boolean cos = i == 1;
			Ui.click(tx, y, tw, 30, () -> {
				cosmeticsTab = cos;
				scroll = 0;
			});
		}
	}

	// ---- skins ----------------------------------------------------------------------------------

	private void skins(float x, float y, float w, float h) {
		// name field and add button
		float bw = Ui.textWidth(Ui.SEMIBOLD, t("rago.locker.add"), 10) + 46;
		float fw = w - bw - 8;
		Ui.glassBorder(x, y, fw, 30, 15, 0xFF141316, name.isEmpty() ? 0x1FFFFFFF : 0x55F2392C);
		Ui.icon("search", x + 12, y + 9, 12, 0xFF8A888F);
		boolean caret = (System.currentTimeMillis() / 500) % 2 == 0;
		float ty = y + (30 - 10 * 1.21f) / 2;
		if (name.isEmpty()) {
			Ui.text(Ui.REGULAR, t("rago.locker.name_hint"), x + 32, ty, 10, 0xFF7C7A82);
			if (caret) {
				Ui.rect(x + 31, y + 9, 1, 12, Theme.TEXT);
			}
		} else {
			Ui.text(Ui.MEDIUM, name, x + 32, ty, 10, Theme.TEXT);
			if (caret) {
				Ui.rect(x + 33 + Ui.textWidth(Ui.MEDIUM, name, 10), y + 9, 1, 12, Theme.TEXT);
			}
		}
		Widgets.button(x + fw + 8, y, bw, 30, busy ? "loader-circle" : "plus", t("rago.locker.add"), true, 10);
		Ui.click(x + fw + 8, y, bw, 30, this::add);

		float gy = y + 40;
		String msg = message;
		if (msg != null) {
			Ui.icon(messageError ? "triangle-alert" : busy ? "loader-circle" : "check", x + 2, gy, 11, messageError ? Theme.ACCENT : busy ? Theme.MUTED : Theme.GREEN);
			Ui.text(Ui.REGULAR, msg, x + 18, gy, 9.5f, messageError ? 0xFFFF8A80 : Theme.MUTED);
		}
		gy += 20;
		float gh = y + h - gy;

		List<Skins.Skin> list = Skins.all();
		int cols = Math.max(1, (int) ((w + GAP) / (CARD_W + GAP)));
		float cw = (w - (cols - 1) * GAP) / cols;
		int count = list.size() + 1;
		int rows = (count + cols - 1) / cols;
		float total = rows * (CARD_H + GAP) - GAP;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - gh)));
		Ui.scissor(x - 4, gy - 4, w + 8, gh + 8);
		Skins.Skin worn = Skins.worn();
		for (int i = 0; i < count; i++) {
			float cx = x + (i % cols) * (cw + GAP);
			float cy = gy + (i / cols) * (CARD_H + GAP) - scroll;
			if (cy > gy + gh || cy + CARD_H < gy) {
				continue;
			}
			if (i == 0) {
				accountCard(cx, cy, cw, worn == null);
			} else {
				Skins.Skin s = list.get(i - 1);
				card(s, cx, cy, cw, s == worn);
			}
		}
		Ui.endScissor();
		if (list.isEmpty()) {
			float ey = gy + CARD_H + 24;
			for (String line : Ui.wrap(Ui.REGULAR, t("rago.locker.empty"), 9.5f, w - 20)) {
				Ui.textCentered(Ui.REGULAR, line, x + w / 2, ey, 9.5f, 0xFF7C7A82);
				ey += 14;
			}
		}
	}

	private void accountCard(float x, float y, float w, boolean selected) {
		boolean hover = Ui.hover(x, y, w, CARD_H);
		if (hover) {
			hoveredAccount = true;
		}
		Skins.Skin acc = Skins.account();
		ResourceLocation tex;
		boolean slim;
		if (acc != null && acc.texture() != null) {
			tex = acc.texture();
			slim = acc.slim;
		} else {
			UUID id = preview != null ? preview.getUniqueID() : mc.getSession().getProfile().getId();
			tex = id == null ? DefaultPlayerSkin.getDefaultSkinLegacy() : DefaultPlayerSkin.getDefaultSkin(id);
			slim = id != null && "slim".equals(DefaultPlayerSkin.getSkinType(id));
		}
		cardFrame("locker:acc", x, y, w, selected, tex, slim, t("rago.locker.my_skin"), mc.getSession().getUsername());
		if (!busy && confirmApply == null) {
			Ui.click(x, y, w, CARD_H, () -> Skins.wear(null));
		}
	}

	private void card(Skins.Skin s, float x, float y, float w, boolean selected) {
		boolean hover = Ui.hover(x, y, w, CARD_H);
		if (hover) {
			hovered = s;
		}
		ResourceLocation tex = s.texture();
		cardFrame("locker:" + s.name, x, y, w, selected, tex, s.slim, s.name, s.slim ? t("rago.locker.slim") : t("rago.locker.classic"));
		NickHider nick = Hooks.nickHider;
		boolean nickSkin = nick != null && nick.isEnabled() && s.name.equalsIgnoreCase(nick.lockerSkinName());
		if (nickSkin && !hover) {
			Ui.icon("venetian-mask", x + w - 20, y + 9, 11, Theme.ACCENT);
		}
		if (busy || confirmApply != null) {
			return;
		}
		Ui.click(x, y, w, CARD_H, () -> {
			Skins.wear(s);
			RagoClient.modules().get("skin_changer").ifPresent(m -> m.setEnabled(true));
		});
		if (hover) {
			float dx = x + w - 26;
			float dh = Widgets.hoverAmount("locker:del" + s.name, dx, y + 6, 20, 20);
			Ui.round(dx, y + 6, 20, 20, 6, Ui.mix(0xCC141316, 0xFF3A1513, dh));
			Ui.icon("trash-2", dx + 4.5f, y + 10.5f, 11, Ui.mix(0xFFB9B7BE, Theme.ACCENT, dh));
			Ui.click(dx, y + 6, 20, 20, () -> {
				Skins.remove(s);
				message = t("rago.locker.removed", s.name);
				messageError = false;
			});
			if (nick != null) {
				// the skin the Nick Hider shows on your player instead of yours
				float nx = dx - 24;
				float nh = Widgets.hoverAmount("locker:nick" + s.name, nx, y + 6, 20, 20);
				Ui.round(nx, y + 6, 20, 20, 6, Ui.mix(0xCC141316, 0xFF2A1A18, nh));
				Ui.icon("venetian-mask", nx + 4.5f, y + 10.5f, 11, nickSkin ? Theme.ACCENT : Ui.mix(0xFFB9B7BE, Theme.ACCENT, nh));
				Ui.click(nx, y + 6, 20, 20, () -> {
					nick.useLockerSkin(s.name);
					nick.setEnabled(true);
					RagoClient.saveConfig();
					message = t("rago.locker.nick_set", s.name);
					messageError = false;
				});
			}
		}
	}

	private void cardFrame(String key, float x, float y, float w, boolean selected, ResourceLocation tex, boolean slim, String title, String sub) {
		float hv = Widgets.hoverAmount(key, x, y, w, CARD_H);
		Ui.glassBorder(x, y, w, CARD_H, 10, Ui.mix(0xFF151417, 0xFF1D1C20, hv),
			selected ? Ui.alpha(Theme.ACCENT, 0.85f) : Ui.mix(0x14FFFFFF, 0x30FFFFFF, hv));
		if (tex != null) {
			body(tex, slim, x + w / 2, y + 12, 2.5f);
		} else {
			Ui.icon("loader-circle", x + w / 2 - 8, y + 44, 16, Theme.MUTED);
		}
		Ui.textCentered(Ui.SEMIBOLD, Ui.wrap(Ui.SEMIBOLD, title, 9.5f, w - 10).get(0), x + w / 2, y + CARD_H - 30, 9.5f, Theme.TEXT);
		Ui.textCentered(Ui.REGULAR, sub, x + w / 2, y + CARD_H - 16, 8, 0xFF8A888F);
		if (selected) {
			Ui.circle(x + 14, y + 14, 7, Theme.ACCENT);
			Ui.icon("check", x + 9.5f, y + 9.5f, 9, 0xFF1A0605);
		}
	}

	/** The skin seen from the front, flat: head, body, arms and legs with their outer layer. */
	static void body(ResourceLocation tex, boolean slim, float cx, float y, float px) {
		int aw = slim ? 3 : 4;
		float left = cx - 8 * px;
		// x, y, w, h in skin pixels on the figure; then the base and outer layer uv corners
		part(tex, left + (4 - aw) * px, y + 8 * px, aw, 12, px, 44, 20, 44, 36);
		part(tex, left + 12 * px, y + 8 * px, aw, 12, px, 36, 52, 52, 52);
		part(tex, left + 4 * px, y + 20 * px, 4, 12, px, 4, 20, 4, 36);
		part(tex, left + 8 * px, y + 20 * px, 4, 12, px, 20, 52, 4, 52);
		part(tex, left + 4 * px, y + 8 * px, 8, 12, px, 20, 20, 20, 36);
		Ui.texture(tex, left + 4 * px, y, 8 * px, 8 * px, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF);
		Ui.texture(tex, left + 3.5f * px, y - 0.5f * px, 9 * px, 9 * px, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF);
	}

	private static void part(ResourceLocation tex, float x, float y, int w, int h, float px, int u, int v, int ou, int ov) {
		Ui.texture(tex, x, y, w * px, h * px, u / 64f, v / 64f, (u + w) / 64f, (v + h) / 64f, 0xFFFFFFFF);
		Ui.texture(tex, x, y, w * px, h * px, ou / 64f, ov / 64f, (ou + w) / 64f, (ov + h) / 64f, 0xFFFFFFFF);
	}

	private void add() {
		String n = name.trim();
		if (busy || n.isEmpty()) {
			return;
		}
		busy = true;
		messageError = false;
		message = t("rago.locker.adding", n);
		Skins.add(n, s -> {
			Skins.wear(s);
			RagoClient.modules().get("skin_changer").ifPresent(m -> m.setEnabled(true));
			name = "";
			message = t("rago.locker.added", s.name);
			messageError = false;
			busy = false;
		}, f -> {
			message = t("rago.locker.error." + f.name().toLowerCase(java.util.Locale.ROOT), n);
			messageError = true;
			busy = false;
		});
	}

	// ---- cosmetics ------------------------------------------------------------------------------

	private void cosmetics(float x, float y, float w, float h) {
		Module cape = RagoClient.modules().get("rago_cape").orElse(null);
		Module cos = RagoClient.modules().get("cosmetics").orElse(null);
		Ui.scissor(x - 4, y - 4, w + 8, h + 8);
		float cy = y - scroll;
		if (cape != null) {
			ChoiceOption design = choice(cape, "cape_design");
			cy = section(x, cy, w, "flag", t("rago.locker.cape"), design, cape, true);
			cy = neon(x, cy, w, choice(cape, "cape_neon"), cape);
		}
		if (cos != null) {
			cy = section(x, cy, w, "crown", t("rago.locker.hat"), choice(cos, "cosmetic_hat"), cos, false);
			cy = neon(x, cy, w, choice(cos, "cosmetic_hat_neon"), cos);
			cy = section(x, cy, w, "wind", t("rago.locker.wings"), choice(cos, "cosmetic_wings"), cos, false);
			cy = neon(x, cy, w, choice(cos, "cosmetic_wings_neon"), cos);
			cy = section(x, cy, w, "shield", t("rago.locker.shield"), choice(cos, "cosmetic_shield"), cos, false);
			cy = neon(x, cy, w, choice(cos, "cosmetic_shield_neon"), cos);
		}
		// the neon glow can be turned off here; colours, sizes and the rest stay in the mod's own settings
		BooleanOption glow = cos == null ? null : bool(cos, "cosmetic_neon_glow");
		if (glow != null && glow.visible() && cos.isEnabled()) {
			Widgets.toggle(x, cy, glow.get());
			Ui.click(x, cy, Widgets.SWITCH_W, Widgets.SWITCH_H, () -> glow.set(!glow.get()));
			Ui.text(Ui.MEDIUM, t("rago.option.cosmetic_neon_glow"), x + Widgets.SWITCH_W + 10, cy + 5, 10, Theme.TEXT);
			cy += Widgets.SWITCH_H + 14;
		}
		String more = t("rago.locker.more");
		float mw = Ui.textWidth(Ui.SEMIBOLD, more, 9.5f) + 44;
		Widgets.button(x, cy + 4, mw, 26, "sliders-horizontal", more, false, 9.5f);
		Ui.click(x, cy + 4, mw, 26, () -> mc.displayGuiScreen(new RagoMenuScreen(this)));
		cy += 40;
		Ui.endScissor();
		float total = cy + scroll - y;
		scroll = Math.max(0, Math.min(scroll, Math.max(0, total - h)));
	}

	private static BooleanOption bool(Module m, String key) {
		for (Option<?> o : m.options()) {
			if (o instanceof BooleanOption && o.key().equals(key)) {
				return (BooleanOption) o;
			}
		}
		return null;
	}

	private static ChoiceOption choice(Module m, String key) {
		for (Option<?> o : m.options()) {
			if (o instanceof ChoiceOption && o.key().equals(key)) {
				return (ChoiceOption) o;
			}
		}
		return null;
	}

	/**
	 * A row of chips, one per choice. For the cape, "none" turns the mod off; for the others, any
	 * choice but "none" turns the mod on.
	 */
	private float section(float x, float y, float w, String icon, String title, ChoiceOption option, Module module, boolean noneDisables) {
		if (option == null) {
			return y;
		}
		Ui.icon(icon, x, y + 1, 12, Theme.ACCENT);
		Ui.text(Ui.SEMIBOLD, title, x + 18, y, 11, Theme.TEXT);
		y += 22;
		float cx = x;
		float chipH = 26;
		List<String> ids = noneDisables ? withNone(option.choices()) : option.choices();
		for (String id : ids) {
			boolean selected = noneDisables
				? "none".equals(id) ? !module.isEnabled() : module.isEnabled() && option.is(id)
				: option.is(id) && (module.isEnabled() || "none".equals(id));
			String label = label(option.key(), id);
			float cw = Ui.textWidth(Ui.MEDIUM, label, 9.5f) + 26;
			if (cx + cw > x + w) {
				cx = x;
				y += chipH + 8;
			}
			float hv = Widgets.hoverAmount("locker:chip" + option.key() + id, cx, y, cw, chipH);
			if (selected) {
				Ui.round(cx, y, cw, chipH, chipH / 2, Theme.ACCENT);
			} else {
				Ui.glassBorder(cx, y, cw, chipH, chipH / 2, Ui.mix(0xFF151417, 0xFF222126, hv), 0x1FFFFFFF);
			}
			Ui.textCentered(Ui.MEDIUM, label, cx + cw / 2, y + (chipH - 9.5f * 1.21f) / 2, 9.5f, selected ? 0xFF1A0605 : Theme.TEXT);
			Ui.click(cx, y, cw, chipH, () -> {
				if (noneDisables && "none".equals(id)) {
					module.setEnabled(false);
					return;
				}
				option.select(id);
				if (!"none".equals(id)) {
					module.setEnabled(true);
				}
			});
			cx += cw + 8;
		}
		return y + chipH + 22;
	}

	/**
	 * The neon colours of the piece just above, as a row of swatches, when that piece is worn and
	 * comes in them.
	 */
	private float neon(float x, float y, float w, ChoiceOption option, Module module) {
		if (option == null || !option.visible() || !module.isEnabled()) {
			return y;
		}
		y -= 12;
		String title = t("rago.locker.neon") + " · " + label(option.key(), option.get());
		Ui.text(Ui.MEDIUM, title, x, y, 9, Theme.MUTED);
		y += 16;
		float d = 18;
		float sx = x;
		for (String id : option.ids()) {
			if (sx + d > x + w) {
				sx = x;
				y += d + 8;
			}
			float hv = Widgets.hoverAmount("locker:neon" + option.key() + id, sx, y, d, d);
			if (option.is(id)) {
				Ui.annulus(sx + d / 2, y + d / 2, d / 2 + 2.5f, 1.5f, Theme.TEXT);
			} else if (hv > 0.01f) {
				Ui.annulus(sx + d / 2, y + d / 2, d / 2 + 2.5f, 1.5f, Ui.alpha(Theme.TEXT, 0.45f * hv));
			}
			Ui.circle(sx + d / 2, y + d / 2, d / 2, NeonColors.swatch(id));
			Ui.click(sx, y, d, d, () -> option.select(id));
			sx += d + 9;
		}
		return y + d + 22;
	}

	private static List<String> withNone(List<String> ids) {
		List<String> out = new java.util.ArrayList<>(ids.size() + 1);
		out.add("none");
		out.addAll(ids);
		return out;
	}

	private static String label(String key, String id) {
		String k = "rago.option." + key + "." + id;
		String v = I18n.format(k);
		return v.equals(k) ? I18n.format("rago.choice." + id) : v;
	}

	// ---- account skin ---------------------------------------------------------------------------

	private void confirm(float w, float h) {
		Ui.rect(0, 0, w, h, 0x99000000);
		Ui.click(0, 0, w, h, () -> { });
		float dw = 360;
		float dh = 132;
		float x = (w - dw) / 2;
		float y = (h - dh) / 2;
		Ui.glassBorder(x, y, dw, dh, 14, 0xFA121114, Theme.BORDER_STRONG);
		Skins.Skin s = confirmApply;
		ResourceLocation tex = s.texture();
		if (tex != null) {
			Ui.round(x + 19, y + 19, 30, 30, 6, 0xFF0B0A0D);
			Ui.texture(tex, x + 20, y + 20, 28, 28, 8 / 64f, 8 / 64f, 16 / 64f, 16 / 64f, 0xFFFFFFFF);
			Ui.texture(tex, x + 18.25f, y + 18.25f, 31.5f, 31.5f, 40 / 64f, 8 / 64f, 48 / 64f, 16 / 64f, 0xFFFFFFFF);
		}
		Ui.text(Ui.SEMIBOLD, t("rago.locker.apply_title", s.name), x + 62, y + 20, 12.5f, Theme.TEXT);
		float ty = y + 40;
		for (String line : Ui.wrap(Ui.REGULAR, t("rago.locker.apply_body"), 9.5f, dw - 82)) {
			Ui.text(Ui.REGULAR, line, x + 62, ty, 9.5f, Theme.MUTED);
			ty += 13;
		}
		float bw = 110;
		Widgets.button(x + dw - 20 - bw * 2 - 10, y + dh - 44, bw, 26, "x", t("gui.cancel"), false, 10);
		Ui.click(x + dw - 20 - bw * 2 - 10, y + dh - 44, bw, 26, () -> confirmApply = null);
		Widgets.button(x + dw - 20 - bw, y + dh - 44, bw, 26, "check", t("rago.locker.apply_go"), true, 10);
		Ui.click(x + dw - 20 - bw, y + dh - 44, bw, 26, () -> {
			confirmApply = null;
			busy = true;
			messageError = false;
			message = t("rago.locker.applying");
			cosmeticsTab = false;
			Thread th = new Thread(() -> {
				Skins.UploadResult r = Skins.setAccountSkin(s);
				messageError = r != Skins.UploadResult.OK;
				message = r == Skins.UploadResult.OK ? t("rago.locker.applied", s.name)
					: t(r == Skins.UploadResult.SIGNED_OUT ? "rago.locker.signed_out" : "rago.locker.apply_failed");
				busy = false;
			}, "Rago account skin");
			th.setDaemon(true);
			th.start();
		});
	}

	// ---- input ----------------------------------------------------------------------------------

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) throws IOException {
		if (button != 0) {
			return;
		}
		Ui.updateMouse();
		if (confirmApply == null && Ui.hover(stageX, stageY, stageW, stageH - 54)) {
			dragging = true;
			lastMouseX = Ui.mouseX();
		}
		if (Ui.dispatchClick()) {
			mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), 1.0F));
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int state) {
		dragging = false;
	}

	@Override
	public void handleMouseInput() throws IOException {
		super.handleMouseInput();
		int d = Mouse.getEventDWheel();
		if (d != 0) {
			scroll -= Math.signum(d) * 40;
		}
	}

	@Override
	protected void keyTyped(char typedChar, int keyCode) {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			if (confirmApply != null) {
				confirmApply = null;
			} else {
				mc.displayGuiScreen(parent);
			}
			return;
		}
		if (cosmeticsTab || confirmApply != null) {
			return;
		}
		if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
			add();
		} else if (keyCode == Keyboard.KEY_BACK) {
			if (!name.isEmpty()) {
				name = isCtrlKeyDown() ? "" : name.substring(0, name.length() - 1);
			}
		} else if (keyCode == Keyboard.KEY_V && isCtrlKeyDown()) {
			String paste = getClipboardString().trim().replaceAll("[^A-Za-z0-9_]", "");
			name = (name + paste).substring(0, Math.min(16, name.length() + paste.length()));
		} else if (ChatAllowedCharacters.isAllowedCharacter(typedChar) && name.length() < 16
			&& (Character.isLetterOrDigit(typedChar) || typedChar == '_') && typedChar < 128) {
			name += typedChar;
		}
	}

	@Override
	public void onGuiClosed() {
		Keyboard.enableRepeatEvents(false);
		RagoClient.saveConfig();
	}

	@Override
	public boolean doesGuiPauseGame() {
		return false;
	}
}
