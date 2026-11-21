package local.k98;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.MediaPlayer;
import android.os.SystemClock;
import android.view.*;
import android.widget.FrameLayout;
import java.io.*;
import java.util.*;

/** One read-only, silent practice recording. Input is painted on top; never opens the core or touches saves. */
final class GuideDemo extends FrameLayout implements TextureView.SurfaceTextureListener {
    interface Status {void set(String s);}
    private final TextureView video;private final Ink ink;private final Controls pad;private final Status status;
    private MediaPlayer player;private Surface surface;private String posterPath,clip,previousStatus="";
    private Bitmap poster;private boolean generic,left,active,started,rendered,failed;private long startTime;
    private List<MoveBook.Move.Beat> beats=Collections.emptyList();private int total,lead;private String leadName="";
    private final Rect scene=new Rect();private final Paint bitmapPaint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Runnable refresh=new Runnable(){public void run(){updateFrame();}};
    GuideDemo(Context c,DisplayOptions o,ControlEditor.Screen s,boolean arcade,Status out){super(c);status=out;
        pad=new Controls(s.w,s.h,arcade,o,s.l,s.t,s.r,s.b,s.gear);setBackgroundColor(0xff101516);
        setContentDescription("本机按键布局的出招演示");video=new TextureView(c);video.setOpaque(false);video.setSurfaceTextureListener(this);
        addView(video,new LayoutParams(-1,-1,Gravity.CENTER));ink=new Ink(c);addView(ink,new LayoutParams(-1,-1));}
    // Fit without stretching or cropping either fighter. The poster, film and overlaid pad use the same bounds.
    static void fit(Rect out,int w,int h){int sw=Math.min(w,Math.round(h*304f/168)),sh=Math.min(h,Math.round(sw*168f/304));int x=(w-sw)/2,y=(h-sh)/2;out.set(x,y,x+sw,y+sh);}
    @Override protected void onMeasure(int ws,int hs){super.onMeasure(ws,hs);fit(scene,getMeasuredWidth(),getMeasuredHeight());
        video.measure(MeasureSpec.makeMeasureSpec(scene.width(),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(scene.height(),MeasureSpec.EXACTLY));}
    private Bitmap load(String path){try(InputStream in=getResources().getAssets().open(path)){return BitmapFactory.decodeStream(in);}catch(IOException bad){return null;}}
    void select(GuideMedia.Role role,MoveBook.Move move,boolean mirror,boolean play){stop();left=mirror;video.setScaleX(left?-1f:1f);
        String wanted=role==null?"fights/fight-1.png":role.poster;
        if(!wanted.equals(posterPath)){posterPath=wanted;poster=load(wanted);generic=role==null||poster==null;if(poster==null)poster=load("fights/fight-1.png");}
        clip=role==null||generic?null:role.moves.get(move.name);lead=clip==null?0:role.lead.getOrDefault(move.name,0);leadName=move.note.replace("命中后追加","");beats=move.beats(left);total=lead;for(MoveBook.Move.Beat b:beats)total+=b.ms;
        failed=false;if(play)replay();else updateFrame();}
    void replay(){stop();active=true;failed=false;startTime=SystemClock.uptimeMillis();if(isAvailable())open();updateFrame();}
    private boolean isAvailable(){return clip!=null&&video.isAvailable()&&video.getSurfaceTexture()!=null;}
    private void open(){if(!active||player!=null||failed||!isAvailable())return;
        MediaPlayer p=null;try(AssetFileDescriptor fd=getResources().getAssets().openFd(clip)){
            p=new MediaPlayer();player=p;surface=new Surface(video.getSurfaceTexture());p.setSurface(surface);p.setVolume(0f,0f);
            p.setOnPreparedListener(m->{if(player!=m||!active)return;try{m.start();started=true;startTime=SystemClock.uptimeMillis();}catch(RuntimeException bad){fallback();}updateFrame();});
            p.setOnInfoListener((m,what,e)->{if(player==m&&what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START){rendered=true;ink.invalidate();}return false;});
            p.setOnCompletionListener(m->{if(player==m){stop();updateFrame();}});
            p.setOnErrorListener((m,what,e)->{if(m==player){fallback();updateFrame();}return true;});
            p.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());p.prepareAsync();
        }catch(IOException|RuntimeException bad){fallback();}}
    private void fallback(){release();failed=true;startTime=SystemClock.uptimeMillis();}
    private void release(){rendered=started=false;MediaPlayer old=player;player=null;if(old!=null){old.setOnPreparedListener(null);old.setOnInfoListener(null);old.setOnCompletionListener(null);old.setOnErrorListener(null);old.release();}
        if(surface!=null){surface.release();surface=null;}}
    void stop(){removeCallbacks(refresh);active=false;release();if(ink!=null){ink.beat=null;ink.invalidate();}}
    private void updateFrame(){removeCallbacks(refresh);int time=0;
        if(active){long elapsed=SystemClock.uptimeMillis()-startTime;
            if(player!=null&&!started&&elapsed>4000){fallback();elapsed=0;}
            if(started&&player!=null){try{time=player.getCurrentPosition();}catch(IllegalStateException bad){fallback();}}
            else if(player==null)time=(int)Math.min(Integer.MAX_VALUE,elapsed);
            if(player==null&&time>total+600)active=false;
        }
        int i=0,limit=lead;MoveBook.Move.Beat b=null;if(active&&time>=lead){for(;i<beats.size();i++){MoveBook.Move.Beat candidate=beats.get(i);limit+=candidate.ms;if(time<limit){b=candidate;break;}}}ink.beat=b;
        String kind=generic?"通用场景 · 按法示意":clip==null||failed?"角色定格 · 按法示意":active&&!rendered?"角色场景":"练习场输入回放";
        String text=kind+(b!=null?"  |  "+(i+1)+"/"+beats.size()+"  "+describe(b):active&&time<lead?"  |  前置："+leadName:active?"":"  |  点重播再看一次");
        if(!previousStatus.equals(text)){previousStatus=text;status.set(text);}ink.invalidate();if(active)postDelayed(refresh,40);
    }
    private static String describe(MoveBook.Move.Beat b){String s=MoveBook.DIR[b.dir];for(int i=0;i<4;i++)if((b.keys&PadLayout.BITS[i])!=0)s+=(s.isEmpty()?"":"＋")+Controls.LABEL[i];return s.isEmpty()?"回中":s;}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){open();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){release();return true;}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){if(started&&!rendered&&player!=null&&t.getTimestamp()>0){rendered=true;ink.invalidate();}}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(visibility!=VISIBLE&&ink!=null){stop();updateFrame();}}
    @Override protected void onDetachedFromWindow(){stop();super.onDetachedFromWindow();}
    private final class Ink extends View {
        MoveBook.Move.Beat beat;Ink(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(scene.width()<1||scene.height()<1)return;int save=canvas.save();canvas.clipRect(scene);
            if(!rendered&&poster!=null){int p=canvas.save();if(left)canvas.scale(-1,1,scene.exactCenterX(),scene.exactCenterY());canvas.drawBitmap(poster,null,scene,bitmapPaint);canvas.restoreToCount(p);}
            float z=Math.min(scene.width()/(float)pad.width,scene.height()/(float)pad.height);canvas.translate(scene.exactCenterX()-pad.width*z/2,scene.exactCenterY()-pad.height*z/2);canvas.scale(z,z);
            int d=beat==null?5:beat.dir,keys=beat==null?0:beat.keys,x=MoveBook.X[d],y=MoveBook.Y[d];keys|=x<0?PadLayout.LEFT:x>0?PadLayout.RIGHT:0;keys|=y<0?PadLayout.UP:y>0?PadLayout.DOWN:0;
            float diag=x!=0&&y!=0?.7071f:1;pad.drawGuide(canvas,keys,x*diag,y*diag);canvas.restoreToCount(save);}
    }
}
