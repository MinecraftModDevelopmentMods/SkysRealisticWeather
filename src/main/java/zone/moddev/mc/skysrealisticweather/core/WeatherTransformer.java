package zone.moddev.mc.skysrealisticweather.core;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraftforge.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Wraps narrowly identified weather calls, using the vanilla result outside active worlds. */
public final class WeatherTransformer implements IClassTransformer, Opcodes {
    private static final String HOOKS = "zone/moddev/mc/skysrealisticweather/game/WeatherHooks";
    private static final String CLIENT = "zone/moddev/mc/skysrealisticweather/client/ClientWeatherHooks";
    private static final String WORLD = "Lnet/minecraft/world/World;";
    private static final String SERVER = "Lnet/minecraft/world/WorldServer;";
    private static final String CHUNK = "Lnet/minecraft/world/chunk/Chunk;";
    private static final String POS = "Lnet/minecraft/util/math/BlockPos;";
    private static final String STATE = "Lnet/minecraft/block/state/IBlockState;";
    private static final String BIOME = "Lnet/minecraft/world/biome/Biome;";
    private static final String BLOCK = "Lnet/minecraft/block/Block;";
    private static final String OVERWORLD_GENERATOR = "net.minecraft.world.gen.ChunkProviderOverworld";
    private static final String BOP_GENERATOR = "biomesoplenty.common.world.ChunkProviderGenerateBOP";
    private static final String LOST_GENERATOR = "mcjty.lostcities.dimensions.world.LostCityChunkGenerator";
    private static final String OTG_FROST = "com.pg85.otg.generator.FrozenSurfaceHelper";
    private static final String PROPERTY_ENUM = "net.minecraft.block.properties.PropertyEnum";
    private static final String SECTION_STATES = "net.minecraft.world.chunk.BlockStateContainer";

    @Override
    public byte[] transform(String name, String target, byte[] bytes) {
        if (bytes == null || !isTarget(target)) return bytes;
        try {
            ClassNode node = new ClassNode();
            new ClassReader(bytes).accept(node, 0);
            switch (target) {
                case SECTION_STATES:
                    if (!specializeSectionStorage(node)) return bytes;
                    break;
                case PROPERTY_ENUM:
                    if (!cachePropertyNameHash(node)) return bytes;
                    break;
                case "net.minecraft.world.World": patchWorld(node); break;
                case "net.minecraft.world.WorldServer": patchServer(node); break;
                case "net.minecraft.world.chunk.Chunk": patchChunk(node); break;
                case "net.minecraft.block.BlockCauldron": patchCauldron(node); break;
                case "net.minecraft.tileentity.TileEntityFurnace": patchFurnace(node); break;
                case "net.minecraft.client.renderer.EntityRenderer": patchRenderer(node); break;
                case OVERWORLD_GENERATOR:
                case BOP_GENERATOR:
                case LOST_GENERATOR: patchGeneration(node, target); break;
                case OTG_FROST: patchOtgFrost(node); break;
                default: return bytes;
            }
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException mismatch) {
            if (PROPERTY_ENUM.equals(target) || SECTION_STATES.equals(target)) return bytes;
            if("net.minecraft.tileentity.TileEntityFurnace".equals(target)) {
                org.apache.logging.log4j.LogManager.getLogger("Sky's Realistic Weather").warn("Vanilla furnace heating hook could not be attached; API temperature zones remain available.",mismatch);
                return bytes;
            }
            if (isGenerator(target)) HookStatus.generationFailed(target, mismatch);
            else HookStatus.failed(target, mismatch);
            return bytes;
        }
    }

    private static boolean isTarget(String name) {
        return "net.minecraft.world.World".equals(name) || "net.minecraft.world.WorldServer".equals(name)
                || "net.minecraft.world.chunk.Chunk".equals(name) || "net.minecraft.block.BlockCauldron".equals(name)
                || "net.minecraft.client.renderer.EntityRenderer".equals(name) || isGenerator(name)
                || "net.minecraft.tileentity.TileEntityFurnace".equals(name)
                || PROPERTY_ENUM.equals(name) || SECTION_STATES.equals(name);
    }

