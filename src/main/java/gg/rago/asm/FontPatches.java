package gg.rago.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The changes to FontRenderer for PolyPatcher's Optimized Font Renderer and Cache Font Data; what
 * each one does is explained in {@code gg.rago.optimize.FontBatch}. They fit the game's font and
 * OptiFine's copy of it: the steps OptiFine lays out otherwise are changed only where OptiFine's
 * code is found, the game's own code already doing what PolyPatcher does.
 */
final class FontPatches {
	private static final String BATCH = "gg/rago/optimize/FontBatch";
	private static final String GL11 = "org/lwjgl/opengl/GL11";
	private static final String FONT = "net/minecraft/client/gui/FontRenderer";

	private FontPatches() {
	}

	private static MethodInsnNode batch(String name, String desc) {
		return new MethodInsnNode(Opcodes.INVOKESTATIC, BATCH, name, desc, false);
	}

	private static boolean isPosX(AbstractInsnNode insn) {
		return insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTFIELD && "F".equals(((FieldInsnNode) insn).desc)
			&& ("field_78295_j".equals(((FieldInsnNode) insn).name) || "posX".equals(((FieldInsnNode) insn).name));
	}

	private static boolean isCall(AbstractInsnNode insn, int opcode, String owner, String name, String desc) {
		return insn instanceof MethodInsnNode && insn.getOpcode() == opcode && owner.equals(((MethodInsnNode) insn).owner)
			&& name.equals(((MethodInsnNode) insn).name) && desc.equals(((MethodInsnNode) insn).desc);
	}

