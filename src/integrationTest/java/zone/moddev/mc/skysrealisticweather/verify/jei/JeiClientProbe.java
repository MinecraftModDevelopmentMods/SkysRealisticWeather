package zone.moddev.mc.skysrealisticweather.verify.jei;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import mezz.jei.api.*;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.*;
import zone.moddev.mc.skysrealisticweather.api.*;
import zone.moddev.mc.skysrealisticweather.climate.WeatherConfiguration;
import zone.moddev.mc.skysrealisticweather.compat.JeiClimatePlugin;
import zone.moddev.mc.skysrealisticweather.game.*;

@JEIPlugin @SideOnly(Side.CLIENT)
public final class JeiClientProbe extends BlankModPlugin {
    private static IJeiRuntime runtime;
    private final boolean enabled=!Boolean.getBoolean("jeiprobe.disabled");
    private int stage=-1,ack=-1,ready,joins,draws,checks,catalogueSize;
    private boolean registered,connecting,reconnect,complete,picture;
    private IJeiRuntime oldRuntime;
    private JeiClimatePlugin.Entry entry;
    private ItemStack pamSeed,magicalSeed;
    private String shot;
    @Override public void onRuntimeAvailable(IJeiRuntime value){runtime=value;}
    private void require(boolean condition,String message){checks++;if(!condition)throw new IllegalStateException("JEI_CLIENT_FAIL "+message);}
    @SubscribeEvent public void chat(ClientChatReceivedEvent e) {
        String s=e.getMessage().getUnformattedText();
        if(s.startsWith("JEI_STAGE:")){stage=Integer.parseInt(s.substring(10));ready=0;draws=0;}
        else if(s.equals("JEI_RECONNECT"))reconnect=true;
        else if(s.equals("JEI_COMPLETE"))complete=true;
    }
    public void tick(Minecraft game)throws ReflectiveOperationException {
        if(!registered){registered=true;MinecraftForge.EVENT_BUS.register(this);}
        if(complete){System.out.println("JEI_CLIENT PASS checks="+checks+" catalogue="+catalogueSize+" joins="+joins);game.shutdown();return;}
        if(reconnect) {
            reconnect=false;oldRuntime=runtime;game.world.sendQuittingDisconnectingPacket();game.loadWorld(null);game.displayGuiScreen(new GuiMainMenu());connecting=false;ready=0;return;
        }
        if(game.world==null) {
            if(game.currentScreen instanceof GuiMainMenu||game.currentScreen instanceof net.minecraft.client.gui.GuiDisconnected) {
                if(++ready<20)return;
                if(oldRuntime!=null&&enabled) {
                    IRecipeCategory c=category(oldRuntime);require(c==null||oldRuntime.getRecipeRegistry().getRecipeWrappers(c).isEmpty(),"logout clears climate recipes");
                    require(entry.description(game).equals(Collections.singletonList("Local climate unavailable")),"old entry safely handles no world");
                }
                if(!connecting){connecting=true;joins++;game.displayGuiScreen(new GuiConnecting(new GuiMainMenu(),game,"127.0.0.1",Integer.getInteger("skyweather.port")));}
            }
            return;
        }
        if(game.player==null||runtime==null||WeatherState.frame(game.world)==null||stage<0||stage==ack)return;
        connecting=false;
        if(++ready==1)WeatherConfiguration.file().getCategory(WeatherConfiguration.CLIENT).get("temperatureUnit").set(stage==1||stage==3?"FAHRENHEIT":"CELSIUS");
        TemperatureSample local=WeatherApi.getTemperatureSample(game.world,game.player.getPosition()).orElse(null);
        if(ready<25||local==null||local.source!=TemperatureSample.Source.ZONE)return;
        float c=stage==1?-15:stage==2?45:20;
        if(Math.abs(TemperatureDisplay.Unit.CELSIUS.temperature(local.temperature)-c)>.01)return;
        IRecipeCategory category=category(runtime);
        if(!enabled) {
            require(category==null,"disabled weather bridge registers no climate category");
            if(draws==0){runtime.getRecipesGui().show(runtime.getRecipeRegistry().createFocus(IFocus.Mode.OUTPUT,new ItemStack(Items.BREAD)));draws=1;return;}
            if(draws<5){draws++;return;}
            require(game.currentScreen instanceof mezz.jei.gui.recipes.RecipesGui,"native JEI remains usable when bridge disabled");
            capture(game,"disabled-"+stage);finish(game);return;
        }
        if(category==null)return;
        if(picture){picture=false;finish(game);return;}
        if(draws>0)return;
        IRecipeRegistry registry=runtime.getRecipeRegistry();
        List<JeiClimatePlugin.Entry> entries=registry.getRecipeWrappers(category);if(entries.isEmpty())return;
        if(stage==0)catalogueSize=entries.size();
        if(stage==4){require(joins==2,"real client reconnect completed");require(entries.size()==catalogueSize,"rejoin rebuilds catalogue without duplicates");}
        IFocus<ItemStack> focus=registry.createFocus(IFocus.Mode.OUTPUT,new ItemStack(Items.WHEAT_SEEDS));
        List<JeiClimatePlugin.Entry> wheat=registry.getRecipeWrappers(category,focus);require(!wheat.isEmpty(),"seed recipe lookup discovers climate category");entry=wheat.get(0);
        String text=TextFormatting.getTextWithoutFormattingCodes(String.join("\n",entry.description(game)));
        System.out.println("JEI_DESCRIPTION stage="+stage+" entries="+entries.size()+"\n"+text);
        String unit=WeatherConfiguration.temperatureUnit().symbol;
        require(text.contains("Preferred temperature:")&&text.contains("Growth limits:")&&text.contains(unit),"requirements use selected display units");
        require(text.contains("Preferred temperature: "+(stage==1||stage==3?"50.0 °F to 78.0 °F":"10.0 °C to 25.6 °C")),"server crop override reaches JEI rather than client defaults");
        require(text.contains("Temperature source: zone")&&text.contains(WeatherConfiguration.temperatureUnit().format(local.temperature)),"live temperature uses server zone");
        require(text.contains("Spring:")&&text.contains("Summer:")&&text.contains("Autumn:")&&text.contains("Winter:")&&text.contains("Estimates exclude weather and heating."),"all seasonal estimates and qualification present");
        if(stage==1)require(text.contains("Too cold; natural growth paused")&&text.contains("5.0 °F"),"cold Fahrenheit status");
        if(stage==2)require(text.contains("Too hot; natural growth paused")&&text.contains("45.0 °C"),"hot Celsius status");
        if(stage==0||stage==3||stage==4)require(text.contains("Suitable; normal natural growth"),"warm suitability recovers");
        IRecipeLayoutDrawable layout=registry.createRecipeLayoutDrawable(category,entry,focus);
        require(layout!=null&&!layout.getItemStacks().getGuiIngredients().get(0).isInput(),"native layout exposes output seed");
        require(entry.getTooltipStrings(50,40).isEmpty(),"body does not obscure the summary with a full tooltip");
        require(entry.getTooltipStrings(category.getBackground().getWidth()-5,5).equals(entry.description(game)),"information marker retains full details");
        require(!registry.getRecipeWrappers(category,registry.createFocus(IFocus.Mode.OUTPUT,new ItemStack(Items.WHEAT))).isEmpty(),"produce lookup discovers climate entry");
        if(stage==0&&Loader.isModLoaded("harvestcraft")) {
            int pam=0,magic=0;boolean exempt=false;
            for(JeiClimatePlugin.Entry e:entries) {
                mezz.jei.ingredients.Ingredients ingredients=new mezz.jei.ingredients.Ingredients();e.getIngredients(ingredients);
                for(List<ItemStack> group:ingredients.getOutputs(ItemStack.class))for(ItemStack stack:group) {
                    String id=stack.getItem().getRegistryName().getResourceDomain();
                    if(id.equals("harvestcraft")) {
                        pam++;if(pamSeed==null&&stack.getItem().getRegistryName().getResourcePath().contains("seed")
                                &&e.description(game).toString().contains("Preferred temperature:"))pamSeed=stack.copy();
                    }
                    if(id.equals("mysticalagriculture")) {
                        magic++;if(e.description(game).toString().contains("No climate temperature limit (exempt)")) {
                            exempt=true;if(magicalSeed==null&&stack.getItem().getRegistryName().getResourcePath().contains("seeds"))magicalSeed=stack.copy();
                        }
                    }
                }
            }
            require(pam>50&&magic>0&&exempt&&pamSeed!=null&&magicalSeed!=null,"HarvestCraft entries and exempt magical crops are indexed");
            System.out.println("JEI_OPTIONAL_ITEMS pam="+pam+" magical="+magic);
        }
        ItemStack shown=stage==2&&pamSeed!=null?pamSeed:stage==3&&magicalSeed!=null?magicalSeed:new ItemStack(Items.WHEAT_SEEDS);
        focus=registry.createFocus(IFocus.Mode.OUTPUT,shown);
        entry=(JeiClimatePlugin.Entry)registry.getRecipeWrappers(category,focus).get(0);
        if(stage==2&&pamSeed!=null)require(entry.description(game).toString().contains("Preferred temperature:"),"actual HarvestCraft page retains crop requirements");
        if(stage==3&&magicalSeed!=null)require(entry.description(game).toString().contains("No climate temperature limit (exempt)")
                &&!entry.description(game).toString().contains("Preferred temperature:"),"actual magical seed page shows exemption");
        System.out.println("JEI_DISPLAY stage="+stage+" item="+shown.getItem().getRegistryName()+" details="+entry.description(game));
        if(stage==2){game.gameSettings.guiScale=2;game.resize(1280,720);}
        runtime.getRecipesGui().show(focus);
        GuiScreen screen=game.currentScreen;require(screen instanceof mezz.jei.gui.recipes.RecipesGui,"actual JEI recipe screen opens");
        Object logic=field(screen,"logic");logic.getClass().getMethod("setRecipeCategory",IRecipeCategory.class).invoke(logic,category);
        shot="stage-"+stage;draws=1;
    }
    private IRecipeCategory category(IJeiRuntime r){return r.getRecipeRegistry().getRecipeCategories().stream().filter(c->c.getUid().equals(JeiClimatePlugin.CATEGORY)).findFirst().orElse(null);}
    private static Object field(Object o,String name)throws ReflectiveOperationException{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    @SubscribeEvent public void drawn(GuiScreenEvent.DrawScreenEvent.Post e)throws ReflectiveOperationException {
        if(!enabled||draws==0||shot==null)return;
        GuiScreen screen=e.getGui();if(!(screen instanceof mezz.jei.gui.recipes.RecipesGui))return;
        List<mezz.jei.gui.recipes.RecipeLayout> layouts=(List<mezz.jei.gui.recipes.RecipeLayout>)field(screen,"recipeLayouts");
        require(!layouts.isEmpty(),"native GUI has a visible recipe layout");
        for(mezz.jei.gui.recipes.RecipeLayout l:layouts) {
            require(l.getRecipeCategory().getUid().equals(JeiClimatePlugin.CATEGORY),"climate category is selected");
            mezz.jei.gui.recipes.RecipesGui gui=(mezz.jei.gui.recipes.RecipesGui)screen;
            int w=l.getRecipeCategory().getBackground().getWidth(),h=l.getRecipeCategory().getBackground().getHeight();
            require(l.getPosX()>=gui.getGuiLeft()+6&&l.getPosX()+w<=gui.getGuiLeft()+gui.getXSize()-6,"recipe fits inside JEI's actual panel");
            require(l.getPosY()>=gui.getGuiTop()+(Integer)field(gui,"headerHeight")&&l.getPosY()+h<=gui.getGuiTop()+gui.getYSize()-5,"recipe clears JEI's header and bottom border");
            require(l.getPosX()+w<=screen.width&&l.getPosY()+h<=screen.height,"recipe panel fits scaled screen");
        }
        draws++;
    }
    @SubscribeEvent public void rendered(TickEvent.RenderTickEvent e)throws ReflectiveOperationException {
        if(e.phase!=TickEvent.Phase.END||shot==null||draws<5)return;
        Minecraft game=Minecraft.getMinecraft();capture(game,shot);
        if(stage==1) {
            List<mezz.jei.gui.recipes.RecipeLayout> layouts=(List<mezz.jei.gui.recipes.RecipeLayout>)field(game.currentScreen,"recipeLayouts");
            mezz.jei.gui.recipes.RecipeLayout layout=layouts.get(0);
            layout.drawOverlays(game,layout.getPosX()+layout.getRecipeCategory().getBackground().getWidth()-5,layout.getPosY()+5);
            capture(game,shot+"-tooltip");
        }
        shot=null;picture=true;
    }
    private void capture(Minecraft game,String name){ScreenShotHelper.saveScreenshot(new File("."),"jei-"+name+".png",game.displayWidth,game.displayHeight,game.getFramebuffer());}
    private void finish(Minecraft game){System.out.println("JEI_CLIENT_STAGE PASS "+stage);game.displayGuiScreen(null);game.player.sendChatMessage("/jeiprobeack "+stage);ack=stage;}
}
