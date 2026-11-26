package zone.moddev.mc.skysrealisticweather.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;
import static org.junit.jupiter.api.Assertions.*;

final class PrecipitationRendererTest {
    @Test
    void vanillaRendererUsesColumnStrengthForBothRainAndSnow() throws Exception {
        byte[] original;
        try (InputStream stream = getClass().getResourceAsStream("/net/minecraft/client/renderer/EntityRenderer.class")) {
            assertNotNull(stream);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int count;
            while ((count = stream.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            original = bytes.toByteArray();
        }
        byte[] transformed = new WeatherTransformer().transform(null, "net.minecraft.client.renderer.EntityRenderer", original);
        ClassNode type = new ClassNode(); new ClassReader(transformed).accept(type, 0);
        MethodNode precipitation = type.methods.stream().filter(m -> m.name.equals("renderRainSnow")).findFirst().get();
        int gates = 0, columns = 0;
        for (AbstractInsnNode instruction : precipitation.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!call.owner.endsWith("/ClientWeatherHooks")) continue;
            if (call.name.equals("precipitationStrength")) gates++;
            if (call.name.equals("columnRainStrength")) columns++;
        }
        assertEquals(1, gates, "a clear camera must not shut off the precipitation pass");
        assertEquals(2, columns, "rain and snow alpha must use their own column's weather");
        new Analyzer<>(new BasicVerifier()).analyze(type.name, precipitation);
        for (String name : new String[] {"addRainParticles", "updateFogColor"}) {
            MethodNode local = type.methods.stream().filter(m -> m.name.equals(name)).findFirst().get();
            assertTrue(java.util.Arrays.stream(local.instructions.toArray()).anyMatch(n -> n instanceof MethodInsnNode
                    && ((MethodInsnNode) n).name.equals("visualRainStrength")), "particles and fog remain camera local");
        }
    }
}
