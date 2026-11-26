package zone.moddev.mc.skysrealisticweather.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.DistantPrecipitationField;
import zone.moddev.mc.skysrealisticweather.game.DistantPrecipitationField.Column;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

/** Coarse billboard curtains, cached four times per second and drawn in at most two batches. */
public final class DistantPrecipitationRenderer {
    private static final ResourceLocation RAIN = new ResourceLocation("textures/environment/rain.png");
    private static final ResourceLocation SNOW = new ResourceLocation("textures/environment/snow.png");
    private final DistantPrecipitationField field = new DistantPrecipitationField();
    private World world;
    private Object settings, episode;
    private long sampledTick = Long.MIN_VALUE, sampledTime;
    private int anchorX, anchorZ, distance;
    private boolean fancy;

    @SubscribeEvent
    public void unload(net.minecraftforge.event.world.WorldEvent.Unload event) {
        if (event.getWorld() == world) { field.clear(); world = null; settings = episode = null; }
    }

    @SubscribeEvent
    public void render(RenderWorldLastEvent event) {
        Minecraft game = Minecraft.getMinecraft();
        Entity camera = game.getRenderViewEntity();
        WeatherState.Frame frame = WeatherState.frame(game.world);
        int requested = WeatherConfiguration.distantWeatherDistance();
        if (game.world == null || camera == null || requested == 0 || frame == null || !frame.active()
                || game.world.provider.getWeatherRenderer() != null || frame.currentEpisode() == null
                        && frame.scheduled || game.world.provider.hasNoSky()) {
            field.clear(); world = null; return;
        }
        float strength = frame.regionalSchedule() ? 1 : game.world.getRainStrength(event.getPartialTicks());
        if (frame.scheduled && (frame.currentEpisode() == null || !frame.currentEpisode().kind.wet) || strength <= 0) {
            field.clear(); world = null; return;
        }
        double x = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * event.getPartialTicks();
        double y = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * event.getPartialTicks();
        double z = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * event.getPartialTicks();
        int ax = (int) Math.floor(x / 8), az = (int) Math.floor(z / 8);
        int range = DistantPrecipitationField.range(requested, game.gameSettings.renderDistanceChunks, game.gameSettings.fancyGraphics);
        long tick = game.world.getTotalWorldTime(), time = game.world.getWorldTime();
        if (world != game.world || settings != frame.settings || episode != frame.episode || tick - sampledTick >= 5
                || tick < sampledTick || Math.abs(time - sampledTime) > 10 || ax != anchorX || az != anchorZ
                || distance != range || fancy != game.gameSettings.fancyGraphics) {
            world = game.world; settings = frame.settings; episode = frame.episode;
            sampledTick = tick; sampledTime = time; anchorX = ax; anchorZ = az;
            distance = range; fancy = game.gameSettings.fancyGraphics;
            field.sample(frame, x, z, range, game.gameSettings.renderDistanceChunks, fancy, strength, world.provider.getCloudHeight());
        }
        if (field.count == 0) return;
        Frustum frustum = new Frustum(); frustum.setPosition(x, y, z);
        game.entityRenderer.enableLightmap();
        GlStateManager.disableCull(); GlStateManager.enableBlend(); GlStateManager.depthMask(false);
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GlStateManager.alphaFunc(516, .01F);
        try {
            float clock = tick % 24000 + event.getPartialTicks();
            game.getTextureManager().bindTexture(RAIN); draw(field, false, x, y, z, clock, fancy, frustum);
            game.getTextureManager().bindTexture(SNOW); draw(field, true, x, y, z, clock, fancy, frustum);
        } finally {
            GlStateManager.depthMask(true); GlStateManager.enableCull(); GlStateManager.disableBlend();
            GlStateManager.alphaFunc(516, .1F); GlStateManager.color(1, 1, 1, 1);
            game.entityRenderer.disableLightmap();
        }
    }

    /** Shared native drawing path, also exercised by the offscreen graphics probe. */
    public static int draw(DistantPrecipitationField field, boolean snow, double x, double y, double z,
                           float clock, boolean fancy, ICamera frustum) {
        Tessellator tessellator = Tessellator.getInstance(); VertexBuffer buffer = tessellator.getBuffer();
        int quads = 0;
        for (int i = 0; i < field.count; i++) {
            Column column = field.columns[i];
            if (column.snow != snow || frustum != null && !frustum.isBoundingBoxInFrustum(column.bounds)) continue;
            double dx = column.x - x, dz = column.z - z, distance = Math.sqrt(dx * dx + dz * dz);
            float alpha = column.strength * DistantPrecipitationField.fade(distance, field.range, fancy) * (snow ? .48F : .36F);
            if (alpha < .01F || distance < 1) continue;
            if (quads++ == 0) {
                buffer.begin(7, DefaultVertexFormats.PARTICLE_POSITION_TEX_COLOR_LMAP);
                buffer.setTranslation(-x, -y, -z);
            }
            double half = column.size * .68, wx = dz / distance * half, wz = -dx / distance * half;
            double scroll = -clock * (snow ? .012 : .09) + (column.x * 31L + column.z * 17L & 255) / 64.0;
            double u = snow ? Math.sin(clock * .01 + column.x) * .15 : 0;
            int light = snow ? (column.light * 3 + 15728880) / 4 : column.light;
            float r = snow ? .94F : .65F, g = snow ? .96F : .72F, b = snow ? 1 : .8F;
            vertex(buffer, column.x - wx, column.top, column.z - wz, u, -column.top / 8 + scroll, r, g, b, alpha, light);
            vertex(buffer, column.x + wx, column.top, column.z + wz, u + column.size * .5, -column.top / 8 + scroll, r, g, b, alpha, light);
            vertex(buffer, column.x + wx, column.bottom, column.z + wz, u + column.size * .5, -column.bottom / 8 + scroll, r, g, b, alpha, light);
            vertex(buffer, column.x - wx, column.bottom, column.z - wz, u, -column.bottom / 8 + scroll, r, g, b, alpha, light);
        }
        if (quads > 0) { tessellator.draw(); buffer.setTranslation(0, 0, 0); }
        return quads;
    }

    private static void vertex(VertexBuffer buffer, double x, double y, double z, double u, double v,
                               float r, float g, float b, float alpha, int light) {
        buffer.pos(x, y, z).tex(u, v).color(r, g, b, alpha).lightmap(light >> 16 & 65535, light & 65535).endVertex();
    }
}
