package gg.rago.loader.compat;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundManager;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.tileentity.TileEntityEnchantmentTable;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.GuiIngameForge;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderBlockOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.EventBus;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * What the game calls where Forge would fire its events: the loader's patches ({@code CompatPatches})
 * put a call to one of these methods in the game's code. Each one does what Forge does at that
 * spot, with the same events, so Rago runs unchanged. All of them run on the game's thread.
 */
@SuppressWarnings({"unused", "rawtypes", "unchecked"})
public final class Hooks {
	private static final EventBus BUS = MinecraftForge.EVENT_BUS;
	private static final ClientTickEvent TICK_START = new ClientTickEvent(TickEvent.Phase.START);
	private static final ClientTickEvent TICK_END = new ClientTickEvent(TickEvent.Phase.END);

	/** The frame's partial tick, for the events that carry it where the game does not pass it. */
	private static float partialTicks;
	/** The overlay's base event of this frame (its partial ticks and screen size). */
	private static RenderGameOverlayEvent overlay;
	/** The screen the GuiOpenEvent chose, between {@link #openGui} and {@link #openedGui}. */
	private static GuiScreen opened;

	private static final Map<WorldProvider, IRenderHandler> SKIES = new WeakHashMap<>();
	private static final VarHandle FOG_RED;
	private static final VarHandle FOG_GREEN;
	private static final VarHandle FOG_BLUE;

