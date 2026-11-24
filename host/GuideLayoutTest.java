package local.k98;
import java.io.*;import java.util.*;

/** Exercises shipped (not mock) input geometry, profile codec and guide parser/demonstrator. */
public final class GuideLayoutTest {
    static int checks,layouts,entries;
    static void ok(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
    static void near(float a,float b,String why){ok(Math.abs(a-b)<.003f,why+" "+a+"/"+b);}
    static MoveBook.Move find(List<MoveBook.Fighter> fs,String charName,String move){for(MoveBook.Fighter f:fs)if(f.name.equals(charName))for(MoveBook.Move m:f.moves)if(m.name.equals(move))return m;throw new AssertionError(charName+move);}
    static void guide(String path)throws Exception{
        List<MoveBook.Fighter> fs=MoveBook.read(new FileInputStream(path));ok(fs.size()==52,"51 fighters/forms plus basics");Set<String> names=new HashSet<>();
        for(MoveBook.Fighter f:fs){ok(names.add(f.name)&&f.moves.size()>=5,"complete card "+f.name);Set<String> ms=new HashSet<>();
            for(MoveBook.Move m:f.moves){entries++;ok(ms.add(m.name),"double move");for(boolean left:new boolean[]{false,true}){
                String sym=m.format(left,false),words=m.format(left,true);ok(!sym.isEmpty()&&!words.isEmpty(),"blank instructions");
                List<MoveBook.Move.Beat> beats=m.beats(left),op=m.beats(!left);ok(beats.size()==op.size()&&!beats.isEmpty(),"demo");
                for(int i=0;i<beats.size();i++){MoveBook.Move.Beat a=beats.get(i),b=op.get(i);ok(a.ms>0&&MoveBook.X[a.dir]==-MoveBook.X[b.dir]&&MoveBook.Y[a.dir]==MoveBook.Y[b.dir]&&a.keys==b.keys,"mirror");ok((a.keys&~(PadLayout.A|PadLayout.B|PadLayout.C|PadLayout.D))==0,"never emit START/COIN");}
            }}
        }
        ok(entries==275,"guide count");MoveBook.Move k=find(fs,"草薙京","荒咬");ok(k.format(false,false).equals("↓↘→ ＋ A"),"Kyo right");ok(k.format(true,false).equals("↓↙← ＋ A"),"Kyo left");ok(k.format(true,true).equals("下、左下、左，同时按 A"),"Chinese steps");
        MoveBook.Move i=find(fs,"八神庵","八稚女");ok(i.dirs.equals("2363214")&&i.keys.equals("A/C"),"Iori super");
        MoveBook.Move l=find(fs,"莉安娜","月光锯");ok(l.beats(false).get(0).ms>l.beats(false).get(1).ms&&l.format(false,true).contains("蓄力"),"charge");
        MoveBook.Move cd=find(fs,"通用操作","吹飞攻击");ok(cd.beats(false).get(0).keys==(PadLayout.C|PadLayout.D),"simultaneous C+D");
        ok(find(fs,"草薙京（95版）","暗拂").keys.equals("A/C"),"hidden fighter");
        for(String bad:new String[]{"", "@\t入门\t测试\n", "@\t角色\t甲\n必杀\t招\t250\tP\t\n"}){boolean hit=false;try{MoveBook.read(new ByteArrayInputStream(bad.getBytes("UTF-8")));}catch(IOException ex){hit=true;}ok(hit,"invalid book rejected");}
    }
    static PadLayout pad(int w,int h,int[] t,boolean arcade,boolean full,ControlProfile p){return new PadLayout(w,h,arcade,new DisplayOptions(full,true,50,p),t[0],t[1],t[2],t[3],Math.round(h*.145f));}
    static void touch(PadLayout p){
        TouchInput t=new TouchInput();t.configure(p);int[] ids={6,2,9};float[] xs={p.dx+p.dr*.72f,p.bx[0],p.bx[2]},ys={p.dy+p.dr*.72f,p.by[0],p.by[2]};
        t.event(0,6,1,ids,xs,ys);t.event(5,2,2,ids,xs,ys);t.event(5,9,3,ids,xs,ys);
        ok(t.poll().keys==(PadLayout.RIGHT|PadLayout.DOWN),"custom diagonal first sample precedes later attack fingers");
        ok(t.poll().keys==(PadLayout.RIGHT|PadLayout.DOWN|PadLayout.A|PadLayout.C),"custom diagonal and two attack fingers");
        t.configure(p);ok(t.poll().keys==0,"no stale state after settings");
        for(int n=1;n<ControlProfile.COUNT;n++){
            int id=n;int[] key={0,PadLayout.A,PadLayout.B,PadLayout.C,PadLayout.D,0,PadLayout.START,PadLayout.COIN,PadLayout.MAX_KEYS};
            if(n==ControlProfile.PAUSE||!p.visible(id))continue;
            float[] x={p.cx[id]},y={p.cy[id]};t.event(0,6,1,ids,x,y);ok(t.poll().keys==key[id],"touch slot "+id);t.event(1,6,1,ids,x,y);ok(t.poll().keys==0,"release slot "+id);
        }
        ok(p.containsSlot(ControlProfile.PAUSE,p.cx[5],p.cy[5]),"pause center");
    }
    static void layouts(){ControlProfile d=ControlProfile.DEFAULT;
        ControlProfile cfg=d.move(0,.21f,.61f).scale(0,82).move(1,.66f,.73f).scale(1,135).move(2,.82f,.73f).scale(2,72).move(3,.66f,.45f).scale(3,148).move(4,.82f,.46f).scale(4,110).move(5,.15f,.16f).scale(5,90).move(6,.76f,.16f).scale(6,80).move(7,.44f,.16f).scale(7,103).move(ControlProfile.MAX_KEY,.93f,.60f).scale(ControlProfile.MAX_KEY,110);
        ok(!d.positioned(0)&&d.size(1)==100,"edit/cancel immutability");String encoded=cfg.encode();ControlProfile persisted=ControlProfile.decode(encoded);ok(encoded.equals(persisted.encode()),"roundtrip");
        String legacy="1"+encoded.substring(1,encoded.lastIndexOf(';'));
        ControlProfile migrated=ControlProfile.decode(legacy);
        for(int id=0;id<8;id++){near(migrated.x(id),cfg.x(id),"legacy X");near(migrated.y(id),cfg.y(id),"legacy Y");ok(migrated.size(id)==cfg.size(id),"legacy size");}
        ok(!migrated.positioned(ControlProfile.MAX_KEY)&&migrated.size(ControlProfile.MAX_KEY)==100,"legacy gains default MAX");
        ok(ControlProfile.decode(migrated.encode()).encode().equals(migrated.encode()),"migrated profile saved in v2");
        for(String bad:new String[]{null,"","2",encoded.replace("0.21","NaN"),encoded.replace("0.21","Infinity"),encoded.replace(",135;",",1024;"),encoded+";"})ok(ControlProfile.decode(bad)==d,"corrupt preferences");
        ok(d.scale(2,1).size(2)==65&&d.scale(2,1000).size(2)==160&&d.move(2,-2,3).x(2)==0&&d.move(2,-2,3).y(2)==1,"bounds");
        int[][] sizes={{2392,1080},{2400,1080},{1920,1080},{1440,1080},{800,360},{640,360},{1600,720},{1280,800}};
        for(int[] s:sizes)for(int[] in:new int[][]{{0,0,0,0},{45,7,0,9},{0,0,45,12}})for(boolean arcade:new boolean[]{false,true})for(boolean full:new boolean[]{false,true}){
            layouts++;PadLayout old=pad(s[0],s[1],in,arcade,full,d),p=pad(s[0],s[1],in,arcade,full,persisted);
            for(int id=0;id<ControlProfile.COUNT;id++){
                near(p.cx[id],p.safeX+cfg.x(id)*p.safeWidth,"normalized X");near(p.cy[id],p.safeY+cfg.y(id)*p.safeHeight,"normalized Y");near(p.rx[id]/old.rx[id],cfg.size(id)/100f,"own size");
                ok(!p.obstructed(id),"reachable in resized layout "+id);if(p.visible(id))ok(p.pickSlot(p.cx[id],p.cy[id])==id,"editor hit equals drawing");
                ok(p.cx[id]-p.rx[id]>=p.safeX-.001&&p.cx[id]+p.rx[id]<=p.safeX+p.safeWidth+.001&&p.cy[id]-p.ry[id]>=p.safeY-.001&&p.cy[id]+p.ry[id]<=p.safeY+p.safeHeight+.001,"in safe bounds");
            }
            touch(p);ok(p.insertsCoin(p.cx[7],p.cy[7])==arcade,"MVS coin only");ok(p.fireAt(old.bx[0],old.by[0])!=PadLayout.A,"old A hitbox removed");
            PadLayout clash=pad(s[0],s[1],in,arcade,full,cfg.move(1,cfg.x(3),cfg.y(3)));ok(clash.obstructed(1),"reject overlapping centres");
            PadLayout corner=pad(s[0],s[1],in,arcade,full,d.move(4,1,0).scale(4,160));ok(corner.cx[4]+corner.rx[4]<=corner.gearLeft+.001||corner.cy[4]-corner.ry[4]>=corner.gearBottom-.001,"reserve settings gear");
        }
    }
    public static void main(String[] args)throws Exception{guide(args[0]);layouts();System.out.println("GUIDE & LAYOUT PASS: 51 fighters/forms + common controls, "+entries+" entries, "+layouts+" custom layouts, "+checks+" assertions");}
}

