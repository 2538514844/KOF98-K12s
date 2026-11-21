package local.k98;

import android.graphics.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import org.junit.*;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,qualifiers="w800dp-h360dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CenteringWindowTest {
    @Test public void launcherAndDisplayModesShareCutoutCoordinates() throws Exception {
        ActivityController<Main> controller=Robolectric.buildActivity(Main.class);
        Main activity=controller.get();
        activity.getAssets().getClass().getMethod("addAssetPath",String.class).invoke(activity.getAssets(),System.getProperty("k98.apk"));
        controller.create();
        try {
            Field options=Main.class.getDeclaredField("displayOptions");options.setAccessible(true);
            Method apply=Main.class.getDeclaredMethod("setCutoutMode");apply.setAccessible(true);
            for(boolean full:new boolean[]{false,true,false}) {
                options.set(activity,new DisplayOptions(full,true,50));apply.invoke(activity);
                assertEquals(WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES,activity.getWindow().getAttributes().layoutInDisplayCutoutMode);
            }
            Field stageField=Main.class.getDeclaredField("stage");stageField.setAccessible(true);
            View stage=(View)stageField.get(activity);
            WindowInsets insets=WindowInsets.class.getDeclaredConstructor(Rect.class).newInstance(new Rect(0,0,54,0));
            stage.dispatchApplyWindowInsets(insets);
            Field right=Main.class.getDeclaredField("safeRight");right.setAccessible(true);
            assertEquals("controls retain the actual right safe inset",54,right.getInt(activity));
        } finally { controller.destroy(); }
    }

    @Test public void actualControlRendererKeepsPictureCentred() throws Exception {
        for(boolean full:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}) {
            int width=800,height=360;
            Controls controls=new Controls(width,height,false,new DisplayOptions(full,true,50),reverse?54:0,0,reverse?0:54,0,54);
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff151819);
            Paint picture=new Paint();picture.setColor(0xff217764);canvas.drawRect(controls.video,picture);
            controls.draw(canvas,0,0,0,255);
            assertEquals(width/2f,controls.video.centerX(),.01f);
            assertEquals(height/2f,controls.video.centerY(),.01f);
            int first=-1,last=-1;
            for(int x=0;x<width;x++)if(bitmap.getPixel(x,height/2)==0xff217764){if(first<0)first=x;last=x;}
            assertTrue(first>=0);assertTrue(Math.abs(first-(width-1-last))<=1);
            File file=new File(System.getProperty("k98.evidence"),"centering-"+(full?"fullscreen":"classic")+(reverse?"-left":"-right")+".png");
            try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        }
    }

    @Test public void picturePixelsReachTopAndBottomWithSafeInsets() throws Exception {
        int[][] sizes={{800,360},{480,270},{2393,1081}};
        for(int[] size:sizes)for(boolean full:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}) {
            int width=size[0],height=size[1];
            Controls controls=new Controls(width,height,false,new DisplayOptions(full,true,50),reverse?54:0,reverse?24:0,reverse?0:54,reverse?0:24,54);
            assertEquals("top edge must reach the screen",0,controls.video.top,0);
            assertEquals("bottom edge must reach the screen",height,controls.video.bottom,0);
            assertEquals(width/2f,controls.video.centerX(),.001f);
            assertEquals(4f/3,controls.video.width()/controls.video.height(),.001f);
            assertTrue("controls keep their safe area",controls.dy+controls.dr<=height-(reverse?0:24));
            Bitmap frame=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888);
            Canvas source=new Canvas(frame);source.drawColor(0xff217764);
            Paint marker=new Paint();marker.setColor(0xffe06030);source.drawRect(0,0,320,1,marker);
            marker.setColor(0xff4080e0);source.drawRect(0,239,320,240,marker);
            Bitmap bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(bitmap);canvas.drawColor(0xff151819);
            canvas.drawBitmap(frame,null,controls.video,new Paint());
            controls.draw(canvas,0,0,0,255);
            assertEquals("first source row remains visible",0xffe06030,bitmap.getPixel(width/2,0));
            assertEquals("last source row remains visible",0xff4080e0,bitmap.getPixel(width/2,height-1));
            for(int y=1;y<height-1;y++)assertNotEquals("no vertical letterbox at row "+y,0xff151819,bitmap.getPixel(width/2,y));
            File file=new File(System.getProperty("k98.evidence"),"full-height-"+width+"x"+height+"-"+(full?"fullscreen":"classic")+(reverse?"-left":"-right")+".png");
            try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
            frame.recycle();bitmap.recycle();
        }
    }
}
