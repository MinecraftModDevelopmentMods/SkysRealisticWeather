package zone.moddev.mc.skysrealisticweather.verify;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.math.AxisAlignedBB;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import zone.moddev.mc.skysrealisticweather.client.DistantPrecipitationRenderer;
import zone.moddev.mc.skysrealisticweather.game.DistantPrecipitationField;
import zone.moddev.mc.skysrealisticweather.game.DistantPrecipitationField.Column;

/** Standalone, mapped offscreen probe. Uses production batches and vanilla textures; creates no window. */
public final class DistantRenderProbe {
    private static final DistantPrecipitationField field = new DistantPrecipitationField();
    private static int rain, snow;
    private static Frustum frustum;
    private static int width = 1280, height = 720;
    private static double cameraX, cameraY = 20, cameraZ = -140;

    public static void main(String[] args) throws Exception {
        Path output = Paths.get(args[2]); Files.createDirectories(output);
        List<String> lines = Files.readAllLines(Paths.get(args[0]), java.nio.charset.StandardCharsets.UTF_8);
        for (String line : lines.subList(1, lines.size())) {
            String[] v = line.split(","); Column c = new Column();
            c.x=Integer.parseInt(v[0]); c.z=Integer.parseInt(v[1]); c.size=Integer.parseInt(v[2]);
            c.bottom=Float.parseFloat(v[3]); c.top=Float.parseFloat(v[4]); c.strength=Float.parseFloat(v[5]);
            c.snow=Boolean.parseBoolean(v[6]); c.light=Integer.parseInt(v[7]);
            c.bounds=new AxisAlignedBB(c.x-c.size,c.bottom,c.z-c.size,c.x+c.size,c.top,c.z+c.size);
            field.columns[field.count++]=c;
        }
        Pbuffer context = new Pbuffer(1920,1080,new PixelFormat().withDepthBits(24),null,null);
        try {
            context.makeCurrent(); OpenGlHelper.initializeTextures();
            System.out.println("DISTANT_GL vendor="+GL11.glGetString(GL11.GL_VENDOR)+" renderer="+GL11.glGetString(GL11.GL_RENDERER)+" version="+GL11.glGetString(GL11.GL_VERSION));
            try (ZipFile textures = new ZipFile(args[1])) {
                rain=texture(textures,"rain"); snow=texture(textures,"snow");
            }
            GlStateManager.enableTexture2D(); GlStateManager.enableDepth(); GlStateManager.disableCull();
            GlStateManager.enableAlpha(); GlStateManager.alphaFunc(516,.01F);
            field.range=192; setup(0,20,-140,64,45,32);
            for(int warm=0;warm<1000;warm++){background();weather(false);GL11.glFinish();}
            background(); int mixed=weather(false); GL11.glFinish(); picture(output.resolve("mixed-curtains.png"));
            background(); int snowy=weather(true); GL11.glFinish(); picture(output.resolve("outside-snow.png"));
            background(); GL11.glFinish(); picture(output.resolve("clear-baseline.png"));
            if (mixed < 20 || snowy < 10 || GL11.glGetError()!=0) throw new IllegalStateException("Offscreen render failed: "+mixed+"/"+snowy);
            try (PrintWriter csv = new PrintWriter(output.resolve("drawing.csv").toFile(),"UTF-8")) {
                csv.println("width,height,range,columns,quads,baseline_p50_ms,draw_p50_ms,draw_p95_ms,gpu_p50_ms");
                for (int resolution : new int[]{1280,1920}) for(int range : new int[]{96,192,256}) {
                    width=resolution; height=resolution==1280?720:1080; field.range=range; setup(0,20,-140,64,45,32);
                    for(int i=0;i<30;i++){background();weather(false);GL11.glFinish();}
                    long[] base=new long[100], draw=new long[100], gpu=new long[100]; int quads=0;
                    boolean timers=GLContext.getCapabilities().OpenGL33;
                    int query=timers?GL15.glGenQueries():0;
                    for(int i=0;i<100;i++) {
                        background(); GL11.glFinish(); long begin=System.nanoTime(); GL11.glFinish();base[i]=System.nanoTime()-begin;
                        if(timers)GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
                        begin=System.nanoTime();quads=weather(false);GL11.glFinish();draw[i]=System.nanoTime()-begin;
                        if(timers){GL15.glEndQuery(GL33.GL_TIME_ELAPSED);gpu[i]=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT);}
                    }
                    if(timers)GL15.glDeleteQueries(query);
                    Arrays.sort(base);Arrays.sort(draw);Arrays.sort(gpu);
                    csv.printf(Locale.ROOT,"%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f%n",width,height,range,field.count,quads,base[50]/1e6,draw[50]/1e6,draw[95]/1e6,gpu[50]/1e6);
                    System.out.println("DISTANT_DRAW width="+width+" range="+range+" quads="+quads+" p95_ms="+draw[95]/1e6+" gpu_p50_ms="+gpu[50]/1e6);
                }
            }
            if(GL11.glGetError()!=0)throw new IllegalStateException("GL error after benchmark");
            System.out.println("DISTANT_GL_PASS");
        } finally { context.destroy(); }
    }

    private static int texture(ZipFile jar,String name)throws IOException {
        BufferedImage image=ImageIO.read(jar.getInputStream(jar.getEntry("assets/minecraft/textures/environment/"+name+".png")));
        ByteBuffer pixels=BufferUtils.createByteBuffer(image.getWidth()*image.getHeight()*4);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getRGB(x,y);pixels.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}
        pixels.flip();int id=GL11.glGenTextures();GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,image.getWidth(),image.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);return id;
    }

    private static void setup(double x,double y,double z,double tx,double ty,double tz) {
        cameraX=x;cameraY=y;cameraZ=z;GL11.glViewport(0,0,width,height);
        double near=.05, top=near*Math.tan(Math.toRadians(35)), side=top*width/height;
        GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.loadIdentity();GL11.glFrustum(-side,side,-top,top,near,600);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.loadIdentity();
        GlStateManager.rotate((float)-Math.toDegrees(Math.atan2(ty-y,Math.hypot(tx-x,tz-z))),1,0,0);
        GlStateManager.rotate((float)Math.toDegrees(Math.atan2(tx-x,z-tz)),0,1,0);
        frustum=new Frustum();frustum.setPosition(x,y,z);
    }

    private static void background() {
        GL11.glClearColor(.55F,.73F,.91F,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        GlStateManager.disableTexture2D();GlStateManager.disableBlend();GlStateManager.depthMask(true);
        GL11.glBegin(GL11.GL_QUADS);
        for(int z=-256;z<256;z+=16)for(int x=-256;x<256;x+=16){
            float shade=((x+z)&16)==0?.9F:1; if(x>=32)GL11.glColor3f(.83F*shade,.9F*shade,.95F*shade);else GL11.glColor3f(.34F*shade,.5F*shade,.24F*shade);
            GL11.glVertex3d(x-cameraX,4-cameraY,z-cameraZ);GL11.glVertex3d(x+16-cameraX,4-cameraY,z-cameraZ);
            GL11.glVertex3d(x+16-cameraX,4-cameraY,z+16-cameraZ);GL11.glVertex3d(x-cameraX,4-cameraY,z+16-cameraZ);
        }
        GL11.glEnd();GlStateManager.color(1,1,1,1);GlStateManager.enableTexture2D();
    }

    private static int weather(boolean snowOnly) {
        GlStateManager.enableBlend();GlStateManager.tryBlendFuncSeparate(770,771,1,0);GlStateManager.depthMask(false);
        int result=0;
        if(!snowOnly){GlStateManager.bindTexture(rain);result+=DistantPrecipitationRenderer.draw(field,false,cameraX,cameraY,cameraZ,100,true,frustum);}
        GlStateManager.bindTexture(snow);result+=DistantPrecipitationRenderer.draw(field,true,cameraX,cameraY,cameraZ,100,true,frustum);
        GlStateManager.depthMask(true);GlStateManager.disableBlend();return result;
    }

    private static void picture(Path file)throws IOException {
        ByteBuffer pixels=BufferUtils.createByteBuffer(width*height*4);GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int i=(y*width+x)*4;image.setRGB(x,height-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}
        ImageIO.write(image,"png",file.toFile());
    }
}
