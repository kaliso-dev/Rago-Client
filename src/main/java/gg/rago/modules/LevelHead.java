package gg.rago.modules;

import gg.rago.config.BooleanOption;
import gg.rago.config.IntOption;
import gg.rago.core.Category;
import gg.rago.core.FeatureClass;
import gg.rago.core.Module;
import gg.rago.hypixel.Hypixel;
import gg.rago.hypixel.HypixelApi;
import gg.rago.render.NameTags;
import gg.rago.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;
import java.util.WeakHashMap;


/**
 * On Hypixel, shows each player's network level or Bedwars star above their name, read from the
 * Hypixel API with your own key ({@code /rago hypixelkey <key>}).
 */
public final class LevelHead extends Module {
	public static final int NETWORK_LEVEL = 0;
	public static final int BEDWARS_STAR = 1;

	private final IntOption display = addOption(new IntOption("level_display", NETWORK_LEVEL, 0, 1));
	private final BooleanOption background = addOption(new BooleanOption("background", true));
	private final HypixelApi api;
	private final Nametags nametags;
	private boolean warned;

	public LevelHead(HypixelApi api, Nametags nametags) {
		super("level_head", FeatureClass.INFORMATIONAL, false);
		this.api = api;
		this.nametags = nametags;
	}

	@Override
	public Category category() {
		return Category.HYPIXEL;
	}

	@Override
	protected void onEnable() {
		warned = false;
	}

	@Override
	public void tick() {
		Minecraft mc = Minecraft.getMinecraft();
		if (warned || mc.thePlayer == null || !Hypixel.connected()) {
			return;
		}
		HypixelApi.Status s = api.status();
		if (s == HypixelApi.Status.NO_KEY || s == HypixelApi.Status.BAD_KEY) {
			warned = true;
			ChatComponentTranslation msg = new ChatComponentTranslation(s == HypixelApi.Status.NO_KEY ? "rago.levelhead.no_key" : "rago.levelhead.bad_key");
			msg.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.GOLD));
			mc.thePlayer.addChatMessage(msg);
		}
	}

	@SubscribeEvent
	public void onNameDrawn(RenderLivingEvent.Specials.Post<?> event) {
		if (!isEnabled() || !(event.entity instanceof EntityPlayer) || !Hypixel.connected()) {
			return;
		}
		EntityPlayer p = (EntityPlayer) event.entity;
		Minecraft mc = Minecraft.getMinecraft();
		boolean self = p == mc.thePlayer;
		if (self ? !nametags.ownShown() : !NameTags.visible(p)) {
			return;
		}
		if (!Hypixel.realPlayer(p.getUniqueID())) {
			return;
		}
		// outside a Bed Wars game, the players around you only get the key's spare requests
		HypixelApi.Stats stats = api.get(p.getUniqueID(), !self && !gg.rago.bedwars.BedwarsGame.playing());
		String text = stats == null ? null : cachedText(stats);
		if (text != null) {
			double line = nametags.lineHeight();
			// above the name tag, which Nametags may draw a little lower than vanilla
			double top = NameTags.nameTop(p, event.y, line) + line - nametags.lowerBy();
			if (nametags.isEnabled()) {
				nametags.label(text, event.x, top, event.z, 0, nametags.throughWalls(), 0xFF);
			} else {
				WorldDraw.label(text, event.x, top, event.z, background.get());
			}
		}
	}

	/** The line for each set of stats, made once rather than every frame; new stats are new objects. */
	private final Map<HypixelApi.Stats, String> texts = new WeakHashMap<>();
	private int textsDisplay = -1;

	private String cachedText(HypixelApi.Stats s) {
		if (display.get() != textsDisplay) {
			textsDisplay = display.get();
			texts.clear();
		}
		String t = texts.get(s);
		if (t == null && !texts.containsKey(s)) {
			t = text(s);
			texts.put(s, t);
		}
		return t;
	}

	private String text(HypixelApi.Stats s) {
		if (display.get() == BEDWARS_STAR) {
			return s.star < 0 ? null : Hypixel.bedwarsStar(s.star);
		}
		return s.level < 1 ? null : EnumChatFormatting.AQUA + "Level: " + EnumChatFormatting.YELLOW + (int) s.level;
	}
}
