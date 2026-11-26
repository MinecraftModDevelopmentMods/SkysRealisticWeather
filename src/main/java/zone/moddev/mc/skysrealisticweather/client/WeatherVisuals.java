package zone.moddev.mc.skysrealisticweather.client;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.event.terraingen.BiomeEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather.Precipitation;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.game.BiomeClimates;
import zone.moddev.mc.skysrealisticweather.game.WeatherState;

public final class WeatherVisuals {
    private static final int STEPS = 12;
    private static final int[] GRASS = {0x92BD5B, 0x7FA34D, 0xC1A256, 0xADA88B};
    private static final int[] LEAVES = {0xA5CB63, 0x659343, 0xC47334, 0xA39A76};
    private static final float[] GRASS_BLEND = {0.26F, 0.10F, 0.52F, 0.48F};
    private static final float[] LEAF_BLEND = {0.28F, 0.11F, 0.74F, 0.58F};
    private final Queue<BlockPos> rebuild = new ArrayDeque<>();
    private final Random effects = new Random();
    private World renderedWorld;
    private volatile Tint tint;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        if (renderedWorld != minecraft.world) {
            renderedWorld = minecraft.world;
            rebuild.clear();
            tint = null;
        }
        if (minecraft.player == null || renderedWorld == null) return;
        WeatherState.Frame frame = WeatherState.frame(renderedWorld);
        if (frame != null && minecraft.player.ticksExisted % 3 == 0) windParticles(minecraft, frame);
        Tint previous = tint;
        if (frame != null && frame.active() && frame.settings.foliage) {
            int season = frame.climate.calendar.season.ordinal();
            int step = (int) (frame.climate.calendar.progress * STEPS);
            if (previous == null || previous.season != season || previous.step != step
                    || previous.strength != frame.settings.profile.strength) {
                tint = new Tint(season, step, frame.settings.profile.strength);
                queueRebuild(minecraft);
            }
        } else if (previous != null) {
            tint = null;
            queueRebuild(minecraft);
        }
        for (int count = 0; count < 12 && !rebuild.isEmpty(); count++) {
            BlockPos position = rebuild.remove();
            if (renderedWorld.isBlockLoaded(position, false)) {
                minecraft.renderGlobal.markBlockRangeForRenderUpdate(position.getX(), 0, position.getZ(),
                        position.getX() + 15, 255, position.getZ() + 15);
            }
        }
    }

    private void windParticles(Minecraft minecraft, WeatherState.Frame frame) {
        BlockPos eye = new BlockPos(minecraft.player.posX,
                minecraft.player.posY + minecraft.player.getEyeHeight(), minecraft.player.posZ);
        if (!renderedWorld.isBlockLoaded(eye, false)) return;
        if (frame.currentEpisode() == null || minecraft.gameSettings.particleSetting == 2
                || renderedWorld.provider.getWeatherRenderer() != null) return;
        if (!renderedWorld.canSeeSky(eye)) return;
        int count = minecraft.gameSettings.particleSetting == 1 ? 4 : 11;
        for (int index = 0; index < count; index++) {
            BlockPos point = eye.add(effects.nextInt(23) - 11, effects.nextInt(7) - 2, effects.nextInt(23) - 11);
            if (!renderedWorld.isBlockLoaded(point, false) || !renderedWorld.canSeeSky(point)
                    || !renderedWorld.isAirBlock(point)) continue;
            net.minecraft.world.biome.Biome biome = renderedWorld.getBiome(point);
            WeatherEpisode episode = frame.currentEpisode(biome);
            if (episode == null) continue;
            WeatherKind kind = episode.kind;
            switch (kind) {
                case HAILSTORM:
                case DUST_STORM:
                case BLIZZARD:
                case SNOW_SQUALL:
                case BLOWING_SNOW: break;
                default: continue;
            }
            float wind = frame.wind(point, biome);
            if (wind < 0.12F || effects.nextFloat() >= wind) continue;
            double bearing = Math.toRadians(episode.windBearing());
            double dx = Math.sin(bearing) * wind;
            double dz = -Math.cos(bearing) * wind;
            Precipitation falling = frame.precipitation(point, biome);
            double x = point.getX() + effects.nextDouble();
            double y = point.getY() + effects.nextDouble();
            double z = point.getZ() + effects.nextDouble();
            if (kind == WeatherKind.DUST_STORM) {
                renderedWorld.spawnParticle(EnumParticleTypes.BLOCK_DUST, x, y, z, dx, 0.05, dz,
                        Block.getStateId(Blocks.SAND.getDefaultState()));
            } else if (kind == WeatherKind.HAILSTORM && falling == Precipitation.HAIL) {
                renderedWorld.spawnParticle(EnumParticleTypes.ITEM_CRACK, x, y, z, dx * 0.25, -0.45, dz * 0.25,
                        Item.getIdFromItem(Items.SNOWBALL));
            } else if (kind == WeatherKind.BLOWING_SNOW ? snowCovered(point) : falling == Precipitation.SNOW) {
                renderedWorld.spawnParticle(EnumParticleTypes.SNOW_SHOVEL, x, y, z, dx, -0.05, dz);
            }
        }
    }

    private boolean snowCovered(BlockPos point) {
        Block ground = renderedWorld.getBlockState(renderedWorld.getPrecipitationHeight(point).down()).getBlock();
        return ground == Blocks.SNOW_LAYER || ground == Blocks.SNOW;
    }

    private void queueRebuild(Minecraft minecraft) {
        rebuild.clear();
        int x = minecraft.player.getPosition().getX() >> 4;
        int z = minecraft.player.getPosition().getZ() >> 4;
        int distance = minecraft.gameSettings.renderDistanceChunks + 1;
        for (int radius = 0; radius <= distance; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                        rebuild.add(new BlockPos((x + dx) << 4, 0, (z + dz) << 4));
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void grass(BiomeEvent.GetGrassColor event) {
        Tint value = tint;
        if (value != null) {
            event.setNewColor(value.apply(event.getNewColor(), GRASS, GRASS_BLEND,
                    foliageScale(event.getBiome()), localCalendar(event.getBiome())));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void foliage(BiomeEvent.GetFoliageColor event) {
        Tint value = tint;
        if (value != null) {
            event.setNewColor(value.apply(event.getNewColor(), LEAVES, LEAF_BLEND,
                    foliageScale(event.getBiome()), localCalendar(event.getBiome())));
        }
    }

    private float foliageScale(net.minecraft.world.biome.Biome biome) {
        float scale = BiomeClimates.seasonalScale(biome);
        WeatherState.Frame frame = WeatherState.clientFrame();
        if (frame == null || frame.regions == null) return scale;
        ClimateProfile profile = frame.settings.biomes.preset(biome).profile;
        float min = 2, max = -2;
        for (int i = 0; i < 4; i++) {
            min = Math.min(min, profile.period(i).temperature);
            max = Math.max(max, profile.period(i).temperature);
        }
        return scale * Math.min(1, (max - min) / 0.8F);
    }

    private zone.moddev.mc.skysrealisticweather.api.SeasonSnapshot localCalendar(net.minecraft.world.biome.Biome biome) {
        WeatherState.Frame frame = WeatherState.clientFrame();
        return frame == null || frame.regions == null ? null : frame.climate(biome).calendar;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fog(EntityViewRenderEvent.FogDensity event) {
        if (event.getState().getMaterial() != Material.AIR) return;
        if (event.getEntity() instanceof EntityLivingBase
                && ((EntityLivingBase) event.getEntity()).isPotionActive(MobEffects.BLINDNESS)) return;
        World world = event.getEntity().world;
        WeatherState.Frame frame = WeatherState.frame(world);
        BlockPos eye = new BlockPos(event.getEntity().posX,
                event.getEntity().posY + event.getEntity().getEyeHeight(), event.getEntity().posZ);
        if (frame == null || !world.isBlockLoaded(eye, false) || !world.canSeeSky(eye)
                || world.getPrecipitationHeight(eye).getY() > eye.getY()) return;
        float amount = frame.fog(eye);
        if (amount <= 0.025F) return;
        GlStateManager.setFog(GlStateManager.FogMode.EXP2);
        event.setDensity(amount * 0.035F);
        event.setCanceled(true);
    }

    @SubscribeEvent
    public void unload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            WeatherState.Frame frame = WeatherState.clientFrame();
            if (frame != null && frame.world == event.getWorld()) WeatherState.client(null);
            tint = null;
        }
    }

    @SubscribeEvent
    public void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        WeatherState.client(null);
        tint = null;
    }

    private static final class Tint {
        final int season;
        final int step;
        final float strength;

        Tint(int season, int step, float strength) {
            this.season = season;
            this.step = step;
            this.strength = strength;
        }

        int apply(int color, int[] targets, float[] blends, float biomeScale,
                  zone.moddev.mc.skysrealisticweather.api.SeasonSnapshot local) {
            int season = local == null ? this.season : local.season.ordinal();
            int step = local == null ? this.step : (int) (local.progress * STEPS);
            double progress = (step + 0.5) / STEPS;
            int adjacent = (season + (progress < 0.5 ? 3 : 1)) % 4;
            double blend = Math.abs(progress - 0.5);
            int a = blend(color, targets[season], Math.min(1, blends[season] * strength * biomeScale));
            int b = blend(color, targets[adjacent], Math.min(1, blends[adjacent] * strength * biomeScale));
            return blend(a, b, blend);
        }

        private static int blend(int a, int b, double strength) {
            int red = Math.round(ClimateProfile.mix(a >> 16 & 255, b >> 16 & 255, strength));
            int green = Math.round(ClimateProfile.mix(a >> 8 & 255, b >> 8 & 255, strength));
            int blue = Math.round(ClimateProfile.mix(a & 255, b & 255, strength));
            return red << 16 | green << 8 | blue;
        }
    }
}
