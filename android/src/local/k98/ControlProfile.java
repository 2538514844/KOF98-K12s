package local.k98;

import java.util.Arrays;

/** Persist positions relative to the usable screen, not pixels. Shared by both display modes. */
final class ControlProfile {
    static final int STICK=0, FIRST_KEY=1, PAUSE=5, START=6, COIN=7, COUNT=8;
    static final int MIN=65, MAX=160;
    static final String[] NAMES={"方向摇杆","A 轻拳","B 轻脚","C 重拳","D 重脚","暂停","START 开始","投币"};
    static final ControlProfile DEFAULT=new ControlProfile();
    private final float[] x,y;
    private final int[] size;
    private ControlProfile(){x=new float[COUNT];y=new float[COUNT];size=new int[COUNT];Arrays.fill(x,-1f);Arrays.fill(y,-1f);Arrays.fill(size,100);}
    private ControlProfile(ControlProfile p){x=p.x.clone();y=p.y.clone();size=p.size.clone();}
    boolean positioned(int id){return x[id]>=0;}
    float x(int id){return x[id];} float y(int id){return y[id];} int size(int id){return size[id];}
    ControlProfile move(int id,float nx,float ny){
        if(!Float.isFinite(nx)||!Float.isFinite(ny))return this;
        ControlProfile p=new ControlProfile(this);p.x[id]=Math.max(0,Math.min(1,nx));p.y[id]=Math.max(0,Math.min(1,ny));return p;
    }
    ControlProfile scale(int id,int percent){ControlProfile p=new ControlProfile(this);p.size[id]=Math.max(MIN,Math.min(MAX,percent));return p;}
    String encode(){StringBuilder s=new StringBuilder("1");for(int i=0;i<COUNT;i++)s.append(';').append(x[i]).append(',').append(y[i]).append(',').append(size[i]);return s.toString();}
    static ControlProfile decode(String raw){
        if(raw==null||raw.length()>650)return DEFAULT;
        try{String[] rows=raw.split(";",-1);if(rows.length!=COUNT+1||!"1".equals(rows[0]))return DEFAULT;
            ControlProfile p=new ControlProfile();for(int i=0;i<COUNT;i++){
                String[] a=rows[i+1].split(",",-1);if(a.length!=3)return DEFAULT;
                float x=Float.parseFloat(a[0]),y=Float.parseFloat(a[1]);int size=Integer.parseInt(a[2]);
                if(!((x==-1&&y==-1)||(x>=0&&x<=1&&y>=0&&y<=1))||size<MIN||size>MAX)return DEFAULT;
                p.x[i]=x;p.y[i]=y;p.size[i]=size;
            }return p;
        }catch(NumberFormatException ex){return DEFAULT;}
    }
}

