package local.k98;

import android.app.*;
import android.content.*;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.io.*;

public final class Main extends Activity {
    private static final int PICK=98;
    private GameScreen game;
    private boolean resumed,busy,pausedMenu;
    private FrameLayout stage;private LoadingScreen loadingScreen;
    private NeoMode selectedMode = NeoMode.AES_EUR;
    private DisplayOptions displayOptions;
    private SharedPreferences displayPrefs;
    private ImageButton settingsButton;
    private AlertDialog settingsDialog;
    private int safeLeft,safeTop,safeRight,safeBottom;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(state!=null)selectedMode=NeoMode.fromName(state.getString("launchMode"));
        displayPrefs=getSharedPreferences("display",MODE_PRIVATE);
        DisplayOptions defaults=DisplayOptions.DEFAULT;
        displayOptions=new DisplayOptions(displayPrefs.getBoolean("fullscreen",defaults.fullscreen),
                displayPrefs.getBoolean("keepAspect",defaults.keepAspect),
                displayPrefs.getInt("transparency",defaults.transparency),ControlProfile.decode(displayPrefs.getString("controls.v1",null)));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        immersive();
        library(null);
    }
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putString("launchMode",selectedMode.name());}
    private void immersive(){getWindow().getDecorView().setSystemUiVisibility(5894);}
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)immersive();}
    private void library(String err){
        releaseLoading();stage=new FrameLayout(this);
        boolean ready=DataDepot.ready(getFilesDir());
        boolean hasSave=selectedMode.canResume(DataDepot.root(getFilesDir()));
        loadingScreen=new LoadingScreen(this,ready,selectedMode,hasSave,err,this::play,this::pick,this::chooseMode);
        stage.addView(loadingScreen,new FrameLayout.LayoutParams(-1,-1));
        settingsButton=DisplaySettings.gear(this);
        settingsButton.setOnClickListener(view -> openSettings());
        FrameLayout.LayoutParams gearParams=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.TOP|Gravity.RIGHT);
        gearParams.topMargin=dp(4)+safeTop;
        gearParams.rightMargin=dp(6)+safeRight;
        stage.addView(settingsButton,gearParams);
        stage.setOnApplyWindowInsetsListener((view,insets)->{
            applyInsets(insets);
            return insets;
        });
        setCutoutMode();
        setContentView(stage);
        stage.requestApplyInsets();
        loadingScreen.active(resumed);
    }
    private int dp(int value){return Math.round(getResources().getDisplayMetrics().density*value);}

    private void setCutoutMode(){
        if(Build.VERSION.SDK_INT<28)return;
        WindowManager.LayoutParams attributes=getWindow().getAttributes();
        // Keep one full-screen coordinate space across the launcher, settings and game.
        // Controls still respect applyInsets(); the window itself must not shift sideways.
        int mode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        if(attributes.layoutInDisplayCutoutMode!=mode){
            attributes.layoutInDisplayCutoutMode=mode;
            getWindow().setAttributes(attributes);
        }
    }
    private void applyInsets(WindowInsets insets){
        int left=insets.getSystemWindowInsetLeft(),top=insets.getSystemWindowInsetTop();
        int right=insets.getSystemWindowInsetRight(),bottom=insets.getSystemWindowInsetBottom();
        if(Build.VERSION.SDK_INT>=28 && insets.getDisplayCutout()!=null){
            DisplayCutout cutout=insets.getDisplayCutout();
            left=Math.max(left,cutout.getSafeInsetLeft());
            right=Math.max(right,cutout.getSafeInsetRight());
            top=Math.max(top,cutout.getSafeInsetTop());
            bottom=Math.max(bottom,cutout.getSafeInsetBottom());
        }
        safeLeft=left;safeTop=top;safeRight=right;safeBottom=bottom;
        FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)settingsButton.getLayoutParams();
        if(params.topMargin!=top+dp(4) || params.rightMargin!=right+dp(6)){
            params.topMargin=top+dp(4);params.rightMargin=right+dp(6);
            settingsButton.setLayoutParams(params);
        }
        if(game!=null)game.setSafeInsets(left,top,right,bottom);
    }
    private void openSettings(){
        if(settingsDialog!=null || pausedMenu || busy || isFinishing() || (game!=null && loadingScreen!=null))return;
        if(game!=null)game.active(false);
        if(loadingScreen!=null)loadingScreen.active(false);
        ControlEditor.Screen screen=new ControlEditor.Screen(stage.getWidth(),stage.getHeight(),safeLeft,safeTop,safeRight,safeBottom,dp(54));
        AlertDialog dialog=DisplaySettings.build(this,displayOptions,selectedMode.arcade,screen,next->{
            displayOptions=next;
            displayPrefs.edit().putBoolean("fullscreen",next.fullscreen)
                    .putBoolean("keepAspect",next.keepAspect)
                    .putInt("transparency",next.transparency).putString("controls.v1",next.profile.encode()).apply();
            if(game!=null)game.setDisplayOptions(next);
            setCutoutMode();
            stage.requestApplyInsets();
        });
        settingsDialog=dialog;
        dialog.setOnDismissListener(ignored->{
            settingsDialog=null;
            if(isFinishing() || isDestroyed())return;
            immersive();
            if(game!=null && !pausedMenu)game.active(resumed);
            if(loadingScreen!=null)loadingScreen.active(resumed);
        });
        dialog.show();
    }
    private void chooseMode() {
        if (busy || game != null || loadingScreen == null) return;
        final NeoMode[] modes = NeoMode.values();
        String[] labels = new String[modes.length];
        for (int i = 0; i < modes.length; i++) {
            NeoMode mode = modes[i];
            labels[i] = mode.label + "  " + mode.name() + (mode == NeoMode.AES_EUR ? "（默认）" : "");
        }
        new AlertDialog.Builder(this).setTitle("选择游戏模式")
            .setSingleChoiceItems(labels, selectedMode.ordinal(), (dialog, index) -> {
                selectedMode = modes[index];
                loadingScreen.updateMode(selectedMode, selectedMode.canResume(DataDepot.root(getFilesDir())));
                dialog.dismiss();
            })
            .setNegativeButton("取消", null).show();
    }
    private void releaseLoading(){if(loadingScreen==null)return;loadingScreen.dispose();if(loadingScreen.getParent() instanceof ViewGroup)((ViewGroup)loadingScreen.getParent()).removeView(loadingScreen);loadingScreen=null;}
    private void pick(){if(busy)return;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");try{startActivityForResult(i,PICK);}catch(ActivityNotFoundException ex){library("未找到系统文件选择器。");}}
    @Override protected void onActivityResult(int req,int code,Intent intent){super.onActivityResult(req,code,intent);if(req!=PICK||code!=RESULT_OK||intent==null||intent.getData()==null||busy||loadingScreen==null)return;final Uri uri=intent.getData();busy=true;loadingScreen.busy("正在校验游戏数据…",false);
        new Thread(()->{String failure=null;try(InputStream stream=getContentResolver().openInputStream(uri)){if(stream==null)throw new IOException("无法打开数据包。");DataDepot.install(getFilesDir(),stream,p->runOnUiThread(()->{if(!isDestroyed()&&busy&&loadingScreen!=null)loadingScreen.progress(p);}));}catch(Exception ex){failure=ex.getMessage();if(failure==null)failure=ex.getClass().getSimpleName();}final String msg=failure;runOnUiThread(()->{if(isFinishing()||isDestroyed())return;busy=false;if(msg==null)play();else library("导入失败："+msg);});},"k98-import").start();
    }
    private void play(){
        if(game!=null||busy)return;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(loadingScreen!=null)loadingScreen.busy("正在启动游戏…",true);
        game=new GameScreen(this,DataDepot.root(getFilesDir()),selectedMode,displayOptions);
        game.setSafeInsets(safeLeft,safeTop,safeRight,safeBottom);
        setCutoutMode();
        stage.addView(game,0,new FrameLayout.LayoutParams(-1,-1));
        stage.requestApplyInsets();
        game.active(resumed && !pausedMenu && settingsDialog==null);
    }
    void started(GameScreen sender){runOnUiThread(()->{if(game!=sender||isDestroyed()||isFinishing())return;releaseLoading();game.requestFocus();});}
    void failed(GameScreen sender,String msg){runOnUiThread(()->{if(game!=sender||isDestroyed()||isFinishing())return;game.shutdown();game=null;pausedMenu=false;getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);library("游戏启动或运行失败："+msg);});}
    void note(String msg){runOnUiThread(()->{if(!isDestroyed()&&!isFinishing())Toast.makeText(this,msg,Toast.LENGTH_LONG).show();});}
    void pauseMenu(){
        if(game==null||pausedMenu||settingsDialog!=null||isFinishing())return;
        pausedMenu=true;game.active(false);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("游戏已暂停")
            .setMessage("重载游戏将重新开始，当前对战进度不保留。")
            .setPositiveButton("继续对战",(d,w)->{})
            .setNeutralButton("重载游戏",(d,w)->{if(game!=null)game.reload();})
            .setNegativeButton("退出游戏",(d,w)->finish()).create();
        dialog.setOnDismissListener(d->{pausedMenu=false;if(!isFinishing()&&game!=null)game.active(resumed);immersive();});
        dialog.show();
    }
    @Override public void onBackPressed(){if(game!=null)pauseMenu();else super.onBackPressed();}
    @Override protected void onPause(){resumed=false;if(loadingScreen!=null)loadingScreen.active(false);if(game!=null)game.active(false);super.onPause();}
    @Override protected void onResume(){super.onResume();resumed=true;immersive();if(loadingScreen!=null)loadingScreen.active(settingsDialog==null);if(game!=null&&!pausedMenu&&settingsDialog==null)game.active(true);}
    @Override protected void onDestroy(){
        if(settingsDialog!=null){settingsDialog.setOnDismissListener(null);settingsDialog.dismiss();settingsDialog=null;}
        releaseLoading();if(game!=null)game.shutdown();super.onDestroy();
    }
}
