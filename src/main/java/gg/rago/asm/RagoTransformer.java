package gg.rago.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
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
 * <p>None of these change what is sent to the server: they only affect drawing and what the
 * client keeps in memory.
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
		add("net.minecraft.client.renderer.entity.layers.LayerArmorBase", new Patch("1.7 armour damage tint",
			"func_177142_b", "shouldCombineTextures", "()Z",
			m -> beforeReturns(m, Opcodes.IRETURN, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "armorDamageTint", "(Z)Z", false))));
		add("net.minecraft.client.gui.GuiPlayerTabOverlay", new Patch("ping numbers in the tab list",
			"func_175245_a", "drawPing", "(IIILnet/minecraft/client/network/NetworkPlayerInfo;)V",
			m -> returnIf(m, "replaceTabPing", "(IIILnet/minecraft/client/network/NetworkPlayerInfo;)Z",
				new int[] {Opcodes.ILOAD, 1, Opcodes.ILOAD, 2, Opcodes.ILOAD, 3, Opcodes.ALOAD, 4}, -1, Opcodes.RETURN)));
		add("net.minecraft.client.gui.GuiNewChat", new Patch("longer chat history",
			"func_146237_a", "setChatLine", "(Lnet/minecraft/util/IChatComponent;IIZ)V",
			RagoTransformer::chatLimit));
		add("net.minecraft.client.renderer.ItemRenderer", new Patch("1.7 block-hit animation",
			"func_78440_a", "renderItemInFirstPerson", "(F)V",
			RagoTransformer::blockHit));
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
	 * While blocking, vanilla 1.8 calls {@code transformFirstPersonItem(equip, 0.0F)} before
	 * {@code doBlockTransformations()}; 1.7 passed the swing progress there, which is what makes a
	 * block-hit visible. The 0.0F becomes {@code Hooks.blockHitSwing(swing)}.
	 */
	private static boolean blockHit(MethodNode m) {
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
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode && isMethod((MethodInsnNode) insn, "func_178096_b", "transformFirstPersonItem", "(FF)V")) {
				AbstractInsnNode zero = insn.getPrevious();
				AbstractInsnNode next = nextReal(insn);
				if (next != null && next.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) next).var == 0) {
					next = nextReal(next); // this.doBlockTransformations()
				}
				if (zero != null && zero.getOpcode() == Opcodes.FCONST_0 && next instanceof MethodInsnNode
					&& isMethod((MethodInsnNode) next, "func_178103_d", "doBlockTransformations", "()V")) {
					InsnList swing = new InsnList();
					swing.add(new VarInsnNode(Opcodes.FLOAD, swingVar));
					swing.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOKS, "blockHitSwing", "(F)F", false));
					m.instructions.insertBefore(zero, swing);
					m.instructions.remove(zero);
					return true;
				}
			}
		}
		return false;
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
