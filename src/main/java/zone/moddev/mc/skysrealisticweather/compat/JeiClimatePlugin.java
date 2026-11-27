package zone.moddev.mc.skysrealisticweather.compat;

import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.gui.*;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.*;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.game.*;

@JEIPlugin
@SideOnly(Side.CLIENT)
public final class JeiClimatePlugin extends BlankModPlugin {
    public static final String CATEGORY="skysrealisticweather.climate";
    private static final int WIDTH=212,HEIGHT=160;
    private IJeiRuntime runtime;
    private List<ItemStack> catalogue=Collections.emptyList();
    private final List<Entry> recipes=new ArrayList<>();
    private World indexedWorld;

    @Override public void register(IModRegistry registry) {
        if(!WeatherConfiguration.integration("jei"))return;
        catalogue=new ArrayList<>(registry.getIngredientRegistry().getIngredients(ItemStack.class));
        registry.addRecipeCategories(new Category(registry.getJeiHelpers().getGuiHelper()));
        registry.addRecipeHandlers(new Handler());
        MinecraftForge.EVENT_BUS.register(this);
    }
    @Override public void onRuntimeAvailable(IJeiRuntime runtime) { this.runtime=runtime; }
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||runtime==null)return;
        Minecraft game=Minecraft.getMinecraft();
        if(game.world==indexedWorld)return;
        if(game.world!=null&&(game.player==null||WeatherState.frame(game.world)==null))return;
        for(Entry entry:recipes)runtime.getRecipeRegistry().removeRecipe(entry);
        recipes.clear();indexedWorld=game.world;
        if(indexedWorld==null)return;
        for(ItemStack stack:catalogue) {
            if(stack==null||stack.isEmpty())continue;
            for(PlantItems.Plant plant:PlantItems.resolve(stack,indexedWorld,game.player.getPosition())) {
                Entry entry=new Entry(stack.copy(),plant);recipes.add(entry);runtime.getRecipeRegistry().addRecipe(entry);
            }
        }
    }
    public static final class Entry extends BlankRecipeWrapper {
        final ItemStack stack;final PlantItems.Plant plant;
        Entry(ItemStack stack,PlantItems.Plant plant){this.stack=stack;this.plant=plant;}
        @Override public void getIngredients(IIngredients ingredients){ingredients.setOutput(ItemStack.class,stack);}
        public List<String> description(Minecraft game) {
            if(game.world==null||game.player==null)return Collections.singletonList("Local climate unavailable");
            List<String> lines=new ArrayList<>(CropPresentation.lines(CropPresentation.item(game.world,game.player.getPosition(),plant),WeatherConfiguration.temperatureUnit()));
            lines.addAll(CropPresentation.phases(game.world,game.player.getPosition(),plant,WeatherConfiguration.temperatureUnit()));
            return lines;
        }
        @Override public void drawInfo(Minecraft game,int width,int height,int mouseX,int mouseY) {
            game.fontRenderer.drawString(game.fontRenderer.trimStringToWidth(stack.getDisplayName(),WIDTH-25),24,5,0x404040);
            int y=25;
            for(String line:description(game)) {
                if(line.contains("AgriCraft crop-stick growth"))continue;
                line=line.replace("Preferred temperature:","Preferred:").replace("Growth limits:","Limits:")
                        .replace("Temperature check only; soil, light and native conditions still apply","Native soil/light rules still apply.")
                        .replace("Typical local seasonal conditions:","Typical seasons:").replace("natural growth","growth");
                int colour=line.contains(TextFormatting.RED.toString())?0xA52B2B:line.contains(TextFormatting.GREEN.toString())?0x286B32:
                        line.contains(TextFormatting.YELLOW.toString())?0x846000:0x404040;
                for(String part:game.fontRenderer.listFormattedStringToWidth(TextFormatting.getTextWithoutFormattingCodes(line),WIDTH)) {
                    if(y>HEIGHT-10)return;
                    game.fontRenderer.drawString(part,0,y,colour);y+=10;
                }
            }
        }
        @Override public List<String> getTooltipStrings(int mouseX,int mouseY){return description(Minecraft.getMinecraft());}
    }
    private static final class Handler implements IRecipeHandler<Entry> {
        public Class<Entry> getRecipeClass(){return Entry.class;}
        public String getRecipeCategoryUid(){return CATEGORY;}
        public String getRecipeCategoryUid(Entry recipe){return CATEGORY;}
        public IRecipeWrapper getRecipeWrapper(Entry recipe){return recipe;}
        public boolean isRecipeValid(Entry recipe){return recipe!=null;}
    }
    private static final class Category implements IRecipeCategory<Entry> {
        final IDrawable background;
        Category(IGuiHelper gui){background=gui.createBlankDrawable(WIDTH,HEIGHT);}
        public String getUid(){return CATEGORY;}
        public String getTitle(){return "Climate requirements";}
        public String getModName(){return "Sky's Realistic Weather";}
        public IDrawable getBackground(){return background;}
        public IDrawable getIcon(){return null;}
        public void drawExtras(Minecraft game){ }
        public void drawAnimations(Minecraft game){ }
        public void setRecipe(IRecipeLayout layout,Entry recipe){layout.getItemStacks().init(0,false,0,0);layout.getItemStacks().set(0,recipe.stack);}
        public void setRecipe(IRecipeLayout layout,Entry recipe,IIngredients ingredients){setRecipe(layout,recipe);}
        public List<String> getTooltipStrings(int mouseX,int mouseY){return Collections.emptyList();}
    }
}
