package gg.rago.loader.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.objectweb.asm.Opcodes.*;

/**
 * The places where Forge calls mods, rebuilt on the vanilla game: each one calls a static method of
 * {@code gg.rago.loader.compat.Hooks}, which posts the same event Forge would. Plus the few fields
 * and methods Forge adds to game classes that Rago uses.
 *
 * <p>Methods are found by SRG name and descriptor. A spot that is not found is reported and left
 * alone; the class is never left half changed, since nothing is written unless every change of
 * that class applied.
 */
public final class CompatPatches {
	public static final String HOOKS = "gg/rago/loader/compat/Hooks";

	private static final String MC = "net/minecraft/client/Minecraft";
	private static final String ER = "net/minecraft/client/renderer/EntityRenderer";
	private static final String GUI = "net/minecraft/client/gui/GuiScreen";
	private static final String INGAME = "net/minecraft/client/gui/GuiIngame";
	private static final String RLE = "net/minecraft/client/renderer/entity/RendererLivingEntity";
	private static final String ELB = "Lnet/minecraft/entity/EntityLivingBase;";
	private static final String GLSM = "net/minecraft/client/renderer/GlStateManager";

	/** One change to one class; returns false when what it looks for is not there. */
	private interface Change {
		boolean apply(ClassNode c);
	}

	private record Named(String what, Change change) { }

	private final Map<String, List<Named>> changes = new HashMap<>();
	private final ClassLoader classes;
	private final Consumer<String> log;
	private final List<String> missed = new ArrayList<>();

	public CompatPatches(ClassLoader classes, Consumer<String> log) {
		this.classes = classes;
		this.log = log;
		events();
		forgeMembers();
	}

	public List<String> missed() {
		return missed;
	}

	private void add(String cls, String what, Change change) {
		changes.computeIfAbsent(cls.replace('/', '.'), k -> new ArrayList<>()).add(new Named(what, change));
	}

	/** {@code what} on one method, found by name and descriptor. */
	private void method(String cls, String name, String desc, String what, Predicate<MethodNode> change) {
		add(cls, what, c -> {
			MethodNode m = find(c, name, desc);
			return m != null && change.test(m);
		});
	}

	public byte[] transform(String name, byte[] bytes) {
		List<Named> list = changes.get(name);
		if (list == null) {
			return bytes;
		}
		ClassNode c = new ClassNode();
		new ClassReader(bytes).accept(c, 0);
		List<String> failed = new ArrayList<>();
		for (Named n : list) {
			boolean ok;
			try {
				ok = n.change.apply(c);
			} catch (RuntimeException e) {
				ok = false;
			}
			if (!ok) {
				failed.add(n.what);
			}
		}
		if (!failed.isEmpty()) {
			missed.addAll(failed);
			log.accept("not hooked in " + name + ": " + String.join(", ", failed) + "; " + name + " is left as it is");
			return bytes;
		}
		FrameWriter w = new FrameWriter(classes);
		c.accept(w);
		log.accept("hooked " + name + ": " + list.size() + " change(s)");
		return w.toByteArray();
	}

	// --- helpers ---

	static MethodNode find(ClassNode c, String name, String desc) {
		for (MethodNode m : c.methods) {
			if (m.name.equals(name) && m.desc.equals(desc)) {
				return m;
			}
		}
		return null;
	}

	private static MethodInsnNode hook(String name, String desc) {
		return new MethodInsnNode(INVOKESTATIC, HOOKS, name, desc, false);
	}

	/** Loads the method's arguments (and {@code this} when {@code self}), in order. */
	private static InsnList args(MethodNode m, boolean self) {
		InsnList out = new InsnList();
		int slot = 0;
		if ((m.access & ACC_STATIC) == 0) {
			if (self) {
				out.add(new VarInsnNode(ALOAD, 0));
			}
			slot = 1;
		}
		for (Type t : Type.getArgumentTypes(m.desc)) {
			out.add(new VarInsnNode(t.getOpcode(ILOAD), slot));
			slot += t.getSize();
		}
		return out;
	}

