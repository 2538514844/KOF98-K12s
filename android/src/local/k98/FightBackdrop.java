package local.k98;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.*;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.Random;

/** A silent offline video layer. At most one decoder is owned at a time. */
final class FightBackdrop extends FrameLayout implements TextureView.SurfaceTextureListener,FightPlayback.Driver {
    private final TextureView video;
    private final ImageView poster;
    private final FightPlayback playback;
    private MediaPlayer player;
    private Surface output;
    private int seekTo;
    FightBackdrop(Context context){
        super(context);setBackgroundColor(0xff17191a);
        video=new TextureView(context);video.setSurfaceTextureListener(this);
        addView(video,new LayoutParams(-1,-1));
        poster=new ImageView(context);poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(poster,new LayoutParams(-1,-1));
        playback=new FightPlayback(this,new Random());
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    void active(boolean value){playback.active(value);}
    void dispose(){playback.dispose();poster.animate().cancel();poster.setImageDrawable(null);}
    @Override protected void onDetachedFromWindow(){dispose();super.onDetachedFromWindow();}
    @Override public void onSurfaceTextureAvailable(SurfaceTexture t,int w,int h){playback.surface(true);}
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture t,int w,int h){transform();}
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture t){playback.surface(false);return true;}
    @Override public void onSurfaceTextureUpdated(SurfaceTexture t){}
    private void transform(){
        if(player==null||video.getWidth()==0||video.getHeight()==0)return;
        int vw=player.getVideoWidth(),vh=player.getVideoHeight();if(vw<=0||vh<=0)return;
        float w=video.getWidth(),h=video.getHeight(),scale=Math.max(w/vw,h/vh);
        Matrix m=new Matrix();m.setScale(vw*scale/w,vh*scale/h,w/2,h/2);video.setTransform(m);
    }
    @Override public void prepare(int clip,int position,long token){
        poster.animate().cancel();poster.setAlpha(1f);
        String base="fights/fight-"+(clip+1);
        try(InputStream in=getContext().getAssets().open(base+".png")){poster.setImageBitmap(BitmapFactory.decodeStream(in));}
        catch(IOException ex){poster.setImageDrawable(null);}
        seekTo=position;
        MediaPlayer p=new MediaPlayer();player=p;
        output=new Surface(video.getSurfaceTexture());p.setSurface(output);p.setVolume(0,0);
        p.setOnPreparedListener(m->{if(player!=m)return;transform();playback.prepared(token);});
        p.setOnVideoSizeChangedListener((m,w,h)->{if(player==m)transform();});
        p.setOnInfoListener((m,what,extra)->{if(what==MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START)playback.firstFrame(token);return false;});
        p.setOnCompletionListener(m->playback.completed(token));
        p.setOnErrorListener((m,what,extra)->{playback.failed(token);return true;});
        try(AssetFileDescriptor fd=getContext().getAssets().openFd(base+".mp4")){
            p.setDataSource(fd.getFileDescriptor(),fd.getStartOffset(),fd.getLength());p.prepareAsync();
        }catch(IOException ex){throw new IllegalStateException("Background clip unavailable",ex);}
    }
    @Override public void start(){if(seekTo>0)player.seekTo(seekTo,MediaPlayer.SEEK_CLOSEST);player.start();}
    @Override public int position(){return player.getCurrentPosition();}
    @Override public void reveal(){poster.animate().alpha(0f).setDuration(280).start();}
    @Override public void release(){
        poster.animate().cancel();poster.setAlpha(1f);
        MediaPlayer old=player;player=null;
        try{if(old!=null){old.setOnPreparedListener(null);old.setOnCompletionListener(null);old.setOnErrorListener(null);old.setOnInfoListener(null);old.setOnVideoSizeChangedListener(null);old.release();}}
        finally{if(output!=null){output.release();output=null;}}
    }
}