	static {
		try {
			MethodHandles.Lookup l = MethodHandles.privateLookupIn(EntityRenderer.class, MethodHandles.lookup());
			FOG_RED = l.findVarHandle(EntityRenderer.class, "field_175080_Q", float.class);
			FOG_GREEN = l.findVarHandle(EntityRenderer.class, "field_175082_R", float.class);
			FOG_BLUE = l.findVarHandle(EntityRenderer.class, "field_175081_S", float.class);
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}

	private Hooks() {
	}

	/** The tick events are reused: they carry nothing but their phase and cannot be canceled. */
	private static final class ClientTickEvent extends TickEvent.ClientTickEvent {
		ClientTickEvent(Phase phase) {
			super(phase);
		}
	}

	private static Minecraft mc() {
		return Minecraft.func_71410_x();
	}

	// --- mods' life ---

	public static void preInit(Minecraft mc) {
		Mods.preInit(mc);
	}

	public static void init(Minecraft mc) {
		Mods.init(mc);
	}

	// --- ticks and input ---

	public static void clientTick(boolean start) {
		BUS.post(start ? TICK_START : TICK_END);
	}

	public static void renderTick(float partial, boolean start) {
		partialTicks = partial;
		BUS.post(new TickEvent.RenderTickEvent(start ? TickEvent.Phase.START : TickEvent.Phase.END, partial));
	}

	/** After each key event the game handles: FML's KeyInputEvent. */
	public static void dispatchKeypresses(Minecraft mc) {
		mc.func_152348_aa();
		BUS.post(new InputEvent.KeyInputEvent());
	}

	/** {@code Mouse.next()} for the game's mouse loop, skipping the events a MouseEvent canceled. */
	public static boolean mouseNext() {
		while (Mouse.next()) {
			if (!BUS.post(new MouseEvent())) {
				return true;
			}
		}
		return false;
	}

	/** {@code Mouse.next()} for a screen's mouse loop, skipping the events MouseInputEvent.Pre canceled. */
	public static boolean guiMouseNext(GuiScreen gui) {
		while (Mouse.next()) {
			if (!BUS.post(new GuiScreenEvent.MouseInputEvent.Pre(gui))) {
				return true;
			}
		}
		return false;
	}

	// --- screens ---

	public static boolean openGui(GuiScreen gui) {
		GuiOpenEvent e = new GuiOpenEvent(gui);
		if (BUS.post(e)) {
			return true;
		}
		opened = e.gui;
		return false;
	}

	public static GuiScreen openedGui() {
		GuiScreen gui = opened;
		opened = null;
		return gui;
	}

	public static void drawScreen(GuiScreen gui, int mouseX, int mouseY, float partial) {
		if (!BUS.post(new GuiScreenEvent.DrawScreenEvent.Pre(gui, mouseX, mouseY, partial))) {
			gui.func_73863_a(mouseX, mouseY, partial);
		}
		BUS.post(new GuiScreenEvent.DrawScreenEvent.Post(gui, mouseX, mouseY, partial));
	}

	public static void initGuiPost(GuiScreen gui, List buttons) {
		BUS.post(new GuiScreenEvent.InitGuiEvent.Post(gui, buttons));
	}

	public static void actionPerformedPost(GuiScreen gui, GuiButton button, List buttons) {
		BUS.post(new GuiScreenEvent.ActionPerformedEvent.Post(gui, button, buttons));
	}

	/** A chat line typed by the player: a client command runs here and goes no further. */
	public static boolean clientCommand(String message, boolean addToChat) {
		Minecraft mc = mc();
		if (mc.field_71439_g == null || ClientCommandHandler.instance.func_71556_a(mc.field_71439_g, message) == 0) {
			return false;
		}
		if (addToChat) {
			mc.field_71456_v.func_146158_b().func_146239_a(message);
		}
		return true;
	}

	// --- world rendering ---

	public static void renderWorldLast(float partial) {
		BUS.post(new RenderWorldLastEvent(mc().field_71438_f, partial));
	}

	private static Block cameraBlock(Minecraft mc, Entity entity, float partial) {
		return mc.field_71441_e == null || entity == null ? null : ActiveRenderInfo.func_180786_a(mc.field_71441_e, entity, partial);
	}

	public static float fov(float fov, float partial) {
		Minecraft mc = mc();
		Entity entity = mc.func_175606_aa();
		EntityViewRenderEvent.FOVModifier e = new EntityViewRenderEvent.FOVModifier(mc.field_71460_t, entity, cameraBlock(mc, entity, partial), partial, fov);
		BUS.post(e);
		return e.getFOV();
	}

	/** In place of the sky color's {@code GlStateManager.clearColor}: FogColors may change it. */
	public static void fogColors(float red, float green, float blue, float alpha) {
		Minecraft mc = mc();
		EntityRenderer renderer = mc.field_71460_t;
		Entity entity = mc.func_175606_aa();
		EntityViewRenderEvent.FogColors e = new EntityViewRenderEvent.FogColors(renderer, entity, cameraBlock(mc, entity, partialTicks), partialTicks, red, green, blue);
		BUS.post(e);
		if (e.red != red || e.green != green || e.blue != blue) {
			FOG_RED.set(renderer, e.red);
			FOG_GREEN.set(renderer, e.green);
			FOG_BLUE.set(renderer, e.blue);
		}
		GlStateManager.func_179082_a(e.red, e.green, e.blue, alpha);
	}

	/** True when FogDensity was canceled: its density is set and the game's own fog is skipped. */
	public static boolean fogDensity(Block block, float partial) {
		Minecraft mc = mc();
		EntityViewRenderEvent.FogDensity e = new EntityViewRenderEvent.FogDensity(mc.field_71460_t, mc.func_175606_aa(), block, partial, 0.1F);
		if (BUS.post(e)) {
			GlStateManager.func_179095_a(e.density);
			return true;
		}
		return false;
	}

	public static boolean renderSky(float partial) {
		Minecraft mc = mc();
		if (mc.field_71441_e == null) {
			return false;
		}
		IRenderHandler sky = getSky(mc.field_71441_e.field_73011_w);
		if (sky == null) {
			return false;
		}
		sky.render(partial, mc.field_71441_e, mc);
		return true;
	}

	public static boolean blockHighlight(RenderGlobal context, EntityPlayer player, MovingObjectPosition target, int subID, float partial) {
		return BUS.post(new DrawBlockHighlightEvent(context, player, target, subID, player.func_70694_bm(), partial));
	}

	public static boolean blockOverlay(String type, float partial) {
		EntityPlayer player = mc().field_71439_g;
		if (player == null) {
			return false;
		}
		return BUS.post(new RenderBlockOverlayEvent(player, partial, RenderBlockOverlayEvent.OverlayType.valueOf(type), null, new BlockPos(player)));
	}

	// --- entities ---

	public static boolean livingPre(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
		return BUS.post(new RenderLivingEvent.Pre(entity, renderer, x, y, z));
	}

	public static void livingPost(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
		BUS.post(new RenderLivingEvent.Post(entity, renderer, x, y, z));
	}

	public static boolean specialsPre(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
		return BUS.post(new RenderLivingEvent.Specials.Pre(entity, renderer, x, y, z));
	}

	public static void specialsPost(RendererLivingEntity renderer, EntityLivingBase entity, double x, double y, double z) {
		BUS.post(new RenderLivingEvent.Specials.Post(entity, renderer, x, y, z));
	}

	public static boolean attack(EntityPlayer player, Entity target) {
		return BUS.post(new AttackEntityEvent(player, target));
	}

	public static boolean rightClickBlock(EntityPlayer player, World world, BlockPos pos, EnumFacing face) {
		return BUS.post(new PlayerInteractEvent(player, PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, pos, face, world));
	}

	// --- the HUD ---

	private static RenderGameOverlayEvent overlay(float partial) {
		if (overlay == null || overlay.partialTicks != partial) {
			overlay = new RenderGameOverlayEvent(partial, new ScaledResolution(mc()));
		}
		return overlay;
	}

	public static boolean overlayPre(float partial) {
		overlay = null;
		return BUS.post(new RenderGameOverlayEvent.Pre(overlay(partial), ElementType.ALL));
	}

	public static void overlayPost(float partial) {
		BUS.post(new RenderGameOverlayEvent.Post(overlay(partial), ElementType.ALL));
	}

	/** Whether the crosshair is drawn: the game's answer, unless CROSSHAIRS is turned off or canceled. */
	public static boolean crosshairs(boolean show, float partial) {
		if (!show) {
			return false;
		}
		return GuiIngameForge.renderCrosshairs && !BUS.post(new RenderGameOverlayEvent.Pre(overlay(partial), ElementType.CROSSHAIRS));
	}

	/** True to skip one part of the HUD ("BOSSHEALTH", "HELMET"). */
	public static boolean element(String name) {
		ElementType type = ElementType.valueOf(name);
		boolean on = switch (type) {
			case BOSSHEALTH -> GuiIngameForge.renderBossHealth;
			case HELMET -> GuiIngameForge.renderHelmet;
			default -> true;
		};
		return !on || BUS.post(new RenderGameOverlayEvent.Pre(overlay(partialTicks), type));
	}

	public static boolean hideObjective() {
		return !GuiIngameForge.renderObjective;
	}

	/** The F3 screen's left column, after mods have added their lines to it. */
	public static List debugLeft(List left) {
		RenderGameOverlayEvent.Text e = new RenderGameOverlayEvent.Text(overlay(partialTicks), new ArrayList<String>(left), new ArrayList<String>());
		BUS.post(e);
		return e.left;
	}

	// --- chat and sounds ---

	/** The chat packet to show, or null to drop it. */
	public static S02PacketChat chat(S02PacketChat packet) {
		ClientChatReceivedEvent e = new ClientChatReceivedEvent(packet.func_179841_c(), packet.func_148915_c());
		if (BUS.post(e) || e.message == null) {
			return null;
		}
		return e.message == packet.func_148915_c() ? packet : new S02PacketChat(e.message, packet.func_179841_c());
	}

	/** The sound to play, or null to play nothing. */
	public static ISound playSound(SoundManager manager, ISound sound) {
		PlaySoundEvent e = new PlaySoundEvent(manager, sound);
		BUS.post(e);
		return e.result;
	}

	// --- members Forge adds to game classes ---

	public static IRenderHandler getSky(WorldProvider provider) {
		synchronized (SKIES) {
			return SKIES.get(provider);
		}
	}

	public static void setSky(WorldProvider provider, IRenderHandler sky) {
		synchronized (SKIES) {
			if (sky == null) {
				SKIES.remove(provider);
			} else {
				SKIES.put(provider, sky);
			}
		}
	}

	private static volatile AxisAlignedBB infinite;

	/** Forge's TileEntity.INFINITE_EXTENT_AABB: always the same object, since mods compare it by identity. */
	public static AxisAlignedBB infiniteBox() {
		AxisAlignedBB box = infinite;
		if (box == null) {
			box = new AxisAlignedBB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
				Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
			infinite = box;
		}
		return box;
	}

	/**
	 * Where a block entity may draw, for culling: a chest's lid and double chest, an enchanting
	 * table's book, and everywhere for the others (banners, beacons, skulls, signs), so nothing that
	 * draws outside its block is ever culled by mistake.
	 */
	public static AxisAlignedBB renderBox(TileEntity tile) {
		BlockPos pos = tile.func_174877_v();
		if (tile instanceof TileEntityChest) {
			return new AxisAlignedBB(pos.func_177982_a(-1, 0, -1), pos.func_177982_a(2, 2, 2));
		}
		if (tile instanceof TileEntityEnchantmentTable) {
			return new AxisAlignedBB(pos, pos.func_177982_a(1, 2, 1));
		}
		return infiniteBox();
	}

	public static boolean reequip(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
		return !ItemStack.func_77989_b(oldStack, newStack);
	}

	public static String registryName(Item item) {
		Object name = Item.field_150901_e.func_177774_c(item);
		return name == null ? null : name.toString();
	}

	/** Kept for the type: Rago's code names it. */
	static ResourceLocation unused() {
		return null;
	}
}