    private static void patchFurnace(ClassNode type) {
        MethodNode update=method(type,"update","func_73660_a","()V");int count=0;
        for(AbstractInsnNode instruction:update.instructions.toArray())if(instruction.getOpcode()==RETURN) {
            InsnList bridge=new InsnList();bridge.add(new VarInsnNode(ALOAD,0));
            bridge.add(hook("zone/moddev/mc/skysrealisticweather/game/TemperatureZones","furnaceTick","(Lnet/minecraft/tileentity/TileEntityFurnace;)V"));
            update.instructions.insertBefore(instruction,bridge);count++;
        }
        expect(count,1,"furnace heating tick");
    }

    private static boolean specializeSectionStorage(ClassNode type) {
        String bits = "net/minecraft/util/BitArray";
        int replaced = 0;
        for (MethodNode method : type.methods) {
            if (!"(I)V".equals(method.desc) && !"(IZ)V".equals(method.desc)) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != NEW || !bits.equals(FMLDeobfuscatingRemapper.INSTANCE.map(((TypeInsnNode) instruction).desc))) continue;
                AbstractInsnNode dup = instruction.getNext();
                if (dup == null || dup.getOpcode() != DUP) return false;
                // The native site constructs BitArray(this.bits, 4096). Skip alternate storage implementations.
                AbstractInsnNode args = dup.getNext();
                if (!(args instanceof VarInsnNode) || args.getOpcode() != ALOAD || ((VarInsnNode) args).var != 0) return false;
                AbstractInsnNode width = args.getNext();
                if (!(width instanceof FieldInsnNode) || width.getOpcode() != GETFIELD || !"I".equals(((FieldInsnNode) width).desc)) return false;
                AbstractInsnNode size = width.getNext();
                if (!(size instanceof IntInsnNode) || ((IntInsnNode) size).operand != 4096) return false;
                AbstractInsnNode init = size.getNext();
                if (!(init instanceof MethodInsnNode) || init.getOpcode() != INVOKESPECIAL) return false;
                MethodInsnNode ctor = (MethodInsnNode) init;
                if (!ctor.owner.equals(((TypeInsnNode) instruction).desc) || !"<init>".equals(ctor.name) || !"(II)V".equals(ctor.desc)) return false;
                method.instructions.remove(instruction);
                method.instructions.remove(dup);
                method.instructions.set(ctor, hook("zone/moddev/mc/skysrealisticweather/game/FourBitSectionStorage",
                        "create", "(II)Lnet/minecraft/util/BitArray;"));
                replaced++;
            }
        }
        return replaced == 1;
    }

    // PropertyEnum finishes building its private String -> Enum table in its constructor.
    // Only replace the hash of that table. Keep subclass and superclass hash logic intact.
    private static boolean cachePropertyNameHash(ClassNode type) {
        String cached = "skysrealisticweather$nameTableHash";
        for (FieldNode field : type.fields) {
            if (cached.equals(field.name)) return false;
        }
        MethodNode constructor = method(type, "<init>", "<init>", "(Ljava/lang/String;Ljava/lang/Class;Ljava/util/Collection;)V");
        MethodNode hash = method(type, "hashCode", "hashCode", "()I");
        FieldInsnNode table = null;
        MethodInsnNode lookup = null;
        for (AbstractInsnNode instruction : hash.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!"java/util/Map".equals(call.owner) || !"hashCode".equals(call.name) || !"()I".equals(call.desc)) continue;
            if (lookup != null || !(call.getPrevious() instanceof FieldInsnNode)) return false;
            table = (FieldInsnNode) call.getPrevious();
            if (table.getOpcode() != GETFIELD || !type.name.equals(table.owner) || !"Ljava/util/Map;".equals(table.desc)) return false;
            lookup = call;
        }
        if (table == null) return false;
        boolean privateFinal = false;
        for (FieldNode field : type.fields) {
            if (table.name.equals(field.name)) privateFinal = (field.access & (ACC_PRIVATE | ACC_FINAL)) == (ACC_PRIVATE | ACC_FINAL);
        }
        if (!privateFinal) return false;
        for (MethodNode member : type.methods) {
            if ("<init>".equals(member.name) && member != constructor) return false;
        }
        type.fields.add(new FieldNode(ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC, cached, "I", null, null));
        int exits = 0;
        for (AbstractInsnNode instruction : constructor.instructions.toArray()) {
            if (instruction.getOpcode() != RETURN) continue;
            InsnList init = new InsnList();
            init.add(new VarInsnNode(ALOAD, 0));
            init.add(new VarInsnNode(ALOAD, 0));
            init.add(new FieldInsnNode(GETFIELD, table.owner, table.name, table.desc));
            init.add(new MethodInsnNode(INVOKEINTERFACE, "java/util/Map", "hashCode", "()I", true));
            init.add(new FieldInsnNode(PUTFIELD, type.name, cached, "I"));
            constructor.instructions.insertBefore(instruction, init);
            exits++;
        }
        expect(exits, 1, "property construction");
        hash.instructions.set(table, new FieldInsnNode(GETFIELD, type.name, cached, "I"));
        hash.instructions.remove(lookup);
        return true;
    }

    private static boolean isGenerator(String name) {
        return OVERWORLD_GENERATOR.equals(name) || BOP_GENERATOR.equals(name)
                || LOST_GENERATOR.equals(name) || OTG_FROST.equals(name);
    }

    private static void patchGeneration(ClassNode type, String target) {
        MethodNode populate = method(type, "populate", "func_185931_b", "(II)V");
        if (LOST_GENERATOR.equals(target)) {
            redirect(populate, "canBlockFreeze", "func_175670_e", "(" + POS + "Z)Z", HOOKS,
                    "generationFreeze", "(" + WORLD + POS + "Z)Z", -1, 1);
        } else {
            redirect(populate, "canBlockFreezeWater", "func_175675_v", "(" + POS + ")Z", HOOKS,
                    "generationFreeze", "(" + WORLD + POS + ")Z", -1, 1);
        }
        redirect(populate, "canSnowAt", "func_175708_f", "(" + POS + "Z)Z", HOOKS,
                "generationSnow", "(" + WORLD + POS + "Z)Z", -1, 1);
        redirect(populate, "setBlockState", "func_180501_a", "(" + POS + STATE + "I)Z", HOOKS,
                "placeGeneratedDeposit", "(" + WORLD + POS + STATE + "I)Z", -1, 2);
    }

    private static void patchOtgFrost(ClassNode type) {
        String otg = "com/pg85/otg/";
        String localWorld = "L" + otg + "LocalWorld;";
        expect((int) type.fields.stream().filter(f -> f.name.equals("world") && f.desc.equals(localWorld)).count(), 1, "OTG world");
        MethodNode column = method(type, "freezeColumn", "freezeColumn", "(II)V");
        int count = 0;
        for (AbstractInsnNode insn : column.instructions.toArray()) {
            if (insn.getOpcode() != INVOKEINTERFACE) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (!(otg + "LocalBiome").equals(call.owner) || !"getTemperatureAt".equals(call.name) || !"(III)F".equals(call.desc)) continue;
            column.instructions.insertBefore(call, new VarInsnNode(ALOAD, 0));
            column.instructions.insertBefore(call, new FieldInsnNode(GETFIELD, type.name, "world", localWorld));
            column.instructions.set(call, hook("zone/moddev/mc/skysrealisticweather/game/OtgWeather", "frostColumn",
                    "(L" + otg + "LocalBiome;III" + localWorld + ")F"));
            count++;
        }
        expect(count, 1, "OTG frost temperature");
    }

    private static void patchWorld(ClassNode type) {
        localStrength(type, "getRainStrength", "func_72867_j", "localRainStrength");
        localStrength(type, "getThunderStrength", "func_72819_i", "localThunderStrength");
        localTemperature(method(type, "canBlockFreezeBody", "canBlockFreezeBody", "(" + POS + "Z)Z"),
                "surfaceTemperature", 0);
        localTemperature(method(type, "canSnowAtBody", "canSnowAtBody", "(" + POS + "Z)Z"),
                "surfaceTemperature", 0);
        MethodNode rain = method(type, "isRainingAt", "func_175727_C", "(" + POS + ")Z");
        int exits = 0;
        for (AbstractInsnNode instruction : rain.instructions.toArray()) {
            if (instruction.getOpcode() != IRETURN) continue;
            InsnList bridge = new InsnList();
            bridge.add(new VarInsnNode(ALOAD, 0));
            bridge.add(new VarInsnNode(ALOAD, 1));
            bridge.add(hook(HOOKS, "rainingAt", "(Z" + WORLD + POS + ")Z"));
            rain.instructions.insertBefore(instruction, bridge);
            exits++;
        }
        expect(exits, 4, "rain checks");
        MethodNode update = method(type, "updateWeatherBody", "updateWeatherBody", "()V");
        int duration = 0;
        for (AbstractInsnNode instruction : update.instructions.toArray()) {
            if (matches(instruction, "setRainTime", "func_76080_g", "(I)V")
                    || matches(instruction, "setThunderTime", "func_76090_f", "(I)V")) {
                if (previousOpcode(instruction) != IADD) continue;
                InsnList bridge = new InsnList();
                bridge.add(new VarInsnNode(ALOAD, 0));
                bridge.add(new InsnNode(ICONST_0 + duration++));
                bridge.add(hook(HOOKS, "weatherDuration", "(I" + WORLD + "I)I"));
                update.instructions.insertBefore(instruction, bridge);
            }
        }
        expect(duration, 4, "fresh weather timers");
    }

    private static void localStrength(ClassNode type, String name, String srg, String target) {
        MethodNode method = method(type, name, srg, "(F)F");
        int exits = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (instruction.getOpcode() != FRETURN) continue;
            InsnList bridge = new InsnList();
            bridge.add(new VarInsnNode(ALOAD, 0));
            bridge.add(hook(HOOKS, target, "(F" + WORLD + ")F"));
            method.instructions.insertBefore(instruction, bridge);
            exits++;
        }
        expect(exits, 1, name);
    }

    private static void patchServer(ClassNode type) {
        MethodNode update = method(type, "updateBlocks", "func_147456_g", "()V");
        redirect(update, "isRainingAt", "func_175727_C", "(" + POS + ")Z", HOOKS,
                "lightningAt", "(" + SERVER + POS + ")Z", -1, 1);
        redirect(update, "canBlockFreezeNoWater", "func_175662_w", "(" + POS + ")Z", HOOKS,
                "freeze", "(" + SERVER + POS + ")Z", -1, 1);
        redirect(update, "canSnowAt", "func_175708_f", "(" + POS + "Z)Z", HOOKS,
                "snow", "(" + SERVER + POS + "Z)Z", -1, 1);
        redirect(update, "setBlockState", "func_175656_a", "(" + POS + STATE + ")Z", HOOKS,
                "placeDeposit", "(" + SERVER + POS + STATE + ")Z", -1, 2);
        redirect(update, "canRain", "func_76738_d", "()Z", HOOKS,
                "sampleRain", "(" + BIOME + SERVER + ")Z", 0, 1);
        redirect(update, "fillWithRain", "func_176224_k", "(" + WORLD + POS + ")V", HOOKS,
                "fillRain", "(" + BLOCK + WORLD + POS + ")V", -1, 1);
    }

    private static void patchChunk(ClassNode type) {
        int populations=0;
        for(MethodNode method:type.methods)for(AbstractInsnNode instruction:method.instructions.toArray()) {
            if(!(instruction instanceof MethodInsnNode)||instruction.getOpcode()!=INVOKEINTERFACE)continue;
            MethodInsnNode call=(MethodInsnNode)instruction;
            if(!FMLDeobfuscatingRemapper.INSTANCE.map(call.owner).equals("net/minecraft/world/chunk/IChunkGenerator")
                    ||!matches(call.owner,call.name,call.desc,"populate","func_185931_b","(II)V"))continue;
            method.instructions.insertBefore(call,new VarInsnNode(ALOAD,0));
            method.instructions.set(call,hook("zone/moddev/mc/skysrealisticweather/game/PlantGrowthHooks","populateChunk",
                    "(Lnet/minecraft/world/chunk/IChunkGenerator;II"+CHUNK+")V"));
            populations++;
        }
        expect(populations,1,"population farming scope");
        MethodNode set = method(type, "setBlockState", "func_177436_a", "(" + POS + STATE + ")" + STATE);
        int exits = 0;
        for (AbstractInsnNode instruction : set.instructions.toArray()) {
            if (instruction.getOpcode() != ARETURN || previousOpcode(instruction) == ACONST_NULL) continue;
            InsnList bridge = new InsnList();
            bridge.add(new InsnNode(DUP));
            bridge.add(new VarInsnNode(ALOAD, 0));
            bridge.add(new VarInsnNode(ALOAD, 1));
            bridge.add(new VarInsnNode(ALOAD, 2));
            bridge.add(hook(HOOKS, "blockChanged", "(" + STATE + CHUNK + POS + STATE + ")V"));
            set.instructions.insertBefore(instruction, bridge);
            exits++;
        }
        expect(exits, 1, "committed block changes");
    }

    private static void patchCauldron(ClassNode type) {
        localTemperature(method(type, "fillWithRain", "func_176224_k", "(" + WORLD + POS + ")V"),
                "rainTemperature", 1);
    }

    private static void patchRenderer(ClassNode type) {
        MethodNode rain = method(type, "renderRainSnow", "func_78474_d", "(F)V");
        MethodNode particles = method(type, "addRainParticles", "func_78484_h", "()V");
        int rainColumn = biomePosition(rain);
        int particleColumn = biomePosition(particles);
        redirect(rain, "canRain", "func_76738_d", "()Z", CLIENT,
                "mayPrecipitate", "(" + BIOME + POS + ")Z", rainColumn, 1);
        redirect(rain, "getEnableSnow", "func_76746_c", "()Z", CLIENT,
                "vanillaSnowBiome", "(" + BIOME + ")Z", -1, 1);
        redirect(rain, "getFloatTemperature", "func_180626_a", "(" + POS + ")F", CLIENT,
                "columnTemperature", "(" + BIOME + POS + ")F", -1, 1);
        redirect(particles, "canRain", "func_76738_d", "()Z", CLIENT,
                "mayPrecipitate", "(" + BIOME + POS + ")Z", particleColumn, 1);
        redirect(particles, "getFloatTemperature", "func_180626_a", "(" + POS + ")F", CLIENT,
                "particleTemperature", "(" + BIOME + POS + ")F", -1, 1);
        redirect(rain, "getRainStrength", "func_72867_j", "(F)F", CLIENT,
                "precipitationStrength", "(" + WORLD + "F)F", -1, 1);
        columnRainStrength(rain, rainColumn);
        redirect(particles, "getRainStrength", "func_72867_j", "(F)F", CLIENT,
                "visualRainStrength", "(" + WORLD + "F)F", -1, 1);
        MethodNode fog = method(type, "updateFogColor", "func_78466_h", "(F)V");
        redirect(fog, "getRainStrength", "func_72867_j", "(F)F", CLIENT,
                "visualRainStrength", "(" + WORLD + "F)F", -1, 1);
    }

    private static void columnRainStrength(MethodNode rain, int position) {
        int strength = -1, biome = -1;
        for (AbstractInsnNode instruction : rain.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (CLIENT.equals(call.owner) && "precipitationStrength".equals(call.name)) {
                strength = storedLocal(instruction, FSTORE);
            } else if (matches(instruction, "getBiome", "func_180494_b", "(" + POS + ")" + BIOME)) {
                biome = storedLocal(instruction, ASTORE);
            }
        }
        if (strength < 0 || biome < 0) throw new IllegalStateException("Missing precipitation locals");
        int count = 0;
        for (AbstractInsnNode instruction : rain.instructions.toArray()) {
            if (!(instruction instanceof VarInsnNode) || instruction.getOpcode() != FLOAD
                    || ((VarInsnNode) instruction).var != strength) continue;
            AbstractInsnNode next = instruction.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (next == null || next.getOpcode() != FMUL) continue; // Leave the entry gate before the loop alone.
            InsnList bridge = new InsnList();
            bridge.add(new VarInsnNode(ALOAD, biome));
            bridge.add(new VarInsnNode(ALOAD, position));
            bridge.add(hook(CLIENT, "columnRainStrength", "(F" + BIOME + POS + ")F"));
            rain.instructions.insert(instruction, bridge);
            count++;
        }
        expect(count, 2, "rain and snow column opacity");
    }

    private static int storedLocal(AbstractInsnNode instruction, int opcode) {
        AbstractInsnNode next = instruction.getNext();
        while (next != null && next.getOpcode() < 0) next = next.getNext();
        if (!(next instanceof VarInsnNode) || next.getOpcode() != opcode) throw new IllegalStateException("Unexpected precipitation local");
        return ((VarInsnNode) next).var;
    }

    private static int biomePosition(MethodNode method) {
        int column = -1;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!matches(instruction, "getBiome", "func_180494_b", "(" + POS + ")" + BIOME)) continue;
            AbstractInsnNode previous = instruction.getPrevious();
            while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
            if (column >= 0 || !(previous instanceof VarInsnNode) || previous.getOpcode() != ALOAD) {
                throw new IllegalStateException("Unexpected biome position in " + method.name);
            }
            column = ((VarInsnNode) previous).var;
        }
        if (column < 0) throw new IllegalStateException("Missing biome position in " + method.name);
        return column;
    }

    private static void localTemperature(MethodNode method, String replacement, int world) {
        redirect(method, "getFloatTemperature", "func_180626_a", "(" + POS + ")F", HOOKS,
                replacement, "(" + BIOME + POS + WORLD + ")F", world, 1);
    }

    private static int previousOpcode(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous == null ? -1 : previous.getOpcode();
    }

    private static void redirect(MethodNode method, String mcp, String srg, String descriptor,
                                 String bridge, String replacement, String signature, int local, int expected) {
        int count = 0;
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            if (!matches(instruction, mcp, srg, descriptor)) continue;
            if (local >= 0) method.instructions.insertBefore(instruction, new VarInsnNode(ALOAD, local));
            method.instructions.set(instruction, hook(bridge, replacement, signature));
            count++;
        }
        expect(count, expected, method.name + " " + mcp);
    }

    private static boolean matches(AbstractInsnNode instruction, String mcp, String srg, String descriptor) {
        if (!(instruction instanceof MethodInsnNode) || instruction.getOpcode() != INVOKEVIRTUAL) return false;
        MethodInsnNode call = (MethodInsnNode) instruction;
        return matches(call.owner, call.name, call.desc, mcp, srg, descriptor);
    }

    private static MethodNode method(ClassNode type, String mcp, String srg, String descriptor) {
        for (MethodNode method : type.methods) {
            if (matches(type.name, method.name, method.desc, mcp, srg, descriptor)) return method;
        }
        throw new IllegalStateException("Missing method " + mcp + descriptor);
    }

    private static boolean matches(String owner, String name, String actual, String mcp, String srg, String descriptor) {
        FMLDeobfuscatingRemapper remapper = FMLDeobfuscatingRemapper.INSTANCE;
        return descriptor.equals(remapper.mapMethodDesc(actual))
                && (name.equals(mcp) || name.equals(srg) || remapper.mapMethodName(owner, name, actual).equals(srg));
    }

    private static MethodInsnNode hook(String bridge, String name, String signature) {
        return new MethodInsnNode(INVOKESTATIC, bridge, name, signature, false);
    }

    private static void expect(int found, int expected, String target) {
        if (found != expected) {
            throw new IllegalStateException("Expected " + expected + " " + target + ", found " + found);
        }
    }
}