	/** renderStringAtPos: the string's characters go into one draw ({@code FontBatch.start}, then {@code finish} at each return). */
	static boolean stringBatch(MethodNode m) {
		List<AbstractInsnNode> returns = new ArrayList<>();
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.RETURN) {
				returns.add(insn);
			}
		}
		if (returns.isEmpty()) {
			return false;
		}
		for (AbstractInsnNode ret : returns) {
			m.instructions.insertBefore(ret, batch("finish", "()V"));
		}
		InsnList head = new InsnList();
		head.add(new VarInsnNode(Opcodes.ALOAD, 0));
		head.add(batch("start", "(L" + FONT + ";)V"));
		m.instructions.insert(head);
		return true;
	}

	/** renderDefaultChar, renderUnicodeChar: the glyph's immediate-mode calls go to the batch. */
	static boolean glyphCalls(MethodNode m) {
		int begins = 0;
		int ends = 0;
		int vertices = 0;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			MethodInsnNode call = null;
			if (isCall(insn, Opcodes.INVOKESTATIC, GL11, "glBegin", "(I)V")) {
				call = batch("begin", "(I)V");
				begins++;
			} else if (isCall(insn, Opcodes.INVOKESTATIC, GL11, "glTexCoord2f", "(FF)V")) {
				call = batch("tex", "(FF)V");
			} else if (isCall(insn, Opcodes.INVOKESTATIC, GL11, "glVertex3f", "(FFF)V")) {
				call = batch("vertex", "(FFF)V");
				vertices++;
			} else if (isCall(insn, Opcodes.INVOKESTATIC, GL11, "glEnd", "()V")) {
				call = batch("end", "()V");
				ends++;
			}
			if (call != null) {
				m.instructions.set(insn, call);
				insn = call;
			}
		}
		return begins == 1 && ends == 1 && vertices >= 3;
	}

	/** bindTexture: the characters gathered are drawn before the texture changes. */
	static boolean textureChange(MethodNode m) {
		InsnList head = new InsnList();
		head.add(new VarInsnNode(Opcodes.ALOAD, 1));
		head.add(batch("texture", "(Lnet/minecraft/util/ResourceLocation;)V"));
		m.instructions.insert(head);
		return true;
	}

	/** setColor: the colour of the characters that follow, for the batch. */
	static boolean colorChange(MethodNode m) {
		InsnList head = new InsnList();
		for (int i = 1; i <= 4; i++) {
			head.add(new VarInsnNode(Opcodes.FLOAD, i));
		}
		head.add(batch("color", "(FFFF)V"));
		m.instructions.insert(head);
		return true;
	}

	/** doDraw: the characters gathered are drawn before an underline or strikethrough (with the Tessellator). */
	static boolean linesAfterCharacters(MethodNode m) {
		int found = 0;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode && insn.getOpcode() == Opcodes.INVOKESTATIC
				&& "net/minecraft/client/renderer/Tessellator".equals(((MethodInsnNode) insn).owner)
				&& ("func_178181_a".equals(((MethodInsnNode) insn).name) || "getInstance".equals(((MethodInsnNode) insn).name))) {
				m.instructions.insertBefore(insn, batch("flush", "()V"));
				found++;
			}
		}
		return found > 0;
	}

	/**
	 * doDraw: OptiFine moves on by the exact width ({@code posX += f}); that f goes through
	 * {@code FontBatch.advance}. The game's own code cuts it already ({@code posX += (int) f}).
	 */
	static boolean wholeSteps(MethodNode m) {
		AbstractInsnNode last = null;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (isPosX(insn)) {
				last = insn;
			}
		}
		if (last == null || last.getPrevious() == null || last.getPrevious().getOpcode() != Opcodes.FADD) {
			return false;
		}
		AbstractInsnNode width = last.getPrevious().getPrevious();
		if (width instanceof VarInsnNode && width.getOpcode() == Opcodes.FLOAD && ((VarInsnNode) width).var == 1) {
			m.instructions.insert(width, batch("advance", "(F)F"));
			return true;
		}
		return width != null && width.getOpcode() == Opcodes.I2F && width.getPrevious() != null
			&& width.getPrevious().getOpcode() == Opcodes.F2I;
	}

	/**
	 * renderStringAtPos: bold adds OptiFine's offset to the width ({@code f += f1}: half a unit for
	 * a unicode glyph); that offset goes through {@code FontBatch.boldAdvance}. The game's own code
	 * adds one ({@code ++f}).
	 */
	static boolean boldStep(MethodNode m) {
		AbstractInsnNode match = null;
		int matches = 0;
		boolean vanilla = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.FADD || !(insn.getNext() instanceof VarInsnNode) || insn.getNext().getOpcode() != Opcodes.FSTORE) {
				continue;
			}
			AbstractInsnNode added = insn.getPrevious();
			AbstractInsnNode to = added == null ? null : added.getPrevious();
			int slot = ((VarInsnNode) insn.getNext()).var;
			if (!(to instanceof VarInsnNode) || to.getOpcode() != Opcodes.FLOAD || ((VarInsnNode) to).var != slot) {
				continue;
			}
			if (added.getOpcode() == Opcodes.FCONST_1) {
				vanilla = true;
			} else if (added instanceof VarInsnNode && added.getOpcode() == Opcodes.FLOAD) {
				match = added;
				matches++;
			}
		}
		if (matches == 1) {
			m.instructions.insert(match, batch("boldAdvance", "(F)F"));
			return true;
		}
		return matches == 0 && vanilla;
	}

	/**
	 * renderChar: OptiFine draws a space or no-break space with {@code charWidthFloat[ch]}, so a
	 * no-break space with the 160th glyph's width; the index goes through
	 * {@code FontBatch.spaceWidthIndex}. The game's own code has no such width.
	 */
	static boolean noBreakSpace(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.GETFIELD && "charWidthFloat".equals(((FieldInsnNode) insn).name)) {
				AbstractInsnNode index = insn.getNext();
				if (index instanceof VarInsnNode && index.getOpcode() == Opcodes.ILOAD && ((VarInsnNode) index).var == 1
					&& index.getNext() != null && index.getNext().getOpcode() == Opcodes.FALOAD) {
					m.instructions.insert(index, batch("spaceWidthIndex", "(I)I"));
					return true;
				}
				return false;
			}
		}
		return true;
	}

	/**
	 * getStringWidth: OptiFine rounds the summed widths ({@code Math.round}); the sum goes through
	 * {@code FontBatch.measured}. The game's own code sums whole widths.
	 */
	static boolean wholeWidth(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (isCall(insn, Opcodes.INVOKESTATIC, "java/lang/Math", "round", "(F)I")) {
				MethodInsnNode call = batch("measured", "(F)I");
				m.instructions.set(insn, call);
				insn = call;
			}
		}
		return true;
	}

	/** The font's searches of its glyph page and lower-case copies of the string, through {@code FontBatch}. */
	static boolean lookups(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			MethodInsnNode call = null;
			if (isCall(insn, Opcodes.INVOKEVIRTUAL, "java/lang/String", "indexOf", "(I)I")) {
				call = batch("indexOf", "(Ljava/lang/String;I)I");
			} else if (isCall(insn, Opcodes.INVOKEVIRTUAL, "java/lang/String", "toLowerCase", "(Ljava/util/Locale;)Ljava/lang/String;")) {
				call = batch("lower", "(Ljava/lang/String;Ljava/util/Locale;)Ljava/lang/String;");
			}
			if (call != null) {
				m.instructions.set(insn, call);
				insn = call;
			}
		}
		return true;
	}

	/**
	 * getStringWidth, after the nick hider's change to the text: a kept width is returned at once;
	 * otherwise the width measured is kept on its way out.
	 */
	static boolean keptWidths(MethodNode m) {
		List<AbstractInsnNode> returns = new ArrayList<>();
		AbstractInsnNode after = null;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.IRETURN) {
				returns.add(insn);
			}
			if (after == null && isCall(insn, Opcodes.INVOKESTATIC, "gg/rago/hooks/Hooks", "nick", "(Ljava/lang/String;)Ljava/lang/String;")
				&& insn.getNext() instanceof VarInsnNode && insn.getNext().getOpcode() == Opcodes.ASTORE && ((VarInsnNode) insn.getNext()).var == 1) {
				after = insn.getNext();
			}
		}
		if (returns.isEmpty()) {
			return false;
		}
		for (AbstractInsnNode ret : returns) {
			InsnList keep = new InsnList();
			keep.add(new VarInsnNode(Opcodes.ALOAD, 0));
			keep.add(new VarInsnNode(Opcodes.ALOAD, 1));
			keep.add(batch("keepWidth", "(IL" + FONT + ";Ljava/lang/String;)I"));
			m.instructions.insertBefore(ret, keep);
		}
		InsnList head = new InsnList();
		head.add(new VarInsnNode(Opcodes.ALOAD, 0));
		head.add(new VarInsnNode(Opcodes.ALOAD, 1));
		head.add(batch("keptWidth", "(L" + FONT + ";Ljava/lang/String;)I"));
		head.add(new InsnNode(Opcodes.DUP));
		LabelNode measure = new LabelNode();
		head.add(new JumpInsnNode(Opcodes.IFLT, measure));
		head.add(new InsnNode(Opcodes.IRETURN));
		head.add(measure);
		head.add(new FrameNode(Opcodes.F_SAME1, 0, null, 1, new Object[] {Opcodes.INTEGER}));
		head.add(new InsnNode(Opcodes.POP));
		if (after != null) {
			m.instructions.insert(after, head);
		} else {
			m.instructions.insert(head);
		}
		return true;
	}

	/** onResourceManagerReload: the font may have changed, the kept widths are dropped. */
	static boolean fontReloaded(MethodNode m) {
		m.instructions.insert(batch("fontChanged", "()V"));
		return true;
	}
}
