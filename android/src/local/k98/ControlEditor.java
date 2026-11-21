package local.k98;

import android.app.Dialog;
import android.content.Context;
import android.graphics.*;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.function.Consumer;

/** Edits a draft. Only the parent settings dialog's Apply updates the live game. */
final class ControlEditor {
    static int dp(Context c,int n){return Math.round(c.getResources().getDisplayMetrics().density*n);}
    static final class Screen {
        final int w,h,l,t,r,b,gear;
        Screen(int w,int h,int l,int t,int r,int b,int gear){this.w=Math.max(1,w);this.h=Math.max(1,h);this.l=l;this.t=t;this.r=r;this.b=b;this.gear=gear;}
    }
    static void open(View owner,DisplayOptions opts,Screen screen,Consumer<ControlProfile> use){
        Context c=owner.getContext();
        Dialog d=new Dialog(c,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout root=new LinearLayout(c);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff171b1d);
        root.setPadding(dp(c,10),dp(c,2),dp(c,10),dp(c,3));
        TextView note=new TextView(c);note.setTextSize(14);note.setTextColor(0xffece7d9);
        final String help="拖动按键改位置，滑动下方滑杆改大小。投币键仅在街机中显示。";
        note.setText(help);root.addView(note);
        Stage preview=new Stage(c,opts,screen);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));
        // Give the slider its own stationary row. A HorizontalScrollView steals its MOVE events.
        LinearLayout sizeRow=new LinearLayout(c);sizeRow.setGravity(Gravity.CENTER_VERTICAL);root.addView(sizeRow,-1,-2);
        TextView amount=new TextView(c);amount.setGravity(Gravity.CENTER);amount.setSingleLine();sizeRow.addView(amount,dp(c,88),-2);
        SeekBar slider=new SeekBar(c);slider.setMax(ControlProfile.MAX-ControlProfile.MIN);slider.setContentDescription("所选按键大小");
        sizeRow.addView(slider,new LinearLayout.LayoutParams(0,dp(c,48),1));
        // Only the picker and action buttons can scroll on narrow or zoomed screens.
        HorizontalScrollView tools=new HorizontalScrollView(c);tools.setHorizontalScrollBarEnabled(true);tools.setFillViewport(true);
        LinearLayout bar=new LinearLayout(c);bar.setGravity(Gravity.CENTER_VERTICAL);tools.addView(bar);root.addView(tools);
        Spinner list=new Spinner(c);list.setContentDescription("选择要调整的按键");
        ArrayAdapter<String> adapter=new ArrayAdapter<>(c,android.R.layout.simple_spinner_item,ControlProfile.NAMES);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);list.setAdapter(adapter);bar.addView(list);
        Button reset=new Button(c);reset.setText("恢复默认");bar.addView(reset);
        Button cancel=new Button(c);cancel.setText("取消");bar.addView(cancel);
        Button done=new Button(c);done.setText("完成");bar.addView(done);
        preview.changed=()->{
            int id=preview.selected;int size=preview.profile.size(id);
            if(list.getSelectedItemPosition()!=id)list.setSelection(id);
            slider.setProgress(size-ControlProfile.MIN);amount.setText("大小 "+size+"%");
        };
        list.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            public void onItemSelected(AdapterView<?> a,View v,int i,long row){if(preview.selected!=i){preview.selected=i;preview.invalidate();preview.changed.run();}}
            public void onNothingSelected(AdapterView<?> a){}
        });
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar s){}
            public void onStopTrackingTouch(SeekBar s){}
            public void onProgressChanged(SeekBar s,int v,boolean user){if(user){int id=preview.selected;int size=v+ControlProfile.MIN;
                ControlProfile p=preview.profile.scale(id,size);if(preview.layout(p).obstructed(id)){
                    preview.changed.run();note.setText("这个大小会挡住其他按键；请先把按键挪开。");return;
                }preview.update(p);amount.setText("大小 "+size+"%");if(!help.contentEquals(note.getText()))note.setText(help);}}
        });
        preview.onCollision=()->note.setText("两个按键靠得太近，已撤销这次移动。请留出一点间距。");
        reset.setOnClickListener(v->{preview.update(ControlProfile.DEFAULT);preview.selected=0;preview.changed.run();note.setText("已恢复默认布局；点完成后，在设置里点应用保存。");});
        cancel.setOnClickListener(v->d.dismiss());done.setOnClickListener(v->{use.accept(preview.profile);d.dismiss();});
        preview.changed.run();d.setContentView(root);
        Window win=d.getWindow();if(win!=null){win.getDecorView().setSystemUiVisibility(5894);
            if(Build.VERSION.SDK_INT>=28){WindowManager.LayoutParams lp=win.getAttributes();lp.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;win.setAttributes(lp);}}
        SettingsWindows.tie(owner,d);d.show();if(win!=null)win.setLayout(-1,-1);
    }
    static final class Stage extends View {
        final Screen screen;final DisplayOptions opts;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        ControlProfile profile,before;Controls pad;int selected=0,drag=-1,pointer=-1;
        Runnable changed=()->{},onCollision=()->{};
        float zoom=1,offsetX,offsetY,grabX,grabY;
        Stage(Context c,DisplayOptions o,Screen s){super(c);opts=o;screen=s;update(o.profile);setContentDescription("按键位置预览，拖动控件更改位置");}
        Controls layout(ControlProfile p){return new Controls(screen.w,screen.h,true,new DisplayOptions(opts.fullscreen,opts.keepAspect,0,p),screen.l,screen.t,screen.r,screen.b,screen.gear);}
        void update(ControlProfile p){profile=p;pad=layout(p);invalidate();}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){zoom=Math.max(.001f,Math.min((w-2f)/screen.w,(h-2f)/screen.h));offsetX=(w-zoom*screen.w)/2;offsetY=(h-zoom*screen.h)/2;}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);int save=c.save();c.translate(offsetX,offsetY);c.scale(zoom,zoom);c.clipRect(0,0,screen.w,screen.h);
            c.drawColor(0xff282f33);paint.setStyle(Paint.Style.FILL);paint.setColor(0xff334b54);c.drawRect(pad.video,paint);
            paint.setColor(0xff424a4c);c.drawRect(pad.gearLeft,screen.t,screen.w-screen.r,pad.gearBottom,paint);
            pad.draw(c,0,0,0,255);
            paint.setColor(pad.obstructed(selected)?0xffff7569:0xffffdf79);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(getContext(),2)/zoom);
            float g=3/zoom,x=pad.cx[selected],y=pad.cy[selected],a=pad.rx[selected]+g,b=pad.ry[selected]+g;
            if(selected<ControlProfile.PAUSE)c.drawOval(x-a,y-b,x+a,y+b,paint);else c.drawRoundRect(x-a,y-b,x+a,y+b,b*.3f,b*.3f,paint);
            c.restoreToCount(save);
        }
        @Override public boolean performClick(){super.performClick();return true;}
        @Override public boolean onTouchEvent(MotionEvent e){
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){int index=e.getActionIndex();float x=(e.getX(index)-offsetX)/zoom,y=(e.getY(index)-offsetY)/zoom;
                int id=pad.pickSlot(x,y);if(id<0)return false;
                drag=selected=id;before=profile;pointer=e.getPointerId(index);grabX=pad.cx[id]-x;grabY=pad.cy[id]-y;changed.run();invalidate();return true;}
            if(drag<0)return false;
            int idx=e.findPointerIndex(pointer);
            if(action==MotionEvent.ACTION_CANCEL||idx<0){update(before);drag=pointer=-1;changed.run();return true;}
            boolean up=(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)&&e.getPointerId(e.getActionIndex())==pointer;
            if(action==MotionEvent.ACTION_MOVE||up){float x=((e.getX(idx)-offsetX)/zoom+grabX-pad.safeX)/pad.safeWidth;
                float y=((e.getY(idx)-offsetY)/zoom+grabY-pad.safeY)/pad.safeHeight;
                // A tap selects a control without freezing its responsive default position.
                if(Math.abs(pad.safeX+x*pad.safeWidth-pad.cx[drag])>.02f||Math.abs(pad.safeY+y*pad.safeHeight-pad.cy[drag])>.02f)
                    update(profile.move(drag,x,y));
                if(up){if(pad.obstructed(drag)){update(before);onCollision.run();}drag=pointer=-1;performClick();changed.run();}}
            return true;
        }
    }
}

