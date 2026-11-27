package zone.moddev.mc.skysrealisticweather.game;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.*;
import zone.moddev.mc.skysrealisticweather.core.*;

/** Packaged integration proof against real mod blocks, rather than copies of their growth algorithms. */
public final class PlantGrowthProbe {
    private final WorldServer world;
    private final File output = new File("plants");
    private final BlockPos pos = new BlockPos(8, 81, 8);
    private int checks;
    private Object savedAgriSeed;
    private final WeatherSettings startup;

    private PlantGrowthProbe(WorldServer world) { this.world = world; startup = WeatherConfiguration.startupSettings(); }
    public static void run(WorldServer world) throws Exception { new PlantGrowthProbe(world).run(); }

    private void run() throws Exception {
        output.mkdirs();
        try {
            switch (System.getProperty("plants.phase", "contracts")) {
                case "reload": reload(); break;
                case "endurance": endurance(); break;
                case "performance": performance(); break;
                default: contracts(); break;
            }
            System.out.println("PLANT_PASS checks=" + checks + " hooks=" + PlantHookStatus.states());
        } catch (Throwable failure) {
            System.out.println("PLANT_FAILED " + failure);
            failure.printStackTrace();
            throw new IllegalStateException(failure);
        } finally { world.getMinecraftServer().initiateShutdown(); }
    }

    private void require(boolean pass, String message) {
        checks++;
        if (!pass) throw new IllegalStateException(message);
    }

    private void frame(float temperature, FarmingSettings farming) {
        world.setWorldTime(6000);
        float offset = (temperature - Biomes.PLAINS.getFloatTemperature(pos)
                - 2 * ClimateProfile.daylightTemperature(6000) * ClimateProfile.temperatureRange(1)) / 2;
        ClimateProfile.Period period = new ClimateProfile.Period(90, offset, 1, 1, 0);
        WeatherSettings settings = new WeatherSettings(new ClimateProfile(2, period, period, period, period), false, true, true, false)
                .withFarming(farming);
        WeatherState.server(new WeatherState.Frame(world, settings, null));
    }

