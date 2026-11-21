package local.k98;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;import android.os.Looper;
import org.junit.*;import static org.junit.Assert.*;import org.junit.runner.RunWith;
import org.robolectric.*;import org.robolectric.annotation.*;import org.robolectric.shadows.ShadowDialog;
import static org.robolectric.Shadows.shadowOf;
import java.util.*;import java.util.concurrent.atomic.AtomicReference;

/** Android framework/widget integration without claiming device or hardware frame timing. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=28,manifest=Config.NONE)
public class SettingsUITest {
    Activity a;Button anchor; ControlEditor.Screen screen=new ControlEditor.Screen(1920,1080,0,0,0,0,144);
    @Before public void prepare()throws Exception {
        a=Robolectric.buildActivity(Activity.class).setup().get();a.setTheme(android.R.style.Theme_Material);
        anchor=new Button(a);a.setContentView(anchor);a.getAssets().getClass().getMethod("addAssetPath",String.class).invoke(a.getAssets(),System.getProperty("k98.apk"));
        try(java.io.InputStream in=a.getAssets().open("guide/commands-zh.tsv")){assertTrue(in.read()>=0);}
    }
    static <T extends View>T find(View v,Class<T> type,String text){if(type.isInstance(v)&&(text==null||(v instanceof TextView&&text.equals(((TextView)v).getText().toString()))))return type.cast(v);
        if(v instanceof ViewGroup){ViewGroup p=(ViewGroup)v;for(int i=0;i<p.getChildCount();i++){T result=find(p.getChildAt(i),type,text);if(result!=null)return result;}}return null;}
    static View decor(Dialog d){return d.getWindow().getDecorView();}
    static void click(Dialog d,String label){Button b=find(decor(d),Button.class,label);assertNotNull(label,b);b.performClick();shadowOf(Looper.getMainLooper()).idle();}
    @Test public void guideSearchFlipReadAndReturn(){MoveGuide.open(anchor,DisplayOptions.DEFAULT,screen,false);Dialog guide=ShadowDialog.getLatestDialog();assertTrue(guide.isShowing());
        assertNotNull(find(decor(guide),TextView.class,"↓↘→ ＋ A"));click(guide,"选角色：草薙京 ▾");AlertDialog picker=(AlertDialog)ShadowDialog.getLatestDialog();
        EditText search=find(decor(picker),EditText.class,null);search.setText("八神庵");ListView list=find(decor(picker),ListView.class,null);assertEquals(1,list.getCount());list.performItemClick(null,0,0);
        assertNotNull(find(decor(guide),TextView.class,"八稚女　·　超必杀"));click(guide,"读法与朝向 ▾");find(decor(guide),Switch.class,"角色面朝左").setChecked(true);
        assertNotNull(find(decor(guide),TextView.class,"↓↙← ＋ A/C"));find(decor(guide),Switch.class,"中文分步读法").setChecked(true);TextView step=find(decor(guide),TextView.class,"下、左下、左，同时按 A 或 C");assertNotNull(step);((View)step.getParent()).performClick();
        assertNotNull(find(decor(guide),TextView.class,"演示：暗拂"));click(guide,"返回设置");assertFalse(guide.isShowing());}
    static void gesture(ControlEditor.Stage v,int action,float x,float y){MotionEvent e=MotionEvent.obtain(1,2,action,x,y,0);try{assertTrue(v.onTouchEvent(e));}finally{e.recycle();}}
    static void measure(View root,int width,int height){root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height);}
    static void dispatch(View root,View target,long t,int action,float x,float y){
        int[] rr=new int[2],tt=new int[2];root.getLocationOnScreen(rr);target.getLocationOnScreen(tt);
        MotionEvent e=MotionEvent.obtain(100,100+t,action,tt[0]-rr[0]+x,tt[1]-rr[1]+y,0);
        try{assertTrue("parent handled "+action,root.dispatchTouchEvent(e));}finally{e.recycle();}
    }
    @Test @Config(qualifiers="w480dp-h270dp-land-mdpi") public void editorDragResizeResetCancelApply(){
        AtomicReference<DisplayOptions> saved=new AtomicReference<>();SharedPreferences prefs=a.getSharedPreferences("display",0);AlertDialog parent=DisplaySettings.build(a,DisplayOptions.DEFAULT,true,screen,o->{saved.set(o);prefs.edit().putString("controls.v1",o.profile.encode()).apply();});parent.show();click(parent,"按键布局");Dialog child=ShadowDialog.getLatestDialog();
        View root=decor(child);measure(root,480,270);ControlEditor.Stage v=find(root,ControlEditor.Stage.class,null);assertNotNull(v);float x=v.offsetX+v.pad.cx[1]*v.zoom,y=v.offsetY+v.pad.cy[1]*v.zoom;
        float nx=v.offsetX+v.zoom*screen.w*.56f,ny=v.offsetY+v.zoom*screen.h*.69f;gesture(v,0,x,y);gesture(v,2,nx,ny);gesture(v,1,nx,ny);
        assertEquals(1,v.selected);assertEquals(.56f,v.profile.x(1),.003);SeekBar slider=find(decor(child),SeekBar.class,null);
        int pad=slider.getPaddingLeft(),track=slider.getWidth()-pad-slider.getPaddingRight();float sy=slider.getHeight()/2f;
        dispatch(root,slider,0,MotionEvent.ACTION_DOWN,pad+track*.36f,sy);
        dispatch(root,slider,20,MotionEvent.ACTION_MOVE,pad+track*.57f,sy);
        int mid=v.profile.size(1);assertTrue("first drag must resize before UP; got "+mid,mid>110&&mid<128);
        dispatch(root,slider,40,MotionEvent.ACTION_MOVE,pad+track*.72f,sy);
        int larger=v.profile.size(1);assertTrue("second move must resize again",larger>mid);
        dispatch(root,slider,60,MotionEvent.ACTION_MOVE,pad+track*.67f,sy);int last=v.profile.size(1);assertTrue("dragging back also works",last<larger&&last>mid);
        dispatch(root,slider,80,MotionEvent.ACTION_UP,pad+track*.67f,sy);
        assertEquals(last,v.profile.size(1));click(child,"完成");assertTrue(parent.isShowing());assertNull(saved.get());assertFalse(prefs.contains("controls.v1"));parent.getButton(-1).performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(last,ControlProfile.decode(prefs.getString("controls.v1",null)).size(1));
        assertEquals(last,saved.get().profile.size(1));assertEquals(.56f,saved.get().profile.x(1),.003);
        AlertDialog again=DisplaySettings.build(a,saved.get(),true,screen,z->fail("cancel must not apply"));again.show();click(again,"按键布局");child=ShadowDialog.getLatestDialog();click(child,"恢复默认");assertEquals(100,find(decor(child),ControlEditor.Stage.class,null).profile.size(1));
        click(child,"取消");again.getButton(-2).performClick();assertEquals(last,saved.get().profile.size(1));}
    @Test @Config(qualifiers="w480dp-h270dp-land-mdpi") public void resizeCollisionRejectsOnlyBlockedMoves(){
        PadLayout p=new PadLayout(screen.w,screen.h);float dist=1.25f*p.rx[1]/screen.w;
        ControlProfile cfg=ControlProfile.DEFAULT.move(1,.45f,.56f).move(2,.45f+dist,.56f);
        ControlEditor.open(anchor,new DisplayOptions(false,true,40,cfg),screen,z->fail("never applied"));Dialog d=ShadowDialog.getLatestDialog();View root=decor(d);measure(root,480,270);
        ControlEditor.Stage stage=find(root,ControlEditor.Stage.class,null);find(root,Spinner.class,null).setSelection(1);shadowOf(Looper.getMainLooper()).idle();measure(root,480,270);assertEquals(1,stage.selected);
        SeekBar bar=find(root,SeekBar.class,null);float left=bar.getPaddingLeft(),w=bar.getWidth()-left-bar.getPaddingRight(),h=bar.getHeight()*.5f;
        dispatch(root,bar,0,0,left+w*.37f,h);dispatch(root,bar,20,2,left+w*.53f,h);int valid=stage.profile.size(1);assertTrue(valid>110&&valid<125);
        dispatch(root,bar,40,2,left+w*.95f,h);assertEquals(valid,stage.profile.size(1));assertEquals(valid-ControlProfile.MIN,bar.getProgress());assertFalse(stage.pad.obstructed(1));
        dispatch(root,bar,60,2,left+w*.45f,h);assertTrue(stage.profile.size(1)<valid);dispatch(root,bar,70,1,left+w*.45f,h);click(d,"取消");}
    @Test @Config(qualifiers="w480dp-h270dp-land-mdpi") public void videoUsesSelectedAssetAndReleasesOnRapidSwitchErrorAndDismiss()throws Exception{
        java.util.List<org.robolectric.shadows.ShadowMediaPlayer> players=new ArrayList<>();
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener((p,s)->players.add(s));
        org.robolectric.shadows.ShadowMediaPlayer.setMediaInfoProvider(ds->new org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(4100,20));
        MoveGuide.open(anchor,DisplayOptions.DEFAULT,screen,false);Dialog d=ShadowDialog.getLatestDialog();View root=decor(d);measure(root,480,270);
        GuideDemo demo=find(root,GuideDemo.class,null);TextureView texture=find(demo,TextureView.class,null);
        android.graphics.SurfaceTexture t=new android.graphics.SurfaceTexture(1);texture.setSurfaceTexture(t);demo.replay();
        assertFalse(players.isEmpty());org.robolectric.shadows.ShadowMediaPlayer first=players.get(players.size()-1);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
        assertTrue(first.isReallyPlaying());assertEquals(0f,first.getLeftVolume(),0f);assertEquals(0f,first.getRightVolume(),0f);first.invokeInfoListener(android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START,0);
        ((View)find(root,TextView.class,"荒咬　·　必杀").getParent()).performClick();assertEquals(org.robolectric.shadows.ShadowMediaPlayer.State.END,first.getState());
        org.robolectric.shadows.ShadowMediaPlayer next=players.get(players.size()-1);assertNotSame(first,next);shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));assertTrue(next.isReallyPlaying());
        next.invokeErrorListener(android.media.MediaPlayer.MEDIA_ERROR_UNKNOWN,0);assertEquals(org.robolectric.shadows.ShadowMediaPlayer.State.END,next.getState());
        TextView status=find(root,TextView.class,"角色定格 · 慢动作  |  1/4  ↓");assertNotNull("keeps poster and sequence when decoding fails",status);
        demo.replay();org.robolectric.shadows.ShadowMediaPlayer last=players.get(players.size()-1);assertNotSame(next,last);click(d,"返回设置");assertEquals(org.robolectric.shadows.ShadowMediaPlayer.State.END,last.getState());
    }
}

