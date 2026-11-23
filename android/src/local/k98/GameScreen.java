package local.k98;

import android.graphics.*;
import android.media.*;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.view.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

final class GameScreen extends SurfaceView implements SurfaceHolder.Callback,Runnable {
    private static final Object CORE=new Object();
    private static final long MAX_LAG_NANOS=100_000_000L;
    private final Object signal=new Object(),drawing=new Object(); private final Main host; private final File root; private final AtomicFile save;
    private final NeoMode mode;
    final DuelRound duel;
    private volatile DisplayOptions displayOptions;
    private int insetLeft,insetTop,insetRight,insetBottom;
    private final AtomicInteger taps=new AtomicInteger();private volatile int physical,stick;
    private final AtomicBoolean reloadRequested=new AtomicBoolean();
    private final TouchInput touchInput=new TouchInput();
    private final TouchMotion touchMotion=new TouchMotion(touchInput);
    private volatile boolean foreground,surface,dead; private volatile Controls layout;
    private final Paint videoPaint=new Paint(); private final Paint loading=new Paint(Paint.ANTI_ALIAS_FLAG);
    GameScreen(Main main,File folder,NeoMode mode,DisplayOptions options){
        this(main,folder,mode,options,null);
    }
    GameScreen(Main main,File folder,NeoMode mode,DisplayOptions options,DuelRound duel){
        super(main);host=main;root=folder;this.mode=mode;displayOptions=options;this.duel=duel;
        save=new AtomicFile(mode.resumeFile(root));
        setFocusableInTouchMode(true);setFocusable(true);getHolder().addCallback(this);
        videoPaint.setFilterBitmap(false);loading.setColor(0xffdedbcf);loading.setTextAlign(Paint.Align.CENTER);
        new Thread(this,"K98-emulation").start();
    }
    private void wake(){synchronized(signal){signal.notifyAll();}}
    void active(boolean yes){if(!yes)releaseInput();foreground=yes;wake();}
    void shutdown(){dead=true;releaseInput();wake();}
    void reload(){if(dead)return;releaseInput();reloadRequested.set(true);wake();}
    private void releaseInput(){touchInput.reset();physical=stick=0;taps.set(0);}
    private boolean ready(){return !dead&&surface&&foreground;}
    private void awaitReady() throws InterruptedException {synchronized(signal){while(!dead&&!ready())signal.wait();}}
    @Override public void surfaceCreated(SurfaceHolder h){synchronized(drawing){surface=true;}wake();}
    @Override public void surfaceChanged(SurfaceHolder h,int fmt,int w,int hgt){resizeControls(w,hgt);wake();}
    @Override public void surfaceDestroyed(SurfaceHolder h){synchronized(drawing){surface=false;}releaseInput();wake();}
    @Override protected void onSizeChanged(int w,int h,int a,int b){super.onSizeChanged(w,h,a,b);resizeControls(w,h);}
    private void resizeControls(int w,int h){
        Controls old=layout;
        if(old==null||old.width!=w||old.height!=h) rebuildControls(w,h);
    }
    private void rebuildControls(int w,int h){
        if(w<=0 || h<=0)return;
        int gear=Math.round(getResources().getDisplayMetrics().density*54f);
        Controls next=new Controls(w,h,mode.arcade,displayOptions,insetLeft,insetTop,insetRight,insetBottom,gear);
        touchInput.configure(next);
        layout=next;
    }
    void setDisplayOptions(DisplayOptions next){
        boolean geometryChanged=next.fullscreen!=displayOptions.fullscreen || next.keepAspect!=displayOptions.keepAspect || next.profile!=displayOptions.profile;
        displayOptions=next;
        if(geometryChanged) rebuildControls(getWidth(),getHeight());
    }
    void setSafeInsets(int left,int top,int right,int bottom){
        if(insetLeft==left && insetTop==top && insetRight==right && insetBottom==bottom)return;
        insetLeft=left;insetTop=top;insetRight=right;insetBottom=bottom;
        rebuildControls(getWidth(),getHeight());
    }
    private void latch(int bits){if(bits==0)return;int old;do{old=taps.get();}while(!taps.compareAndSet(old,old|bits));}
    @Override public boolean onTouchEvent(MotionEvent ev){Controls l=layout;int act=ev.getActionMasked();if(l==null)return true;
        if(!ready()||act==MotionEvent.ACTION_CANCEL){touchInput.reset();return true;}
        if(act==MotionEvent.ACTION_DOWN){requestUnbufferedDispatch(ev);if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);}
        int changed=ev.getActionIndex();
        if(act==MotionEvent.ACTION_DOWN||act==MotionEvent.ACTION_POINTER_DOWN){if(l.pause.contains(ev.getX(changed),ev.getY(changed))){releaseInput();host.pauseMenu();return true;}}
        touchMotion.accept(ev);return true;
    }
    private int map(int key){switch(key){case KeyEvent.KEYCODE_BUTTON_SELECT:case KeyEvent.KEYCODE_5:return mode.arcade?Controls.COIN:0;case KeyEvent.KEYCODE_DPAD_UP:return Controls.UP;case KeyEvent.KEYCODE_DPAD_DOWN:return Controls.DOWN;case KeyEvent.KEYCODE_DPAD_LEFT:return Controls.LEFT;case KeyEvent.KEYCODE_DPAD_RIGHT:return Controls.RIGHT;case KeyEvent.KEYCODE_BUTTON_START:case KeyEvent.KEYCODE_ENTER:return Controls.START;case KeyEvent.KEYCODE_BUTTON_A:case KeyEvent.KEYCODE_Z:return Controls.A;case KeyEvent.KEYCODE_BUTTON_B:case KeyEvent.KEYCODE_X:return Controls.B;case KeyEvent.KEYCODE_BUTTON_X:case KeyEvent.KEYCODE_A:return Controls.C;case KeyEvent.KEYCODE_BUTTON_Y:case KeyEvent.KEYCODE_S:return Controls.D;default:return 0;}}
    @Override public boolean onKeyDown(int key,KeyEvent e){int m=map(key);if(m==0)return super.onKeyDown(key,e);if(e.getRepeatCount()==0)latch(m);physical|=m;return true;}
    @Override public boolean onKeyUp(int key,KeyEvent e){int m=map(key);if(m==0)return super.onKeyUp(key,e);physical&=~m;return true;}
    @Override public boolean onGenericMotionEvent(MotionEvent e){if((e.getSource()&InputDevice.SOURCE_JOYSTICK)!=InputDevice.SOURCE_JOYSTICK)return super.onGenericMotionEvent(e);float x=e.getAxisValue(MotionEvent.AXIS_HAT_X),y=e.getAxisValue(MotionEvent.AXIS_HAT_Y);if(x==0)x=e.getAxisValue(MotionEvent.AXIS_X);if(y==0)y=e.getAxisValue(MotionEvent.AXIS_Y);stick=(x<-.37f?Controls.LEFT:x>.37f?Controls.RIGHT:0)|(y<-.37f?Controls.UP:y>.37f?Controls.DOWN:0);return true;}
    private void paint(Bitmap frame,int mask,float thumbX,float thumbY){
        synchronized(drawing){
            if(!surface||dead||!foreground)return;
            Canvas c;
            try{c=getHolder().lockHardwareCanvas();}catch(RuntimeException ex){return;}
            try{
                c.drawColor(0xff151819);
                Controls controls=layout;
                if(controls==null)return;
                if(frame!=null)c.drawBitmap(frame,null,controls.video,videoPaint);
                else{
                    loading.setTextSize(Math.max(18,c.getHeight()*.038f));
                    c.drawText("正在启动…",c.getWidth()*.5f,c.getHeight()*.5f,loading);
                }
                controls.draw(c,mask,thumbX,thumbY,displayOptions.controlAlpha());
            }finally{
                try{getHolder().unlockCanvasAndPost(c);}catch(RuntimeException ignored){}
            }
        }
    }
    private AudioTrack audio(int rate){int min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT);if(min<=0)return null;AudioTrack a=null;try{AudioFormat f=new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build();AudioAttributes attrs=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();a=new AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(f).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(min*2,4*rate/30)).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();if(a.getState()==AudioTrack.STATE_INITIALIZED){a.play();return a;}}catch(RuntimeException ignored){}drop(a);return null;}
    private static void drop(AudioTrack a){if(a==null)return;try{a.pause();a.flush();}catch(RuntimeException ignored){}try{a.release();}catch(RuntimeException ignored){}}
    private void restore(){try(DataInputStream in=new DataInputStream(new BufferedInputStream(save.openRead()))){int size=Bridge.stateSize();if(size<=0||in.readInt()!=0x4b393801||in.readInt()!=size)return;byte[] bytes=new byte[size];in.readFully(bytes);if(!Bridge.state(bytes,false))host.note("未能恢复上次进度，已重新启动。");}catch(FileNotFoundException ignored){}catch(IOException ex){host.note("存档读取失败，已重新启动。");}}
    private void checkpoint(){int size=Bridge.stateSize();if(size<=0)return;byte[] bytes=new byte[size];if(!Bridge.state(bytes,true))return;FileOutputStream out=null;try{out=save.startWrite();DataOutputStream d=new DataOutputStream(out);d.writeInt(0x4b393801);d.writeInt(size);d.write(bytes);d.flush();save.finishWrite(out);}catch(IOException ex){if(out!=null)save.failWrite(out);host.note("未能保存进度，请检查手机剩余空间。");}}
    @Override public void run(){try{awaitReady();if(dead)return;synchronized(CORE){if(dead)return;session();}}catch(InterruptedException ex){Thread.currentThread().interrupt();}catch(IOException|LinkageError|RuntimeException ex){host.failed(this,ex.getMessage());}}
    private int openCore(File system) throws IOException {
        File saves=duel==null?mode.saveDirectory(root):new File(root,"bluetooth/save");
        if(!saves.isDirectory()&&!saves.mkdirs())throw new IOException("无法创建游戏存档目录");
        return Bridge.openMode(root.getAbsolutePath(),system.getAbsolutePath(),
                               saves.getAbsolutePath(),mode.name());
    }
    private void session() throws InterruptedException,IOException {
        int[] rgb=new int[640*512],wh=new int[2];short[] pcm=new short[8192];Bitmap frame=null;AudioTrack out=null;boolean dirty=false,open=false,wantAudio=true,audioNotified=false,announced=false;long tick=0;int frames=0;
        try{paint(null,0,0,0);File system=ModeFiles.prepare(root,mode);int rc=openCore(system);if(rc!=0){host.failed(this,mode.name()+" 载入失败 ("+rc+")。请检查游戏数据。");return;}open=true;
            if(duel==null)restore();
            else{
                if(duel.host)for(int i=0;i<1080;i++){
                    if(dead)return;
                    if(Bridge.frame(DuelRound.menuKeys(i,duel.team),rgb,pcm,wh)<0)throw new IOException("无法进入对战模式");
                }
                duel.synchronizeCore();
            }
            long step=Math.round(1e9/Bridge.fps());int rate=Bridge.rate();
            while(!dead){if(!ready()){drop(out);out=null;wantAudio=true;if(dirty){checkpoint();dirty=false;}awaitReady();tick=0;if(dead)break;continue;}
                // UI only queues this request. All native and save operations stay on this thread.
                if(reloadRequested.getAndSet(false)){
                    if(!DataDepot.ready(root.getParentFile())){
                        host.note("游戏数据不完整，暂时无法重载。");continue;
                    }
                    drop(out);out=null;wantAudio=true;releaseInput();paint(null,0,0,0);
                    dirty=false;Bridge.close();open=false;
                    int reloadCode=openCore(system);
                    if(reloadCode!=0){host.failed(this,"重载失败 ("+reloadCode+")，可重新启动游戏。");break;}
                    open=true;
                    // Only invalidate the auto-resume after a successful reload; do not restore it.
                    save.delete();dirty=true;frames=0;frame=null;tick=0;
                    step=Math.round(1e9/Bridge.fps());rate=Bridge.rate();audioNotified=false;
                    continue;
                }
                if(wantAudio){out=audio(rate);wantAudio=false;if(out==null&&!audioNotified){audioNotified=true;host.note("暂时无法打开声音输出。");}}
                long now=System.nanoTime();
                if(tick==0||now-tick>MAX_LAG_NANOS)tick=now;
                long lag=now-tick;
                long cutoffMs=SystemClock.uptimeMillis();
                // A genuinely overdue frame can still sample a delivered historical direction.
                // A frame running on time takes the current input as late as possible.
                if(lag>=step)cutoffMs-=lag/1_000_000L;
                int hardwareKeys=physical|stick|taps.getAndSet(0);
                TouchInput.Snapshot input=touchInput.poll(cutoffMs,hardwareKeys);
                int mask=input.keys|hardwareKeys;
                touchMotion.traceFrame(frames,cutoffMs,mask);
                int keys=duel==null?mask:duel.exchange(frames,mask);
                int n=Bridge.frame(keys,rgb,pcm,wh);if(n<0){host.failed(this,"核心已停止。");break;}
                if(++frames>=10&&duel==null)dirty=true;
                if(wh[0]>0&&wh[0]<=640&&wh[1]>0&&wh[1]<=512){if(frame==null||frame.getWidth()!=wh[0]||frame.getHeight()!=wh[1])frame=Bitmap.createBitmap(wh[0],wh[1],Bitmap.Config.ARGB_8888);frame.setPixels(rgb,0,wh[0],0,0,wh[0],wh[1]);}
                // Present the completed video before a blocking audio write can delay it.
                paint(frame,mask,input.x,input.y);
                if(!announced&&frame!=null){announced=true;host.started(this);}
                if(out!=null){int len=2*n,at=0;while(at<len&&ready()){int wrote=out.write(pcm,at,len-at,AudioTrack.WRITE_BLOCKING);if(wrote<=0){drop(out);out=null;host.note("声音输出中断，切换后台后可重试。");break;}at+=wrote;}}
                tick+=step;long delay=tick-System.nanoTime();if(delay>0)LockSupport.parkNanos(delay);else if(delay < -MAX_LAG_NANOS)tick=0;
            }
        }finally{drop(out);try{if(open&&dirty)checkpoint();}finally{Bridge.close();}}
    }
}
