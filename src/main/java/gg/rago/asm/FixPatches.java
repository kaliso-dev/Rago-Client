package gg.rago.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * The changes to game methods behind the fixes PolyPatcher makes ({@code gg.rago.fixes.FixHooks}
 * says what each fix does). Like the other patches, each one looks for the code it changes and
 * reports whether it found all of it; a patch that finds only part leaves the method as it was.
 */
final class FixPatches {
	static final String FIX = "gg/rago/fixes/FixHooks";

	private FixPatches() {
	}

	private static MethodInsnNode fix(String name, String desc) {
		return new MethodInsnNode(Opcodes.INVOKESTATIC, FIX, name, desc, false);
	}

	private static boolean isCall(AbstractInsnNode insn, String srg, String mcp, String desc) {
		return insn instanceof MethodInsnNode && RagoTransformer.isMethod((MethodInsnNode) insn, srg, mcp, desc);
	}

	/** Replaces every call of srg/mcp+desc with {@code FixHooks.hook}, which takes the same values (the receiver first). */
	static boolean replaceCalls(MethodNode m, String srg, String mcp, String desc, String hook, String hookDesc) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (isCall(insn, srg, mcp, desc)) {
				MethodInsnNode call = fix(hook, hookDesc);
				m.instructions.set(insn, call);
				insn = call;
				found = true;
			}
		}
		return found;
	}

	/** At the start of the method: {@code FixHooks.hook(this)}. */
	static boolean headWithThis(MethodNode m, String hook, String desc) {
		InsnList head = new InsnList();
		head.add(new VarInsnNode(Opcodes.ALOAD, 0));
		head.add(fix(hook, desc));
		m.instructions.insert(head);
		return true;
	}

	// ---- F5 shaders -----------------------------------------------------------------------------

	private static final String LOAD_SHADER = "(Lnet/minecraft/entity/Entity;)V";

	/** Minecraft.runTick: the F5 key's two loadEntityShader calls go through {@code FixHooks.perspectiveShader}. */
	static boolean keepShaders(MethodNode m) {
		return replaceCalls(m, "func_175066_a", "loadEntityShader", LOAD_SHADER, "perspectiveShader",
			"(Lnet/minecraft/client/renderer/EntityRenderer;Lnet/minecraft/entity/Entity;)V");
	}

	/** Minecraft.setRenderViewEntity: the entity given to loadEntityShader goes through {@code FixHooks.viewShader}. */
	static boolean spectatorShader(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (isCall(insn, "func_175066_a", "loadEntityShader", LOAD_SHADER)) {
				m.instructions.insertBefore(insn, fix("viewShader", "(Lnet/minecraft/entity/Entity;)Lnet/minecraft/entity/Entity;"));
				return true;
			}
		}
		return false;
	}

	// ---- culling fix ----------------------------------------------------------------------------

	/**
	 * RenderGlobal.setupTerrain: {@code FixHooks.cullingFrame} first; the side dropped from your own
	 * chunk ({@code Set.remove} after the view's facing) through {@code cullingDropSide}; the walk to
	 * a neighbour ({@code setFrameIndex} after {@code getRenderChunkOffset}) through
	 * {@code cullingEnter}, with the direction walked; the "can you see through this chunk" check
	 * through {@code cullingVisible}, with the chunk.
	 */
	static boolean cullingFix(MethodNode m) {
		// the walk to a neighbour: getRenderChunkOffset(position, chunk, direction, ...) gives the
		// locals of the chunk walked from and of the direction (OptiFine adds two arguments)
		int chunk = -1;
		int direction = -1;
		AbstractInsnNode offset = null;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null && offset == null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode && ("func_181562_a".equals(((MethodInsnNode) insn).name)
				|| "getRenderChunkOffset".equals(((MethodInsnNode) insn).name))
				&& ((MethodInsnNode) insn).desc.startsWith("(Lnet/minecraft/util/BlockPos;Lnet/minecraft/client/renderer/chunk/RenderChunk;Lnet/minecraft/util/EnumFacing;")) {
				AbstractInsnNode[] args = arguments(insn, org.objectweb.asm.Type.getArgumentTypes(((MethodInsnNode) insn).desc).length);
				if (args == null || args[1].getOpcode() != Opcodes.ALOAD || args[2].getOpcode() != Opcodes.ALOAD) {
					return false;
				}
				chunk = ((VarInsnNode) args[1]).var;
				direction = ((VarInsnNode) args[2]).var;
				offset = insn;
			}
		}
		if (offset == null) {
			return false;
		}
		boolean dropped = false;
		boolean entered = false;
		boolean visible = false;
		boolean afterOffset = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn == offset) {
				afterOffset = true;
			} else if (insn.getOpcode() == Opcodes.INVOKEINTERFACE && "java/util/Set".equals(((MethodInsnNode) insn).owner)
				&& "remove".equals(((MethodInsnNode) insn).name)) {
				MethodInsnNode before = RagoTransformer.previousCall(insn, 5);
				if (before != null && RagoTransformer.isMethod(before, "func_176734_d", "getOpposite", "()Lnet/minecraft/util/EnumFacing;")) {
					MethodInsnNode call = fix("cullingDropSide", "(Ljava/util/Set;Ljava/lang/Object;)Z");
					m.instructions.set(insn, call);
					insn = call;
					dropped = true;
				}
			} else if (afterOffset && !entered && isCall(insn, "func_178577_a", "setFrameIndex", "(I)Z")) {
				InsnList call = new InsnList();
				call.add(new VarInsnNode(Opcodes.ALOAD, direction));
				MethodInsnNode hook = fix("cullingEnter", "(Lnet/minecraft/client/renderer/chunk/RenderChunk;ILnet/minecraft/util/EnumFacing;)Z");
				call.add(hook);
				m.instructions.insertBefore(insn, call);
				m.instructions.remove(insn);
				insn = hook;
				entered = true;
			} else if (!visible && isCall(insn, "func_178495_a", "isVisible", "(Lnet/minecraft/util/EnumFacing;Lnet/minecraft/util/EnumFacing;)Z")) {
				InsnList call = new InsnList();
				call.add(new VarInsnNode(Opcodes.ALOAD, chunk));
				MethodInsnNode hook = fix("cullingVisible", "(Lnet/minecraft/client/renderer/chunk/CompiledChunk;Lnet/minecraft/util/EnumFacing;"
					+ "Lnet/minecraft/util/EnumFacing;Lnet/minecraft/client/renderer/chunk/RenderChunk;)Z");
				call.add(hook);
				m.instructions.insertBefore(insn, call);
				m.instructions.remove(insn);
				insn = hook;
				visible = true;
			}
		}
		if (!dropped || !entered || !visible) {
			return false;
		}
		InsnList head = new InsnList();
		head.add(new VarInsnNode(Opcodes.ALOAD, 0));
		head.add(new VarInsnNode(Opcodes.ILOAD, 5));
		head.add(fix("cullingFrame", "(Lnet/minecraft/client/renderer/RenderGlobal;I)V"));
		m.instructions.insert(head);
		return true;
	}

	/** The {@code count} instructions that load a call's arguments, when each is a single load; else null. */
	private static AbstractInsnNode[] arguments(AbstractInsnNode call, int count) {
		AbstractInsnNode[] args = new AbstractInsnNode[count];
		AbstractInsnNode n = call;
		for (int i = count - 1; i >= 0; i--) {
			n = RagoTransformer.previousReal(n);
			if (n == null || n.getOpcode() < Opcodes.ILOAD || n.getOpcode() > Opcodes.ALOAD) {
				return null;
			}
			args[i] = n;
		}
		return args;
	}

	// ---- sky ------------------------------------------------------------------------------------

	/**
	 * In renderSky, what follows {@code getHorizon()}: the {@code dsub} that takes the horizon from
	 * the height of your eyes, or the {@code FixHooks.skyHeight} call that replaced it; else null.
	 */
	private static AbstractInsnNode horizonSub(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode && "()D".equals(((MethodInsnNode) insn).desc)
				&& ("func_72919_O".equals(((MethodInsnNode) insn).name) || "getHorizon".equals(((MethodInsnNode) insn).name))) {
				AbstractInsnNode sub = RagoTransformer.nextReal(insn);
				if (sub != null && (sub.getOpcode() == Opcodes.DSUB || sub instanceof MethodInsnNode
					&& FIX.equals(((MethodInsnNode) sub).owner) && "skyHeight".equals(((MethodInsnNode) sub).name))) {
					return sub;
				}
			}
		}
		return null;
	}

	/** The local that keeps "eyes height minus horizon" in renderSky, or -1. */
	private static int horizonLocal(MethodNode m) {
		AbstractInsnNode sub = horizonSub(m);
		AbstractInsnNode store = sub == null ? null : RagoTransformer.nextReal(sub);
		return store != null && store.getOpcode() == Opcodes.DSTORE ? ((VarInsnNode) store).var : -1;
	}

	/** renderSky: "eyes height minus horizon" is worked out by {@code FixHooks.skyHeight(eyes, horizon)}. */
	static boolean skyHeight(MethodNode m) {
		AbstractInsnNode sub = horizonSub(m);
		if (sub == null || sub.getOpcode() != Opcodes.DSUB || horizonLocal(m) < 0) {
			return false;
		}
		m.instructions.set(sub, fix("skyHeight", "(DD)D"));
		return true;
	}

	/** renderSky: the comparison of that height with 0 goes through {@code FixHooks.voidBox}. */
	static boolean voidBox(MethodNode m) {
		int horizon = horizonLocal(m);
		if (horizon < 0) {
			return false;
		}
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.DLOAD && ((VarInsnNode) insn).var == horizon) {
				AbstractInsnNode zero = RagoTransformer.nextReal(insn);
				AbstractInsnNode compare = zero == null ? null : RagoTransformer.nextReal(zero);
				if (zero != null && zero.getOpcode() == Opcodes.DCONST_0 && compare != null
					&& (compare.getOpcode() == Opcodes.DCMPG || compare.getOpcode() == Opcodes.DCMPL)) {
					m.instructions.insert(compare, fix("voidBox", "(I)I"));
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * RenderGlobal.renderSky(WorldRenderer, float, boolean), which builds the flat planes of the sky:
	 * OptiFine works their size out from its {@code renderDistance} field and keeps it in a local,
	 * which then goes through {@code FixHooks.skyPlaneSize}. Vanilla's planes have a fixed size, the
	 * one the fix gives back, so there is nothing to change without OptiFine.
	 */
	static boolean skyPlaneSize(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.GETFIELD || !"renderDistance".equals(((FieldInsnNode) insn).name)
				|| !"I".equals(((FieldInsnNode) insn).desc)) {
				continue;
			}
			// the arithmetic that follows, up to the local the size is kept in
			for (AbstractInsnNode next = insn.getNext(); next != null; next = next.getNext()) {
				int op = next.getOpcode();
				if (op == Opcodes.ISTORE) {
					m.instructions.insertBefore(next, fix("skyPlaneSize", "(I)I"));
					return true;
				}
				if (op != -1 && op != Opcodes.BIPUSH && op != Opcodes.SIPUSH && (op < Opcodes.ICONST_M1 || op > Opcodes.ICONST_5)
					&& op != Opcodes.IADD && op != Opcodes.ISUB && op != Opcodes.IMUL && op != Opcodes.IDIV) {
					return false;
				}
			}
			return false;
		}
		// vanilla: the loops run from -384 to 384 (the fix's size); nothing to do
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.SIPUSH && ((IntInsnNode) insn).operand == -384) {
				return true;
			}
		}
		return false;
	}

	// ---- items ----------------------------------------------------------------------------------

	/**
	 * RenderItem.renderQuads: after each quad is drawn (Forge's LightUtil.renderQuadColor, or
	 * renderQuad with OptiFine), {@code FixHooks.itemQuadNormal(renderer, quad)}.
	 */
	static boolean itemLighting(MethodNode m) {
		String desc = "(Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/client/renderer/block/model/BakedQuad;I)V";
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (!(insn instanceof MethodInsnNode) || !desc.equals(((MethodInsnNode) insn).desc)) {
				continue;
			}
			MethodInsnNode call = (MethodInsnNode) insn;
			boolean forge = "net/minecraftforge/client/model/pipeline/LightUtil".equals(call.owner) && "renderQuadColor".equals(call.name);
			boolean own = "net/minecraft/client/renderer/entity/RenderItem".equals(call.owner)
				&& ("func_175033_a".equals(call.name) || "renderQuad".equals(call.name));
			if (!forge && !own) {
				continue;
			}
			AbstractInsnNode[] args = arguments(call, 3);
			if (args == null || args[0].getOpcode() != Opcodes.ALOAD || args[1].getOpcode() != Opcodes.ALOAD) {
				return false;
			}
			InsnList after = new InsnList();
			after.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) args[0]).var));
			after.add(new VarInsnNode(Opcodes.ALOAD, ((VarInsnNode) args[1]).var));
			after.add(fix("itemQuadNormal", "(Lnet/minecraft/client/renderer/WorldRenderer;Lnet/minecraft/client/renderer/block/model/BakedQuad;)V"));
			m.instructions.insert(call, after);
			found = true;
		}
		return found;
	}

	/** RenderItem.renderItemModelTransform: ForgeHooksClient.handleCameraTransforms goes through {@code FixHooks.cameraTransforms}. */
	static boolean negativeScale(MethodNode m) {
		String desc = "(Lnet/minecraft/client/resources/model/IBakedModel;"
			+ "Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)Lnet/minecraft/client/resources/model/IBakedModel;";
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn instanceof MethodInsnNode && "net/minecraftforge/client/ForgeHooksClient".equals(((MethodInsnNode) insn).owner)
				&& "handleCameraTransforms".equals(((MethodInsnNode) insn).name) && desc.equals(((MethodInsnNode) insn).desc)) {
				MethodInsnNode call = fix("cameraTransforms", desc);
				m.instructions.set(insn, call);
				insn = call;
				found = true;
			} else if (insn instanceof MethodInsnNode && "net/optifine/reflect/Reflector".equals(((MethodInsnNode) insn).owner)
				&& "call".equals(((MethodInsnNode) insn).name)
				&& "(Lnet/optifine/reflect/ReflectorMethod;[Ljava/lang/Object;)Ljava/lang/Object;".equals(((MethodInsnNode) insn).desc)
				&& loadsReflector(insn, "ForgeHooksClient_handleCameraTransforms")) {
				// OptiFine calls Forge's method through its reflection helper
				MethodInsnNode call = fix("reflectedCameraTransforms", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
				m.instructions.set(insn, call);
				insn = call;
				found = true;
			}
		}
		return found;
	}

	/** Whether OptiFine's reflection handle {@code name} is read in the 12 instructions before this call. */
	private static boolean loadsReflector(AbstractInsnNode call, String name) {
		AbstractInsnNode n = call;
		for (int i = 0; i < 12 && n != null; i++) {
			n = RagoTransformer.previousReal(n);
			if (n != null && n.getOpcode() == Opcodes.GETSTATIC && name.equals(((FieldInsnNode) n).name)
				&& "net/optifine/reflect/Reflector".equals(((FieldInsnNode) n).owner)) {
				return true;
			}
		}
		return false;
	}

	// ---- tab list -------------------------------------------------------------------------------

	/**
	 * GuiPlayerTabOverlay.renderPlayerlist: {@code player != null && player.isWearing(HAT)} becomes
	 * {@code FixHooks.tabHat(player, HAT)}.
	 */
	static boolean tabHat(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.GETSTATIC || !"HAT".equals(((FieldInsnNode) insn).name)
				|| !"net/minecraft/entity/player/EnumPlayerModelParts".equals(((FieldInsnNode) insn).owner)) {
				continue;
			}
			AbstractInsnNode wearing = RagoTransformer.nextReal(insn);
			AbstractInsnNode player = RagoTransformer.previousReal(insn);
			AbstractInsnNode nullCheck = player == null ? null : RagoTransformer.previousReal(player);
			AbstractInsnNode playerAgain = nullCheck == null ? null : RagoTransformer.previousReal(nullCheck);
			if (!isCall(wearing, "func_175148_a", "isWearing", "(Lnet/minecraft/entity/player/EnumPlayerModelParts;)Z")
				|| player.getOpcode() != Opcodes.ALOAD || nullCheck.getOpcode() != Opcodes.IFNULL
				|| playerAgain == null || playerAgain.getOpcode() != Opcodes.ALOAD
				|| ((VarInsnNode) playerAgain).var != ((VarInsnNode) player).var) {
				return false;
			}
			m.instructions.remove(playerAgain);
			m.instructions.remove(nullCheck);
			m.instructions.set(wearing, fix("tabHat",
				"(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/entity/player/EnumPlayerModelParts;)Z"));
			return true;
		}
		return false;
	}

	// ---- leaks and logs -------------------------------------------------------------------------

	/** TextureManager.deleteTexture: {@code FixHooks.textureDeleted(this, location)} before it returns. */
	static boolean textureLeak(MethodNode m) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.RETURN) {
				InsnList call = new InsnList();
				call.add(new VarInsnNode(Opcodes.ALOAD, 0));
				call.add(new VarInsnNode(Opcodes.ALOAD, 1));
				call.add(fix("textureDeleted", "(Lnet/minecraft/client/renderer/texture/TextureManager;Lnet/minecraft/util/ResourceLocation;)V"));
				m.instructions.insertBefore(insn, call);
				found = true;
			}
		}
		return found;
	}

	/**
	 * EnchantmentHelper: its shared helper objects keep the entities and damage source of the last
	 * hit (and so the world they are in, after you leave it). Each object field the method sets on
	 * them ({@code getstatic helper; aload; putfield}) is set back to null before it returns.
	 */
	static boolean enchantmentLeak(MethodNode m) {
		List<FieldInsnNode[]> fields = new ArrayList<>();
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() != Opcodes.PUTFIELD || !((FieldInsnNode) insn).desc.startsWith("L")) {
				continue;
			}
			AbstractInsnNode value = RagoTransformer.previousReal(insn);
			AbstractInsnNode helper = value == null ? null : RagoTransformer.previousReal(value);
			if (value != null && value.getOpcode() == Opcodes.ALOAD && helper != null && helper.getOpcode() == Opcodes.GETSTATIC
				&& ((FieldInsnNode) helper).owner.startsWith("net/minecraft/enchantment/EnchantmentHelper")) {
				fields.add(new FieldInsnNode[] {(FieldInsnNode) helper, (FieldInsnNode) insn});
			}
		}
		if (fields.isEmpty()) {
			return false;
		}
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			int op = insn.getOpcode();
			if (op >= Opcodes.IRETURN && op <= Opcodes.RETURN) {
				InsnList clear = new InsnList();
				for (FieldInsnNode[] f : fields) {
					clear.add(new FieldInsnNode(Opcodes.GETSTATIC, f[0].owner, f[0].name, f[0].desc));
					clear.add(new InsnNode(Opcodes.ACONST_NULL));
					clear.add(new FieldInsnNode(Opcodes.PUTFIELD, f[1].owner, f[1].name, f[1].desc));
				}
				m.instructions.insertBefore(insn, clear);
				found = true;
			}
		}
		return found;
	}

	/** Util.runTask: the fatal log of a failed task goes through {@code FixHooks.taskError}. */
	static boolean taskErrors(MethodNode m) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.INVOKEINTERFACE && "org/apache/logging/log4j/Logger".equals(((MethodInsnNode) insn).owner)
				&& "fatal".equals(((MethodInsnNode) insn).name) && "(Ljava/lang/String;Ljava/lang/Throwable;)V".equals(((MethodInsnNode) insn).desc)) {
				MethodInsnNode call = fix("taskError", "(Lorg/apache/logging/log4j/Logger;Ljava/lang/String;Ljava/lang/Throwable;)V");
				m.instructions.set(insn, call);
				insn = call;
				found = true;
			}
		}
		return found;
	}

	/** TileEntityRendererDispatcher.renderTileEntity, first: return when {@code FixHooks.skipTileEntity(tile)}. */
	static boolean invalidTileEntities(MethodNode m) {
		InsnList head = new InsnList();
		LabelNode go = new LabelNode();
		head.add(new VarInsnNode(Opcodes.ALOAD, 1));
		head.add(fix("skipTileEntity", "(Lnet/minecraft/tileentity/TileEntity;)Z"));
		head.add(new JumpInsnNode(Opcodes.IFEQ, go));
		head.add(new InsnNode(Opcodes.RETURN));
		head.add(go);
		head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
		m.instructions.insert(head);
		return true;
	}

	// ---- light ----------------------------------------------------------------------------------

	/** The World field that holds how far the light update in progress may spread. */
	static final String LIGHT_SPREAD = "rago$lightSpread";

	/**
	 * World.checkLightFor: its first call, {@code isAreaLoaded(pos, 17, allowEmpty)}, becomes
	 * {@code FixHooks.lightSpread(this, pos, 17, allowEmpty)}, kept in {@link #LIGHT_SPREAD} and
	 * tested like the boolean it replaces (-1 means not loaded); the two distance limits of 17 (the
	 * light going down, then up) then read that field.
	 */
	static boolean lightSpread(MethodNode m) {
		AbstractInsnNode area = null;
		List<AbstractInsnNode> limits = new ArrayList<>();
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (area == null) {
				if (isCall(insn, "func_175648_a", "isAreaLoaded", "(Lnet/minecraft/util/BlockPos;IZ)Z") && pushes17(previous(previous(insn)))) {
					area = insn;
				}
			} else if (pushes17(insn) && next(insn) != null && next(insn).getOpcode() == Opcodes.IF_ICMPGE) {
				limits.add(insn);
			}
		}
		if (area == null || limits.size() != 2) {
			return false;
		}
		InsnList spread = new InsnList();
		spread.add(fix("lightSpread", "(Lnet/minecraft/world/World;Lnet/minecraft/util/BlockPos;IZ)I"));
		spread.add(new InsnNode(Opcodes.DUP));
		spread.add(new VarInsnNode(Opcodes.ALOAD, 0));
		spread.add(new InsnNode(Opcodes.SWAP));
		spread.add(new FieldInsnNode(Opcodes.PUTFIELD, "net/minecraft/world/World", LIGHT_SPREAD, "I"));
		spread.add(new InsnNode(Opcodes.ICONST_1));
		spread.add(new InsnNode(Opcodes.IADD));
		m.instructions.insert(area, spread);
		m.instructions.remove(area);
		for (AbstractInsnNode limit : limits) {
			InsnList read = new InsnList();
			read.add(new VarInsnNode(Opcodes.ALOAD, 0));
			read.add(new FieldInsnNode(Opcodes.GETFIELD, "net/minecraft/world/World", LIGHT_SPREAD, "I"));
			m.instructions.insert(limit, read);
			m.instructions.remove(limit);
		}
		return true;
	}

	/**
	 * Chunk.setBlockState: in {@code relightBlock(x, y + 1, z)}, made when a block that stops light
	 * is put at or above the column's height map, {@code y + 1} becomes {@code FixHooks.relightFrom(y)}.
	 * The other relightBlock call, for the top block taken away, has no {@code + 1} and stays.
	 */
	static boolean relightFrom(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (isCall(insn, "func_76615_h", "relightBlock", "(III)V")) {
				AbstractInsnNode z = previous(insn);
				AbstractInsnNode add = previous(z);
				AbstractInsnNode one = previous(add);
				if (z != null && z.getOpcode() == Opcodes.ILOAD && add != null && add.getOpcode() == Opcodes.IADD
					&& one != null && one.getOpcode() == Opcodes.ICONST_1) {
					m.instructions.remove(one);
					m.instructions.set(add, fix("relightFrom", "(I)I"));
					return true;
				}
			}
		}
		return false;
	}

	private static boolean pushes17(AbstractInsnNode insn) {
		return insn != null && insn.getOpcode() == Opcodes.BIPUSH && ((IntInsnNode) insn).operand == 17;
	}

	/** The instruction before, skipping labels, line numbers and frames. */
	private static AbstractInsnNode previous(AbstractInsnNode insn) {
		do {
			insn = insn == null ? null : insn.getPrevious();
		} while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	/** The instruction after, skipping labels, line numbers and frames. */
	private static AbstractInsnNode next(AbstractInsnNode insn) {
		do {
			insn = insn == null ? null : insn.getNext();
		} while (insn != null && insn.getOpcode() < 0);
		return insn;
	}

	// ---- menus ----------------------------------------------------------------------------------

	/** GuiScreenAddServer / GuiScreenServerList.actionPerformed: the address stored goes through {@code FixHooks.serverAddress}. */
	static boolean serverAddress(MethodNode m) {
		boolean found = false;
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() == Opcodes.PUTFIELD && "net/minecraft/client/multiplayer/ServerData".equals(((FieldInsnNode) insn).owner)
				&& ("field_78845_b".equals(((FieldInsnNode) insn).name) || "serverIP".equals(((FieldInsnNode) insn).name))) {
				m.instructions.insertBefore(insn, fix("serverAddress", "(Ljava/lang/String;)Ljava/lang/String;"));
				found = true;
			}
		}
		return found;
	}

	/** GuiLanguage$List.elementClicked: {@code FixHooks.languageClick()} first, refreshResources through {@code FixHooks.languageChanged}. */
	static boolean languageReload(MethodNode m) {
		if (!replaceCalls(m, "func_110436_a", "refreshResources", "()V", "languageChanged", "(Lnet/minecraft/client/Minecraft;)V")) {
			return false;
		}
		m.instructions.insert(fix("languageClick", "()V"));
		return true;
	}

	/**
	 * A sound slider's mousePressed or mouseDragged, whole, in a try/catch: an error goes to
	 * {@code FixHooks.soundMenuError} and the method returns as if nothing was clicked.
	 */
	static boolean soundSlider(MethodNode m) {
		LabelNode start = new LabelNode();
		LabelNode end = new LabelNode();
		LabelNode handler = new LabelNode();
		m.instructions.insert(start);
		m.instructions.add(end);
		m.instructions.add(handler);
		m.instructions.add(new FrameNode(Opcodes.F_FULL, 4,
			new Object[] {"net/minecraft/client/gui/GuiScreenOptionsSounds$Button", "net/minecraft/client/Minecraft", Opcodes.INTEGER, Opcodes.INTEGER},
			1, new Object[] {"java/lang/Throwable"}));
		m.instructions.add(fix("soundMenuError", "(Ljava/lang/Throwable;)V"));
		if (m.desc.endsWith(")Z")) {
			m.instructions.add(new InsnNode(Opcodes.ICONST_0));
			m.instructions.add(new InsnNode(Opcodes.IRETURN));
		} else {
			m.instructions.add(new InsnNode(Opcodes.RETURN));
		}
		m.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, "java/lang/Throwable"));
		return true;
	}

	/** EntityRenderer.renderWorldDirections: {@code FixHooks.debugCrosshair(true)} around its orientCamera call. */
	static boolean debugCrosshair(MethodNode m) {
		for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (isCall(insn, "func_78467_g", "orientCamera", "(F)V")) {
				InsnList before = new InsnList();
				before.add(new InsnNode(Opcodes.ICONST_1));
				before.add(fix("debugCrosshair", "(Z)V"));
				InsnList after = new InsnList();
				after.add(new InsnNode(Opcodes.ICONST_0));
				after.add(fix("debugCrosshair", "(Z)V"));
				m.instructions.insertBefore(insn, before);
				m.instructions.insert(insn, after);
				return true;
			}
		}
		return false;
	}
}