    private void pad(BlockPos p, IBlockState state) {
        world.getChunkFromBlockCoords(p);
        byte[] biomes = world.getChunkFromBlockCoords(p).getBiomeArray(); Arrays.fill(biomes, (byte) BiomeId.plains());
        for (net.minecraft.util.EnumFacing side : net.minecraft.util.EnumFacing.Plane.HORIZONTAL) world.setBlockToAir(p.offset(side));
        // Clear the previous plant before laying soil: 1.11 farmland rejects a solid block above it.
        world.setBlockToAir(p); world.setBlockToAir(p.up());
        world.setBlockState(p.down(), Blocks.FARMLAND.getDefaultState().withProperty(BlockFarmland.MOISTURE, 7), 2);
        world.setBlockState(p.up(), Blocks.LEAVES.getDefaultState(), 2);
        world.setBlockState(p.east(2), Blocks.GLOWSTONE.getDefaultState(), 2);
        if (state.getBlock() == Blocks.CACTUS || state.getBlock() == Blocks.REEDS || state.getBlock().getRegistryName().getResourceDomain().equals("agricraft")) world.setBlockToAir(p.up());
        if (state.getBlock() == Blocks.CACTUS || state.getBlock() == Blocks.REEDS) world.setBlockState(p.down(), Blocks.SAND.getDefaultState(), 2);
        if (state.getBlock() == Blocks.REEDS) world.setBlockState(p.down().west(), Blocks.WATER.getDefaultState(), 2);
        if (state.getBlock() == Blocks.COCOA) world.setBlockState(p.offset(state.getValue(BlockCocoa.FACING)), Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, BlockPlanks.EnumType.JUNGLE), 2);
        world.setBlockState(p, state, 2);
        world.getChunkFromBlockCoords(p).generateSkylightMap();
        world.setLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, p, 15);
        world.setLightFor(net.minecraft.world.EnumSkyBlock.BLOCK, p.up(), 15);
    }

    private static final class BiomeId { static int plains() { return net.minecraft.world.biome.Biome.getIdForBiome(Biomes.PLAINS); } }

    private static IProperty age(IBlockState state) {
        IProperty p = state.getBlock().getBlockState().getProperty("age");
        if (p == null) throw new IllegalArgumentException("No age property: " + state);
        return p;
    }
    private int ageAt(BlockPos p) { IBlockState s = world.getBlockState(p); return ((Number) s.getValue(age(s))).intValue(); }

    private int attempts(BlockPos p, Block block, long seed, int attempts) {
        world.setBlockState(p, block.getDefaultState(), 2);
        Random random = new Random(seed);
        int changes = 0;
        for (int tick = 0; tick < attempts; tick++) {
            IBlockState before = world.getBlockState(p);
            block.updateTick(world, p, before, random);
            IBlockState after = world.getBlockState(p);
            require(after.getBlock() == block, "growth retains plant " + block.getRegistryName());
            if (before != after) { changes++; world.setBlockState(p, block.getDefaultState(), 2); }
        }
        return changes;
    }

    private void contracts() throws Exception {
        List<Block> plants = new ArrayList<>(Arrays.asList(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS,
                Blocks.PUMPKIN_STEM, Blocks.MELON_STEM, Blocks.COCOA, Blocks.REEDS, Blocks.CACTUS));
        for (Block block : Block.REGISTRY) {
            String name = block.getClass().getName();
            if (name.equals("com.pam.harvestcraft.blocks.growables.BlockPamCrop") || name.equals("com.pam.harvestcraft.blocks.growables.BlockPamFruit")
                    || name.equals("com.pam.harvestcraft.blocks.growables.BlockPamFruitLog") || name.equals("com.blakebr0.mysticalagriculture.blocks.crop.BlockMysticalCrop")
                    || name.equals("com.blakebr0.mysticalagriculture.blocks.crop.BlockInferiumCrop")) plants.add(block);
        }
        try (PrintWriter csv = writer("contracts.csv")) {
            csv.println("plant,profile,native_cold,blocked_cold,ideal_native,ideal_climate,heat");
            for (Block block : plants) {
                pad(pos, block.getDefaultState());
                frame(1.1F, startup.farming);
                PlantGrowthSample sample = WeatherApi.getPlantGrowth(world, pos).get();
                boolean magical = block.getRegistryName().getResourceDomain().equals("mysticalagriculture");
                frame(-0.3F, startup.farming.withEnabled(false));
                int nativeCold = attempts(pos, block, 1911, 600);
                frame(-0.3F, startup.farming);
                int cold = attempts(pos, block, 1911, 600);
                require(nativeCold > 0, "native crop grows: " + block.getRegistryName());
                require(magical ? cold == nativeCold : cold == 0, "cold behaviour " + block.getRegistryName() + "=" + cold);
                FarmingSettings.Resolved resolved = startup.farming.resolve(sample.plantId, block.getDefaultState());
                require(magical || resolved != null && resolved.profile != null, "bundled profile " + sample.plantId);
                float ideal = magical ? 1.1F : (resolved.profile.idealMinimum + resolved.profile.idealMaximum) / 2;
                frame(ideal, startup.farming.withEnabled(false)); int normal = attempts(pos, block, 911, 600);
                frame(ideal, startup.farming); int suitable = attempts(pos, block, 911, 600);
                require(normal == suitable && suitable > 0, "ideal native RNG trace " + sample.plantId);
                frame(3.5F, startup.farming); int hot = attempts(pos, block, 911, 600);
                require(magical ? hot == suitable : hot == 0, "heat behaviour " + sample.plantId);
                if (block instanceof IGrowable) {
                    IGrowable growable = (IGrowable) block;
                    for (int trial = 0; trial < 12; trial++) {
                        world.setBlockState(pos, block.getDefaultState(), 2); frame(-0.3F, startup.farming.withEnabled(false));
                        boolean eligible = growable.canUseBonemeal(world, new Random(trial), pos, world.getBlockState(pos));
                        world.rand.setSeed(trial); if (eligible) growable.grow(world, new Random(trial), pos, world.getBlockState(pos));
                        int nativeAge = ageAt(pos);
                        world.setBlockState(pos, block.getDefaultState(), 2); frame(-0.3F, startup.farming);
                        require(eligible == growable.canUseBonemeal(world, new Random(trial), pos, world.getBlockState(pos)), "fertiliser eligibility " + sample.plantId);
                        world.rand.setSeed(trial); if (eligible) growable.grow(world, new Random(trial), pos, world.getBlockState(pos));
                        require(ageAt(pos) == nativeAge, "direct fertiliser native trace " + sample.plantId);
                    }
                }
                csv.printf(Locale.ROOT, "%s,%s,%d,%d,%d,%d,%d%n", sample.plantId, sample.profileId, nativeCold, cold, normal, suitable, hot);
            }
        }
        require(plants.size() >= 9, "vanilla coverage");
        for (Block block : plants) if (!block.getRegistryName().getResourceDomain().equals("minecraft")) {
            pad(pos, block.getDefaultState());
            boolean magic = block.getRegistryName().getResourceDomain().equals("mysticalagriculture");
            FarmingSettings disabledAdapter = new FarmingSettings(true, false, false, false, false,
                    FarmingSettings.defaultProfiles(), new String[0], Collections.emptyMap());
            frame(-0.3F, disabledAdapter);
            require(attempts(pos, block, 66, 600) > 0, "per-adapter disabled native " + block.getRegistryName());
            if (magic) {
                FarmingSettings optedIn = new FarmingSettings(true, true, true, true, true, FarmingSettings.defaultProfiles(),
                        new String[] {block.getRegistryName() + "=warm"}, Collections.emptyMap());
                frame(-0.3F, optedIn);
                require(attempts(pos, block, 66, 600) == 0, "magical explicit opt-in " + block.getRegistryName());
            }
        }
        pad(pos, Blocks.WHEAT.getDefaultState()); frame(0.225F, startup.farming);
        Random probability = new Random(701); int allowed = 0;
        for (int i = 0; i < 20000; i++) if (PlantGrowthHooks.allow(world, pos, probability)) allowed++;
        require(Math.abs(allowed - 10000) < 500, "half suitability probability " + allowed);
        tooltipContracts(plants);
        mappingContracts();
        environmentContracts();
        saplingFertilizer();
        populationScope();
        if (Loader.isModLoaded("agricraft")) agricraft();
        if (Loader.isModLoaded("forestry")) forestry();
        require(PlantHookStatus.states().values().stream().allMatch(s -> s.equals("attached")), "all present adapters attached " + PlantHookStatus.states());
        migration();
        save();
    }

    private String tooltip(net.minecraft.item.ItemStack stack) {
        net.minecraftforge.common.util.FakePlayer player = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        player.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        net.minecraft.item.ItemStack before = stack.copy(); IBlockState previous = world.getBlockState(pos);
        List<String> lines = new ArrayList<>(); lines.add("Original item tooltip");
        new zone.moddev.mc.skysrealisticweather.client.PlantTooltips().tooltip(
                new net.minecraftforge.event.entity.player.ItemTooltipEvent(stack, player, lines, false));
        require(net.minecraft.item.ItemStack.areItemStacksEqual(before, stack) && world.getBlockState(pos) == previous, "tooltip does not alter inventory or world");
        require(lines.get(0).equals("Original item tooltip"), "existing item tooltip retained");
        return String.join("\n", lines);
    }

    private void tooltipContracts(List<Block> plants) throws Exception {
        pad(pos, Blocks.WHEAT.getDefaultState());
        frame(-0.3F, startup.farming);
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("Too cold"), "seed tooltip paused cold");
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT)).contains("5.6 °C to 21.1 °C"), "harvested wheat profile");
        frame(0.8F, startup.farming);
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("Suitable; normal"), "seed tooltip suitable");
        frame(0.2F, startup.farming);
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("about 43%"), "seed tooltip slowed");
        frame(3.5F, startup.farming);
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("Too hot"), "seed tooltip paused heat");
        frame(.8F, startup.farming.withEnabled(false));
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("rules disabled"), "disabled tooltip native");
        frame(.8F, startup.farming);
        require(tooltip(new net.minecraft.item.ItemStack(Blocks.SAPLING, 1, 1)).contains("5.6 °C to 21.1 °C"), "spruce state selector");
        require(tooltip(new net.minecraft.item.ItemStack(Blocks.SAPLING, 1, 3)).contains("20.0 °C to 37.8 °C"), "jungle state selector");
        require(tooltip(new net.minecraft.item.ItemStack(Items.DYE, 1, 3)).contains("20.0 °C to 37.8 °C"), "cocoa dye metadata");
        require(!tooltip(new net.minecraft.item.ItemStack(Items.DYE, 1, 4)).contains("temperature"), "other dyes are not cocoa");
        require(tooltip(new net.minecraft.item.ItemStack(Items.MELON)).contains("17.8 °C to 34.4 °C"), "melon slice crop profile");
        require(tooltip(new net.minecraft.item.ItemStack(Blocks.PUMPKIN)).contains("17.8 °C to 34.4 °C"), "pumpkin crop profile");
        require(!tooltip(new net.minecraft.item.ItemStack(Items.BREAD)).contains("temperature"), "crafted food is not a crop");
        FarmingSettings custom = new FarmingSettings(true, true, true, true, true, new String[] {"custom,0,0.7,1.2,3"},
                new String[] {"minecraft:wheat=custom"}, Collections.emptyMap());
        frame(.8F, custom);
        zone.moddev.mc.skysrealisticweather.network.WeatherMessage outgoing = new zone.moddev.mc.skysrealisticweather.network.WeatherMessage(WeatherState.frame(world));
        io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
        try {
            outgoing.toBytes(buffer);
            zone.moddev.mc.skysrealisticweather.network.WeatherMessage incoming = new zone.moddev.mc.skysrealisticweather.network.WeatherMessage(); incoming.fromBytes(buffer);
            WeatherState.server(new WeatherState.Frame(world, incoming.settings, null));
            require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("12.2 °C to 23.3 °C"), "tooltip uses synchronized server custom profiles");
        } finally { buffer.release(); }
        frame(.8F, startup.farming);
        WeatherConfiguration.file().getCategory(WeatherConfiguration.CLIENT).get("temperatureUnit").set("FAHRENHEIT");
        try {
            String text = tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS));
            require(text.contains("42.0 °F to 70.0 °F") && text.contains("Here: 58.0 °F"), "Fahrenheit tooltip preference applies immediately");
            require(ClimateReadout.compact(WeatherApi.getClimate(world, pos).get(), WeatherApi.getWeather(world, pos).get()).contains("58.0 °F"), "compact HUD uses Fahrenheit preference");
            require(ClimateReadout.details(WeatherApi.getClimate(world, pos).get(), WeatherApi.getWeather(world, pos).get(), null).stream()
                    .anyMatch(line -> line.startsWith("Temp ") && line.contains("58.0 °F")), "F3 HUD uses Fahrenheit preference");
            require(WeatherApi.getTemperature(world, pos).getAsDouble() == (double) .8F, "display units leave API temperature in MC units");
        } finally { WeatherConfiguration.file().getCategory(WeatherConfiguration.CLIENT).get("temperatureUnit").set("CELSIUS"); }
        world.rand.setSeed(771); tooltip(new net.minecraft.item.ItemStack(Items.WHEAT));
        require(world.rand.nextLong() == new Random(771).nextLong(), "tooltip consumes no world random numbers");
        int resolved = 0;
        for (Block block : plants) {
            IBlockState state = block.getDefaultState();
            IProperty property = age(state);
            IBlockState mature = state.withProperty(property, (Comparable) Collections.max(property.getAllowedValues()));
            net.minecraft.item.Item product = block.getItemDropped(mature, new Random(0), 0);
            if (product != null && product != Items.AIR) {
                net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(product, 1, block.damageDropped(mature));
                require(PlantItems.resolve(stack, world, pos).stream().anyMatch(p -> p.id.equals(block.getRegistryName().toString())), "harvest identity " + block.getRegistryName());
                require(tooltip(stack).contains("Here:"), "harvest tooltip " + block.getRegistryName()); resolved++;
            }
            net.minecraft.item.Item seed = block.getItemDropped(state, new Random(0), 0);
            if (seed != null && seed != Items.AIR) {
                net.minecraft.item.ItemStack stack = new net.minecraft.item.ItemStack(seed, 1, block.damageDropped(state));
                require(PlantItems.resolve(stack, world, pos).stream().anyMatch(p -> p.id.equals(block.getRegistryName().toString())), "seed identity " + block.getRegistryName());
            }
        }
        WeatherState.server(null);
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("unavailable"), "no unsynced climate prediction");
        frame(.8F, startup.farming);
        System.out.println("PLANT_TOOLTIPS PASS harvested=" + resolved);
    }

    private void migration() throws Exception {
        File original = WeatherConfiguration.file().getConfigFile();
        String baseline = new String(Files.readAllBytes(original.toPath()), StandardCharsets.UTF_8);
        try { for(String version:new String[]{"5","6","7"}) {
        File legacy = new File(output,"v"+version+".cfg");
        String content = baseline.replaceFirst("~CONFIG_VERSION: [0-9]+", "~CONFIG_VERSION: "+version);
        Files.write(legacy.toPath(), content.getBytes(StandardCharsets.UTF_8));
        net.minecraftforge.common.config.Configuration old = new net.minecraftforge.common.config.Configuration(legacy, version);
        old.getCategory("presets.burlington.winter").get("temperature").set("-1.6");
        String[] custom = {"COLD_SNAP,12,0,12,75"};
        old.getCategory("presets.burlington").get("weatherEvents").set(custom);
        old.getCategory(FarmingSettings.CATEGORY).get("mappings").set(new String[] {"example:crop=exempt"}); old.save();
            require(new String(Files.readAllBytes(legacy.toPath()),StandardCharsets.UTF_8).contains("~CONFIG_VERSION: "+version),"actual legacy version "+version+" fixture");
            WeatherConfiguration.load(legacy);
            require(WeatherConfiguration.preset("burlington").profile.period(3).temperature == -1.6F, "legacy climate edit retained");
            require(Arrays.equals(custom, WeatherConfiguration.preset("burlington").weather.rows()), "legacy event edit retained");
            require(Arrays.equals(new String[] {"example:crop=exempt"}, WeatherConfiguration.startupSettings().farming.mappingRows()), "legacy optional plant overrides retained");
            require(new String(Files.readAllBytes(legacy.toPath()), StandardCharsets.UTF_8).contains("~CONFIG_VERSION: 8"), "legacy upgrades to v8");
        }} finally { WeatherConfiguration.load(original); }
    }

    private void mappingContracts() throws Exception {
        pad(pos, Blocks.WHEAT.getDefaultState()); frame(-0.3F, startup.farming);
        BlockEvent.CropGrowEvent.Pre deny = new BlockEvent.CropGrowEvent.Pre(world, pos, world.getBlockState(pos));
        deny.setResult(Event.Result.DENY); new PlantGrowthHooks().crop(deny); require(deny.getResult() == Event.Result.DENY, "another mod DENY retained");
        deny.setResult(Event.Result.ALLOW); new PlantGrowthHooks().crop(deny); require(deny.getResult() == Event.Result.ALLOW, "another mod ALLOW retained");
        WeatherState.server(new WeatherState.Frame(world, WeatherState.frame(world).settings.withOptions(true, true, true, true, true), null));
        require(attempts(pos, Blocks.WHEAT, 123, 600) > 0, "passive growth native");
        frame(-0.3F, startup.farming);
        BlockPos unloaded = new BlockPos(900008, 81, 900008);
        require(!world.isBlockLoaded(unloaded, false) && !WeatherApi.getPlantGrowth(world, unloaded).isPresent()
                && !world.isBlockLoaded(unloaded, false), "plant API never loads terrain");
        require(!WeatherApi.getPlantGrowth(world.getMinecraftServer().getWorld(-1), pos).isPresent(), "unsupported dimension query");
        Map<String, PlantGrowthProfile> registration = Collections.singletonMap("minecraft:wheat", PlantGrowthProfile.EXEMPT);
        FarmingSettings override = new FarmingSettings(true, true, true, true, true, FarmingSettings.defaultProfiles(), new String[0], registration);
        frame(-0.3F, override); require(WeatherApi.getPlantGrowth(world, pos).get().naturalGrowthMultiplier == 1, "API overrides bundled");
        override = new FarmingSettings(true, true, true, true, true, FarmingSettings.defaultProfiles(), new String[] {"minecraft:wheat=warm"}, registration);
        frame(-0.3F, override); require(WeatherApi.getPlantGrowth(world, pos).get().naturalGrowthMultiplier == 0, "config overrides API");
        for (BlockPlanks.EnumType type : BlockPlanks.EnumType.values()) {
            IBlockState sapling = Blocks.SAPLING.getDefaultState().withProperty(BlockSapling.TYPE, type);
            pad(pos, sapling); frame(-0.3F, startup.farming);
            require(!net.minecraftforge.event.terraingen.TerrainGen.saplingGrowTree(world, new Random(1), pos), "cold sapling " + type);
            FarmingSettings.Resolved r = startup.farming.resolve("minecraft:sapling", sapling);
            frame((r.profile.idealMinimum + r.profile.idealMaximum) / 2, startup.farming);
            require(net.minecraftforge.event.terraingen.TerrainGen.saplingGrowTree(world, new Random(1), pos), "ideal sapling " + type);
        }
        pad(pos, Blocks.WHEAT.getDefaultState()); frame(-0.3F, startup.farming);
        require(world.getMinecraftServer().getCommandManager().executeCommand(world.getMinecraftServer(), "skyweather crop 8 81 8") == 1, "operator crop coordinate command");
        net.minecraftforge.common.util.FakePlayer ordinary = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(world);
        require(!zone.moddev.mc.skysrealisticweather.admin.WeatherCommand.permitted(ordinary), "non-operator crop access denied");
        List<String> messages = new ArrayList<>();
        net.minecraftforge.common.util.FakePlayer viewer = new net.minecraftforge.common.util.FakePlayer(world,
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("0a076ef8-c6b8-4c66-a33e-c6496f36c109"), "WeatherInspector")) {
            @Override public boolean canUseCommand(int level, String command) { return level <= 2; }
            @Override public void sendMessage(net.minecraft.util.text.ITextComponent message) { messages.add(message.getUnformattedText()); }
        };
        viewer.setPositionAndRotation(pos.getX() + 0.5, pos.getY() + 0.05 - viewer.getEyeHeight(), pos.getZ() - 2.5, 0, 0);
        ((net.minecraft.command.ICommand) new zone.moddev.mc.skysrealisticweather.admin.WeatherCommand()).execute(world.getMinecraftServer(), viewer, new String[] {"crop"});
        require(messages.stream().anyMatch(message -> message.contains("minecraft:wheat") && message.contains("natural growth") && message.contains("°C")), "server-side crop crosshair command");
        new zone.moddev.mc.skysrealisticweather.network.TemperaturePreferenceMessage(TemperatureDisplay.Unit.FAHRENHEIT).apply(viewer);
        messages.clear();
        ((net.minecraft.command.ICommand) new zone.moddev.mc.skysrealisticweather.admin.WeatherCommand()).execute(world.getMinecraftServer(), viewer, new String[] {"crop"});
        ((net.minecraft.command.ICommand) new zone.moddev.mc.skysrealisticweather.admin.WeatherCommand()).execute(world.getMinecraftServer(), viewer, new String[] {"climate", "8", "81", "8"});
        require(messages.stream().anyMatch(message -> message.contains("natural growth") && message.contains("°F"))
                && messages.stream().anyMatch(message -> message.startsWith("Temp ") && message.contains("°F")), "server commands respect per-player Fahrenheit preference");
    }

    private void environmentContracts() throws Exception {
        pad(pos, Blocks.WHEAT.getDefaultState());
        WeatherSettings settings = startup.withMode(ClimateMode.PER_BIOME, startup.biomes);
        world.setWorldTime(365L * 24000 + 6000);
        WeatherState.server(new WeatherState.Frame(world, settings, null));
        byte[] biomes = world.getChunkFromBlockCoords(pos).getBiomeArray();
        Arrays.fill(biomes, (byte) net.minecraft.world.biome.Biome.getIdForBiome(Biomes.JUNGLE));
        PlantGrowthSample jungle = WeatherApi.getPlantGrowth(world, pos).get();
        require(jungle.temperature == (float) WeatherApi.getTemperature(world, pos).getAsDouble(), "per-biome crop temperature matches weather");
        Arrays.fill(biomes, (byte) net.minecraft.world.biome.Biome.getIdForBiome(Biomes.ICE_PLAINS));
        PlantGrowthSample arctic = WeatherApi.getPlantGrowth(world, pos).get();
        require(arctic.temperature < jungle.temperature && arctic.naturalGrowthMultiplier < jungle.naturalGrowthMultiplier, "native Arctic crop climate remains colder than tropical");
        BlockPos high = new BlockPos(8, 200, 8); pad(high, Blocks.WHEAT.getDefaultState());
        pad(pos, Blocks.WHEAT.getDefaultState()); frame(0.8F, startup.farming);
        require(WeatherApi.getPlantGrowth(world, high).get().temperature < WeatherApi.getPlantGrowth(world, pos).get().temperature, "altitude affects plant temperature");
        // Existing water and rain mechanics remain the source of soil hydration.
        BlockPos soil = new BlockPos(24, 80, 24); world.getChunkFromBlockCoords(soil);
        Arrays.fill(world.getChunkFromBlockCoords(soil).getBiomeArray(), (byte) net.minecraft.world.biome.Biome.getIdForBiome(Biomes.PLAINS));
        world.setBlockState(soil.up(), Blocks.WHEAT.getDefaultState(), 2);
        world.setBlockState(soil.east(), Blocks.WATER.getDefaultState(), 2);
        world.setBlockState(soil, Blocks.FARMLAND.getDefaultState().withProperty(BlockFarmland.MOISTURE, 0), 2);
        Blocks.FARMLAND.updateTick(world, soil, world.getBlockState(soil), new Random(1));
        require(world.getBlockState(soil).getValue(BlockFarmland.MOISTURE) == 7, "existing irrigation hydrates soil");
        world.setBlockState(soil.east(), Blocks.ICE.getDefaultState(), 2);
        world.getWorldInfo().setRaining(false); world.getWorldInfo().setRainTime(10000);
        for (int i = 0; i < 100; i++) world.updateWeatherBody();
        Blocks.FARMLAND.updateTick(world, soil, world.getBlockState(soil), new Random(1));
        require(world.getBlockState(soil).getValue(BlockFarmland.MOISTURE) == 6, "frozen irrigation no longer hydrates soil");
    }

    private void saplingFertilizer() {
        List<Block> trees = new ArrayList<>(Collections.singletonList(Blocks.SAPLING));
        Block apple = Block.REGISTRY.getObject(new ResourceLocation("harvestcraft:apple_sapling"));
        if (apple != null && apple != Blocks.AIR) trees.add(apple);
        BlockPos base = new BlockPos(200, 81, 200);
        for (Block tree : trees) {
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) for (int y = 0; y <= 14; y++) {
                BlockPos at = base.add(x, y, z); world.getChunkFromBlockCoords(at); world.setBlockState(at, Blocks.AIR.getDefaultState(), 2);
            }
            Arrays.fill(world.getChunkFromBlockCoords(base).getBiomeArray(), (byte) BiomeId.plains());
            world.setBlockState(base.down(), Blocks.DIRT.getDefaultState(), 2); world.setBlockState(base, tree.getDefaultState(), 2);
            frame(-0.3F, startup.farming);
            for (int attempt = 0; attempt < 20 && world.getBlockState(base).getBlock() == tree; attempt++)
                ((IGrowable) tree).grow(world, new Random(attempt), base, world.getBlockState(base));
            require(world.getBlockState(base).getBlock() == Blocks.LOG || world.getBlockState(base).getBlock() == Blocks.LOG2,
                    "sapling direct fertiliser grows a tree in cold climate " + tree.getRegistryName());
        }
    }

    private void populationScope() {
        IBlockState sapling=Blocks.SAPLING.getDefaultState().withProperty(BlockSapling.TYPE,BlockPlanks.EnumType.ACACIA);
        pad(pos,sapling);frame(-0.3F,startup.farming);
        require(!net.minecraftforge.event.terraingen.TerrainGen.saplingGrowTree(world,new Random(1),pos),"natural cold tree remains gated");
        net.minecraft.world.chunk.IChunkGenerator inner=populationGenerator(() -> {
            require(net.minecraftforge.event.terraingen.TerrainGen.saplingGrowTree(world,new Random(1),pos),"nested population keeps native trees");
            throw new IllegalStateException("population fixture");
        });
        net.minecraft.world.chunk.IChunkGenerator outer=populationGenerator(() -> {
            require(PlantGrowthHooks.allow(world,pos,new Random(1)),"population bypasses farming gate");
            try{PlantGrowthHooks.populateChunk(inner,0,0,world.getChunkFromBlockCoords(pos));}
            catch(IllegalStateException expected){require(expected.getMessage().equals("population fixture"),"native generator exception preserved");}
            require(net.minecraftforge.event.terraingen.TerrainGen.saplingGrowTree(world,new Random(1),pos),"outer population scope restored after nested failure");
        });
        PlantGrowthHooks.populateChunk(outer,0,0,world.getChunkFromBlockCoords(pos));
        require(!net.minecraftforge.event.terraingen.TerrainGen.saplingGrowTree(world,new Random(1),pos),"natural gate restored after population");
        try{PlantGrowthHooks.populateChunk(inner,0,0,world.getChunkFromBlockCoords(pos));}
        catch(IllegalStateException expected){require(expected.getMessage().equals("population fixture"),"top-level population exception preserved");}
        require(!PlantGrowthHooks.allow(world,pos,new Random(1)),"population scope cleared after failure");
    }

    private static net.minecraft.world.chunk.IChunkGenerator populationGenerator(Runnable callback) {
        return (net.minecraft.world.chunk.IChunkGenerator)Proxy.newProxyInstance(net.minecraft.world.chunk.IChunkGenerator.class.getClassLoader(),
                new Class[]{net.minecraft.world.chunk.IChunkGenerator.class},(proxy,method,args) -> {
                    if(method.getName().equals("populate")||method.getName().equals("func_185931_b")){callback.run();return null;}
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) {
            method.setAccessible(true); return method.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass() + "." + name);
    }

    private void agricraft() throws Exception {
        Object registry = Class.forName("com.infinityraider.agricraft.api.v1.AgriApi").getMethod("getPlantRegistry").invoke(null);
        Object plant = ((Optional<?>) invoke(registry, "get", "wheat_plant")).get();
        Class<?> plantType = Class.forName("com.infinityraider.agricraft.api.v1.plant.IAgriPlant");
        Class<?> statType = Class.forName("com.infinityraider.agricraft.api.v1.stat.IAgriStat");
        Object stat = Class.forName("com.infinityraider.agricraft.farming.PlantStats").getConstructor().newInstance();
        Class<?> seedType = Class.forName("com.infinityraider.agricraft.api.v1.seed.AgriSeed");
        Object seed = seedType.getConstructor(plantType, statType).newInstance(plant, stat);
        savedAgriSeed = seed;
        frame(-0.3F, startup.farming);
        net.minecraft.item.ItemStack seedStack = (net.minecraft.item.ItemStack) invoke(seed, "toStack");
        require(PlantItems.resolve(seedStack, world, pos).get(0).id.equals("agricraft:wheat_plant"), "AgriCraft NBT tooltip identity");
        require(tooltip(seedStack).contains("5.6 °C to 21.1 °C") && tooltip(seedStack).contains("Too cold"), "AgriCraft seed tooltip");
        Block block = Block.REGISTRY.getObject(new ResourceLocation("agricraft:crop"));
        pad(pos, block.getDefaultState()); Object crop = world.getTileEntity(pos);
        require(crop != null, "AgriCraft crop tile");
        invoke(crop, "setSeed", seed);
        require((Boolean) invoke(crop, "isFertile"), "AgriCraft native light, soil and air eligibility");
        frame(-0.3F, startup.farming);
        for (int i = 0; i < 100; i++) block.updateTick(world, pos, world.getBlockState(pos), world.rand);
        require(((Number) invoke(crop, "getGrowthStage")).intValue() == 0, "AgriCraft cold natural growth paused");
        require(WeatherApi.getPlantGrowth(world, pos).get().plantId.equals("agricraft:wheat_plant"), "AgriCraft plant identity");
        Class<?> fertilizerType = Class.forName("com.infinityraider.agricraft.api.v1.fertilizer.IAgriFertilizer");
        Object fertilizer = Proxy.newProxyInstance(fertilizerType.getClassLoader(), new Class[] {fertilizerType}, (proxy, method, args) -> method.getReturnType() == boolean.class ? true : null);
        require((Boolean) invoke(crop, "acceptsFertilizer", fertilizer), "AgriCraft native fertiliser eligibility");
        for (int i = 0; i < 100; i++) invoke(crop, "onApplyFertilizer", fertilizer, world.rand);
        require(((Number) invoke(crop, "getGrowthStage")).intValue() > 0, "AgriCraft direct fertiliser callback bypass");
        invoke(crop, "setGrowthStage", 0); frame(0.8F, startup.farming);
        for (int i = 0; i < 100; i++) block.updateTick(world, pos, world.getBlockState(pos), world.rand);
        require(((Number) invoke(crop, "getGrowthStage")).intValue() > 0, "AgriCraft suitable natural growth");
        require(invoke(invoke(crop, "getSeed"), "getStat").equals(stat), "AgriCraft genetics retained");
        System.out.println("PLANT_AGRICRAFT PASS");
    }

    private void forestry() throws Exception {
        pad(pos, Blocks.WHEAT.getDefaultState()); frame(1.0F, startup.farming);
        Object manager = Class.forName("forestry.api.core.ForestryAPI").getField("climateManager").get(null);
        require(Math.abs(((Number) invoke(invoke(manager, "getInfo", world, pos), "getTemperature")).floatValue() - WeatherApi.getTemperature(world, pos).getAsDouble()) < 0.0001, "Forestry ambient temperature bridge");
        require(Math.abs(((Number) invoke(invoke(manager, "getInfo", world, pos), "getHumidity")).floatValue() - 0.5F) < 0.0001, "Forestry ambient humidity proxy");
        Class<?> positionType = Class.forName("forestry.api.climate.IClimatePosition");
        Class<?> regionType = Class.forName("forestry.api.climate.IClimateRegion");
        Object position = Proxy.newProxyInstance(positionType.getClassLoader(), new Class[] {positionType}, (proxy, method, args) -> {
            switch (method.getName()) { case "getTemperature": return 0.8F; case "getHumidity": return 0.73F;
                case "getInfo": return invoke(manager, "createInfo", 0.8F, 0.73F);
                case "equals": return proxy == args[0]; case "hashCode": return System.identityHashCode(proxy); default: return null; }
        });
        Object region = Proxy.newProxyInstance(regionType.getClassLoader(), new Class[] {regionType}, (proxy, method, args) -> {
            switch (method.getName()) { case "getWorld": return world; case "getPosition": return pos.equals(args[0]) ? position : null; case "getPositions": return Collections.singletonMap(pos, position);
                case "getSources": return Collections.emptyList(); case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy); default: return null; }
        });
        invoke(manager, "addRegion", region);
        frame(-0.3F, startup.farming);
        require(((Number) invoke(invoke(manager, "getInfo", world, pos), "getTemperature")).floatValue() == 0.8F, "Forestry controlled temperature retained");
        require(((Number) invoke(invoke(manager, "getInfo", world, pos), "getHumidity")).floatValue() == 0.73F, "Forestry controlled humidity retained");
        PlantGrowthSample sample = WeatherApi.getPlantGrowth(world, pos).get();
        require(sample.controlledClimate && sample.naturalGrowthMultiplier == 1, "ordinary crops recognise controlled climate");
        require(tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("controlled climate")
                && tooltip(new net.minecraft.item.ItemStack(Items.WHEAT_SEEDS)).contains("Suitable; normal"), "tooltip uses Forestry controlled temperature");
        invoke(manager, "removeRegion", region);
        frame(1.0F, startup.farming.withEnabled(false));
        require(((Number) invoke(invoke(manager, "getInfo", world, pos), "getTemperature")).floatValue() == Biomes.PLAINS.getTemperature(), "Forestry native fallback with master disabled");
        System.out.println("PLANT_FORESTRY PASS");
    }

    private void save() throws Exception {
        pad(pos, Blocks.WHEAT.getDefaultState().withProperty(BlockCrops.AGE, 5));
        JsonObject data = new JsonObject(); data.addProperty("age", 5); data.addProperty("block", "minecraft:wheat");
        if (savedAgriSeed != null) {
            BlockPos agriPos = new BlockPos(12, 81, 8);
            pad(agriPos, Block.REGISTRY.getObject(new ResourceLocation("agricraft:crop")).getDefaultState());
            Object crop = world.getTileEntity(agriPos); invoke(crop, "setSeed", savedAgriSeed); invoke(crop, "setGrowthStage", 4);
            data.addProperty("agricraft", true);
            Object stat = invoke(savedAgriSeed, "getStat");
            data.addProperty("agriStats", invoke(stat, "getGrowth") + "," + invoke(stat, "getGain") + "," + invoke(stat, "getStrength"));
        }
        Files.write(new File(output, "saved.json").toPath(), new Gson().toJson(data).getBytes(StandardCharsets.UTF_8));
        world.getMinecraftServer().saveAllWorlds(false);
    }
    private void reload() throws Exception {
        world.getChunkFromBlockCoords(pos);
        JsonObject data = new JsonParser().parse(new InputStreamReader(new FileInputStream(new File(output, "saved.json")), StandardCharsets.UTF_8)).getAsJsonObject();
        require(world.getBlockState(pos).getBlock().getRegistryName().toString().equals(data.get("block").getAsString()) && ageAt(pos) == data.get("age").getAsInt(), "crop survives physical process reload");
        if (data.has("agricraft")) {
            Object crop = world.getTileEntity(new BlockPos(12, 81, 8));
            require(crop != null && ((Number) invoke(crop, "getGrowthStage")).intValue() == 4, "AgriCraft stage survives process reload");
            Object seed = invoke(crop, "getSeed"), stat = invoke(seed, "getStat");
            require(invoke(invoke(seed, "getPlant"), "getId").equals("wheat_plant"), "AgriCraft seed survives reload");
            require(data.get("agriStats").getAsString().equals(invoke(stat, "getGrowth") + "," + invoke(stat, "getGain") + "," + invoke(stat, "getStrength")), "AgriCraft genetics survive reload");
        }
    }

    private PrintWriter writer(String name) throws Exception { return new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(output, name)), StandardCharsets.UTF_8)); }

    private void endurance() throws Exception { PlantEnduranceProbe.run(world); }
    private void performance() throws Exception { PlantEnduranceProbe.performance(world); }
}
