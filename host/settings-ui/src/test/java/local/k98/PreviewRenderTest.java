package local.k98;
import android.app.*;import android.graphics.*;import android.view.*;import android.widget.*;
import java.io.*;import java.util.*;import org.junit.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;
import static local.k98.SettingsUITest.*;

/** Rasterize the actual Android view hierarchy, including bundled WebP, pad and small-screen layout. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE,qualifiers="w480dp-h270dp-land-mdpi") @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class PreviewRenderTest {
    static Bitmap raster(View v){Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));return b;}
    static void png(Bitmap b,String name)throws IOException{try(FileOutputStream f=new FileOutputStream(new File(System.getProperty("k98.evidence"),name))){assertTrue(b.compress(Bitmap.CompressFormat.PNG,100,f));}}
    static int dist(int a,int b){return Math.abs(Color.red(a)-Color.red(b))+Math.abs(Color.green(a)-Color.green(b))+Math.abs(Color.blue(a)-Color.blue(b));}
    @Test public void sceneAndCustomControlsVisibleWhileListScrollsAndOnZoom()throws Exception{
        Activity a=Robolectric.buildActivity(Activity.class).setup().get();a.setTheme(android.R.style.Theme_Material);
        Button anchor=new Button(a);a.setContentView(anchor);a.getAssets().getClass().getMethod("addAssetPath",String.class).invoke(a.getAssets(),System.getProperty("k98.apk"));
        DisplayOptions cfg=new DisplayOptions(true,true,100,ControlProfile.DEFAULT.move(1,.42f,.80f));ControlEditor.Screen phone=new ControlEditor.Screen(1920,1080,0,0,0,0,84);
        MoveGuide.open(anchor,cfg,phone,false);Dialog guide=ShadowDialog.getLatestDialog();View root=decor(guide);measure(root,480,270);org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();measure(root,480,270);
        GuideDemo demo=find(root,GuideDemo.class,null);demo.stop();ScrollView list=find(root,ScrollView.class,null);assertNotNull(demo);assertNotNull(list);
        Rect area=new Rect();GuideDemo.fit(area,demo.getWidth(),demo.getHeight());assertTrue("large enough on 480dp phone: "+area,area.height()>=135&&area.width()>=240);
        int[] pos=new int[2];demo.getLocationOnScreen(pos);int y=pos[1];list.scrollTo(0,150);assertTrue(list.getScrollY()>0);demo.getLocationOnScreen(pos);assertEquals(y,pos[1]);
        list.scrollTo(0,0);Bitmap b=raster(demo),raw;try(InputStream in=a.getAssets().open("guide/scenes/p01.webp")){raw=BitmapFactory.decodeStream(in);}assertNotNull(raw);
        // Compare pixels against the shipped image in separate stage and fighter windows, not just a non-black test.
        for(float[] region:new float[][]{{.25f,.12f,.75f,.32f},{.48f,.38f,.66f,.76f},{.67f,.42f,.78f,.75f}}){int match=0,count=0;HashSet<Integer> colors=new HashSet<>();
            for(int yy=(int)(region[1]*area.height());yy<region[3]*area.height();yy+=2)for(int xx=(int)(region[0]*area.width());xx<region[2]*area.width();xx+=2){
                int actual=b.getPixel(area.left+xx,area.top+yy);int oracle=raw.getPixel(Math.min(raw.getWidth()-1,(int)((xx+.5)*raw.getWidth()/area.width())),Math.min(raw.getHeight()-1,(int)((yy+.5)*raw.getHeight()/area.height())));if(dist(actual,oracle)<105)match++;colors.add(actual);count++;}
            assertTrue("picture absent in expected fighter/stage area: "+match+"/"+count+" colors="+colors.size(),match>count*.62&&colors.size()>40);}
        Controls pad=new Controls(phone.w,phone.h,false,cfg,0,0,0,0,phone.gear);float scale=Math.min(area.width()/(float)phone.w,area.height()/(float)phone.h);
        int cx=Math.round(area.exactCenterX()+(pad.cx[1]-phone.w/2f)*scale),cy=Math.round(area.exactCenterY()+(pad.cy[1]-phone.h/2f)*scale);int altered=0;for(int yy=cy-4;yy<=cy+4;yy++)for(int xx=cx-4;xx<=cx+4;xx++){
            int sample=raw.getPixel((xx-area.left)*raw.getWidth()/area.width(),(yy-area.top)*raw.getHeight()/area.height());if(dist(sample,b.getPixel(xx,yy))>60)altered++;}assertTrue("custom A is visible over image even at gameplay transparency 100%",altered>25);
        png(raster(root),"guide-480x270.png");click(guide,"放大演示");measure(root,480,270);Rect expanded=new Rect();GuideDemo.fit(expanded,demo.getWidth(),demo.getHeight());assertTrue("zoom must enlarge actual image, not just panel",expanded.height()>area.height()*1.15);assertFalse(list.isShown());png(raster(root),"guide-expanded-480x270.png");
        click(guide,"显示列表");measure(root,480,270);assertTrue(list.isShown());click(guide,"读法与朝向 ▾");find(root,Switch.class,"角色面朝左").setChecked(true);demo.stop();Bitmap mirrored=raster(demo);
        int diff=0,ok=0;for(int yy=area.top+area.height()/5;yy<area.centerY();yy+=3)for(int xx=area.left+area.width()/4;xx<area.centerX();xx+=3){int m=mirrored.getPixel(xx,yy);if(dist(m,b.getPixel(area.right-1-(xx-area.left),yy))<30)ok++;if(dist(m,b.getPixel(xx,yy))>60)diff++;}assertTrue("stage and fighters mirror; controls do not",ok>60&&diff>30);
        click(guide,"返回设置");assertFalse(guide.isShowing());
    }
}
