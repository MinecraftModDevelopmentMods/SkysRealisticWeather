package zone.moddev.mc.skysrealisticweather.admin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import zone.moddev.mc.skysrealisticweather.WeatherMod;
import zone.moddev.mc.skysrealisticweather.api.ClimateSample;
import zone.moddev.mc.skysrealisticweather.api.ClimateMode;
import zone.moddev.mc.skysrealisticweather.api.LocalWeather;
import zone.moddev.mc.skysrealisticweather.api.Season;
import zone.moddev.mc.skysrealisticweather.api.SeasonSnapshot;
import zone.moddev.mc.skysrealisticweather.api.WeatherApi;
import zone.moddev.mc.skysrealisticweather.api.WeatherEpisode;
import zone.moddev.mc.skysrealisticweather.api.WeatherKind;
import zone.moddev.mc.skysrealisticweather.climate.ClimateProfile;
import zone.moddev.mc.skysrealisticweather.climate.Preset;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.climate.WeatherSettings;
import zone.moddev.mc.skysrealisticweather.game.ServerWeather;
import zone.moddev.mc.skysrealisticweather.game.ClimateReadout;
import zone.moddev.mc.skysrealisticweather.network.WeatherAdminMessage;

public final class WeatherCommand extends CommandBase {
    public static final String NAME = "skyweather";
    private static final int OPERATOR_LEVEL = 2;
    private static final String[] ACTIONS = {"status", "climate", "crop", "gui", "season", "time", "weather", "preset", "presets",
            "strength", "phase", "option", "mode", "reset", "world", "validate", "dump"};
    private static final String[] SEASONS = {"spring", "summer", "autumn", "winter"};
    private static final String[] OPTIONS = {"passive", "surface", "foliage", "fog", "scheduled", "plants"};
    private static final String[] TIMES = {"dawn", "noon", "dusk", "midnight"};
    private static final String USAGE = "/skyweather <status|climate|crop|gui|season|time|weather|preset|presets|strength|phase|option|mode|reset|world|validate|dump>";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return OPERATOR_LEVEL;
    }

    public static boolean permitted(ICommandSender sender) {
        return sender.canUseCommand(OPERATOR_LEVEL, NAME);
    }

    public static boolean open(EntityPlayerMP player) {
        if (!permitted(player)) {
            player.sendMessage(new TextComponentString("The Weather Controller requires operator permission."));
            return false;
        }
        try {
            ServerWeather weather = requireOverworld(player);
            sendView(player, weather, true, false, "");
            return true;
        } catch (CommandException unavailable) {
            player.sendMessage(new TextComponentString(unavailable.getMessage()));
            return false;
        }
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!permitted(sender)) throw new CommandException("commands.generic.permission");
        ServerWeather weather = requireOverworld(sender);
        try {
            String result = run(weather, sender, args);
            if (result == null) return;
            sender.sendMessage(new TextComponentString(result));
            if (sender instanceof EntityPlayerMP) sendView((EntityPlayerMP) sender, weather, false, false, result);
        } catch (ArithmeticException overflow) {
            throw new CommandException("The requested date is outside Minecraft's clock range.");
        } catch (IllegalStateException | IllegalArgumentException invalid) {
            throw new CommandException(invalid.getMessage());
        } catch (CommandException invalid) {
            if (sender instanceof EntityPlayerMP) {
                String message = new TextComponentTranslation(invalid.getMessage(), invalid.getErrorObjects()).getUnformattedText();
                sendView((EntityPlayerMP) sender, weather, false, true, message);
            }
            throw invalid;
        }
    }

    private static ServerWeather requireOverworld(ICommandSender sender) throws CommandException {
        ServerWeather weather = ServerWeather.INSTANCE;
        if (weather.world() == null || sender.getEntityWorld() != weather.world()) {
            throw new CommandException("Weather controls are available in the Overworld.");
        }
        return weather;
    }

    private static void sendView(EntityPlayerMP player, ServerWeather weather, boolean open, boolean error, String message) {
        WeatherMod.NETWORK.sendTo(new WeatherAdminMessage(weather, open, error, message), player);
    }

    private static String run(ServerWeather weather, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) return status(weather);
        switch (args[0]) {
            case "validate":
            case "dump":
                arity(args,1,1,"/skyweather "+args[0]);
                java.util.Map<String,Object> report=PackDiagnostics.collect(weather);
                if(args[0].equals("validate"))return PackDiagnostics.summary(report);
                try { return "Weather report saved to "+PackDiagnostics.dump(weather,report); }
                catch(java.io.IOException failed){throw new CommandException("Cannot write weather report: "+failed.getMessage());}
            case "world":
                arity(args,2,2,"/skyweather world <save|discard|reset>");
                switch(args[1]) {
                    case "save": weather.saveWorld(); return "Saved explicit weather overrides to this world.";
                    case "discard": weather.restore(); return "Discarded unsaved weather previews.";
                    case "reset": weather.resetWorld(); return "Removed world overrides; using pack defaults.";
                    default: throw new WrongUsageException("/skyweather world <save|discard|reset>");
                }
            case "mode":
                arity(args, 2, 2, "/skyweather mode <per_biome|universal|real_world>");
                try {
                    ClimateMode mode = ClimateMode.valueOf(args[1].toUpperCase(Locale.ROOT));
                    if (mode == ClimateMode.REAL_WORLD && WeatherConfiguration.startupLocation() == null) {
                        throw new CommandException("Configure a real-world location and select Real World Climate in the mod config first.");
                    }
                    weather.useMode(mode);
                    return "Previewing " + mode.label + ".";
                } catch (IllegalArgumentException invalid) {
                    throw new CommandException("Choose per_biome, universal or real_world.");
                }
            case "status":
                arity(args, 1, 1, "/skyweather status");
                return status(weather);
            case "crop":
                localCrop(weather, sender, args);
                return null;
            case "climate":
                localClimate(weather, sender, args);
                return null;
            case "gui":
                arity(args, 1, 1, "/skyweather gui");
                open(getCommandSenderAsPlayer(sender));
                return null;
            case "season":
                return season(weather, args);
            case "time":
                return time(weather, args);
            case "weather":
                return force(weather, args);
            case "presets":
                arity(args, 1, 1, "/skyweather presets");
                return "Weather presets  " + String.join(", ", presetIds());
            case "preset":
                arity(args, 2, 2, "/skyweather preset <id>");
                Preset preset = WeatherConfiguration.preset(args[1]);
                if (preset == null) throw new CommandException("Unknown preset %s. Use /skyweather presets.", args[1]);
                weather.usePreset(preset);
                return "Previewing " + preset.label + ".";
            case "strength":
            case "phase":
                return climate(weather, args);
            case "option":
                return option(weather, args);
            case "reset":
                arity(args, 1, 1, "/skyweather reset");
                weather.restore();
                return "Restored saved weather settings; unsaved previews discarded.";
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    private static void localClimate(ServerWeather weather, ICommandSender sender, String[] args) throws CommandException {
        Entity entity = sender.getCommandSenderEntity();
        BlockPos position;
        if (args.length == 1) {
            position = entity == null ? sender.getPosition() : ClimateReadout.eyes(entity);
        } else if (args.length == 4) {
            position = parseBlockPos(sender, args, 1, false);
        } else {
            throw new WrongUsageException("/skyweather climate [x y z]");
        }
        ClimateSample sample = WeatherApi.getClimate(weather.world(), position).orElseThrow(
                () -> new CommandException("Climate is unavailable at that position. Choose a loaded Overworld chunk."));
        LocalWeather local = WeatherApi.getWeather(weather.world(), position).get();
        WeatherEpisode episode = WeatherApi.getWeatherEpisode(weather.world(), position).orElse(null);
        sender.sendMessage(new TextComponentString(String.format(Locale.ROOT, "Local climate at %d %d %d",
                position.getX(), position.getY(), position.getZ())));
        for (String line : ClimateReadout.details(sample, local, episode, zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.forSender(sender))) {
            sender.sendMessage(new TextComponentString(line));
        }
        WeatherApi.getPopulationPolicy(weather.world(), weather.world().getBiome(position)).ifPresent(policy ->
                sender.sendMessage(new TextComponentString("Population surface policy  " + ClimateReadout.label(policy))));
        WeatherApi.getTemperatureSample(weather.world(),position).ifPresent(value -> sender.sendMessage(new TextComponentString(
                ClimateReadout.temperature(value,zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.forSender(sender)))));
    }

    private static String season(ServerWeather weather, String[] args) throws CommandException {
        if (args.length == 1) return status(weather);
        String usage = "/skyweather season <next|previous|set <season> [day]|advance <days>>";
        long now = weather.calendarTime();
        ClimateProfile profile = weather.settings().profile;
        long target;
        switch (args[1]) {
            case "next":
            case "previous":
                arity(args, 2, 2, usage);
                target = SeasonClock.step(now, profile, "next".equals(args[1]));
                break;
            case "set":
                arity(args, 3, 4, usage);
                Season phase = season(args[2]);
                int day = args.length == 4 ? parseInt(args[3], 1, profile.period(phase.ordinal()).days) : 1;
                target = SeasonClock.phase(now, profile, phase, day);
                break;
            case "advance":
                arity(args, 3, 3, usage);
                target = SeasonClock.advance(now, parseInt(args[2], 1));
                break;
            default:
                throw new WrongUsageException(usage);
        }
        if (target < 0) throw new CommandException("The requested season is before the start of this world.");
        weather.setCalendar(target);
        return status(weather);
    }

    private static String time(ServerWeather weather, String[] args) throws CommandException {
        arity(args, 2, 2, "/skyweather time <dawn|noon|dusk|midnight|0-23999>");
        int index = Arrays.asList(TIMES).indexOf(args[1]);
        int tick = index < 0 ? parseInt(args[1], 0, ClimateProfile.TICKS_PER_DAY - 1) : index * 6000;
        weather.setDaylight(SeasonClock.timeOfDay(weather.world().getWorldTime(), tick));
        return "Time of day set to " + tick + ".";
    }

    private static String force(ServerWeather weather, String[] args) throws CommandException {
        String usage = "/skyweather weather <auto|clear|rain|thunder|event> [seconds] [intensity] [dry]";
        arity(args, 2, 5, usage);
        if ("auto".equals(args[1])) {
            arity(args, 2, 2, usage);
            weather.automatic();
            return "Automatic weather resumed.";
        }
        if (!weather.active()) throw new CommandException("Activate the mod with /skyweather option passive false.");
        WeatherKind kind = kind(args[1]);
        if (kind == null) arity(args, 2, 3, usage);
        int seconds = args.length >= 3 ? parseInt(args[2], 1, WeatherEpisode.MAX_DURATION / 20) : 90;
        float peak = args.length >= 4 ? number(args[3], 0.01F, 1) : 0.85F;
        if (args.length == 5 && !"dry".equals(args[4])) throw new WrongUsageException(usage);
        weather.force(kind, seconds, peak, args.length == 5);
        return "Previewing " + label(kind) + " for " + seconds + " seconds.";
    }

    private static WeatherKind kind(String value) throws CommandException {
        switch (value) {
            case "clear": return null;
            case "rain": return WeatherKind.FRONTAL_PRECIPITATION;
            case "thunder": return WeatherKind.THUNDERSTORM;
            default:
                try {
                    return WeatherKind.valueOf(value.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException invalid) {
                    throw new CommandException("Unknown weather event %s. Press Tab for event IDs.", value);
                }
        }
    }

    public static String label(WeatherKind kind) {
        return kind == null ? "Clear" : ClimateReadout.label(kind);
    }

    private static String climate(ServerWeather weather, String[] args) throws CommandException {
        ClimateProfile old = weather.settings().profile;
        ClimateProfile.Period[] periods = new ClimateProfile.Period[ClimateProfile.SEASON_COUNT];
        for (int index = 0; index < periods.length; index++) periods[index] = old.period(index);
        if ("strength".equals(args[0])) {
            arity(args, 2, 2, "/skyweather strength <0-2>");
            weather.editClimate(new ClimateProfile(number(args[1], 0, 2), periods));
            return "Updated seasonal strength for this session.";
        }
        arity(args, 7, 7, "/skyweather phase <season> <days> <temperature> <wetness> <thunder> <fog>");
        Season phase = season(args[1]);
        periods[phase.ordinal()] = new ClimateProfile.Period(parseInt(args[2], 1, 365), number(args[3], -2, 2),
                number(args[4], 0.25F, 4), number(args[5], 0.25F, 4), number(args[6], 0, 1));
        weather.editClimate(new ClimateProfile(old.strength, periods));
        return "Updated " + weather.phaseLabels()[phase.ordinal()] + " for this session.";
    }

    private static void localCrop(ServerWeather weather, ICommandSender sender, String[] args) throws CommandException {
        arity(args, 1, 4, "/skyweather crop [x y z]");
        BlockPos position;
        if (args.length == 4) position = parseBlockPos(sender, args, 1, false);
        else if (args.length == 1 && sender.getCommandSenderEntity() instanceof net.minecraft.entity.player.EntityPlayer) {
            net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) sender.getCommandSenderEntity();
            net.minecraft.util.math.Vec3d eye = new net.minecraft.util.math.Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
            net.minecraft.util.math.Vec3d look = player.getLook(1);
            net.minecraft.util.math.RayTraceResult hit = weather.world().rayTraceBlocks(eye,
                    eye.addVector(look.x * 6, look.y * 6, look.z * 6), false, false, true);
            if (hit == null || hit.getBlockPos() == null) throw new CommandException("Look at a plant or supply coordinates.");
            position = hit.getBlockPos();
        } else throw new WrongUsageException("/skyweather crop <x y z>");
        if (!weather.world().isBlockLoaded(position, false)) throw new CommandException("That plant's chunk is not loaded.");
        zone.moddev.mc.skysrealisticweather.api.PlantGrowthSample plant = WeatherApi.getPlantGrowth(weather.world(), position).orElse(null);
        if (plant == null) throw new CommandException("No plant at that position.");
        sender.sendMessage(new TextComponentString(String.format(Locale.ROOT,
                "%s | %s | %s (%s) | natural growth %.0f%% | %s | fertiliser: native eligibility",
                plant.plantId, plant.profileId, zone.moddev.mc.skysrealisticweather.game.TemperatureDisplay.forSender(sender).format(plant.temperature), plant.controlledClimate ? "controlled" : "ambient",
                plant.naturalGrowthMultiplier * 100, plant.reason)));
        sender.sendMessage(new TextComponentString("Plant hooks: " + zone.moddev.mc.skysrealisticweather.core.PlantHookStatus.states()));
    }

    private static String option(ServerWeather weather, String[] args) throws CommandException {
        arity(args, 3, 3, "/skyweather option <passive|surface|foliage|fog|scheduled|plants> <true|false>");
        if (!Arrays.asList(OPTIONS).contains(args[1])) throw new CommandException("Unknown weather option %s.", args[1]);
        boolean value = parseBoolean(args[2]);
        if (args[1].equals("plants")) {
            weather.editPlants(value);
            return "Natural plant growth rules " + (value ? "enabled" : "disabled") + " for this session.";
        }
        WeatherSettings old = weather.settings();
        weather.editOptions(args[1].equals("passive") ? value : old.passive,
                args[1].equals("surface") ? value : old.surface, args[1].equals("foliage") ? value : old.foliage,
                args[1].equals("fog") ? value : old.fog, args[1].equals("scheduled") ? value : old.scheduled);
        return "Set " + args[1] + " to " + value + " for this session.";
    }

    private static Season season(String value) throws CommandException {
        try {
            return Season.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new CommandException("Expected spring, summer, autumn or winter.");
        }
    }

    private static float number(String value, float min, float max) throws CommandException {
        double number = parseDouble(value, min, max);
        if (!Double.isFinite(number)) throw new CommandException("Expected a finite number from %s to %s.", min, max);
        return (float) number;
    }

    private static String status(ServerWeather weather) {
        SeasonSnapshot calendar = WeatherApi.getSeason(weather.world()).get();
        WeatherEpisode episode = WeatherApi.getWeatherEpisode(weather.world()).orElse(null);
        long seconds = weather.previewSeconds();
        String condition = seconds > 0 ? label(weather.previewKind()) + " preview (" + seconds + "s)"
                : episode != null ? label(episode.kind) : weather.world().isThundering() ? "Thunder"
                : weather.world().isRaining() ? "Rain or snow" : "Clear";
        return weather.phaseLabels()[calendar.season.ordinal()] + " day " + calendar.dayOfSeason + "/"
                + calendar.lengthInDays + " | " + condition + " | " + weather.settings().mode.label + " | Preset " + weather.preset()
                + (weather.edited() ? " (session preview)" : "")
                + " | Plants " + weather.settings().farming.enabled + " | Plant hooks "
                + zone.moddev.mc.skysrealisticweather.core.PlantHookStatus.states();
    }

    private static void arity(String[] args, int min, int max, String usage) throws WrongUsageException {
        if (args.length < min || args.length > max) throw new WrongUsageException(usage);
    }

    private static List<String> presetIds() {
        List<String> ids = new ArrayList<>();
        for (Preset preset : WeatherConfiguration.presets()) ids.add(preset.id);
        return ids;
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (!permitted(sender)) return Collections.emptyList();
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, ACTIONS);
        if (args.length <= 4 && args[0].equals("climate")) return getTabCompletionCoordinate(args, 1, pos);
        if (args.length == 2) {
            switch (args[0]) {
                case "world": return getListOfStringsMatchingLastWord(args, "save", "discard", "reset");
                case "mode": return getListOfStringsMatchingLastWord(args, "per_biome", "universal", "real_world");
                case "season": return getListOfStringsMatchingLastWord(args, "next", "previous", "set", "advance");
                case "time": return getListOfStringsMatchingLastWord(args, TIMES);
                case "preset": return getListOfStringsMatchingLastWord(args, presetIds());
                case "phase": return getListOfStringsMatchingLastWord(args, SEASONS);
                case "option": return getListOfStringsMatchingLastWord(args, OPTIONS);
                case "weather":
                    List<String> events = new ArrayList<>(Arrays.asList("auto", "clear", "rain", "thunder"));
                    for (WeatherKind event : WeatherKind.values()) events.add(event.name().toLowerCase(Locale.ROOT));
                    return getListOfStringsMatchingLastWord(args, events);
                default: break;
            }
        }
        if (args.length == 3 && args[0].equals("season") && args[1].equals("set")) {
            return getListOfStringsMatchingLastWord(args, SEASONS);
        }
        if (args.length == 3 && args[0].equals("option")) return getListOfStringsMatchingLastWord(args, "true", "false");
        return Collections.emptyList();
    }
}
