package zone.moddev.mc.skysrealisticweather.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Redirects individual natural-growth rolls and ambient fallbacks; no third-party jar is rewritten. */
public final class PlantTransformer implements IClassTransformer, Opcodes {
    private static final String HOOKS = "zone/moddev/mc/skysrealisticweather/game/PlantGrowthHooks";
    private static final String WORLD = "Lnet/minecraft/world/World;", POS = "Lnet/minecraft/util/math/BlockPos;";

    @Override
    public byte[] transform(String name, String target, byte[] bytes) {
        if (bytes == null || !target(target)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            if (target.equals("net.minecraft.block.BlockSapling") || target.equals("com.pam.harvestcraft.blocks.growables.BlockPamSapling")) fertilizer(node);
            else if (target.equals("forestry.core.climate.ClimateManager")) forestry(node);
            else if (target.equals("com.infinityraider.agricraft.blocks.BlockCrop")) agricraft(node);
            else randomRoll(node);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            byte[] result = writer.toByteArray();
            PlantHookStatus.attached(target);
            return result;
        } catch (RuntimeException mismatch) {
            PlantHookStatus.failed(target, mismatch);
            return bytes;
        }
    }

    private static boolean target(String target) {
        return target.equals("net.minecraft.block.BlockSapling")
                || target.equals("com.pam.harvestcraft.blocks.growables.BlockPamSapling")
                || target.equals("com.pam.harvestcraft.blocks.growables.BlockPamCrop")
                || target.equals("com.pam.harvestcraft.blocks.growables.BlockPamFruit")
                || target.equals("com.pam.harvestcraft.blocks.growables.BlockPamFruitLog")
                || target.equals("com.blakebr0.mysticalagriculture.blocks.crop.BlockMysticalCrop")
                || target.equals("com.infinityraider.agricraft.blocks.BlockCrop")
                || target.equals("forestry.core.climate.ClimateManager");
    }

    private static MethodNode tick(ClassNode node) {
        for (MethodNode method : node.methods) if ((method.name.equals("updateTick") || method.name.equals("func_180650_b"))
                && method.desc.equals("(" + WORLD + POS + "Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V")) return method;
        throw new IllegalArgumentException("natural tick signature differs");
    }

    private static void randomRoll(ClassNode node) {
        MethodNode method = tick(node);
        int rolls = 0;
        for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) i;
            if (call.name.equals("onCropsGrowPre")) return; // Generic Forge listener already covers this version.
            if (call.owner.equals("java/util/Random") && call.name.equals("nextInt") && call.desc.equals("(I)I")) rolls++;
        }
        if (rolls != 1) throw new IllegalArgumentException("expected one natural growth roll, found " + rolls);
        for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
            MethodInsnNode call = (MethodInsnNode) i;
            if (!call.owner.equals("java/util/Random") || !call.name.equals("nextInt")) continue;
            AbstractInsnNode next = call.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (!(next instanceof JumpInsnNode) || next.getOpcode() != IFNE) throw new IllegalArgumentException("growth roll is not a zero test");
            InsnList arguments = new InsnList();
            arguments.add(new VarInsnNode(ALOAD, 1)); arguments.add(new VarInsnNode(ALOAD, 2));
            method.instructions.insertBefore(call, arguments);
            method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOKS, "naturalRoll", "(Ljava/util/Random;I" + WORLD + POS + ")I", false));
        }
    }

    private static void fertilizer(ClassNode node) {
        int changes = 0;
        for (MethodNode method : node.methods) {
            if (!(method.name.equals("grow") || method.name.equals("func_176474_b"))
                    || !method.desc.equals("(" + WORLD + "Ljava/util/Random;" + POS + "Lnet/minecraft/block/state/IBlockState;)V")) continue;
            for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) i;
                if (!call.owner.equals(node.name) || !call.desc.equals("(" + WORLD + POS + "Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;)V")) continue;
                InsnList arguments = new InsnList();
                arguments.add(new LdcInsnNode(Type.getObjectType(call.owner))); arguments.add(new LdcInsnNode(call.name));
                method.instructions.insertBefore(call, arguments);
                method.instructions.set(call, new MethodInsnNode(INVOKESTATIC, HOOKS, "fertilizeSapling",
                        "(Ljava/lang/Object;" + WORLD + POS + "Lnet/minecraft/block/state/IBlockState;Ljava/util/Random;Ljava/lang/Class;Ljava/lang/String;)V", false));
                changes++;
            }
        }
        if (changes != 1) throw new IllegalArgumentException("sapling fertiliser callback differs");
    }

    private static void agricraft(ClassNode node) {
        MethodNode tick = tick(node);
        int changes = 0;
        for (AbstractInsnNode i : tick.instructions.toArray()) if (i instanceof InvokeDynamicInsnNode) {
            InvokeDynamicInsnNode dynamic = (InvokeDynamicInsnNode) i;
            for (int index = 0; index < dynamic.bsmArgs.length; index++) {
                Object value = dynamic.bsmArgs[index];
                if (!(value instanceof Handle)) continue;
                Handle handle = (Handle) value;
                if (handle.getOwner().equals("com/infinityraider/agricraft/tiles/TileEntityCrop") && handle.getName().equals("onGrowthTick")
                        && handle.getDesc().equals("()Lcom/infinityraider/agricraft/api/v1/util/MethodResult;")) {
                    dynamic.bsmArgs[index] = new Handle(H_INVOKESTATIC, HOOKS, "agriNaturalTick", "(Ljava/lang/Object;)V");
                    changes++;
                }
            }
        }
        if (changes != 1) throw new IllegalArgumentException("AgriCraft natural callback differs");
    }

    private static void forestry(ClassNode node) {
        String info = "Lforestry/api/climate/IClimateInfo;";
        int changes = 0;
        for (MethodNode method : node.methods) {
            if (!method.name.equals("getInfo") || !method.desc.equals("(" + WORLD + POS + ")" + info)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.getOpcode() != INVOKESTATIC || !call.owner.equals("forestry/core/climate/BiomeClimateInfo")
                        || !call.name.equals("getInfo") || !call.desc.equals("(Lnet/minecraft/world/biome/Biome;)Lforestry/core/climate/BiomeClimateInfo;")) continue;
                InsnList bridge = new InsnList();
                bridge.add(new VarInsnNode(ALOAD, 1)); bridge.add(new VarInsnNode(ALOAD, 2));
                bridge.add(new MethodInsnNode(INVOKESTATIC, "zone/moddev/mc/skysrealisticweather/compat/ForestryClimate",
                        "ambient", "(" + info + WORLD + POS + ")" + info, false));
                method.instructions.insert(call, bridge);
                changes++;
            }
        }
        if (changes != 1) throw new IllegalArgumentException("Forestry ambient climate fallback differs");
    }
}
