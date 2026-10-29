package gg.rago.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Routes the result of {@code Minecraft.getLimitFramerate()} through
 * {@code gg.rago.optimize.FrameLimit.limit(int)}. Runs after Forge's deobfuscation, so the method
 * has its SRG name in a normal game and its MCP name in the dev environment. If the method is not
 * found the class is left untouched and the game keeps vanilla's caps.
 */
public final class RagoTransformer implements IClassTransformer {
	private static final Logger LOG = LogManager.getLogger("Rago");
	private static final String HOOK_OWNER = "gg/rago/optimize/FrameLimit";

	@Override
	public byte[] transform(String name, String transformedName, byte[] bytes) {
		if (bytes == null || !"net.minecraft.client.Minecraft".equals(transformedName)) {
			return bytes;
		}
		try {
			ClassNode node = new ClassNode();
			new ClassReader(bytes).accept(node, 0);
			int patched = 0;
			for (MethodNode m : node.methods) {
				if ("()I".equals(m.desc) && ("func_90020_K".equals(m.name) || "getLimitFramerate".equals(m.name))) {
					for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
						if (insn.getOpcode() == Opcodes.IRETURN) {
							m.instructions.insertBefore(insn, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, "limit", "(I)I", false));
							patched++;
						}
					}
				}
			}
			if (patched == 0) {
				LOG.warn("getLimitFramerate not found; menu and unfocused FPS caps stay vanilla");
				return bytes;
			}
			ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
			node.accept(writer);
			LOG.info("Frame-rate cap hook installed");
			return writer.toByteArray();
		} catch (RuntimeException e) {
			LOG.error("Could not patch Minecraft for the frame-rate cap; keeping vanilla behaviour", e);
			return bytes;
		}
	}
}
