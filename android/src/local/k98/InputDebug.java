package local.k98;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputFilter;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** On-device controls and document export; recording itself has no Android dependencies. */
final class InputDebug {
    static final int EXPORT=99;
    private final Main host;
    private final InputTrace trace;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable expire;
    private AlertDialog dialog;
    private boolean choosing,writing;
    private String note="";
    private String[] pendingExport;
    private String pendingNote;

    InputDebug(Main host,InputTrace trace){this.host=host;this.trace=trace;expire=()->trace.stop("duration_limit",SystemClock.uptimeMillis());}
    boolean visible(){return dialog!=null||choosing;}
    void show(){
        if(dialog!=null)return;
        if(choosing||writing){host.note("正在导出，请稍候。");return;}
        LinearLayout body=new LinearLayout(host);body.setOrientation(LinearLayout.VERTICAL);
        int padding=Math.round(16*host.getResources().getDisplayMetrics().density);
        body.setPadding(padding,0,padding,0);
        ScrollView scroll=new ScrollView(host);scroll.addView(body);
        TextView status=new TextView(host);status.setText(status());body.addView(status);
        TextView help=new TextView(host);
        help.setText("记录触屏位置、摇杆方向和每帧游戏输入。开始后试几次连招；失败时可回来标记，再停止并导出 ZIP。\n单次最多 2 分钟，达到容量上限会提前停止；导出前请勿退出应用。");
        body.addView(help);
        EditText description=new EditText(host);description.setText(note);description.setMaxLines(3);
        description.setHint("备注（选填）：角色、站位、想出的招式，例如右侧草薙京，↓↙←↓↙←＋A");
        description.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});body.addView(description);
        Button mark=new Button(host);mark.setText("标记刚才没出招");mark.setEnabled(trace.recording());body.addView(mark);
        Button stop=new Button(host);stop.setText("停止记录");stop.setEnabled(trace.recording());body.addView(stop);
        GameScreen game=host.currentGame();
        if(game!=null&&game.duel!=null){TextView hint=new TextView(host);hint.setText("蓝牙对战在此菜单中继续运行。请退出蓝牙对战后再导出。");body.addView(hint);}
        AlertDialog current=new AlertDialog.Builder(host).setTitle("输入调试").setView(scroll)
                .setPositiveButton(startLabel(),null)
                .setNeutralButton(trace.recording()?"停止并导出 ZIP":"导出 ZIP",null)
                .setNegativeButton("返回",null).create();
        dialog=current;host.inputDebugVisibilityChanged();
        Runnable refresh=new Runnable(){public void run(){
            if(dialog!=current)return;
            status.setText(status());mark.setEnabled(trace.recording());stop.setEnabled(trace.recording());
            current.getButton(AlertDialog.BUTTON_POSITIVE).setText(startLabel());
            current.getButton(AlertDialog.BUTTON_NEUTRAL).setText(trace.recording()?"停止并导出 ZIP":"导出 ZIP");
            handler.postDelayed(this,500);
        }};
        current.setOnDismissListener(d->{
            note=description.getText().toString();handler.removeCallbacks(refresh);
            if(dialog==current)dialog=null;
            if(!host.isDestroyed()&&!host.isFinishing())host.inputDebugVisibilityChanged();
        });
        current.show();
        Button start=current.getButton(AlertDialog.BUTTON_POSITIVE);
        start.setEnabled(game!=null);
        start.setOnClickListener(v->{
            if(host.currentGame()!=game||game==null){host.note("请先进入游戏。");return;}
            if(!trace.recording()){
                game.startInputTrace(metadata());
                handler.removeCallbacks(expire);handler.postDelayed(expire,InputTrace.MAX_DURATION_MS);
            }
            current.dismiss();
        });
        Button export=current.getButton(AlertDialog.BUTTON_NEUTRAL);export.setEnabled(trace.hasData());
        export.setOnClickListener(v->{
            if(host.currentGame()!=null&&host.currentGame().duel!=null){host.note("请先退出蓝牙对战，再从启动页右上角设置导出。");return;}
            note=description.getText().toString();
            if(export())current.dismiss();
        });
        mark.setOnClickListener(v->{
            if(trace.marker("failed_attempt",SystemClock.uptimeMillis())){host.note("已标记，会保留标记前的操作。");current.dismiss();}
        });
        stop.setOnClickListener(v->{trace.stop("user",SystemClock.uptimeMillis());handler.removeCallbacks(expire);status.setText(status());mark.setEnabled(false);stop.setEnabled(false);start.setText(startLabel());export.setText("导出 ZIP");});
        handler.postDelayed(refresh,500);
    }
    private String startLabel(){return trace.recording()?"继续记录":trace.hasData()?"重新记录（覆盖本次）":"开始记录";}
    private String status(){
        if(!trace.hasData())return "尚未开始。请在进入游戏后开启。";
        String reason=trace.reason();
        String state=trace.recording()?"记录中":"duration_limit".equals(reason)?"已满 2 分钟，自动停止":"size_limit".equals(reason)?"已达容量上限，自动停止":"已停止";
        return state+" · "+trace.elapsed(SystemClock.uptimeMillis())/1000+" 秒 · "+trace.size()+" 条记录";
    }
    private String metadata(){
        String version="unknown";int code=0;
        try{PackageInfo info=host.getPackageManager().getPackageInfo(host.getPackageName(),0);version=info.versionName;code=info.versionCode;}
        catch(PackageManager.NameNotFoundException ignored){}
        return "\"appVersion\":"+InputTrace.quote(version)+",\"versionCode\":"+code
                +",\"manufacturer\":"+InputTrace.quote(Build.MANUFACTURER)+",\"model\":"+InputTrace.quote(Build.MODEL)
                +",\"androidRelease\":"+InputTrace.quote(Build.VERSION.RELEASE)+",\"sdk\":"+Build.VERSION.SDK_INT
                +",\"density\":"+host.getResources().getDisplayMetrics().density
                +",\"displayRefreshHz\":"+host.getWindowManager().getDefaultDisplay().getRefreshRate();
    }
    private boolean export(){
        if(!trace.hasData()||choosing||writing)return false;
        trace.stop("export",SystemClock.uptimeMillis());handler.removeCallbacks(expire);
        pendingExport=trace.snapshot();pendingNote=note;
        String date=new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.US).format(new Date(trace.wallTime()));
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/zip").putExtra(Intent.EXTRA_TITLE,"KOF98-input-"+date+".zip");
        choosing=true;
        try{host.startActivityForResult(intent,EXPORT);return true;}
        catch(ActivityNotFoundException|SecurityException e){
            choosing=false;pendingExport=null;pendingNote=null;
            host.note("无法打开系统文件保存器，记录仍保留，可以重新导出。");return false;
        }
    }
    void result(int code,Intent intent){
        if(!choosing)return;
        choosing=false;host.inputDebugVisibilityChanged();
        final String[] records=pendingExport;final String description=pendingNote;
        pendingExport=null;pendingNote=null;
        Uri uri=intent==null?null:intent.getData();
        if(code!=Activity.RESULT_OK||uri==null||records==null){host.note("已取消导出，记录仍保留。");return;}
        writing=true;
        new Thread(()->{
            String failure=null;
            try(OutputStream out=host.getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new IOException("无法打开保存位置");
                InputTrace.writeZip(out,records,description);
            }catch(Exception e){failure=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            final String error=failure;
            host.runOnUiThread(()->{writing=false;host.note(error==null?"调试 ZIP 已导出，可在文件管理器中分享。":"导出失败："+error+"。记录仍保留，可重新导出。");});
        },"K98-input-export").start();
    }
    void close(){
        handler.removeCallbacksAndMessages(null);trace.stop("activity_destroyed",SystemClock.uptimeMillis());
        if(dialog!=null){dialog.setOnDismissListener(null);dialog.dismiss();dialog=null;}
        pendingExport=null;pendingNote=null;
    }
}
