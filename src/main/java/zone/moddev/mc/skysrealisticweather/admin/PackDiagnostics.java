package zone.moddev.mc.skysrealisticweather.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.config.*;
import net.minecraftforge.oredict.OreDictionary;
import zone.moddev.mc.skysrealisticweather.BuildInfo;
import zone.moddev.mc.skysrealisticweather.climate.*;
import zone.moddev.mc.skysrealisticweather.compat.*;
import zone.moddev.mc.skysrealisticweather.core.*;
import zone.moddev.mc.skysrealisticweather.game.*;
import zone.moddev.mc.skysrealisticweather.instrument.Thermometer;

/** Explicit administrator snapshot of definitions/registries; never walks terrain. */
public final class PackDiagnostics {
    private PackDiagnostics() { }
    public static Map<String,Object> collect(ServerWeather weather) {
        WeatherSettings settings=weather.settings();
        Map<String,Object> result=new LinkedHashMap<>();List<String> warnings=new ArrayList<>(settings.farming.issues());
        result.put("schema",1);result.put("version",BuildInfo.VERSION);result.put("mode",settings.mode.name());
        result.put("weatherHooks",HookStatus.available());result.put("plantHooks",PlantHookStatus.states());result.put("integrations",Integrations.status());
        result.put("scriptsPendingRestart",ScriptRules.pending());result.put("calendarOffsetTicks",settings.calendarOffset);
        result.put("worldOverrides",weather.worldData().overrides().toString());
        result.put("worldDataProblem",weather.worldData().problem());
        if(weather.worldData().problem()!=null)warnings.add(weather.worldData().problem());
        if(ScriptRules.pending())warnings.add("Script definitions changed; restart this world to activate them.");
        if(!HookStatus.available())warnings.add("Weather hooks unavailable: passive fallback.");
        Integrations.status().forEach((key,status)->{if(status.startsWith("unavailable")||status.equals("awaiting callback"))warnings.add("Integration "+key+": "+status);});
        int mercury=OreDictionary.getOres(Thermometer.MERCURY,false).size();result.put("mercuryOreEntries",mercury);
        if(mercury==0)warnings.add("No ingotMercury provider: the thermometer recipe is unavailable.");
        List<Map<String,Object>> biomes=new ArrayList<>();
        for(Biome biome:Biome.REGISTRY) {
            if(biome.getRegistryName()==null)continue;
            String id=biome.getRegistryName().toString();Map<String,Object> row=new LinkedHashMap<>();
            row.put("biome",id);row.put("climate",settings.biomes.preset(biome).id);
            row.put("source",ScriptRules.biomes().containsKey(id)?"script":settings.biomes.mappings.containsKey(id)?"config":"automatic");
            row.put("nativeSnowSurface",settings.keepsNativeSurface(biome.getRegistryName()));biomes.add(row);
        }
        result.put("biomes",biomes);
        Configuration config=WeatherConfiguration.file();
        for(Map.Entry<String,Property> entry:config.getCategory("biome_climates").entrySet()) {
            String preset=entry.getValue().getString().trim().toLowerCase(Locale.ROOT);
            if(WeatherConfiguration.preset(preset)==null)warnings.add("Unknown biome preset: "+entry.getKey()+" = "+preset);
            if(!registeredBiome(entry.getKey()))warnings.add("Biome is not registered (optional mod may be absent): "+entry.getKey());
        }
        ScriptRules.biomes().keySet().forEach(id->{if(!registeredBiome(id))warnings.add("Script biome is not registered: "+id);});
        if(settings.mode==zone.moddev.mc.skysrealisticweather.api.ClimateMode.REAL_WORLD&&WeatherConfiguration.startupLocation()==null)warnings.add("Real World Climate has no configured location; seasonal fallback is active.");
        List<Map<String,Object>> rules=settings.farming.rules();result.put("cropRules",rules);
        for(Map<String,Object> rule:rules) {
            if(Boolean.FALSE.equals(rule.get("resolved")))warnings.add("Unresolved crop profile: "+rule.get("selector")+" = "+rule.get("profile"));
            String selector=(String)rule.get("selector"),id=selector.split("\\[",2)[0];
            if(!Block.REGISTRY.containsKey(new ResourceLocation(id))) {
                if(!rule.get("source").equals("bundled"))warnings.add("Crop ID not registered as a block (may be an AgriCraft plant): "+selector);
                continue;
            }
            if(selector.contains("[")) {
                Block block=Block.REGISTRY.getObject(new ResourceLocation(id));
                for(String pair:selector.substring(selector.indexOf('[')+1,selector.length()-1).split(",")) {
                    String[] parts=pair.split("=",2);net.minecraft.block.properties.IProperty property=block.getBlockState().getProperty(parts[0]);
                    if(property==null||!property.parseValue(parts[1]).isPresent())warnings.add("Invalid block-state selector: "+selector);
                }
            }
        }
        List<Map<String,Object>> resolved=new ArrayList<>();
        for(Block block:Block.REGISTRY)for(IBlockState state:block.getBlockState().getValidStates()) {
            FarmingSettings.Resolved rule=settings.farming.resolve(block.getRegistryName().toString(),state);
            if(rule==null)continue;Map<String,Object> row=new LinkedHashMap<>();row.put("state",state.toString());row.put("profile",rule.id);row.put("source",rule.source);row.put("resolved",rule.profile!=null);resolved.add(row);
        }
        result.put("resolvedCropStates",resolved);result.put("warnings",warnings);return result;
    }
    private static boolean registeredBiome(String id) {
        try{return Biome.REGISTRY.containsKey(new ResourceLocation(id));}catch(IllegalArgumentException invalid){return false;}
    }
    @SuppressWarnings("unchecked") public static String summary(Map<String,Object> report) {
        List<String> warnings=(List<String>)report.get("warnings");
        return "Weather validation: "+warnings.size()+" warnings; "+((List<?>)report.get("biomes")).size()+" biomes, "+((List<?>)report.get("resolvedCropStates")).size()+" mapped crop states. Use /skyweather dump for details.";
    }
    public static Path dump(ServerWeather weather,Map<String,Object> report) throws IOException {
        Path directory=weather.world().getSaveHandler().getWorldDirectory().toPath().resolve("skyweather-reports");Files.createDirectories(directory);
        Path file=Files.createTempFile(directory,"climate-",".json");
        Files.write(file,new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report).getBytes(StandardCharsets.UTF_8));return file;
    }
}
