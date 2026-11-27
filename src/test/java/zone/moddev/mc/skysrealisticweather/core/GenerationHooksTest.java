package zone.moddev.mc.skysrealisticweather.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

final class GenerationHooksTest {
    @Test
    void nativeChunkPopulationScopesFarmingHooks() throws Exception {
        String target="net.minecraft.world.chunk.Chunk";
        byte[] original;
        try(InputStream stream=getClass().getResourceAsStream("/"+target.replace('.','/')+".class")) {
            assertNotNull(stream);ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
            while((count=stream.read(buffer))>=0)bytes.write(buffer,0,count);original=bytes.toByteArray();
        }
        ClassNode type=new ClassNode();new ClassReader(new WeatherTransformer().transform(null,target,original)).accept(type,0);
        int wrappers=0;
        for(MethodNode method:type.methods)for(AbstractInsnNode instruction:method.instructions.toArray())if(instruction instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)instruction;
            if(call.owner.equals("zone/moddev/mc/skysrealisticweather/game/PlantGrowthHooks")&&call.name.equals("populateChunk")) {
                wrappers++;new Analyzer<>(new BasicVerifier()).analyze(type.name,method);
            }
        }
        assertEquals(1,wrappers,"natural farming gates must not intercept a generator's sapling callbacks");
    }
    @Test
    void nativeOverworldPopulationUsesSeasonalSnowIceAndOwnership() throws Exception {
        String target = "net.minecraft.world.gen.ChunkProviderOverworld";
        byte[] original;
        try (InputStream stream = getClass().getResourceAsStream("/"+target.replace('.', '/')+".class")) {
            assertNotNull(stream);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            original = bytes.toByteArray();
        }
        byte[] patched = new WeatherTransformer().transform(null, target, original);
        ClassNode type = new ClassNode(); new ClassReader(patched).accept(type, 0);
        MethodNode population = type.methods.stream().filter(m -> m.name.equals("populate")).findFirst().get();
        int freeze = 0, snow = 0, deposits = 0;
        for (AbstractInsnNode instruction : population.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!call.owner.equals("zone/moddev/mc/skysrealisticweather/game/WeatherHooks")) continue;
            if (call.name.equals("generationFreeze")) freeze++;
            if (call.name.equals("generationSnow")) snow++;
            if (call.name.equals("placeGeneratedDeposit")) deposits++;
        }
        assertEquals(1, freeze); assertEquals(1, snow); assertEquals(2, deposits);
        new Analyzer<>(new BasicVerifier()).analyze(type.name, population);
    }
}
