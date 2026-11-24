package local.k98;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

final class DisplaySettings {
    private static int dp(Context context, int value) {
        return Math.round(context.getResources().getDisplayMetrics().density * value);
    }

    static ImageButton gear(Context context) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(R.drawable.ic_settings);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setContentDescription("游戏设置");
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xda202528);
        circle.setStroke(dp(context,1),0xff697174);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x55ffffff),circle,null));
        button.setPadding(0,0,0,0);
        return button;
    }

    static AlertDialog build(Context context, DisplayOptions current, boolean arcade, ControlEditor.Screen screen,
                             Consumer<DisplayOptions> apply) {
        return build(context,current,arcade,screen,apply,null);
    }
    static AlertDialog build(Context context, DisplayOptions current, boolean arcade, ControlEditor.Screen screen,
                             Consumer<DisplayOptions> apply,Runnable debug) {
        ScrollView scroll = new ScrollView(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(context,20),0,dp(context,20),0);
        scroll.addView(body);
        final ControlProfile[] draft={current.profile};
        LinearLayout links=new LinearLayout(context);
        Button guide=new Button(context);guide.setText("出招表预览");
        Button edit=new Button(context);edit.setText("按键布局");
        links.addView(guide,new LinearLayout.LayoutParams(0,-2,1));links.addView(edit,new LinearLayout.LayoutParams(0,-2,1));body.addView(links);
        if(debug!=null){Button trace=new Button(context);trace.setText("输入调试");trace.setOnClickListener(v->debug.run());body.addView(trace);}
        TextView hint=new TextView(context);hint.setText("按键布局可以拖动、缩放；点「应用」保存更改。");hint.setTextSize(13);body.addView(hint);

        Switch full = new Switch(context);
        full.setText("全屏模式");
        full.setMinHeight(dp(context,46));
        full.setChecked(current.fullscreen);
        body.addView(full);
        CheckBox aspect = new CheckBox(context);
        aspect.setText("保持原始 4:3 比例");
        aspect.setMinHeight(dp(context,42));
        aspect.setChecked(current.keepAspect);
        body.addView(aspect);

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView amount = new TextView(context);
        amount.setTextColor(0xfff2f0e7);
        row.addView(amount,new LinearLayout.LayoutParams(dp(context,132),-2));
        SeekBar slider = new SeekBar(context);
        slider.setMax(100);
        slider.setProgress(current.transparency);
        slider.setContentDescription("全屏按键透明度");
        row.addView(slider,new LinearLayout.LayoutParams(0,dp(context,48),1));
        body.addView(row);

        Preview preview = new Preview(context, arcade, screen);
        body.addView(preview,new LinearLayout.LayoutParams(-1,dp(context,80)));
        Runnable refresh = () -> {
            DisplayOptions options = new DisplayOptions(full.isChecked(),aspect.isChecked(),slider.getProgress(),draft[0]);
            aspect.setEnabled(options.fullscreen);
            slider.setEnabled(options.fullscreen);
            amount.setAlpha(options.fullscreen ? 1f : .55f);
            amount.setText("按键透明度  " + options.transparency + "%");
            preview.update(options);
        };
        full.setOnCheckedChangeListener((button, checked) -> refresh.run());
        aspect.setOnCheckedChangeListener((button, checked) -> refresh.run());
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar,int progress,boolean user) { refresh.run(); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        refresh.run();
        guide.setOnClickListener(v->MoveGuide.open(guide,new DisplayOptions(full.isChecked(),aspect.isChecked(),slider.getProgress(),draft[0]),screen,arcade));
        edit.setOnClickListener(v->ControlEditor.open(edit,new DisplayOptions(full.isChecked(),aspect.isChecked(),slider.getProgress(),draft[0]),screen,p->{draft[0]=p;refresh.run();}));
        return new AlertDialog.Builder(context).setTitle("游戏设置").setView(scroll)
            .setPositiveButton("应用",(dialog,which) -> apply.accept(
                new DisplayOptions(full.isChecked(),aspect.isChecked(),slider.getProgress(),draft[0])))
            .setNegativeButton("取消",null).create();
    }

    // Draw a miniature phone at its actual aspect ratio using the gameplay renderer.
    private static final class Preview extends View {
        private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final boolean arcade;
        private final ControlEditor.Screen screen;
        private Bitmap poster;
        private Controls controls;
        private DisplayOptions options = DisplayOptions.DEFAULT;

        Preview(Context context,boolean arcade,ControlEditor.Screen screen) {
            super(context);
            this.arcade=arcade;
            this.screen=screen;
            setContentDescription("画面与按键预览");
            try (InputStream image=context.getAssets().open("fights/fight-1.png")) {
                BitmapFactory.Options decode=new BitmapFactory.Options();
                decode.inSampleSize=2;
                poster=BitmapFactory.decodeStream(image,null,decode);
            } catch(IOException ignored) {}
        }
        void update(DisplayOptions next) {
            if(controls==null || options.fullscreen!=next.fullscreen || options.keepAspect!=next.keepAspect || options.profile!=next.profile){
                controls=new Controls(screen.w,screen.h,arcade,next,screen.l,screen.t,screen.r,screen.b,screen.gear);
            }
            options=next;
            invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            if(controls==null)return;
            float scale=Math.min((float)getWidth()/controls.width,(float)getHeight()/controls.height);
            int saved=canvas.save();
            canvas.translate((getWidth()-controls.width*scale)/2,(getHeight()-controls.height*scale)/2);
            canvas.scale(scale,scale);
            canvas.clipRect(0,0,controls.width,controls.height);
            canvas.drawColor(0xff151819);
            if(poster!=null)canvas.drawBitmap(poster,null,controls.video,paint);
            controls.draw(canvas,0,0,0,options.controlAlpha());
            canvas.restoreToCount(saved);
        }
    }
}
