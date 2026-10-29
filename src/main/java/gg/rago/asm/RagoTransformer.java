package gg.rago.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
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

/**
 * Rago's small, targeted changes to game classes. Each one calls a static method of
 * {@code gg.rago.hooks.Hooks}, which decides from the player's settings whether vanilla behaviour
 * changes; with the feature off the game behaves exactly as without Rago. Methods are found by
 * their SRG name (normal game) or MCP name (dev environment) and descriptor. If a method is not
 * found, for example because another mod rewrote it, that one change is skipped and logged; the
 * class is never left half patched.
 *
 * <p>None of these change what is sent to the server: they affect drawing, what the client keeps
 * in memory, window handling, and (mouse delay fix, off by default) which entity the crosshair
 * points at, which then matches what the camera shows.
 */
public final class RagoTransformer implements IClassTransformer {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final String HOOKS = "gg/rago/hooks/Hooks";

	private interface Patcher {
		/** Changes the method and returns whether it found what it was looking for. */
		boolean apply(MethodNode m);
	}

	private static final class Patch {
		final String what;
		final String srg;
		final String mcp;
		final String desc;
		final Patcher patcher;

		Patch(String what, String srg, String mcp, String desc, Patcher patcher) {
			this.what = what;
			this.srg = srg;
			this.mcp = mcp;
			this.desc = desc;
			this.patcher = patcher;
		}
	}

	private static final Map<String, List<Patch>> PATCHES = new HashMap<>();

