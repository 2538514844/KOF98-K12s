package local.k98;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Left controls / right fight / central dark fade, including loading progress. */
final class LoadingScreen extends FrameLayout {
    private static final int BG=0xff17191a,WHITE=0xfff2f0e7,MUTED=0xffbbc0bc,GOLD=0xffffd663;
    private final FightBackdrop backdrop;
    private final ScrollView scroll;
    private final LinearLayout panel;
    private final TextView title,status,eyebrow;
    private final Button primary,importButton,modeButton;
    private final boolean hasData;
    private final ProgressBar progress;
    LoadingScreen(Context context,boolean hasData,NeoMode mode,boolean hasSave,String error,Runnable start,Runnable pick,Runnable chooseMode){
        super(context);this.hasData=hasData;setBackgroundColor(BG);
        backdrop=new FightBackdrop(context);addView(backdrop,new LayoutParams(-1,-1));
        addView(new Shade(context),new LayoutParams(-1,-1));
        scroll=new ScrollView(context);scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);
        panel=new LinearLayout(context);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER_VERTICAL);
        scroll.addView(panel,new ScrollView.LayoutParams(-1,-1));addView(scroll,new LayoutParams(-1,-1));
        eyebrow=text("NEO GEO  /  1998",14,GOLD,true);title=text("拳皇 ’98",38,WHITE,true);
        panel.addView(eyebrow);panel.addView(title);
        status=text(error!=null?error:hasData?"游戏数据已就绪":"等待游戏数据 · KOF98-data.zip",16,error==null?MUTED:0xffff9d8d,false);
        status.setPadding(0,dp(4),0,dp(8));panel.addView(status);
        progress=new ProgressBar(context,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(GOLD));progress.setIndeterminateTintList(ColorStateList.valueOf(GOLD));progress.setVisibility(INVISIBLE);
        panel.addView(progress,new LinearLayout.LayoutParams(-1,dp(5)));
        modeButton=button("",false);
        modeButton.setOnClickListener(v->chooseMode.run());
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(214),dp(46));
        mp.topMargin=dp(4);panel.addView(modeButton,mp);
        primary=button("",true);
        updateMode(mode,hasSave);
        primary.setOnClickListener(v->{if(hasData)start.run();else pick.run();});
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(194),dp(46));bp.topMargin=dp(4);panel.addView(primary,bp);
        if(hasData){importButton=button("重新导入游戏数据",false);importButton.setOnClickListener(v->pick.run());
            LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(194),dp(40));ip.topMargin=dp(6);panel.addView(importButton,ip);
        }else importButton=null;
    }
    void updateMode(NeoMode mode,boolean hasSave) {
        modeButton.setText("模式  " + mode.name() + "  ▾");
        modeButton.setContentDescription("选择游戏模式，当前为" + mode.label + "，" + mode.name());
        primary.setText(hasData ? (hasSave ? "继续游戏" : "开始游戏") : "导入游戏数据");
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView text(String value,int size,int color,boolean bold){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String label,boolean gold){Button b=new Button(getContext());b.setAllCaps(false);b.setText(label);b.setTextSize(16);b.setTextColor(gold?BG:MUTED);b.setPadding(dp(4),0,dp(4),0);b.setMinWidth(0);b.setMinimumWidth(0);GradientDrawable d=new GradientDrawable();d.setColor(gold?GOLD:0x0017191a);d.setCornerRadius(dp(4));b.setBackground(d);return b;}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){
        super.onSizeChanged(w,h,oldw,oldh);
        FrameLayout.LayoutParams scene=(FrameLayout.LayoutParams)backdrop.getLayoutParams();scene.leftMargin=Math.round(w*.35f);scene.width=w-scene.leftMargin;backdrop.setLayoutParams(scene);
        FrameLayout.LayoutParams left=(FrameLayout.LayoutParams)scroll.getLayoutParams();left.width=Math.round(w*.47f);scroll.setLayoutParams(left);
        panel.setPadding(Math.round(w*.0525f),dp(14),dp(14),dp(14));
        float heightDp=h/getResources().getDisplayMetrics().density;
        title.setTextSize(Math.max(24,Math.min(38,heightDp/8.5f)));
        float textSize=Math.max(12,Math.min(16,heightDp/23f));status.setTextSize(textSize);eyebrow.setTextSize(Math.max(11,textSize-2));
        int width=Math.min(dp(214),Math.round(w*.27f));primary.getLayoutParams().width=width;primary.setTextSize(textSize);
        modeButton.getLayoutParams().width=width;modeButton.setTextSize(textSize);
        if(importButton!=null){importButton.getLayoutParams().width=width;importButton.setTextSize(textSize-1);}
    }
    void busy(String message,boolean indeterminate){status.setText(message);status.setTextColor(MUTED);primary.setEnabled(false);modeButton.setEnabled(false);if(importButton!=null)importButton.setEnabled(false);progress.setIndeterminate(indeterminate);progress.setProgress(0);progress.setVisibility(VISIBLE);}
    void progress(int value){progress.setProgress(value);}
    void addDuel(Runnable open){
        Button duel=button("蓝牙对战",false);
        duel.setOnClickListener(v->{if(primary.isEnabled())open.run();});
        panel.addView(duel,panel.indexOfChild(primary)+1,new LinearLayout.LayoutParams(dp(194),dp(42)));
    }
    void active(boolean value){backdrop.active(value);}
    void dispose(){backdrop.dispose();}
    private static final class Shade extends View {
        private final Paint paint=new Paint();private Shader center,edges;
        Shade(Context c){super(c);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onSizeChanged(int w,int h,int a,int b){
            center=new LinearGradient(0,0,w,0,new int[]{BG,BG,0xfc17191a,0xe017191a,0x8f17191a,0x2e17191a,0x0017191a},new float[]{0,.31f,.37f,.43f,.50f,.59f,.72f},Shader.TileMode.CLAMP);
            edges=new LinearGradient(0,0,0,h,new int[]{0xb317191a,0x2617191a,0x0017191a,0x0017191a,0x4017191a,0xd917191a},new float[]{0,.18f,.32f,.68f,.83f,1},Shader.TileMode.CLAMP);
        }
        @Override protected void onDraw(Canvas canvas){if(center==null)return;paint.setShader(edges);canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(center);canvas.drawRect(0,0,getWidth(),getHeight(),paint);}
    }
}