	/** At the start: {@code if (check) return [value];} */
	private static boolean returnIfAtHead(MethodNode m, InsnList check) {
		LabelNode go = new LabelNode();
		InsnList code = new InsnList();
		code.add(check);
		code.add(new JumpInsnNode(IFEQ, go));
		Type ret = Type.getReturnType(m.desc);
		switch (ret.getSort()) {
			case Type.VOID -> code.add(new InsnNode(RETURN));
			case Type.BOOLEAN, Type.INT -> {
				code.add(new InsnNode(ICONST_0));
				code.add(new InsnNode(IRETURN));
			}
			default -> {
				return false;
			}
		}
		code.add(go);
		m.instructions.insert(code);
		return true;
	}

	private static boolean beforeReturns(MethodNode m, java.util.function.Supplier<InsnList> code) {
		int n = 0;
		for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
			int op = i.getOpcode();
			if (op >= IRETURN && op <= RETURN) {
				m.instructions.insertBefore(i, code.get());
				n++;
			}
		}
		return n > 0;
	}

	private static List<MethodInsnNode> calls(MethodNode m, String owner, String name, String desc) {
		List<MethodInsnNode> out = new ArrayList<>();
		for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
			if (i instanceof MethodInsnNode mi && mi.name.equals(name) && mi.desc.equals(desc) && (owner == null || mi.owner.equals(owner))) {
				out.add(mi);
			}
		}
		return out;
	}

	private static InsnList list(AbstractInsnNode... insns) {
		InsnList out = new InsnList();
		for (AbstractInsnNode i : insns) {
			out.add(i);
		}
		return out;
	}

	// --- the events ---

	private void events() {
		// mods start: preInit before the first resource load, init before "Post startup", as Forge does
		method(MC, "func_71384_a", "()V", "mod loading", m -> {
			List<MethodInsnNode> refresh = calls(m, MC, "func_110436_a", "()V");
			AbstractInsnNode post = null;
			for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
				if (i instanceof LdcInsnNode l && "Post startup".equals(l.cst)) {
					post = i.getPrevious();
				}
			}
			if (refresh.isEmpty() || post == null) {
				return false;
			}
			AbstractInsnNode before = refresh.get(0).getPrevious();
			m.instructions.insertBefore(before, list(new VarInsnNode(ALOAD, 0), hook("preInit", "(L" + MC + ";)V")));
			m.instructions.insertBefore(post, list(new VarInsnNode(ALOAD, 0), hook("init", "(L" + MC + ";)V")));
			return true;
		});
		method(MC, "func_71407_l", "()V", "client tick, keys and mouse", m -> {
			m.instructions.insert(list(new InsnNode(ICONST_1), hook("clientTick", "(Z)V")));
			beforeReturns(m, () -> list(new InsnNode(ICONST_0), hook("clientTick", "(Z)V")));
			List<MethodInsnNode> keys = calls(m, MC, "func_152348_aa", "()V");
			List<MethodInsnNode> mouse = calls(m, "org/lwjgl/input/Mouse", "next", "()Z");
			if (keys.isEmpty() || mouse.isEmpty()) {
				return false;
			}
			for (MethodInsnNode k : keys) {
				m.instructions.set(k, hook("dispatchKeypresses", "(L" + MC + ";)V"));
			}
			for (MethodInsnNode n : mouse) {
				n.owner = HOOKS;
				n.name = "mouseNext";
			}
			return true;
		});
		method(MC, "func_147108_a", "(Lnet/minecraft/client/gui/GuiScreen;)V", "GuiOpenEvent", m -> {
			LabelNode go = new LabelNode();
			m.instructions.insert(list(new VarInsnNode(ALOAD, 1), hook("openGui", "(Lnet/minecraft/client/gui/GuiScreen;)Z"),
				new JumpInsnNode(IFEQ, go), new InsnNode(RETURN), go,
				hook("openedGui", "()Lnet/minecraft/client/gui/GuiScreen;"), new VarInsnNode(ASTORE, 1)));
			return true;
		});
		method(ER, "func_181560_a", "(FJ)V", "render tick and screen drawing", m -> {
			m.instructions.insert(list(new VarInsnNode(FLOAD, 1), new InsnNode(ICONST_1), hook("renderTick", "(FZ)V")));
			beforeReturns(m, () -> list(new VarInsnNode(FLOAD, 1), new InsnNode(ICONST_0), hook("renderTick", "(FZ)V")));
			List<MethodInsnNode> draw = calls(m, null, "func_73863_a", "(IIF)V");
			for (MethodInsnNode d : draw) {
				m.instructions.set(d, hook("drawScreen", "(L" + GUI + ";IIF)V"));
			}
			return !draw.isEmpty();
		});
		method(ER, "func_175068_a", "(IFJ)V", "RenderWorldLastEvent", m -> {
			for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
				if (i instanceof LdcInsnNode l && "hand".equals(l.cst) && i.getNext() instanceof MethodInsnNode after) {
					m.instructions.insert(after, list(new VarInsnNode(FLOAD, 2), hook("renderWorldLast", "(F)V")));
					return true;
				}
			}
			return false;
		});
		method(ER, "func_78481_a", "(FZ)F", "FOVModifier", m ->
			beforeReturns(m, () -> list(new VarInsnNode(FLOAD, 1), hook("fov", "(FF)F"))));
		method(ER, "func_78466_h", "(F)V", "FogColors", m -> {
			List<MethodInsnNode> clear = calls(m, GLSM, "func_179082_a", "(FFFF)V");
			for (MethodInsnNode c : clear) {
				m.instructions.set(c, hook("fogColors", "(FFFF)V"));
			}
			return !clear.isEmpty();
		});
		method(ER, "func_78468_a", "(IF)V", "FogDensity", m -> {
			// after the block at the camera is known, the event may set the density and skip vanilla's
			List<MethodInsnNode> at = calls(m, "net/minecraft/client/renderer/ActiveRenderInfo", "func_180786_a",
				"(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;F)Lnet/minecraft/block/Block;");
			List<MethodInsnNode> end = calls(m, GLSM, "func_179142_g", "()V");
			if (at.size() != 1 || end.isEmpty() || !(at.get(0).getNext() instanceof VarInsnNode store) || store.getOpcode() != ASTORE) {
				return false;
			}
			LabelNode skip = new LabelNode();
			m.instructions.insertBefore(end.get(0), skip);
			m.instructions.insert(store, list(new VarInsnNode(ALOAD, store.var), new VarInsnNode(FLOAD, 2),
				hook("fogDensity", "(Lnet/minecraft/block/Block;F)Z"), new JumpInsnNode(IFNE, skip)));
			return true;
		});
		method(INGAME, "func_175180_a", "(F)V", "RenderGameOverlayEvent", m -> {
			List<MethodInsnNode> cross = calls(m, INGAME, "func_175183_b", "()Z");
			if (cross.isEmpty()) {
				return false;
			}
			for (MethodInsnNode c : cross) {
				m.instructions.insert(c, list(new VarInsnNode(FLOAD, 1), hook("crosshairs", "(ZF)Z")));
			}
			beforeReturns(m, () -> list(new VarInsnNode(FLOAD, 1), hook("overlayPost", "(F)V")));
			return returnIfAtHead(m, list(new VarInsnNode(FLOAD, 1), hook("overlayPre", "(F)Z")));
		});
		method(INGAME, "func_73828_d", "()V", "boss health", m ->
			returnIfAtHead(m, list(new LdcInsnNode("BOSSHEALTH"), hook("element", "(Ljava/lang/String;)Z"))));
		method(INGAME, "func_180476_e", "(Lnet/minecraft/client/gui/ScaledResolution;)V", "helmet", m ->
			returnIfAtHead(m, list(new LdcInsnNode("HELMET"), hook("element", "(Ljava/lang/String;)Z"))));
		method(INGAME, "func_180475_a", "(Lnet/minecraft/scoreboard/ScoreObjective;Lnet/minecraft/client/gui/ScaledResolution;)V", "scoreboard", m ->
			returnIfAtHead(m, list(hook("hideObjective", "()Z"))));
		method("net/minecraft/client/gui/GuiOverlayDebug", "func_180798_a", "()V", "debug text", m -> {
			List<MethodInsnNode> call = calls(m, null, "call", "()Ljava/util/List;");
			for (MethodInsnNode c : call) {
				m.instructions.insert(c, hook("debugLeft", "(Ljava/util/List;)Ljava/util/List;"));
			}
			return !call.isEmpty();
		});
		method("net/minecraft/client/network/NetHandlerPlayClient", "func_147251_a", "(Lnet/minecraft/network/play/server/S02PacketChat;)V", "ClientChatReceivedEvent", m -> {
			List<MethodInsnNode> check = calls(m, "net/minecraft/network/PacketThreadUtil", "func_180031_a",
				"(Lnet/minecraft/network/Packet;Lnet/minecraft/network/INetHandler;Lnet/minecraft/util/IThreadListener;)V");
			if (check.size() != 1) {
				return false;
			}
			LabelNode go = new LabelNode();
			m.instructions.insert(check.get(0), list(new VarInsnNode(ALOAD, 1),
				hook("chat", "(Lnet/minecraft/network/play/server/S02PacketChat;)Lnet/minecraft/network/play/server/S02PacketChat;"),
				new InsnNode(DUP), new JumpInsnNode(IFNONNULL, go), new InsnNode(POP), new InsnNode(RETURN), go,
				new VarInsnNode(ASTORE, 1)));
			return true;
		});
		String living = "(" + ELB + "DDD)";
		method(RLE, "func_76986_a", "(" + ELB + "DDDFF)V", "RenderLivingEvent", m -> {
			beforeReturns(m, () -> list(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 1), new VarInsnNode(DLOAD, 2),
				new VarInsnNode(DLOAD, 4), new VarInsnNode(DLOAD, 6), hook("livingPost", "(L" + RLE + ";" + ELB + "DDD)V")));
			return returnIfAtHead(m, list(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 1), new VarInsnNode(DLOAD, 2),
				new VarInsnNode(DLOAD, 4), new VarInsnNode(DLOAD, 6), hook("livingPre", "(L" + RLE + ";" + ELB + "DDD)Z")));
		});
		method(RLE, "func_177067_a", living + "V", "RenderLivingEvent.Specials", m -> {
			beforeReturns(m, () -> {
				InsnList a = args(m, true);
				a.add(hook("specialsPost", "(L" + RLE + ";" + ELB + "DDD)V"));
				return a;
			});
			InsnList a = args(m, true);
			a.add(hook("specialsPre", "(L" + RLE + ";" + ELB + "DDD)Z"));
			return returnIfAtHead(m, a);
		});
		method("net/minecraft/client/renderer/RenderGlobal", "func_72731_b",
			"(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)V", "DrawBlockHighlightEvent", m -> {
				InsnList a = args(m, true);
				a.add(hook("blockHighlight", "(Lnet/minecraft/client/renderer/RenderGlobal;Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/MovingObjectPosition;IF)Z"));
				return returnIfAtHead(m, a);
			});
		method("net/minecraft/client/renderer/RenderGlobal", "func_174976_a", "(FI)V", "sky renderer", m ->
			returnIfAtHead(m, list(new VarInsnNode(FLOAD, 1), hook("renderSky", "(F)Z"))));
		method("net/minecraft/client/renderer/ItemRenderer", "func_78442_d", "(F)V", "fire overlay", m ->
			returnIfAtHead(m, list(new LdcInsnNode("FIRE"), new VarInsnNode(FLOAD, 1), hook("blockOverlay", "(Ljava/lang/String;F)Z"))));
		method("net/minecraft/client/renderer/ItemRenderer", "func_78448_c", "(F)V", "water overlay", m ->
			returnIfAtHead(m, list(new LdcInsnNode("WATER"), new VarInsnNode(FLOAD, 1), hook("blockOverlay", "(Ljava/lang/String;F)Z"))));
		method("net/minecraft/client/renderer/ItemRenderer", "func_78441_a", "()V", "re-equip check", CompatPatches::reequip);
		method("net/minecraft/client/audio/SoundManager", "func_148611_c", "(Lnet/minecraft/client/audio/ISound;)V", "PlaySoundEvent", m -> {
			LabelNode go = new LabelNode();
			m.instructions.insert(list(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 1),
				hook("playSound", "(Lnet/minecraft/client/audio/SoundManager;Lnet/minecraft/client/audio/ISound;)Lnet/minecraft/client/audio/ISound;"),
				new InsnNode(DUP), new JumpInsnNode(IFNONNULL, go), new InsnNode(POP), new InsnNode(RETURN), go, new VarInsnNode(ASTORE, 1)));
			return true;
		});
		method(GUI, "func_146280_a", "(L" + MC + ";II)V", "InitGuiEvent", m ->
			beforeReturns(m, () -> list(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 0),
				new FieldInsnNode(GETFIELD, GUI, "field_146292_n", "Ljava/util/List;"), hook("initGuiPost", "(L" + GUI + ";Ljava/util/List;)V"))));
		method(GUI, "func_73864_a", "(III)V", "ActionPerformedEvent", m -> {
			List<MethodInsnNode> act = calls(m, GUI, "func_146284_a", "(Lnet/minecraft/client/gui/GuiButton;)V");
			for (MethodInsnNode a : act) {
				if (!(a.getPrevious() instanceof VarInsnNode button) || button.getOpcode() != ALOAD) {
					return false;
				}
				m.instructions.insert(a, list(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, button.var), new VarInsnNode(ALOAD, 0),
					new FieldInsnNode(GETFIELD, GUI, "field_146292_n", "Ljava/util/List;"),
					hook("actionPerformedPost", "(L" + GUI + ";Lnet/minecraft/client/gui/GuiButton;Ljava/util/List;)V")));
			}
			return !act.isEmpty();
		});
		method(GUI, "func_146269_k", "()V", "GuiScreenEvent.MouseInputEvent", m -> {
			List<MethodInsnNode> next = calls(m, "org/lwjgl/input/Mouse", "next", "()Z");
			for (MethodInsnNode n : next) {
				m.instructions.insertBefore(n, new VarInsnNode(ALOAD, 0));
				n.owner = HOOKS;
				n.name = "guiMouseNext";
				n.desc = "(L" + GUI + ";)Z";
			}
			return !next.isEmpty();
		});
		method(GUI, "func_175281_b", "(Ljava/lang/String;Z)V", "client commands", m ->
			returnIfAtHead(m, list(new VarInsnNode(ALOAD, 1), new VarInsnNode(ILOAD, 2), hook("clientCommand", "(Ljava/lang/String;Z)Z"))));
		method("net/minecraft/entity/player/EntityPlayer", "func_71059_n", "(Lnet/minecraft/entity/Entity;)V", "AttackEntityEvent", m ->
			returnIfAtHead(m, list(new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 1),
				hook("attack", "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/Entity;)Z"))));
		method("net/minecraft/client/multiplayer/PlayerControllerMP", "func_178890_a",
			"(Lnet/minecraft/client/entity/EntityPlayerSP;Lnet/minecraft/client/multiplayer/WorldClient;Lnet/minecraft/item/ItemStack;"
				+ "Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/Vec3;)Z", "PlayerInteractEvent", m ->
				returnIfAtHead(m, list(new VarInsnNode(ALOAD, 1), new VarInsnNode(ALOAD, 2), new VarInsnNode(ALOAD, 4), new VarInsnNode(ALOAD, 5),
					hook("rightClickBlock", "(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)Z"))));
	}

	// --- what Forge adds to game classes and Rago uses ---

	private void forgeMembers() {
		add("net/minecraft/world/WorldProvider", "sky renderer field", c -> {
			String ih = "Lnet/minecraftforge/client/IRenderHandler;";
			c.methods.add(simple("getSkyRenderer", "()" + ih, new VarInsnNode(ALOAD, 0),
				hook("getSky", "(Lnet/minecraft/world/WorldProvider;)" + ih), new InsnNode(ARETURN)));
			c.methods.add(simple("setSkyRenderer", "(" + ih + ")V", new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, 1),
				hook("setSky", "(Lnet/minecraft/world/WorldProvider;" + ih + ")V"), new InsnNode(RETURN)));
			return true;
		});
		add("net/minecraft/world/World", "persistent chunks", c -> {
			c.methods.add(simple("getPersistentChunks", "()Lcom/google/common/collect/ImmutableSetMultimap;",
				new MethodInsnNode(INVOKESTATIC, "com/google/common/collect/ImmutableSetMultimap", "of", "()Lcom/google/common/collect/ImmutableSetMultimap;", false),
				new InsnNode(ARETURN)));
			return true;
		});
		add("net/minecraft/tileentity/TileEntity", "render bounding box", c -> {
			String aabb = "Lnet/minecraft/util/AxisAlignedBB;";
			c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC | ACC_FINAL, "INFINITE_EXTENT_AABB", aabb, null, null));
			MethodNode clinit = find(c, "<clinit>", "()V");
			if (clinit == null) {
				return false;
			}
			clinit.instructions.insert(list(hook("infiniteBox", "()" + aabb),
				new FieldInsnNode(PUTSTATIC, "net/minecraft/tileentity/TileEntity", "INFINITE_EXTENT_AABB", aabb)));
			c.methods.add(simple("getRenderBoundingBox", "()" + aabb, new VarInsnNode(ALOAD, 0),
				hook("renderBox", "(Lnet/minecraft/tileentity/TileEntity;)" + aabb), new InsnNode(ARETURN)));
			return true;
		});
		add(RLE, "name tag range", c -> {
			c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "NAME_TAG_RANGE", "F", null, 64.0f));
			c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "NAME_TAG_RANGE_SNEAK", "F", null, 32.0f));
			return true;
		});
		add("net/minecraft/client/renderer/OpenGlHelper", "last lightmap coordinates", c -> {
			c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "lastBrightnessX", "F", null, 0.0f));
			c.fields.add(new FieldNode(ACC_PUBLIC | ACC_STATIC, "lastBrightnessY", "F", null, 0.0f));
			MethodNode m = find(c, "func_77475_a", "(IFF)V");
			if (m == null) {
				return false;
			}
			String gl = "net/minecraft/client/renderer/OpenGlHelper";
			LabelNode skip = new LabelNode();
			m.instructions.insert(list(new VarInsnNode(ILOAD, 0), new FieldInsnNode(GETSTATIC, gl, "field_77476_b", "I"),
				new JumpInsnNode(IF_ICMPNE, skip), new VarInsnNode(FLOAD, 1), new FieldInsnNode(PUTSTATIC, gl, "lastBrightnessX", "F"),
				new VarInsnNode(FLOAD, 2), new FieldInsnNode(PUTSTATIC, gl, "lastBrightnessY", "F"), skip));
			return true;
		});
		add("net/minecraft/client/renderer/entity/RenderManager", "skin map", c -> {
			c.methods.add(simple("getSkinMap", "()Ljava/util/Map;", new VarInsnNode(ALOAD, 0),
				new FieldInsnNode(GETFIELD, "net/minecraft/client/renderer/entity/RenderManager", "field_178636_l", "Ljava/util/Map;"),
				new InsnNode(ARETURN)));
			return true;
		});
		add("net/minecraft/item/Item", "re-equip and registry name", c -> {
			String is = "Lnet/minecraft/item/ItemStack;";
			c.methods.add(simple("shouldCauseReequipAnimation", "(" + is + is + "Z)Z", new VarInsnNode(ALOAD, 1), new VarInsnNode(ALOAD, 2),
				new VarInsnNode(ILOAD, 3), hook("reequip", "(" + is + is + "Z)Z"), new InsnNode(IRETURN)));
			c.methods.add(simple("getRegistryName", "()Ljava/lang/String;", new VarInsnNode(ALOAD, 0),
				hook("registryName", "(Lnet/minecraft/item/Item;)Ljava/lang/String;"), new InsnNode(ARETURN)));
			return true;
		});
		add("net/minecraft/entity/player/EntityPlayer", "display name string", c -> {
			c.methods.add(simple("getDisplayNameString", "()Ljava/lang/String;", new VarInsnNode(ALOAD, 0),
				new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/entity/player/EntityPlayer", "func_70005_c_", "()Ljava/lang/String;", false),
				new InsnNode(ARETURN)));
			return true;
		});
	}

	/**
	 * Forge's check in {@code ItemRenderer.updateEquippedItem}: when the held stack changed but
	 * {@code item.shouldCauseReequipAnimation(old, new, slotChanged)} says no, the new stack is
	 * taken without lowering the hand. Mods (Rago among them) look for that call to change it.
	 */
	private static boolean reequip(MethodNode m) {
		String ir = "net/minecraft/client/renderer/ItemRenderer";
		String stack = "Lnet/minecraft/item/ItemStack;";
		String inv = "net/minecraft/entity/player/InventoryPlayer";
		int player = -1;
		for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
			if (i instanceof FieldInsnNode f && f.name.equals("field_71439_g") && i.getNext() instanceof VarInsnNode v && v.getOpcode() == ASTORE) {
				player = v.var;
			}
			if (player < 0 || !(i instanceof MethodInsnNode call) || !call.name.equals("func_179549_c")
				|| !(call.getPrevious() instanceof VarInsnNode current) || !(call.getNext() instanceof JumpInsnNode same)
				|| same.getOpcode() != IFNE) {
				continue;
			}
			LabelNode unchanged = new LabelNode();
			LabelNode slotSame = new LabelNode();
			LabelNode slotKnown = new LabelNode();
			m.instructions.insert(same, list(
				new VarInsnNode(ALOAD, 0), new FieldInsnNode(GETFIELD, ir, "field_78453_b", stack),
				new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/item/ItemStack", "func_77973_b", "()Lnet/minecraft/item/Item;", false),
				new VarInsnNode(ALOAD, 0), new FieldInsnNode(GETFIELD, ir, "field_78453_b", stack), new VarInsnNode(ALOAD, current.var),
				new VarInsnNode(ALOAD, 0), new FieldInsnNode(GETFIELD, ir, "field_78450_g", "I"),
				new VarInsnNode(ALOAD, player), new FieldInsnNode(GETFIELD, "net/minecraft/entity/player/EntityPlayer", "field_71071_by", "L" + inv + ";"),
				new FieldInsnNode(GETFIELD, inv, "field_70461_c", "I"),
				new JumpInsnNode(IF_ICMPEQ, slotSame), new InsnNode(ICONST_1), new JumpInsnNode(GOTO, slotKnown),
				slotSame, new InsnNode(ICONST_0), slotKnown,
				new MethodInsnNode(INVOKEVIRTUAL, "net/minecraft/item/Item", "shouldCauseReequipAnimation", "(" + stack + stack + "Z)Z", false),
				new JumpInsnNode(IFNE, unchanged),
				new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, current.var), new FieldInsnNode(PUTFIELD, ir, "field_78453_b", stack),
				new VarInsnNode(ALOAD, 0), new VarInsnNode(ALOAD, player),
				new FieldInsnNode(GETFIELD, "net/minecraft/entity/player/EntityPlayer", "field_71071_by", "L" + inv + ";"),
				new FieldInsnNode(GETFIELD, inv, "field_70461_c", "I"), new FieldInsnNode(PUTFIELD, ir, "field_78450_g", "I"),
				new InsnNode(RETURN), unchanged));
			return true;
		}
		return false;
	}

	private static MethodNode simple(String name, String desc, AbstractInsnNode... code) {
		MethodNode m = new MethodNode(ACC_PUBLIC, name, desc, null, null);
		for (AbstractInsnNode i : code) {
			m.instructions.add(i);
		}
		return m;
	}
}