	static {
		add("net.minecraft.client.Minecraft", new Patch("menu and unfocused frame-rate caps",
			"func_90020_K", "getLimitFramerate", "()I",
			m -> beforeReturns(m, Opcodes.IRETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, "gg/rago/optimize/FrameLimit", "limit", "(I)I", false))));
		add("net.minecraft.client.renderer.entity.RenderManager", new Patch("occlusion culling of entities",
			"func_178635_a", "shouldRender", "(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/renderer/culling/ICamera;DDD)Z",
			m -> returnIf(m, "skipEntity", "(Lnet/minecraft/entity/Entity;)Z", new int[] {Opcodes.ALOAD, 1}, Opcodes.ICONST_0, Opcodes.IRETURN)));
		add("net.minecraft.client.renderer.EntityRenderer", new Patch("hurt camera strength",
			"func_78482_e", "hurtCameraEffect", "(F)V",
			RagoTransformer::hurtShake));
		add("net.minecraft.client.renderer.EntityRenderer", new Patch("smooth sneak camera",
			"func_78467_g", "orientCamera", "(F)V",
			m -> {
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn instanceof MethodInsnNode && "()F".equals(((MethodInsnNode) insn).desc)
						&& ("func_70047_e".equals(((MethodInsnNode) insn).name) || "getEyeHeight".equals(((MethodInsnNode) insn).name))) {
						m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "cameraEyeHeight", "(F)F", false));
						return true;
					}
				}
				return false;
			}));
		add("net.minecraft.client.renderer.EntityRenderer", new Patch("parallax fix",
			"func_78467_g", "orientCamera", "(F)V",
			m -> {
				boolean found = false;
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn instanceof LdcInsnNode && Float.valueOf(-0.1f).equals(((LdcInsnNode) insn).cst)) {
						m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "cameraOffset", "(F)F", false));
						found = true;
					}
				}
				return found;
			}));
		add("net.minecraft.util.ScreenShotHelper", new Patch("screenshot buttons",
			"func_148259_a", "saveScreenshot",
			"(Ljava/io/File;Ljava/lang/String;IILnet/minecraft/client/shader/Framebuffer;)Lnet/minecraft/util/IChatComponent;",
			m -> beforeReturns(m, Opcodes.ARETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "screenshot",
				"(Lnet/minecraft/util/IChatComponent;)Lnet/minecraft/util/IChatComponent;", false))));
		add("net.minecraft.network.play.server.S48PacketResourcePackSend", new Patch("server resource pack check",
			"func_179783_a", "getURL", "()Ljava/lang/String;",
			m -> beforeReturns(m, Opcodes.ARETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "resourcePackUrl",
				"(Ljava/lang/String;)Ljava/lang/String;", false))));
		add("net.minecraft.client.renderer.entity.layers.LayerArmorBase", new Patch("1.7 armour damage tint",
			"func_177142_b", "shouldCombineTextures", "()Z",
			m -> beforeReturns(m, Opcodes.IRETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "armorDamageTint", "(Z)Z", false))));
		add("net.minecraft.client.gui.GuiPlayerTabOverlay", new Patch("ping numbers in the tab list",
			"func_175245_a", "drawPing", "(IIILnet/minecraft/client/network/NetworkPlayerInfo;)V",
			m -> returnIf(m, "replaceTabPing", "(IIILnet/minecraft/client/network/NetworkPlayerInfo;)Z",
				new int[] {Opcodes.ILOAD, 1, Opcodes.ILOAD, 2, Opcodes.ILOAD, 3, Opcodes.ALOAD, 4}, -1, Opcodes.RETURN)));
		add("net.minecraft.client.gui.GuiPlayerTabOverlay", new Patch("Bed Wars stats in the tab list",
			"func_175243_a", "getPlayerName", "(Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;",
			m -> {
				boolean found = false;
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() == Opcodes.ARETURN) {
						InsnList call = new InsnList();
						call.add(new VarInsnNode(Opcodes.ALOAD, 1));
						call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabName",
							"(Ljava/lang/String;Lnet/minecraft/client/network/NetworkPlayerInfo;)Ljava/lang/String;", false));
						m.instructions.insertBefore(insn, call);
						found = true;
					}
				}
				return found;
			}));
		add("net.minecraft.client.gui.GuiPlayerTabOverlay", new Patch("tab list look",
			"func_175249_a", "renderPlayerlist", "(ILnet/minecraft/scoreboard/Scoreboard;Lnet/minecraft/scoreboard/ScoreObjective;)V",
			RagoTransformer::tabLook));
		add("net.minecraft.entity.player.EntityPlayer", new Patch("eating fix",
			"func_70071_h_", "onUpdate", "()V",
			m -> {
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					AbstractInsnNode prev = insn.getPrevious();
					if (insn.getOpcode() == Opcodes.IF_ACMPNE && prev instanceof FieldInsnNode
						&& ("field_71074_e".equals(((FieldInsnNode) prev).name) || "itemInUse".equals(((FieldInsnNode) prev).name))) {
						InsnList check = new InsnList();
						check.add(new VarInsnNode(Opcodes.ALOAD, 0));
						check.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "keepItemInUse",
							"(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/EntityPlayer;)Z", false));
						check.add(new JumpInsnNode(Opcodes.IFEQ, ((JumpInsnNode) insn).label));
						m.instructions.insertBefore(insn, check);
						m.instructions.remove(insn);
						return true;
					}
				}
				return false;
			}));
		add("net.minecraft.client.gui.inventory.GuiContainer", new Patch("fast shop clicks",
			"func_146984_a", "handleMouseClick", "(Lnet/minecraft/inventory/Slot;III)V",
			m -> returnIf(m, "containerClick", "(Lnet/minecraft/client/gui/inventory/GuiContainer;Lnet/minecraft/inventory/Slot;III)Z",
				new int[] {Opcodes.ALOAD, 0, Opcodes.ALOAD, 1, Opcodes.ILOAD, 2, Opcodes.ILOAD, 3, Opcodes.ILOAD, 4}, -1, Opcodes.RETURN)));
		add("net.minecraft.client.gui.inventory.GuiContainer", new Patch("fast shop highlights",
			"func_146977_a", "drawSlot", "(Lnet/minecraft/inventory/Slot;)V",
			m -> {
				InsnList head = new InsnList();
				head.add(new VarInsnNode(Opcodes.ALOAD, 0));
				head.add(new VarInsnNode(Opcodes.ALOAD, 1));
				head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "beforeSlot",
					"(Lnet/minecraft/client/gui/inventory/GuiContainer;Lnet/minecraft/inventory/Slot;)V", false));
				m.instructions.insert(head);
				return true;
			}));
		add("net.minecraft.client.Minecraft", new Patch("hit delay fix",
			"func_147116_af", "clickMouse", "()V",
			m -> {
				boolean found = false;
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 10
						&& insn.getNext() instanceof FieldInsnNode && insn.getNext().getOpcode() == Opcodes.PUTFIELD
						&& ("field_71429_W".equals(((FieldInsnNode) insn.getNext()).name)
							|| "leftClickCounter".equals(((FieldInsnNode) insn.getNext()).name))) {
						m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "missDelay", "(I)I", false));
						found = true;
					}
				}
				return found;
			}));
		add("net.minecraft.client.particle.EffectRenderer", new Patch("no block breaking particles",
			"func_180533_a", "addBlockDestroyEffects", "(Lnet/minecraft/util/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
			m -> returnIf(m, "hideBlockBreak", "()Z", new int[0], -1, Opcodes.RETURN)));
		add("net.minecraft.client.particle.EffectRenderer", new Patch("no block mining particles",
			"func_180532_a", "addBlockHitEffects", "(Lnet/minecraft/util/BlockPos;Lnet/minecraft/util/EnumFacing;)V",
			m -> returnIf(m, "hideBlockHit", "()Z", new int[0], -1, Opcodes.RETURN)));
		add("net.minecraft.client.gui.GuiNewChat", new Patch("longer chat history",
			"func_146237_a", "setChatLine", "(Lnet/minecraft/util/IChatComponent;IIZ)V",
			RagoTransformer::chatLimit));
		add("net.minecraft.client.renderer.ItemRenderer", new Patch("1.7 swing while blocking, eating and drawing a bow",
			"func_78440_a", "renderItemInFirstPerson", "(F)V",
			RagoTransformer::useSwing));
		add("net.minecraft.client.renderer.ItemRenderer", new Patch("held item position and size",
			"func_178096_b", "transformFirstPersonItem", "(FF)V",
			m -> {
				m.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "heldItemOffset", "()V", false));
				return beforeReturns(m, Opcodes.RETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "heldItemScale", "()V", false));
			}));
		add("net.minecraft.client.renderer.ItemRenderer", new Patch("no re-equip animation when the held item only changes",
			"func_78441_a", "updateEquippedItem", "()V",
			RagoTransformer::reequip));
		add("net.minecraft.client.gui.GuiNewChat", new Patch("chat background and text shadow",
			"func_146230_a", "drawChat", "(I)V",
			RagoTransformer::chatLook));
		add("net.minecraft.entity.EntityLivingBase", new Patch("mouse delay fix",
			"func_70676_i", "getLook", "(F)Lnet/minecraft/util/Vec3;",
			RagoTransformer::lookFix));
		add("net.minecraft.entity.EntityLivingBase", new Patch("swing speed",
			"func_82166_i", "getArmSwingAnimationEnd", "()I",
			m -> {
				boolean found = false;
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() == Opcodes.IRETURN) {
						InsnList call = new InsnList();
						call.add(new VarInsnNode(Opcodes.ALOAD, 0));
						call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "swingDuration", "(ILnet/minecraft/entity/EntityLivingBase;)I", false));
						m.instructions.insertBefore(insn, call);
						found = true;
					}
				}
				return found;
			}));
		add("net.minecraft.client.renderer.entity.RendererLivingEntity", new Patch("hit colour",
			"func_177092_a", "setBrightness", "(Lnet/minecraft/entity/EntityLivingBase;FZ)Z",
			RagoTransformer::hitColor));
		add("net.minecraft.client.renderer.entity.RenderEntityItem", new Patch("unstacked dropped items",
			"func_177078_a", "func_177078_a", "(Lnet/minecraft/item/ItemStack;)I",
			m -> beforeReturns(m, Opcodes.IRETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "itemCopies", "(I)I", false))));
		add("net.minecraft.client.renderer.entity.RenderEntityItem", new Patch("dropped item physics and size",
			"func_177077_a", "func_177077_a", "(Lnet/minecraft/entity/item/EntityItem;DDDFLnet/minecraft/client/resources/model/IBakedModel;)I",
			RagoTransformer::droppedItem));
		add("net.minecraft.client.Minecraft", new Patch("fast world switching",
			"func_71353_a", "loadWorld", "(Lnet/minecraft/client/multiplayer/WorldClient;Ljava/lang/String;)V",
			m -> {
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn instanceof MethodInsnNode && "java/lang/System".equals(((MethodInsnNode) insn).owner)
						&& "gc".equals(((MethodInsnNode) insn).name)) {
						m.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "worldSwitchGc", "()V", false));
						return true;
					}
				}
				return false;
			}));
		add("net.minecraft.client.entity.AbstractClientPlayer", new Patch("Rago cape",
			"func_110303_q", "getLocationCape", "()Lnet/minecraft/util/ResourceLocation;",
			m -> {
				boolean found = false;
				for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
					if (insn.getOpcode() == Opcodes.ARETURN) {
						InsnList call = new InsnList();
						call.add(new VarInsnNode(Opcodes.ALOAD, 0));
						call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "cape",
							"(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/entity/AbstractClientPlayer;)Lnet/minecraft/util/ResourceLocation;", false));
						m.instructions.insertBefore(insn, call);
						found = true;
					}
				}
				return found;
			}));
		add("net.minecraft.client.entity.AbstractClientPlayer", new Patch("locker skin",
			"func_110306_p", "getLocationSkin", "()Lnet/minecraft/util/ResourceLocation;",
			m -> ownPlayerFilter(m, Opcodes.ARETURN, "skin",
				"(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/client/entity/AbstractClientPlayer;)Lnet/minecraft/util/ResourceLocation;")));
		add("net.minecraft.client.entity.AbstractClientPlayer", new Patch("locker skin model",
			"func_175154_l", "getSkinType", "()Ljava/lang/String;",
			m -> ownPlayerFilter(m, Opcodes.ARETURN, "skinModel",
				"(Ljava/lang/String;Lnet/minecraft/client/entity/AbstractClientPlayer;)Ljava/lang/String;")));
		add("net.minecraft.client.renderer.entity.RenderItem", new Patch("glint colour on items",
			"func_180451_a", "renderEffect", "(Lnet/minecraft/client/resources/model/IBakedModel;)V",
			RagoTransformer::itemGlint));
		add("net.minecraft.client.renderer.entity.layers.LayerArmorBase", new Patch("glint colour on armour",
			"func_177183_a", "renderGlint",
			"(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/model/ModelBase;FFFFFFF)V",
			RagoTransformer::armorGlint));
		add("net.minecraft.client.Minecraft", new Patch("borderless fullscreen",
			"func_71352_k", "toggleFullscreen", "()V",
			m -> returnIf(m, "toggleFullscreen", "()Z", new int[0], -1, Opcodes.RETURN)));
	}

	/** Passes each value a player method returns through a hook that also gets the player. */
	private static boolean ownPlayerFilter(MethodNode m, int returnOp, String hook, String desc) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == returnOp) {
				InsnList call = new InsnList();
				call.add(new VarInsnNode(Opcodes.ALOAD, 0));
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, desc, false));
				m.instructions.insertBefore(insn, call);
				found = true;
			}
		}
		return found;
	}

	private static void add(String className, Patch patch) {
		PATCHES.computeIfAbsent(className, k -> new ArrayList<>()).add(patch);
	}

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		List<Patch> patches = bytes == null ? null : PATCHES.get(transformedName);
		if (patches == null) {
			return bytes;
		}
		try {
			ClassNode node = new ClassNode();
			new ClassReader(bytes).accept(node, 0);
			int applied = 0;
			for (Patch p : patches) {
				MethodNode target = null;
				for (MethodNode m : node.methods) {
					if (p.desc.equals(m.desc) && (p.srg.equals(m.name) || p.mcp.equals(m.name))) {
						target = m;
						break;
					}
				}
				if (target == null) {
					LOG.warn("{}: {}.{} not found, feature unavailable", p.what, transformedName, p.mcp);
					continue;
				}
				// patch a copy, so a pattern that is only half found leaves the method untouched
				MethodNode copy = copyOf(target);
				if (p.patcher.apply(copy)) {
					node.methods.set(node.methods.indexOf(target), copy);
					applied++;
					LOG.info("Hook installed: {}", p.what);
				} else {
					LOG.warn("{}: unexpected code in {}.{}, feature unavailable", p.what, transformedName, p.mcp);
					dump(transformedName, bytes);
				}
			}
			if (applied == 0) {
				return bytes;
			}
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			return writer.toByteArray();
		} catch (RuntimeException e) {
			LOG.error("Could not patch {}; keeping vanilla behaviour", transformedName, e);
			return bytes;
		}
	}

	/** With {@code -Drago.dumpClasses=true}, saves a class a patch did not fit, to see what changed it. */
	private static void dump(String name, byte[] bytes) {
		if (!Boolean.getBoolean("rago.dumpClasses")) {
			return;
		}
		try {
			java.nio.file.Path file = java.nio.file.Paths.get("rago-dump", name + ".class");
			java.nio.file.Files.createDirectories(file.getParent());
			java.nio.file.Files.write(file, bytes);
		} catch (java.io.IOException e) {
			LOG.warn("Could not dump {}", name, e);
		}
	}

	private static MethodNode copyOf(MethodNode m) {
		MethodNode copy = new MethodNode(Opcodes.ASM5, m.access, m.name, m.desc, m.signature,
			m.exceptions.toArray(new String[0]));
		m.accept(copy);
		return copy;
	}

	/** Passes vanilla's glint colour constant through {@code Hooks.glintColor}. */
	private static boolean itemGlint(MethodNode m) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof LdcInsnNode && Integer.valueOf(-8372020).equals(((LdcInsnNode) insn).cst)) {
				m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "glintColor", "(I)I", false));
				found = true;
			}
		}
		return found;
	}

	/** After the armour glint's colour is set (the call that follows the 0.76 dimming), sets ours. */
	private static boolean armorGlint(MethodNode m) {
		boolean dim = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof LdcInsnNode && Float.valueOf(0.76f).equals(((LdcInsnNode) insn).cst)) {
				dim = true;
			} else if (dim && insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals("net/minecraft/client/renderer/GlStateManager")
				&& (((MethodInsnNode) insn).name.equals("func_179131_c") || ((MethodInsnNode) insn).name.equals("color"))) {
				m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "armorGlint", "()V", false));
				return true;
			}
		}
		return false;
	}

	/** Passes every returned value of the given return opcode through {@code hook}. */
	private static boolean beforeReturns(MethodNode m, int returnOpcode, MethodInsnNode hook) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == returnOpcode) {
				m.instructions.insertBefore(insn, new MethodInsnNode(hook.getOpcode(), hook.owner, hook.name, hook.desc, false));
				found = true;
			}
		}
		return found;
	}

	/**
	 * At the start of the method: loads the given (opcode, slot) pairs, calls {@code Hooks.hook}
	 * and, if it returns true, returns at once ({@code value} is pushed first unless it is -1).
	 */
	private static boolean returnIf(MethodNode m, String hook, String desc, int[] loads, int value, int returnOpcode) {
		InsnList head = new InsnList();
		for (int i = 0; i < loads.length; i += 2) {
			head.add(new VarInsnNode(loads[i], loads[i + 1]));
		}
		head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, hook, desc, false));
		LabelNode go = new LabelNode();
		head.add(new JumpInsnNode(Opcodes.IFEQ, go));
		if (value != -1) {
			head.add(new InsnNode(value));
		}
		head.add(new InsnNode(returnOpcode));
		head.add(go);
		head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		m.instructions.insert(head);
		return true;
	}

	/** Every returned copy count goes through {@code Hooks.droppedItem} with the method's arguments. */
	private static boolean droppedItem(MethodNode m) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.IRETURN) {
				InsnList call = new InsnList();
				call.add(new VarInsnNode(Opcodes.ALOAD, 1));
				call.add(new VarInsnNode(Opcodes.DLOAD, 2));
				call.add(new VarInsnNode(Opcodes.DLOAD, 4));
				call.add(new VarInsnNode(Opcodes.DLOAD, 6));
				call.add(new VarInsnNode(Opcodes.FLOAD, 8));
				call.add(new VarInsnNode(Opcodes.ALOAD, 9));
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "droppedItem",
					"(ILnet/minecraft/entity/item/EntityItem;DDDFLnet/minecraft/client/resources/model/IBakedModel;)I", false));
				m.instructions.insertBefore(insn, call);
				found = true;
			}
		}
		return found;
	}

	/** The 14° tilt when the player is hurt becomes {@code Hooks.hurtShake(14)}; the death tilt is kept. */
	private static boolean hurtShake(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof LdcInsnNode && Float.valueOf(14f).equals(((LdcInsnNode) insn).cst)) {
				m.instructions.insert(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "hurtShake", "(F)F", false));
				return true;
			}
		}
		return false;
	}

	/** The two {@code > 100} line limits in setChatLine read {@code Hooks.chatHistory()} instead. */
	private static boolean chatLimit(MethodNode m) {
		int found = 0;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 100) {
				AbstractInsnNode call = new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "chatHistory", "()I", false);
				m.instructions.set(insn, call);
				insn = call;
				found++;
			}
		}
		return found > 0;
	}

	/**
	 * While an item is in use, vanilla 1.8 calls {@code transformFirstPersonItem(equip, 0.0F)}: when
	 * eating or drinking (after {@code performDrinking}), blocking (before {@code doBlockTransformations})
	 * and drawing a bow (before {@code doBowTransformations}). 1.7 passed the swing progress there,
	 * which is what makes a block-hit or a swing while eating visible. Each 0.0F becomes
	 * {@code Hooks.useSwing(swing, kind)}; the block case must be found, the others are optional.
	 */
	private static boolean useSwing(MethodNode m) {
		int swingVar = -1;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode && isMethod((MethodInsnNode) insn, "func_70678_g", "getSwingProgress", "(F)F")
				&& insn.getNext() != null && insn.getNext().getOpcode() == Opcodes.FSTORE) {
				swingVar = ((VarInsnNode) insn.getNext()).var;
				break;
			}
		}
		if (swingVar < 0) {
			return false;
		}
		boolean block = false;
		List<AbstractInsnNode[]> sites = new ArrayList<>();
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode) || !isMethod((MethodInsnNode) insn, "func_178096_b", "transformFirstPersonItem", "(FF)V")) {
				continue;
			}
			AbstractInsnNode zero = insn.getPrevious();
			if (zero == null || zero.getOpcode() != Opcodes.FCONST_0) {
				continue;
			}
			int kind = -1;
			MethodInsnNode after = nextCall(insn, 4);
			MethodInsnNode before = previousCall(zero, 5);
			if (after != null && isMethod(after, "func_178103_d", "doBlockTransformations", "()V")) {
				kind = 1;
				block = true;
			} else if (after != null && isMethod(after, "func_178098_a", "doBowTransformations", "(FLnet/minecraft/client/entity/AbstractClientPlayer;)V")) {
				kind = 2;
			} else if (before != null && isMethod(before, "func_178104_a", "performDrinking", "(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V")) {
				kind = 0;
			}
			if (kind >= 0) {
				sites.add(new AbstractInsnNode[] {zero, new IntInsnNode(Opcodes.BIPUSH, kind)});
			}
		}
		if (!block) {
			return false;
		}
		for (AbstractInsnNode[] site : sites) {
			InsnList swing = new InsnList();
			swing.add(new VarInsnNode(Opcodes.FLOAD, swingVar));
			swing.add(site[1]);
			swing.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "useSwing", "(FI)F", false));
			m.instructions.insertBefore(site[0], swing);
			m.instructions.remove(site[0]);
		}
		return true;
	}

	/** The first method call within {@code limit} real instructions after {@code insn}. */
	private static MethodInsnNode nextCall(AbstractInsnNode insn, int limit) {
		AbstractInsnNode n = insn;
		for (int i = 0; i < limit && n != null; i++) {
			n = nextReal(n);
			if (n instanceof MethodInsnNode) {
				return (MethodInsnNode) n;
			}
		}
		return null;
	}

	private static MethodInsnNode previousCall(AbstractInsnNode insn, int limit) {
		AbstractInsnNode n = insn.getPrevious();
		for (int i = 0; i < limit && n != null; n = n.getPrevious()) {
			if (n.getOpcode() < 0) {
				continue;
			}
			if (n instanceof MethodInsnNode) {
				return (MethodInsnNode) n;
			}
			i++;
		}
		return null;
	}

	/**
	 * Forge's {@code Item.shouldCauseReequipAnimation} call goes through {@code Hooks.reequip}.
	 * OptiFine's copy of the method makes that call through its own reflection helper
	 * ({@code Reflector.callBoolean(item, ForgeItem_shouldCauseReequipAnimation, args)}), which goes
	 * through {@code Hooks.reequipArgs} instead.
	 */
	private static boolean reequip(MethodNode m) {
		boolean reflected = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode && "ForgeItem_shouldCauseReequipAnimation".equals(((FieldInsnNode) insn).name)) {
				reflected = true;
			}
			if (!(insn instanceof MethodInsnNode)) {
				continue;
			}
			MethodInsnNode call = (MethodInsnNode) insn;
			if ("shouldCauseReequipAnimation".equals(call.name)) {
				m.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "reequip",
					"(Lnet/minecraft/item/Item;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;Z)Z", false));
				return true;
			}
			if (reflected && "callBoolean".equals(call.name) && call.owner.endsWith("/Reflector")
				&& call.desc.equals("(Ljava/lang/Object;L" + call.owner + "Method;[Ljava/lang/Object;)Z")) {
				m.instructions.set(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "reequipArgs",
					"(Ljava/lang/Object;Ljava/lang/Object;[Ljava/lang/Object;)Z", false));
				return true;
			}
		}
		return false;
	}

	/**
	 * In drawChat, the colour of the first {@code drawRect} (each line's dark background) goes
	 * through {@code Hooks.chatBackground}, and {@code drawStringWithShadow} through {@code Hooks.chatText}.
	 */
	private static boolean chatLook(MethodNode m) {
		boolean background = false;
		boolean text = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode)) {
				continue;
			}
			MethodInsnNode call = (MethodInsnNode) insn;
			if (!background && isMethod(call, "func_73734_a", "drawRect", "(IIIII)V")) {
				m.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "chatBackground", "(I)I", false));
				background = true;
			} else if (isMethod(call, "func_175063_a", "drawStringWithShadow", "(Ljava/lang/String;FFI)I")) {
				AbstractInsnNode replaced = new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "chatText",
					"(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;FFI)I", false);
				m.instructions.set(call, replaced);
				insn = replaced;
				text = true;
			}
		}
		return background && text;
	}

	/**
	 * renderPlayerlist: {@code Hooks.tabBegin(width)} first and {@code Hooks.tabEnd()} before
	 * returning, rectangle colours through {@code Hooks.tabRect}, the header and footer fields
	 * through {@code Hooks.tabHeaderFooter}, and text through {@code Hooks.tabText}.
	 */
	private static boolean tabLook(MethodNode m) {
		int rects = 0;
		int fields = 0;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.GETFIELD
				&& "Lnet/minecraft/util/IChatComponent;".equals(((FieldInsnNode) insn).desc)) {
				String name = ((FieldInsnNode) insn).name;
				boolean header = "field_175256_i".equals(name) || "header".equals(name);
				if (!header && !"field_175255_h".equals(name) && !"footer".equals(name)) {
					continue;
				}
				InsnList call = new InsnList();
				call.add(new InsnNode(header ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabHeaderFooter",
					"(Lnet/minecraft/util/IChatComponent;Z)Lnet/minecraft/util/IChatComponent;", false));
				m.instructions.insert(insn, call);
				fields++;
			} else if (insn instanceof MethodInsnNode) {
				MethodInsnNode call = (MethodInsnNode) insn;
				if (isMethod(call, "func_73734_a", "drawRect", "(IIIII)V")) {
					m.instructions.insertBefore(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabRect", "(I)I", false));
					rects++;
				} else if (isMethod(call, "func_175063_a", "drawStringWithShadow", "(Ljava/lang/String;FFI)I")) {
					AbstractInsnNode replaced = new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabText",
						"(Lnet/minecraft/client/gui/FontRenderer;Ljava/lang/String;FFI)I", false);
					m.instructions.set(call, replaced);
					insn = replaced;
				}
			}
		}
		if (rects == 0 || fields == 0) {
			return false;
		}
		beforeReturns(m, Opcodes.RETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabEnd", "()V", false));
		InsnList head = new InsnList();
		head.add(new VarInsnNode(Opcodes.ILOAD, 1));
		head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "tabBegin", "(I)V", false));
		m.instructions.insert(head);
		return true;
	}

	/** At the start of getLook: {@code Hooks.look(this, partialTicks)} returns a direction to use instead, or null. */
	private static boolean lookFix(MethodNode m) {
		InsnList head = new InsnList();
		head.add(new VarInsnNode(Opcodes.ALOAD, 0));
		head.add(new VarInsnNode(Opcodes.FLOAD, 1));
		head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "look",
			"(Lnet/minecraft/entity/EntityLivingBase;F)Lnet/minecraft/util/Vec3;", false));
		head.add(new InsnNode(Opcodes.DUP));
		LabelNode vanilla = new LabelNode();
		head.add(new JumpInsnNode(Opcodes.IFNULL, vanilla));
		head.add(new InsnNode(Opcodes.ARETURN));
		head.add(vanilla);
		head.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] {"net/minecraft/util/Vec3"}));
		head.add(new InsnNode(Opcodes.POP));
		m.instructions.insert(head);
		return true;
	}

	/** The hurt tint's four components (1, 0, 0, 0.3) each go through {@code Hooks.hitColor(value, index)}. */
	private static boolean hitColor(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof LdcInsnNode) || !Float.valueOf(0.3f).equals(((LdcInsnNode) insn).cst)) {
				continue;
			}
			// walk back over the three earlier puts to the 1.0F that starts the colour
			AbstractInsnNode[] consts = new AbstractInsnNode[4];
			consts[3] = insn;
			AbstractInsnNode n = insn;
			for (int i = 2; i >= 0; i--) {
				n = n.getPrevious();
				while (n != null && !isFloatConst(n)) {
					n = n.getPrevious();
				}
				if (n == null) {
					return false;
				}
				consts[i] = n;
			}
			if (consts[0].getOpcode() != Opcodes.FCONST_1 || consts[1].getOpcode() != Opcodes.FCONST_0 || consts[2].getOpcode() != Opcodes.FCONST_0) {
				return false;
			}
			for (int i = 0; i < 4; i++) {
				InsnList call = new InsnList();
				call.add(new IntInsnNode(Opcodes.BIPUSH, i));
				call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "hitColor", "(FI)F", false));
				m.instructions.insert(consts[i], call);
			}
			return true;
		}
		return false;
	}

	private static boolean isFloatConst(AbstractInsnNode n) {
		int op = n.getOpcode();
		return op == Opcodes.FCONST_0 || op == Opcodes.FCONST_1 || op == Opcodes.FCONST_2
			|| n instanceof LdcInsnNode && ((LdcInsnNode) n).cst instanceof Float;
	}

	private static AbstractInsnNode nextReal(AbstractInsnNode insn) {
		AbstractInsnNode n = insn.getNext();
		while (n != null && n.getOpcode() < 0) {
			n = n.getNext();
		}
		return n;
	}

	private static boolean isMethod(MethodInsnNode insn, String srg, String mcp, String desc) {
		return desc.equals(insn.desc) && (srg.equals(insn.name) || mcp.equals(insn.name));
	}
}
