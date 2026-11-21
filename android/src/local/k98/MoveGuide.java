package local.k98;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.io.IOException;
import java.util.*;

/** Offline landscape reference: the stage stays on screen when the move list scrolls. */
final class MoveGuide {
    private final Context c;private final List<MoveBook.Fighter> fighters;private final GuideMedia media;
    private final SharedPreferences prefs;private final Dialog dialog;private final Map<MoveBook.Move,View> rows=new HashMap<>();
    private MoveBook.Fighter fighter;private MoveBook.Move current;private Button select;private Switch facing,reading;
    private TextView caption,command,wideTitle;private LinearLayout list,right;private ScrollView scroll;private GuideDemo demo;
    private int dp(int n){return ControlEditor.dp(c,n);}
    static void open(View owner,DisplayOptions o,ControlEditor.Screen screen,boolean arcade){
        Context c=owner.getContext();try{new MoveGuide(c,o,screen,arcade).show(owner);}
        catch(IOException bad){new AlertDialog.Builder(c).setTitle("无法读取出招表").setMessage(bad.getMessage()).setPositiveButton("返回",null).show();}
    }
    MoveGuide(Context c,DisplayOptions o,ControlEditor.Screen screen,boolean arcade)throws IOException{
        this.c=c;fighters=MoveBook.read(c.getAssets().open("guide/commands-zh.tsv"));media=new GuideMedia(c.getAssets());prefs=c.getSharedPreferences("moveGuide",Context.MODE_PRIVATE);
        fighter=fighters.get(Math.min(1,fighters.size()-1));String previous=prefs.getString("fighter","");for(MoveBook.Fighter f:fighters)if(f.name.equals(previous))fighter=f;current=fighter.moves.get(0);
        dialog=new Dialog(c,android.R.style.Theme_Material_NoActionBar_Fullscreen);
        LinearLayout root=column();root.setPadding(dp(7),dp(2),dp(7),dp(3));root.setBackgroundColor(0xff171b1d);
        LinearLayout head=new LinearLayout(c);head.setGravity(Gravity.CENTER_VERTICAL);root.addView(head,-1,-2);
        TextView header=text("出招表",18);header.setTypeface(null,Typeface.BOLD);head.addView(header);
        select=button("选角色："+fighter.name+" ▾");select.setMaxLines(1);select.setHorizontallyScrolling(false);select.setEllipsize(TextUtils.TruncateAt.END);head.addView(select,new LinearLayout.LayoutParams(0,-2,1));select.setOnClickListener(v->choose());
        wideTitle=text("",15);wideTitle.setMaxLines(2);wideTitle.setEllipsize(TextUtils.TruncateAt.END);wideTitle.setVisibility(View.GONE);head.addView(wideTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button wideReplay=button("重播"),expand=button("放大演示"),back=button("返回设置");head.addView(wideReplay);wideReplay.setVisibility(View.GONE);head.addView(expand);head.addView(back);back.setOnClickListener(v->dialog.dismiss());

        LinearLayout split=new LinearLayout(c);root.addView(split,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout left=column();split.addView(left,new LinearLayout.LayoutParams(0,-1,.63f));
        TextView status=text("",13);status.setTextColor(0xffe7cc87);status.setSingleLine();status.setEllipsize(TextUtils.TruncateAt.END);
        demo=new GuideDemo(c,o,screen,arcade,status::setText);left.addView(demo,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout foot=new LinearLayout(c);foot.setGravity(Gravity.CENTER_VERTICAL);left.addView(foot,-1,-2);
        LinearLayout labels=column();foot.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        caption=text("",16);caption.setSingleLine();caption.setEllipsize(TextUtils.TruncateAt.END);caption.setTypeface(null,Typeface.BOLD);labels.addView(caption);
        command=text("",14);command.setSingleLine();command.setEllipsize(TextUtils.TruncateAt.END);labels.addView(command);
        Button replay=button("重播");foot.addView(replay);replay.setOnClickListener(v->demo.replay());left.addView(status,-1,-2);

        right=column();right.setPadding(dp(8),0,0,0);split.addView(right,new LinearLayout.LayoutParams(0,-1,.37f));
        scroll=new ScrollView(c);scroll.setVerticalScrollBarEnabled(true);scroll.setContentDescription("可独立滚动的招式列表");right.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        LinearLayout content=column();scroll.addView(content);Button options=button("读法与朝向 ▾");content.addView(options,-1,-2);
        LinearLayout toggles=column();content.addView(toggles);toggles.setVisibility(View.GONE);
        facing=toggle(toggles,"角色面朝左",prefs.getBoolean("left",false));reading=toggle(toggles,"中文分步读法",prefs.getBoolean("words",false));
        toggles.addView(text(color("A 轻拳　B 轻脚\nC 重拳　D 重脚"),13));toggles.addView(text("／ 任选其一（演示第一键），＋ 同时按。分步读法不是 SP 一键出招。",13));
        options.setOnClickListener(v->{boolean open=toggles.getVisibility()!=View.VISIBLE;toggles.setVisibility(open?View.VISIBLE:View.GONE);options.setText(open?"收起选项 ▴":"读法与朝向 ▾");});
        list=column();content.addView(list);
        facing.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("left",on).apply();populate(false,true);});
        reading.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("words",on).apply();populate(false,false);});
        wideReplay.setOnClickListener(v->demo.replay());
        expand.setOnClickListener(v->{boolean wide=right.getVisibility()==View.VISIBLE;right.setVisibility(wide?View.GONE:View.VISIBLE);foot.setVisibility(wide?View.GONE:View.VISIBLE);header.setVisibility(wide?View.GONE:View.VISIBLE);select.setVisibility(wide?View.GONE:View.VISIBLE);wideTitle.setVisibility(wide?View.VISIBLE:View.GONE);wideReplay.setVisibility(wide?View.VISIBLE:View.GONE);expand.setText(wide?"显示列表":"放大演示");});
        dialog.setOnKeyListener((d,key,e)->{if(key!=KeyEvent.KEYCODE_BACK||right.getVisibility()!=View.GONE)return false;if(e.getAction()==KeyEvent.ACTION_UP)expand.performClick();return true;});
        dialog.setContentView(root);populate(true,false);
    }
    private void show(View owner){dialog.getWindow().getDecorView().setSystemUiVisibility(5894);SettingsWindows.tie(owner,dialog);
        dialog.setOnDismissListener(d->demo.stop());dialog.show();dialog.getWindow().setLayout(-1,-1);demo.replay();}
    private LinearLayout column(){LinearLayout v=new LinearLayout(c);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private TextView text(CharSequence s,int size){TextView t=new TextView(c);t.setTextSize(size);t.setTextColor(0xffe5e6db);t.setText(s);return t;}
    private Button button(String s){Button b=new Button(c);b.setAllCaps(false);b.setText(s);b.setTextSize(14);b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(dp(8),0,dp(8),0);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));return b;}
    private Switch toggle(LinearLayout parent,String label,boolean value){Switch s=new Switch(c);s.setText(label);s.setMinHeight(dp(44));s.setChecked(value);parent.addView(s);return s;}
    static CharSequence color(String s){SpannableString out=new SpannableString(s);
        for(int i=0;i<s.length();i++){int k=s.charAt(i)-'A';if(k>=0&&k<4)out.setSpan(new ForegroundColorSpan(Controls.COLORS[k]),i,i+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return out;}
    private void choose(){
        LinearLayout panel=new LinearLayout(c);panel.setPadding(dp(14),0,dp(14),0);panel.setOrientation(LinearLayout.VERTICAL);
        EditText query=new EditText(c);query.setSingleLine();query.setHint("输入角色或队伍名");panel.addView(query);
        ListView items=new ListView(c);panel.addView(items,new LinearLayout.LayoutParams(-1,0,1));
        ArrayAdapter<MoveBook.Fighter> data=new ArrayAdapter<>(c,android.R.layout.simple_list_item_1,new ArrayList<>(fighters));items.setAdapter(data);
        AlertDialog picker=new AlertDialog.Builder(c).setTitle("选择角色").setView(panel).setNegativeButton("返回",null).create();
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int n){}public void afterTextChanged(Editable e){}
            public void onTextChanged(CharSequence s,int a,int before,int n){String key=s.toString().trim();data.clear();for(MoveBook.Fighter f:fighters)if(f.toString().contains(key))data.add(f);data.notifyDataSetChanged();items.setSelection(0);}});
        items.setOnItemClickListener((p,v,i,id)->{fighter=data.getItem(i);prefs.edit().putString("fighter",fighter.name).apply();picker.dismiss();current=fighter.moves.get(0);populate(true,true);});
        SettingsWindows.tie(select,picker);picker.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        picker.show();picker.getWindow().setLayout(Math.min(c.getResources().getDisplayMetrics().widthPixels,dp(530)),-1);
        int pos=fighters.indexOf(fighter);if(pos>=0)items.setSelection(pos);
    }
    private void populate(boolean resetScroll,boolean play){
        list.removeAllViews();rows.clear();select.setText("选角色："+fighter.name+" ▾");
        list.addView(text(fighter.group+" · "+(reading.isChecked()?"分步读法":"原版指令"),13));
        for(MoveBook.Move m:fighter.moves){LinearLayout row=column();row.setPadding(dp(7),dp(5),dp(4),dp(5));row.setMinimumHeight(dp(48));
            TextView title=text(m.name+"　·　"+m.type,16);title.setTypeface(null,Typeface.BOLD);row.addView(title);row.addView(text(color(m.format(facing.isChecked(),reading.isChecked())),16));
            if(!m.note.isEmpty())row.addView(text(m.note,13));row.setFocusable(true);row.setClickable(true);
            StateListDrawable bg=new StateListDrawable();bg.addState(new int[]{android.R.attr.state_pressed},new ColorDrawable(0xff435750));bg.addState(new int[]{android.R.attr.state_selected},new ColorDrawable(0xff344740));bg.addState(new int[]{android.R.attr.state_focused},new ColorDrawable(0xff3d4949));bg.addState(new int[]{},new ColorDrawable(0x00000000));row.setBackground(bg);
            row.setOnClickListener(v->pick(m,true));rows.put(m,row);list.addView(row);View line=new View(c);line.setBackgroundColor(0xff384043);list.addView(line,-1,1);
        }pick(current,play);if(resetScroll)scroll.scrollTo(0,0);
    }
    private void pick(MoveBook.Move m,boolean play){current=m;for(Map.Entry<MoveBook.Move,View> e:rows.entrySet())e.getValue().setSelected(e.getKey()==m);
        String input=m.format(facing.isChecked(),false);caption.setText("演示："+m.name);command.setText(color("指令："+input));wideTitle.setText(color(fighter.name+" · "+m.name+"  "+input));demo.select(media.get(fighter),m,facing.isChecked(),play);}
}
