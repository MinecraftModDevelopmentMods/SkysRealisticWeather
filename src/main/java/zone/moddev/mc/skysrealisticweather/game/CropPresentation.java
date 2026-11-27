package zone.moddev.mc.skysrealisticweather.game;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.*;

/** Shared readouts; raw server samples let each viewer choose Celsius or Fahrenheit. */
public final class CropPresentation {
    public static final String KEY="skysrealisticweather";
    private CropPresentation() { }
    public static NBTTagCompound block(World world,BlockPos pos) {
        NBTTagCompound tag=new NBTTagCompound();
        if(world==null||pos==null||!world.isBlockLoaded(pos,false))return tag;
        IBlockState state=world.getBlockState(pos);
        if(!(state.getBlock() instanceof net.minecraftforge.common.IPlantable)&&!(state.getBlock() instanceof net.minecraft.block.IGrowable))return tag;
        tag.setBoolean("crop",true);
        WeatherState.Frame frame=WeatherState.frame(world);
        if(frame==null)return tag;
        PlantGrowthSample sample=PlantGrowthHooks.sample(world,pos);
        if(sample==null)return tag;
        FarmingSettings.Resolved rule=frame.settings.farming.resolve(sample.plantId,state);
        write(tag,rule==null?null:rule.profile,sample);
        WeatherApi.getTemperatureSample(world,pos).ifPresent(value->tag.setString("source",value.source.name().toLowerCase(Locale.ROOT).replace('_',' ')
                +(value.zoneId==null?"":" ("+value.zoneId+")")));
        return tag;
    }
    public static NBTTagCompound item(World world,BlockPos pos,PlantItems.Plant plant) {
        NBTTagCompound tag=new NBTTagCompound();tag.setBoolean("crop",true);
        WeatherState.Frame frame=WeatherState.frame(world);
        if(frame==null)return tag;
        FarmingSettings.Resolved rule=frame.settings.farming.resolve(plant.id,plant.state);
        write(tag,rule==null?null:rule.profile,PlantGrowthHooks.sample(world,pos,plant.state,plant.id,plant.magical));
        WeatherApi.getTemperatureSample(world,pos).ifPresent(value->tag.setString("source",value.source.name().toLowerCase(Locale.ROOT).replace('_',' ')));
        return tag;
    }
    private static void write(NBTTagCompound tag,PlantGrowthProfile profile,PlantGrowthSample sample) {
        if(profile!=null){
            tag.setBoolean("hasProfile",true);tag.setBoolean("exempt",profile.exempt);
            if(!profile.exempt){tag.setFloat("min",profile.minimum);tag.setFloat("idealMin",profile.idealMinimum);tag.setFloat("idealMax",profile.idealMaximum);tag.setFloat("max",profile.maximum);}
        }
        if(sample==null)return;
        tag.setBoolean("available",true);tag.setString("plant",sample.plantId);tag.setString("profile",sample.profileId);
        tag.setFloat("temperature",sample.temperature);tag.setFloat("multiplier",sample.naturalGrowthMultiplier);
        tag.setBoolean("controlled",sample.controlledClimate);tag.setInteger("reason",sample.reason.ordinal());
    }
    public static List<String> lines(NBTTagCompound tag,TemperatureDisplay.Unit unit) {
        if(!tag.getBoolean("crop"))return Collections.emptyList();
        try {
            PlantGrowthProfile profile=tag.getBoolean("hasProfile")?(tag.getBoolean("exempt")?PlantGrowthProfile.EXEMPT
                    :new PlantGrowthProfile(tag.getFloat("min"),tag.getFloat("idealMin"),tag.getFloat("idealMax"),tag.getFloat("max"))):null;
            PlantGrowthSample sample=null;
            if(tag.getBoolean("available")) {
                float temperature=tag.getFloat("temperature"),multiplier=tag.getFloat("multiplier");
                if(!Float.isFinite(temperature)||!Float.isFinite(multiplier)||multiplier<0||multiplier>1)throw new IllegalArgumentException("Invalid crop readout");
                sample=new PlantGrowthSample(tag.getString("plant"),tag.getString("profile"),temperature,multiplier,tag.getBoolean("controlled"),
                        PlantGrowthSample.Reason.values()[tag.getInteger("reason")]);
            }
            List<String> lines=lines(profile,sample,unit);
            if(tag.hasKey("source"))lines.add(TextFormatting.GRAY+"Temperature source: "+tag.getString("source"));
            return lines;
        }catch(IllegalArgumentException|IndexOutOfBoundsException invalid){return Collections.singletonList("Sky's Weather: local climate unavailable here");}
    }
    public static List<String> phases(World world,BlockPos pos,PlantItems.Plant plant,TemperatureDisplay.Unit unit) {
        WeatherState.Frame frame=WeatherState.frame(world);
        if(frame==null||!frame.active()||!world.isBlockLoaded(pos,false))return Collections.emptyList();
        FarmingSettings.Resolved rule=frame.settings.farming.resolve(plant.id,plant.state);
        if(rule==null||rule.profile==null||rule.profile.exempt||!frame.settings.farming.enabled)return Collections.emptyList();
        net.minecraft.world.biome.Biome biome=world.getBiome(pos);
        PlantGrowthSample current=PlantGrowthHooks.sample(world,pos,plant.state,plant.id,plant.magical);
        if(current==null||current.reason==PlantGrowthSample.Reason.ADAPTER_DISABLED||current.reason==PlantGrowthSample.Reason.EXEMPT)return Collections.emptyList();
        String[] labels=frame.regions==null?frame.settings.phaseLabels():frame.settings.biomes.preset(biome).phaseLabels();
        List<String> result=new ArrayList<>();result.add("Typical local seasonal conditions:");
        float original=biome.getFloatTemperature(pos);
        for(int i=0;i<4;i++){
            float temperature=frame.phaseTemperature(biome,original,i);
            if(!Float.isFinite(temperature))return Collections.emptyList();
            float multiplier=rule.profile.multiplier(temperature);
            result.add((multiplier<=0?TextFormatting.RED:multiplier>=1?TextFormatting.GREEN:TextFormatting.YELLOW)+labels[i]+": "+unit.format(temperature)
                    +" - "+Math.round(multiplier*100)+"%");
        }
        result.add("Estimates exclude weather and heating.");return result;
    }
    public static List<String> lines(PlantGrowthProfile profile, PlantGrowthSample sample, TemperatureDisplay.Unit unit) {
        List<String> lines = new ArrayList<>();
        if (profile != null && !profile.exempt) {
            lines.add(TextFormatting.DARK_AQUA + "Preferred temperature: " + unit.format(profile.idealMinimum) + " to " + unit.format(profile.idealMaximum));
            lines.add(TextFormatting.GRAY + "Growth limits: " + unit.format(profile.minimum) + " / " + unit.format(profile.maximum));
        }
        if (sample == null) { lines.add(TextFormatting.GRAY + "Sky's Weather: local climate unavailable here"); return lines; }
        if (sample.plantId.startsWith("agricraft:")) lines.add(0, TextFormatting.GRAY + "AgriCraft crop-stick growth");
        TextFormatting colour = TextFormatting.GRAY;
        String status;
        switch (sample.reason) {
            case DISABLED: status = "Climate growth rules disabled; native growth"; break;
            case ADAPTER_DISABLED: status = "Plant integration disabled; native growth"; break;
            case EXEMPT: status = "No climate temperature limit (exempt)"; break;
            case UNMAPPED: status = "No temperature profile; native growth"; break;
            default:
                if (sample.naturalGrowthMultiplier <= 0) {
                    colour = TextFormatting.RED;
                    status = sample.reason == PlantGrowthSample.Reason.COLD ? "Too cold; natural growth paused" : "Too hot; natural growth paused";
                } else if (sample.naturalGrowthMultiplier >= 1) {
                    colour = TextFormatting.GREEN; status = "Suitable; normal natural growth";
                } else {
                    colour = TextFormatting.YELLOW;
                    float percent = sample.naturalGrowthMultiplier * 100;
                    String rate = percent < 1 ? "<1" : percent > 99 ? ">99" : "about " + Math.round(percent);
                    status = (sample.reason == PlantGrowthSample.Reason.COLD ? "Cool" : "Warm") + "; " + rate + "% natural growth";
                }
        }
        lines.add(colour + "Here: " + unit.format(sample.temperature) + (sample.controlledClimate ? " (controlled climate)" : "") + " - " + status);
        lines.add(TextFormatting.GRAY + "Temperature check only; soil, light and native conditions still apply");
        return lines;
    }
}
