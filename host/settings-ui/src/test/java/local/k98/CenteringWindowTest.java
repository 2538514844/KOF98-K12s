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
}
