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
    @Test public void maxButtonIsSmallerAndLightsWithABCThenReleases() throws Exception {
        Controls p=new Controls(2400,1080,false,DisplayOptions.DEFAULT,120,0,0,48,162);
        int id=ControlProfile.MAX_KEY;assertEquals(.68f,p.rx[id]/p.rx[1],.001f);
        TouchInput input=new TouchInput();input.configure(p);TouchMotion motion=new TouchMotion(input);
        Bitmap bitmap=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(bitmap);
        try {
            for(int state=0;state<3;state++){
                if(state==1)send(motion,100,MotionEvent.ACTION_DOWN,p.cx[id],p.cy[id]);
                if(state==2)send(motion,120,MotionEvent.ACTION_UP,p.cx[id],p.cy[id]);
                int keys=input.poll().keys;assertEquals(state==1?PadLayout.MAX_KEYS:0,keys);
                assertButtonColors(bitmap,canvas,p,keys,keys);
                assertEquals(state==1?0xfff8d567:0xff35342d,
                        bitmap.getPixel(Math.round(p.cx[id]),Math.round(p.cy[id]+p.rx[id]*.65f)));
                File file=new File(System.getProperty("k98.evidence"),"max-"+state+".png");
                try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
                canvas.drawColor(0xff151819);p.draw(canvas,keys,0,0,128);
                assertNotEquals("MAX is inside the transparency layer",0xff151819,
                        bitmap.getPixel(Math.round(p.cx[id]),Math.round(p.cy[id]+p.rx[id]*.65f)));
            }
        } finally { bitmap.recycle(); }
    }

    @Test public void offsetThumbChordsLightBothButtonsAndReleaseIndependently() throws Exception {
        Controls p=new Controls(2400,1080,false,DisplayOptions.DEFAULT,120,0,0,48,162);
        TouchInput input=new TouchInput();input.configure(p);TouchMotion motion=new TouchMotion(input);
        Bitmap bitmap=Bitmap.createBitmap(p.width,p.height,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        try {
            for(int first:new int[]{0,2})for(float along:new float[]{-.25f,0,.25f})for(float across:new float[]{-.4f,0,.4f}) {
                input.reset();float vx=p.bx[first+1]-p.bx[first],vy=p.by[first+1]-p.by[first];
                float length=(float)Math.hypot(vx,vy),ux=vx/length,uy=vy/length;
                float x=(p.bx[first]+p.bx[first+1])*.5f+p.br*(along*ux-across*uy);
                float y=(p.by[first]+p.by[first+1])*.5f+p.br*(along*uy+across*ux);
                send(motion,100,MotionEvent.ACTION_DOWN,x,y);
                int chord=PadLayout.BITS[first]|PadLayout.BITS[first+1];
                int keys=input.poll(110).keys;assertEquals(chord,keys);
                assertButtonColors(bitmap,canvas,p,keys,chord);
                if(along==.25f&&across==.4f) {
                    File file=new File(System.getProperty("k98.evidence"),"thumb-offset-"+(first==0?"AB":"CD")+".png");
                    try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
                }
                send(motion,115,MotionEvent.ACTION_MOVE,p.bx[first],p.by[first]);
                keys=input.poll(127).keys;assertEquals(PadLayout.BITS[first],keys);
                assertButtonColors(bitmap,canvas,p,keys,PadLayout.BITS[first]);
                send(motion,130,MotionEvent.ACTION_UP,p.bx[first],p.by[first]);
                keys=input.poll(144).keys;assertEquals(0,keys);assertButtonColors(bitmap,canvas,p,keys,0);
            }
        } finally { bitmap.recycle(); }
    }
    private static void send(TouchMotion motion,long time,int action,float x,float y) {
        MotionEvent event=MotionEvent.obtain(100,time,action,x,y,0);
        try{motion.accept(event);}finally{event.recycle();}
    }
    private static void assertButtonColors(Bitmap bitmap,Canvas canvas,Controls p,int keys,int expected) {
        canvas.drawColor(0xff151819);p.draw(canvas,keys,0,0,255);
        for(int i=0;i<4;i++)assertEquals("button fill "+Controls.LABEL[i],
                (expected&PadLayout.BITS[i])==0?0xff242a2c:Controls.COLORS[i],
                bitmap.getPixel(Math.round(p.bx[i]),Math.round(p.by[i]+p.rx[i+1]*.65f)));
    }

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
            // Use the unpainted frame as the reference so antialiased outer strokes count.
            int[] reference=new int[width];bitmap.getPixels(reference,0,width,0,height/2,width,1);
            controls.draw(canvas,0,0,0,255);
            int start=0,end=width-1;
            while(reference[start]!=0xff217764)start++;
            while(reference[end]!=0xff217764)end--;
            int stickEnd=-1,firstKey=width;
            int[] row=new int[width];
            for(int y=(int)(controls.safeY+controls.safeHeight*.4f);y<height-1;y++){
                bitmap.getPixels(row,0,width,0,y,width,1);
                // Ignore the coloured source markers at the bottom of the frame.
                if(row[width/2]!=reference[width/2])continue;
                for(int x=0;x<width;x++)if(row[x]!=reference[x]){
                    if(x<width/2)stickEnd=Math.max(stickEnd,x);else firstKey=Math.min(firstKey,x);
                }
            }
            assertTrue(stickEnd>=0 && firstKey<width);
            int leftGap=start-stickEnd-1,rightGap=firstKey-end-1;
            String gaps=width+"x"+height+" reverse="+reverse+" visible gaps="+leftGap+","+rightGap;
            System.out.println(gaps);
            assertTrue(gaps,Math.abs(leftGap-rightGap)<=1);
            if(width==2393)assertTrue(gaps,leftGap>0 && rightGap>0);
            assertEquals("first source row remains visible",0xffe06030,bitmap.getPixel(width/2,0));
            assertEquals("last source row remains visible",0xff4080e0,bitmap.getPixel(width/2,height-1));
            for(int y=1;y<height-1;y++)assertNotEquals("no vertical letterbox at row "+y,0xff151819,bitmap.getPixel(width/2,y));
            File file=new File(System.getProperty("k98.evidence"),"full-height-"+width+"x"+height+"-"+(full?"fullscreen":"classic")+(reverse?"-left":"-right")+".png");
            try(FileOutputStream out=new FileOutputStream(file)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
            frame.recycle();bitmap.recycle();
        }
    }
}
