package local.k98;

import java.util.Random;
import static local.k98.PadLayout.*;
import static local.k98.TouchInput.*;

/** Replays real pointer lifecycles through the same input class packaged in the APK. */
public final class TouchReplayTest {
    private static int cases,failures,checks;
    private static final int[][] SIZES={{2392,1080},{2340,1080},{1920,1080},{1280,720}};
    private static final int[] EIGHT={RIGHT,RIGHT|DOWN,DOWN,DOWN|LEFT,LEFT,LEFT|UP,UP,UP|RIGHT};
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    private static void eq(int got,int want,String message){check(got==want,message+" got="+got+" expected="+want);}
    private static void test(String label,Runnable body){
        cases++;try{body.run();System.out.println("PASS "+label);}
        catch(Throwable error){failures++;System.out.println("FAIL "+label+": "+error);}
    }
    private static final class Rig {
        final PadLayout l; final TouchInput t=new TouchInput();long automaticTime;
        Rig(int w,int h){l=new PadLayout(w,h);t.configure(l);}
        void event(int action,int changed,float[]... contacts){
            automaticTime+=40;at(automaticTime,action,changed,contacts);
        }
        void at(long time,int action,int changed,float[]... contacts){
            int n=contacts.length;int[] ids=new int[n];float[] xs=new float[n],ys=new float[n];
            for(int i=0;i<n;i++){ids[i]=(int)contacts[i][0];xs[i]=contacts[i][1];ys[i]=contacts[i][2];}
            t.event(action,changed,n,ids,xs,ys,time);
        }
        float[] stick(int id,double angle,double radius){
            double r=Math.toRadians(angle);
            return new float[]{id,l.dx+(float)(Math.cos(r)*radius*l.dr),l.dy+(float)(Math.sin(r)*radius*l.dr)};
        }
        float[] stickOffset(int id,float x,float y){
            return new float[]{id,l.dx+x*l.dr,l.dy+y*l.dr};
        }
        float[] button(int id,int i){return new float[]{id,l.bx[i],l.by[i]};}
        float[] start(int id){return new float[]{id,l.startX,(l.menuTop+l.menuBottom)/2};}
        int poll(){return t.poll().keys;}
    }
    private static void replaySize(final int w,final int h){
        final String suffix=" @"+w+"x"+h;
        test("outer overshoot retains direction; legacy reproduces drop"+suffix,()->{
            Rig r=new Rig(w,h);LegacyControls old=new LegacyControls(w,h);
            r.event(DOWN_EVENT,7,r.stick(7,0,.7));eq(r.poll(),RIGHT,"initial");
            for(double radius:new double[]{1.22,1.24,1.8,3}){
                float[] p=r.stick(7,0,radius);r.event(MOVE_EVENT,7,p);eq(r.poll(),RIGHT,"overshoot");
                if(radius>1.23)eq(old.hit(p[1],p[2]),0,"legacy cutoff reproduction");
            }
            r.event(UP_EVENT,7,r.stick(7,0,3));eq(r.poll(),0,"release outside");
        });
        test("eight directions and both quarter-circle motions"+suffix,()->{
            Rig r=new Rig(w,h);
            for(int i=0;i<8;i++){
                r.event(DOWN_EVENT,4,r.stick(4,45*i,.8));eq(r.poll(),EIGHT[i],"sector "+i);
                if((i&1)!=0)eq(r.poll(),EIGHT[i],"second diagonal sample "+i);
                r.event(UP_EVENT,4,r.stick(4,45*i,.8));eq(r.poll(),0,"release");
            }
            int[][] paths={{2,1,0},{0,1,2},{2,3,4},{4,3,2}};
            for(int[] path:paths){
                r.event(DOWN_EVENT,4,r.stick(4,45*path[0],.8));
                for(int i:path){r.event(MOVE_EVENT,4,r.stick(4,45*i,.8));eq(r.poll(),EIGHT[i],"motion");if((i&1)!=0)eq(r.poll(),EIGHT[i],"motion diagonal second sample");}
                r.event(UP_EVENT,4,r.stick(4,0,0));eq(r.poll(),0,"motion release");
            }
        });
        test("diagonal boundary hysteresis"+suffix,()->{
            Rig r=new Rig(w,h);LegacyControls old=new LegacyControls(w,h);
            r.event(DOWN_EVENT,4,r.stick(4,0,.7));
            for(int i=0;i<40;i++){r.event(MOVE_EVENT,4,r.stick(4,i%2==0?22:23,.7));eq(r.poll(),RIGHT,"boundary hold");}
            r.event(MOVE_EVENT,4,r.stick(4,27,.7));eq(r.poll(),RIGHT|DOWN,"intentional diagonal");
            r.event(MOVE_EVENT,4,r.stick(4,22,.7));eq(r.poll(),RIGHT|DOWN,"diagonal hold");
            r.event(MOVE_EVENT,4,r.stick(4,18,.7));eq(r.poll(),RIGHT,"intentional cardinal");
            float[] p=r.stick(4,22.5,.7),q=r.stick(4,24,.7);
            check(old.hit(p[1],p[2])!=old.hit(q[1],q[2]),"legacy boundary jitter reproduction");
        });
        test("forward is accepted near the nominal diagonal boundary"+suffix,()->{
            Rig r=new Rig(w,h);
            r.event(DOWN_EVENT,7,r.stick(7,90,.8));eq(r.poll(),DOWN,"start down");
            r.event(MOVE_EVENT,7,r.stick(7,64,.8));eq(r.poll(),RIGHT|DOWN,"enter diagonal");
            eq(r.poll(),RIGHT|DOWN,"diagonal second sample before next turn");
            r.event(MOVE_EVENT,7,r.stick(7,21,.8));eq(r.poll(),RIGHT,"leave diagonal for forward");
        });
        test("dead zone starts, holds, recenters and switches"+suffix,()->{
            Rig r=new Rig(w,h);r.event(DOWN_EVENT,0,r.stick(0,0,0));eq(r.poll(),0,"center");
            r.event(MOVE_EVENT,0,r.stick(0,0,.09));eq(r.poll(),0,"entry dead zone");
            r.event(MOVE_EVENT,0,r.stick(0,0,.11));eq(r.poll(),RIGHT,"leave center");
            r.event(MOVE_EVENT,0,r.stick(0,0,.08));eq(r.poll(),RIGHT,"radial hysteresis");
            r.event(MOVE_EVENT,0,r.stick(0,0,.04));eq(r.poll(),0,"return to center");
            r.event(MOVE_EVENT,0,r.stick(0,180,.09));eq(r.poll(),0,"sub-threshold reverse remains neutral");
            r.event(MOVE_EVENT,0,r.stick(0,180,.17));eq(r.poll(),LEFT,"small intentional reverse is retained");
            r.event(MOVE_EVENT,0,r.stick(0,180,.27));eq(r.poll(),LEFT,"deliberate reverse");
        });
        test("center overshoot into down and forward diagonal with punch"+suffix,()->{
            for(int sign:new int[]{1,-1}){
                Rig r=new Rig(w,h);
                int forward=sign>0?RIGHT:LEFT;
                r.event(DOWN_EVENT,7,r.stickOffset(7,sign*.75f,0));eq(r.poll(),forward,"forward");
                r.event(MOVE_EVENT,7,r.stickOffset(7,0,0));eq(r.poll(),0,"center");
                r.event(MOVE_EVENT,7,r.stickOffset(7,-sign*.20f,0));eq(r.poll(),forward==RIGHT?LEFT:RIGHT,"shallow reverse is retained");
                r.event(MOVE_EVENT,7,r.stickOffset(7,-sign*.16f,.13f));eq(r.poll(),(forward==RIGHT?LEFT:RIGHT)|DOWN,"shallow diagonal is retained");
                r.event(MOVE_EVENT,7,r.stickOffset(7,-sign*.17f,.20f));eq(r.poll(),(forward==RIGHT?LEFT:RIGHT)|DOWN,"diagonal stays held");
                r.event(MOVE_EVENT,7,r.stickOffset(7,0,.65f));eq(r.poll(),DOWN,"straight down");

                float[] finish=r.stickOffset(7,sign*.18f,.19f),a=r.button(1,0);
                r.event(POINTER_DOWN,1,finish,a);
                r.event(POINTER_UP,1,finish,a);
                eq(r.poll(),forward|DOWN|A,"shallow finishing diagonal and punch together");
            }
        });
        test("horizontal and diagonal reversals take effect on the crossing sample"+suffix,()->{
            for(int sign:new int[]{1,-1}){
                Rig r=new Rig(w,h);
                int forward=sign>0?RIGHT:LEFT,back=sign>0?LEFT:RIGHT;
                r.event(DOWN_EVENT,7,r.stickOffset(7,sign*.75f,0));eq(r.poll(),forward,"forward");
                r.event(MOVE_EVENT,7,r.stickOffset(7,-sign*.25f,0));eq(r.poll(),back,"short reverse after center");
                r.event(MOVE_EVENT,7,r.stickOffset(7,-sign*.27f,0));eq(r.poll(),back,"horizontal reversal");
                r.event(MOVE_EVENT,7,r.stickOffset(7,-sign*.18f,0));eq(r.poll(),back,"keep accepted reversal");
                r.event(MOVE_EVENT,7,r.stickOffset(7,sign*.23f,-.20f));eq(r.poll(),forward|UP,"shallow diagonal reversal");
                r.event(MOVE_EVENT,7,r.stickOffset(7,sign*.23f,-.24f));eq(r.poll(),forward|UP,"diagonal reversal stays held");
                r.event(MOVE_EVENT,7,r.stickOffset(7,-sign*.8f,0));eq(r.poll(),back,"fast full reversal");
            }
        });
        test("right-to-left quarter-circle keeps shallow down-left and left"+suffix,()->{
            Rig r=new Rig(w,h);
            r.event(DOWN_EVENT,7,r.stick(7,0,.75));eq(r.poll(),RIGHT,"prior right input");
            r.event(MOVE_EVENT,7,r.stick(7,90,.30));
            eq(r.poll(),RIGHT|DOWN,"crossing diagonal");
            eq(r.poll(),RIGHT|DOWN,"crossing diagonal second sample");
            eq(r.poll(),DOWN,"reach down before left quarter-circle");
            r.event(MOVE_EVENT,7,r.stick(7,135,.20));eq(r.poll(),LEFT|DOWN,"shallow down-left is retained");
            eq(r.poll(),LEFT|DOWN,"shallow diagonal second sample");
            r.event(MOVE_EVENT,7,r.stick(7,180,.20));eq(r.poll(),LEFT,"shallow left is retained");
        });
        test("only the stick owner's release clears the previous side"+suffix,()->{
            Rig r=new Rig(w,h);
            float[] right=r.stick(7,0,.75),left=r.stick(7,180,.18),a=r.button(3,0);
            r.event(DOWN_EVENT,7,right);r.poll();
            r.event(POINTER_DOWN,3,right,a);r.poll();
            r.event(POINTER_UP,3,right,a);r.poll();
            r.event(MOVE_EVENT,7,left);eq(r.poll(),LEFT,"owner's shallow left direction is retained");
            r.event(POINTER_DOWN,3,left,a);eq(r.poll(),LEFT|A,"attack finger preserves stick direction");
            r.event(POINTER_UP,7,left,a);eq(r.poll(),A,"punch stays held after owner release");
            r.event(POINTER_DOWN,7,left,a);eq(r.poll(),LEFT|A,"fresh stick can start shallow to the left");
        });
        test("one owner despite competing fingers; legacy opposite directions"+suffix,()->{
            Rig r=new Rig(w,h);LegacyControls old=new LegacyControls(w,h);
            float[] a=r.stick(17,0,.75),b=r.stick(3,180,.75);
            r.event(DOWN_EVENT,17,a);r.event(POINTER_DOWN,3,b,a);eq(r.poll(),RIGHT,"second stick ignored");
            eq(old.hit(a[1],a[2])|old.hit(b[1],b[2]),LEFT|RIGHT,"legacy contradiction");
            r.event(POINTER_UP,17,b,a);eq(r.poll(),0,"owner released");
            r.event(MOVE_EVENT,3,b);eq(r.poll(),0,"remaining finger cannot take over");
            r.event(UP_EVENT,3,b);r.event(DOWN_EVENT,3,b);eq(r.poll(),LEFT,"new gesture may own");
        });
        test("pointer indices reordered and non-owner lifted"+suffix,()->{
            Rig r=new Rig(w,h);r.event(DOWN_EVENT,31,r.stick(31,180,.8));
            r.event(POINTER_DOWN,2,r.button(2,0),r.stick(31,180,.8));eq(r.poll(),LEFT|A,"two fingers");
            r.event(MOVE_EVENT,31,r.stick(31,135,1.7),r.button(2,0));eq(r.poll(),LEFT|DOWN|A,"index swap");
            eq(r.poll(),LEFT|DOWN|A,"second frame during held diagonal");
            r.event(POINTER_UP,2,r.button(2,0),r.stick(31,180,1.7));eq(r.poll(),LEFT,"non-owner up");
            r.event(UP_EVENT,31,r.stick(31,180,1.7));eq(r.poll(),0,"all released");
        });
        test("captured stick cannot become attack; legacy reproduces crossover"+suffix,()->{
            Rig r=new Rig(w,h);LegacyControls old=new LegacyControls(w,h);
            r.event(DOWN_EVENT,1,r.stick(1,0,.7));r.poll();
            float[] onA=r.button(1,0);r.event(MOVE_EVENT,1,onA);
            eq(r.poll()&(A|B|C|D|START),0,"stick must never become action");
            check((r.t.held()&DIRECTIONS)!=0,"overshot stick remains active");
            eq(old.hit(onA[1],onA[2]),A,"legacy changes control role");
        });
        test("attack and empty-space fingers cannot become joystick"+suffix,()->{
            Rig r=new Rig(w,h);
            r.event(DOWN_EVENT,5,r.button(5,0));eq(r.poll(),A,"attack down");
            r.event(MOVE_EVENT,5,r.stick(5,180,.7));eq(r.poll(),0,"attack cannot steer");
            r.event(UP_EVENT,5,r.stick(5,180,.7));r.poll();
            r.event(DOWN_EVENT,5,new float[]{5,w*.5f,h*.5f});
            r.event(MOVE_EVENT,5,r.stick(5,0,.7));eq(r.poll(),0,"empty down ignored");
        });
        test("four attacks with joystick; combinations and releases"+suffix,()->{
            Rig r=new Rig(w,h);float[] s=r.stick(9,135,.8),a=r.button(1,0),b=r.button(2,1),c=r.button(3,2),d=r.button(4,3);
            r.event(DOWN_EVENT,9,s);r.poll();
            r.event(POINTER_DOWN,1,s,a);eq(r.poll(),LEFT|DOWN|A,"A");
            r.event(POINTER_DOWN,2,s,a,b);eq(r.poll(),LEFT|DOWN|A|B,"A+B");
            r.event(POINTER_DOWN,3,s,a,b,c);r.poll();
            r.event(POINTER_DOWN,4,s,a,b,c,d);eq(r.poll(),LEFT|DOWN|A|B|C|D,"four buttons");
            r.event(POINTER_UP,2,d,b,c,a,s);eq(r.poll(),LEFT|DOWN|A|C|D,"only B released");
        });
        test("MAX sends ABC while retaining other fingers and releases independently"+suffix,()->{
            Rig r=new Rig(w,h);int max=ControlProfile.MAX_KEY;
            float[] m={6,r.l.cx[max],r.l.cy[max]},s=r.stick(9,180,.8),a=r.button(1,0),d=r.button(4,3);
            r.event(DOWN_EVENT,9,s);eq(r.poll(),LEFT,"left before MAX");
            r.event(POINTER_DOWN,6,s,m);eq(r.poll(),LEFT|MAX_KEYS,"MAX plus direction");
            r.event(POINTER_DOWN,1,s,m,a);eq(r.poll(),LEFT|MAX_KEYS,"physical A overlaps MAX");
            r.event(POINTER_DOWN,4,d,m,s,a);eq(r.poll(),LEFT|MAX_KEYS|D,"add D");
            r.event(POINTER_UP,6,a,s,m,d);eq(r.poll(),LEFT|A|D,"releasing MAX retains real A and D");
            r.event(CANCEL_EVENT,0);eq(r.poll(),0,"cancel all");
            r.at(100,DOWN_EVENT,6,m);r.at(104,UP_EVENT,6,m);
            eq(r.poll(),MAX_KEYS,"sub-frame MAX tap");eq(r.poll(),0,"no stuck macro");
        });
        test("MAX pointer role and migrated layouts avoid accidental attacks"+suffix,()->{
            Rig r=new Rig(w,h);int id=ControlProfile.MAX_KEY;float[] max={6,r.l.cx[id],r.l.cy[id]},a=r.button(6,0);
            r.event(DOWN_EVENT,6,max);eq(r.poll(),MAX_KEYS,"MAX down");
            r.event(MOVE_EVENT,6,a);eq(r.poll(),0,"MAX cannot slide into a single attack");
            r.event(MOVE_EVENT,6,max);eq(r.poll(),MAX_KEYS,"return to MAX");
            r.t.reset();r.event(DOWN_EVENT,6,a);eq(r.poll(),A,"A down");
            r.event(MOVE_EVENT,6,max);eq(r.poll(),0,"A cannot slide into MAX");r.t.reset();
            ControlProfile cfg=ControlProfile.DEFAULT.move(1,r.l.cx[id]/w,r.l.cy[id]/h);
            PadLayout p=new PadLayout(w,h,false,new DisplayOptions(false,false,50,cfg),0,0,0,0);
            check(!p.obstructed(id),"auto MAX avoids pre-existing custom A");
            check(Math.abs(p.bx[0]-r.l.cx[id])<.01f&&Math.abs(p.by[0]-r.l.cy[id])<.01f,"existing A position preserved");
            r.t.configure(p);r.at(120,DOWN_EVENT,6,new float[]{6,p.cx[id],p.cy[id]});eq(r.poll(),MAX_KEYS,"relocated MAX works");
        });
        test("one thumb can press adjacent button rims together"+suffix,()->{
            for(int[] pair:new int[][]{{0,1},{2,3},{0,2},{1,3}}){
                Rig r=new Rig(w,h);int a=pair[0],b=pair[1],chord=BITS[a]|BITS[b];
                float[] rim={3,(r.l.bx[a]+r.l.bx[b])*.5f,(r.l.by[a]+r.l.by[b])*.5f};
                r.at(100,DOWN_EVENT,3,rim);
                eq(r.t.held(),chord,"both rims held");eq(r.poll(),chord,"same-frame chord");
                r.at(110,MOVE_EVENT,3,r.button(3,a));eq(r.poll(),BITS[a],"only centre key remains");
                r.at(120,UP_EVENT,3,r.button(3,a));eq(r.poll(),0,"no stuck chord");
                r.at(130,DOWN_EVENT,3,rim);r.at(134,UP_EVENT,3,rim);
                eq(r.poll(),chord,"short rim tap preserved");eq(r.poll(),0,"short chord released");
            }
        });
        test("all multi-finger chords survive one frame and independent releases"+suffix,()->{
            for(int subset=1;subset<16;subset++){
                Rig r=new Rig(w,h);java.util.List<float[]> contacts=new java.util.ArrayList<>();
                contacts.add(r.stick(31,0,.8));r.at(100,DOWN_EVENT,31,contacts.toArray(new float[0][]));r.poll();
                int chord=0;
                for(int key=0;key<4;key++)if((subset&(1<<key))!=0){
                    contacts.add(r.button(key+2,key));chord|=BITS[key];
                    java.util.Collections.rotate(contacts,1);
                    r.at(101+key,POINTER_DOWN,key+2,contacts.toArray(new float[0][]));
                }
                eq(r.poll(),RIGHT|chord,"combined keys "+subset);
                for(int key=0;key<4;key++)if((subset&(1<<key))!=0){
                    r.at(110+key,POINTER_UP,key+2,contacts.toArray(new float[0][]));
                    final int id=key+2;contacts.removeIf(p->p[0]==id);chord&=~BITS[key];
                    eq(r.poll(),RIGHT|chord,"release one key "+key+" subset "+subset);
                }
                r.at(120,UP_EVENT,31,contacts.toArray(new float[0][]));eq(r.poll(),0,"all released");
            }
        });
        test("thumb chords tolerate offset instead of requiring a precise midpoint"+suffix,()->{
            Rig r=new Rig(w,h);
            for(int[] pair:new int[][]{{0,1},{2,3},{0,2},{1,3}}){
                int a=pair[0],b=pair[1];float vx=r.l.bx[b]-r.l.bx[a],vy=r.l.by[b]-r.l.by[a];
                float length=(float)Math.hypot(vx,vy),ux=vx/length,uy=vy/length,radius=r.l.br;
                float mx=(r.l.bx[a]+r.l.bx[b])*.5f,my=(r.l.by[a]+r.l.by[b])*.5f;
                for(int along=-5;along<=5;along++)for(int across=-8;across<=8;across++){
                    float x=mx+radius*.05f*(along*ux-across*uy);
                    float y=my+radius*.05f*(along*uy+across*ux);
                    float[] finger={3,x,y};r.t.reset();r.at(100,DOWN_EVENT,3,finger);
                    eq(r.poll(),BITS[a]|BITS[b],"offset pair "+a+"/"+b+" at "+along+","+across);
                    r.at(110,UP_EVENT,3,finger);eq(r.poll(),0,"offset releases");
                }
            }
        });
        test("broad single-key inner areas do not accidentally become chords"+suffix,()->{
            Rig r=new Rig(w,h);
            for(int key=0;key<4;key++)for(int angle=0;angle<360;angle+=10){
                double a=Math.toRadians(angle);
                float x=r.l.bx[key]+(float)Math.cos(a)*r.l.br*.70f;
                float y=r.l.by[key]+(float)Math.sin(a)*r.l.br*.70f;
                eq(r.l.fireAt(x,y),BITS[key],"single button "+key+" at angle "+angle);
            }
            ControlProfile p=ControlProfile.DEFAULT.move(1,.55f,.7f).move(2,.90f,.7f);
            PadLayout wide=new PadLayout(w,h,false,new DisplayOptions(false,false,50,p),0,0,0,0);
            eq(wide.fireAt((wide.bx[0]+wide.bx[1])*.5f,(wide.by[0]+wide.by[1])*.5f),0,"far buttons have no phantom bridge");
        });
        test("single-key centres remain independent in close custom layouts"+suffix,()->{
            Rig r=new Rig(w,h);float radius=r.l.br;
            ControlProfile p=ControlProfile.DEFAULT.move(1,.70f,.70f).scale(1,65)
                    .move(2,.70f+radius*1.68f/w,.70f).scale(2,160);
            PadLayout l=new PadLayout(w,h,false,new DisplayOptions(false,false,50,p),0,0,0,0);
            eq(l.fireAt(l.bx[0],l.by[0]),A,"small A centre");
            eq(l.fireAt(l.bx[1],l.by[1]),B,"large B centre");
            eq(r.l.fireAt((r.l.bx[0]+r.l.bx[3])*.5f,(r.l.by[0]+r.l.by[3])*.5f),0,"middle of four buttons is not a chord");
        });
        test("short attacks latch once while stick is held"+suffix,()->{
            Rig r=new Rig(w,h);float[] s=r.stick(8,0,.7),a=r.button(1,0);
            r.event(DOWN_EVENT,8,s);r.poll();
            r.event(POINTER_DOWN,1,s,a);r.event(POINTER_UP,1,s,a);
            eq(r.poll(),RIGHT|A,"sub-frame button preserved");eq(r.poll(),RIGHT,"no stuck attack");
        });
        test("pending short attack survives next gesture"+suffix,()->{
            Rig r=new Rig(w,h);float[] a=r.button(1,0);
            r.event(DOWN_EVENT,1,a);r.event(UP_EVENT,1,a);
            r.event(DOWN_EVENT,8,r.stick(8,180,.7));
            eq(r.poll(),A,"tap keeps its neutral direction");eq(r.poll(),LEFT,"new gesture after tap");
        });
        test("slow double sweep both ways, final down and A in the same sample"+suffix,()->{
            for(int dwell:new int[]{2,4,8,16,24})for(boolean left:new boolean[]{false,true}){
                Rig r=new Rig(w,h);
                int[] path=left?new int[]{4,3,2,3,4,3,2}:new int[]{0,1,2,1,0,1,2};
                for(int i=0;i<path.length;i++){
                    long start=(long)i*dwell*17;
                    float[] s=r.stick(7,45*path[i],.8);
                    if(i==path.length-1)r.at(start,POINTER_DOWN,2,s,r.button(2,0));
                    else r.at(start,i==0?DOWN_EVENT:MOVE_EVENT,7,s);
                    for(int f=0;f<dwell;f++)eq(r.t.poll(start+17L*f).keys,
                            EIGHT[path[i]]|(i==path.length-1?A:0),"dwell="+dwell+" step="+i+" frame="+f);
                }
            }
        });
        test("overdue frames keep turn order across port reads"+suffix,()->{
            Rig r=new Rig(w,h);
            r.at(5,DOWN_EVENT,7,r.stick(7,0,.8));
            eq(r.t.poll(16).keys,RIGHT,"first frame");
            r.at(22,MOVE_EVENT,7,r.stick(7,45,.8));
            r.at(40,MOVE_EVENT,7,r.stick(7,90,.8));
            eq(r.t.poll(33).keys,RIGHT|DOWN,"diagonal at outstanding frame");
            eq(r.t.poll(50).keys,RIGHT|DOWN,"fast diagonal gets a second sample");
            eq(r.t.poll(67).keys,DOWN,"last direction");
            r.at(52,MOVE_EVENT,7,r.stick(7,45,.8));
            r.at(54,MOVE_EVENT,7,r.stick(7,0,.8));
            eq(r.t.poll(84).keys,RIGHT|DOWN,"first turn inside one frame");
            eq(r.t.poll(101).keys,RIGHT|DOWN,"fast diagonal gets a second sample");
            eq(r.t.poll(118).keys,RIGHT,"second turn on the next port read");
            eq(r.t.poll(135).keys,RIGHT,"no direction backlog");
        });
        test("excessive turn backlog stays bounded and keeps newest directions"+suffix,()->{
            Rig r=new Rig(w,h);int total=25,capacity=16,last=0;
            for(int i=0;i<total;i++){
                float[] p=r.stick(7,45*(i%8),.8);
                r.at(i+1,i==0?DOWN_EVENT:MOVE_EVENT,7,p);
            }
            for(int i=0;i<capacity;i++){
                last=r.t.poll().keys;
                check(last!=0,"queued turn "+i+" is retained");
            }
            eq(last,EIGHT[(total-1)%8],"backlog ends at the newest direction");
        });
        test("rapid 236 keeps each direction and the finishing punch"+suffix,()->{
            Rig r=new Rig(w,h);
            float[] down=r.stick(7,90,.8),diagonal=r.stick(7,45,.8),right=r.stick(7,0,.8),a=r.button(1,0);
            r.at(1,DOWN_EVENT,7,down);eq(r.t.poll(17).keys,DOWN,"start down");
            r.at(18,MOVE_EVENT,7,diagonal);
            r.at(20,POINTER_DOWN,1,right,a);
            r.at(21,POINTER_UP,1,right,a);
            eq(r.t.poll(34).keys,RIGHT|DOWN,"middle diagonal survives the fast sweep");
            eq(r.t.poll(51).keys,RIGHT|DOWN,"fast diagonal survives a second frame");
            eq(r.t.poll(68).keys,RIGHT|A,"punch stays on the final direction");
            eq(r.t.poll(85).keys,RIGHT|A,"short finishing tap gets one extra sample");
            eq(r.t.poll(102).keys,RIGHT,"tap releases cleanly");
        });
        test("short diagonal after held down survives two core samples on both sides"+suffix,()->{
            for(boolean rightward:new boolean[]{false,true})for(int heldMs:new int[]{60,120,500}){
                Rig r=new Rig(w,h);int forward=rightward?RIGHT:LEFT;
                float[] down=r.stick(7,90,.8),diagonal=r.stick(7,rightward?45:135,.5),horizontal=r.stick(7,rightward?0:180,.8),a=r.button(1,0);
                r.at(0,DOWN_EVENT,7,down);
                for(int time=0;time<heldMs;time+=17)eq(r.t.poll(time).keys,DOWN,"held down");
                r.at(heldMs,MOVE_EVENT,7,diagonal);
                r.at(heldMs+11,MOVE_EVENT,7,horizontal);
                r.at(heldMs+16,POINTER_DOWN,1,horizontal,a);
                eq(r.t.poll(heldMs+17).keys,forward|DOWN,"first diagonal after long down");
                eq(r.t.poll(heldMs+34).keys,forward|DOWN,"second diagonal after long down");
                eq(r.t.poll(heldMs+51).keys,forward|A,"forward and punch follow both diagonal samples");
                r.at(heldMs+60,CANCEL_EVENT,7,horizontal,a);
                eq(r.t.poll(heldMs+68).keys,0,"cancel immediately clears the queue");
            }
        });
        test("long-held diagonal adds no delay when leaving"+suffix,()->{
            Rig r=new Rig(w,h);float[] down=r.stick(7,90,.8),diagonal=r.stick(7,135,.5),left=r.stick(7,180,.8);
            r.at(0,DOWN_EVENT,7,down);eq(r.t.poll(0).keys,DOWN,"initial down");
            r.at(80,MOVE_EVENT,7,diagonal);
            for(int time=85;time<=170;time+=17)eq(r.t.poll(time).keys,LEFT|DOWN,"held diagonal");
            r.at(175,MOVE_EVENT,7,left);eq(r.t.poll(187).keys,LEFT,"already sampled diagonal has no tail");
        });
        test("sparse quarter-circle swipes synthesize a missed diagonal"+suffix,()->{
            for(boolean rightward:new boolean[]{true,false}){
                Rig r=new Rig(w,h);int forward=rightward?RIGHT:LEFT;
                float[] down=r.stick(7,90,.8),horizontal=r.stick(7,rightward?20:160,.8),a=r.button(1,0);
                r.at(1,DOWN_EVENT,7,down);eq(r.t.poll(17).keys,DOWN,"start down");
                // A short swipe may deliver only the two endpoints, skipping the diagonal sample.
                r.at(18,MOVE_EVENT,7,horizontal);
                eq(r.t.poll(34).keys,forward|DOWN,"inferred diagonal crossing");
                r.at(20,POINTER_DOWN,1,horizontal,a);r.at(21,POINTER_UP,1,horizontal,a);
                eq(r.t.poll(51).keys,forward|DOWN,"fast inferred diagonal gets a second sample");
                eq(r.t.poll(68).keys,forward|A,"final forward and punch");
                eq(r.t.poll(85).keys,forward|A,"finishing punch survives one frame");
                eq(r.t.poll(102).keys,forward,"punch releases cleanly");
            }
        });
        test("rapid 214214 keeps every left quarter-circle turn and final punch"+suffix,()->{
            for(double radius:new double[]{.12,.8}){
                Rig r=new Rig(w,h);float[] down=r.stick(7,90,radius),diagonal=r.stick(7,135,radius),
                        left=r.stick(7,180,radius),a=r.button(1,0);
                r.at(1,DOWN_EVENT,7,down);eq(r.t.poll(17).keys,DOWN,"first down radius="+radius);
                r.at(18,MOVE_EVENT,7,diagonal);
                r.at(20,MOVE_EVENT,7,left);
                r.at(21,MOVE_EVENT,7,diagonal);
                r.at(22,MOVE_EVENT,7,down);
                r.at(24,MOVE_EVENT,7,diagonal);
                r.at(26,POINTER_DOWN,1,left,a);
                r.at(27,POINTER_UP,1,left,a);
                int[] expected={DOWN|LEFT,DOWN|LEFT,LEFT,DOWN|LEFT,DOWN|LEFT,DOWN,
                        DOWN|LEFT,DOWN|LEFT,LEFT|A,LEFT|A,LEFT};
                long[] times={34,51,68,85,102,119,136,153,170,187,204};
                for(int i=0;i<expected.length;i++)eq(r.t.poll(times[i]).keys,expected[i],
                        "214214 radius="+radius+" frame="+i);
            }
        });
        test("sparse 214214 infers each skipped down-left crossing"+suffix,()->{
            Rig r=new Rig(w,h);float[] down=r.stick(7,90,.2),left=r.stick(7,180,.2),a=r.button(1,0);
            r.at(1,DOWN_EVENT,7,down);eq(r.t.poll(17).keys,DOWN,"first down");
            r.at(18,MOVE_EVENT,7,left);r.at(20,MOVE_EVENT,7,down);
            r.at(22,MOVE_EVENT,7,left);r.at(23,POINTER_DOWN,1,left,a);
            r.at(24,POINTER_UP,1,left,a);
            int[] expected={DOWN|LEFT,DOWN|LEFT,LEFT,DOWN|LEFT,DOWN|LEFT,DOWN,
                    DOWN|LEFT,DOWN|LEFT,LEFT|A,LEFT|A,LEFT};
            long[] times={34,51,68,85,102,119,136,153,170,187,204};
            for(int i=0;i<expected.length;i++)eq(r.t.poll(times[i]).keys,expected[i],"sparse 214214 frame="+i);
        });
        test("short straight quarter-circle swipes keep diagonals near center"+suffix,()->{
            for(boolean rightward:new boolean[]{true,false})for(double radius:new double[]{.12,.18}){
                Rig r=new Rig(w,h);int horizontal=rightward?RIGHT:LEFT;
                r.event(DOWN_EVENT,7,r.stickOffset(7,0,(float)radius));
                eq(r.poll(),DOWN,"start down radius="+radius);
                int[] expected={DOWN,horizontal|DOWN,horizontal,horizontal};
                for(int i=1;i<=4;i++){
                    double t=i/4.0;
                    // Model a short straight swipe that bows inward by about 2% of stick radius.
                    double inward=1-.16*4*t*(1-t);
                    r.event(MOVE_EVENT,7,r.stickOffset(7,(float)(horizontal==RIGHT?1:-1)*(float)(radius*t*inward),
                            (float)(radius*(1-t)*inward)));
                    eq(r.poll(),expected[i-1],"swipe="+(rightward?"right":"left")+" radius="+radius+" step="+i);
                    if(expected[i-1]==(horizontal|DOWN))eq(r.poll(),horizontal|DOWN,"short swipe diagonal second sample");
                }
            }
        });
        test("rapid 236236 keeps both half-circles and the final punch"+suffix,()->{
            Rig r=new Rig(w,h);
            float[] down=r.stick(7,90,.8),diagonal=r.stick(7,45,.8),right=r.stick(7,0,.8),a=r.button(1,0);
            r.at(1,DOWN_EVENT,7,down);eq(r.t.poll(17).keys,DOWN,"start first half-circle");
            r.at(18,MOVE_EVENT,7,diagonal);
            r.at(20,MOVE_EVENT,7,right);
            r.at(21,MOVE_EVENT,7,diagonal);
            r.at(22,MOVE_EVENT,7,down);
            r.at(24,MOVE_EVENT,7,diagonal);
            r.at(26,POINTER_DOWN,1,right,a);
            r.at(27,POINTER_UP,1,right,a);
            eq(r.t.poll(34).keys,RIGHT|DOWN,"first diagonal");
            eq(r.t.poll(51).keys,RIGHT|DOWN,"first diagonal gets a second sample");
            eq(r.t.poll(68).keys,RIGHT,"first forward");
            eq(r.t.poll(85).keys,RIGHT|DOWN,"return diagonal");
            eq(r.t.poll(102).keys,RIGHT|DOWN,"return diagonal gets a second sample");
            eq(r.t.poll(119).keys,DOWN,"second half-circle starts at down");
            eq(r.t.poll(136).keys,RIGHT|DOWN,"second diagonal");
            eq(r.t.poll(153).keys,RIGHT|DOWN,"second diagonal gets a second sample");
            eq(r.t.poll(170).keys,RIGHT|A,"final forward and punch");
            eq(r.t.poll(187).keys,RIGHT|A,"finishing punch survives one more sample");
            eq(r.t.poll(204).keys,RIGHT,"punch releases cleanly");
        });
        test("held-forward 623 sweep keeps down and return diagonal"+suffix,()->{
            Rig r=new Rig(w,h);
            float[] right=r.stick(7,0,.8),diagonal=r.stick(7,45,.8),down=r.stick(7,90,.8),a=r.button(1,0);
            r.at(1,DOWN_EVENT,7,right);eq(r.t.poll(17).keys,RIGHT,"hold forward");
            r.at(18,MOVE_EVENT,7,diagonal);
            r.at(20,MOVE_EVENT,7,down);
            r.at(22,POINTER_DOWN,1,diagonal,a);
            r.at(23,POINTER_UP,1,diagonal,a);
            eq(r.t.poll(34).keys,RIGHT|DOWN,"outbound diagonal");
            eq(r.t.poll(51).keys,RIGHT|DOWN,"fast outbound diagonal gets a second sample");
            eq(r.t.poll(68).keys,DOWN,"pure down survives");
            eq(r.t.poll(85).keys,RIGHT|DOWN|A,"return diagonal and punch");
            eq(r.t.poll(102).keys,RIGHT|DOWN|A,"short finishing tap gets one extra sample");
            eq(r.t.poll(119).keys,RIGHT|DOWN,"tap releases cleanly");
        });
        test("punch uses its own direction at the next poll, including during catch-up"+suffix,()->{
            Rig r=new Rig(w,h);float[] down=r.stick(7,90,.8),right=r.stick(7,0,.8),a=r.button(1,0);
            r.at(2,DOWN_EVENT,7,right);eq(r.t.poll(16).keys,RIGHT,"initial");
            r.at(28,MOVE_EVENT,7,r.stick(7,45,.8));
            r.at(43,POINTER_DOWN,1,down,a);
            r.at(45,POINTER_UP,1,right,a);
            eq(r.t.poll(33).keys,RIGHT|DOWN,"earlier diagonal stays ahead of the punch");
            eq(r.t.poll(50).keys,RIGHT|DOWN,"fast diagonal gets a second sample");
            eq(r.t.poll(67).keys,DOWN|A,"punch uses the direction at touch-down");
            eq(r.t.poll(84).keys,RIGHT|DOWN|A,"skipped return diagonal stays ahead of forward");
            eq(r.t.poll(101).keys,RIGHT|DOWN|A,"fast inferred diagonal gets a second sample");
            eq(r.t.poll(118).keys,RIGHT,"later direction follows the punch");
            eq(r.t.poll(135).keys,RIGHT,"no stale punch");
        });
        test("punch finishing a turn between polls"+suffix,()->{
            for(int order=0;order<4;order++){
                Rig r=new Rig(w,h);
                float[] down=r.stick(7,90,.8),diagonal=r.stick(7,45,.8),right=r.stick(7,0,.8),a=r.button(1,0);
                r.at(1,DOWN_EVENT,7,down);eq(r.t.poll(16).keys,DOWN,"start quarter-circle");
                r.at(18,MOVE_EVENT,7,diagonal);eq(r.t.poll(33).keys,RIGHT|DOWN,"cross diagonal");
                r.at(36,POINTER_DOWN,1,diagonal,a);
                if(order==2){
                    r.at(39,POINTER_UP,1,diagonal,a);
                    r.at(42,MOVE_EVENT,7,right);
                }else{
                    r.at(39,MOVE_EVENT,7,right,a);
                    if(order==1)r.at(42,POINTER_UP,1,right,a);
                    if(order==3)r.at(42,POINTER_UP,7,right,a);
                }
                eq(r.t.poll(50).keys,RIGHT|DOWN|A,"punch remains on its touch-down direction, order="+order);
                int held=order==0?RIGHT|A:order==3?A:RIGHT;
                int next=order==2?RIGHT:RIGHT|A;
                eq(r.t.poll(67).keys,next,"next queued direction, order="+order);
                eq(r.t.poll(84).keys,held,"no replay, order="+order);
            }
        });
        test("short turn-ending attack releases and yields to real controls"+suffix,()->{
            for(int next=0;next<5;next++){
                Rig r=new Rig(w,h);
                float[] diagonal=r.stick(7,45,.8),right=r.stick(7,0,.8),left=r.stick(7,180,.8);
                float[] a=r.button(1,0),b=r.button(2,1);
                r.event(DOWN_EVENT,7,diagonal);r.poll();
                eq(r.poll(),RIGHT|DOWN,"settled diagonal before testing attack release");
                r.event(POINTER_DOWN,1,right,a);r.event(POINTER_UP,1,right,a);
                eq(r.poll(),RIGHT|A,"turn and punch on first poll");
                if(next==0){
                    r.event(UP_EVENT,7,right);
                    eq(r.poll(),A,"stick releases immediately");eq(r.poll(),0,"tap released");
                }else if(next==1){
                    r.event(POINTER_DOWN,2,right,b);
                    eq(r.poll(),RIGHT|B,"new button wins");eq(r.poll(),RIGHT|B,"no false A+B");
                }else if(next==2){
                    r.event(MOVE_EVENT,7,left);r.event(MOVE_EVENT,7,right);
                    eq(r.poll(),LEFT,"first intervening turn ends the tap");
                    eq(r.poll(),RIGHT,"next turn remains ordered");
                    eq(r.poll(),RIGHT,"no late punch");
                }else if(next==3){
                    eq(r.t.poll(100,B).keys,RIGHT,"physical button wins");eq(r.poll(),RIGHT,"no late A");
                }else{
                    r.t.reset();eq(r.poll(),0,"pause drops the extra sample");
                }
            }
        });
        test("released chord member does not follow a later turn"+suffix,()->{
            Rig r=new Rig(w,h);
            float[] diagonal=r.stick(7,45,.8),right=r.stick(7,0,.8),a=r.button(1,0),b=r.button(2,1);
            r.event(DOWN_EVENT,7,diagonal);r.poll();
            r.event(POINTER_DOWN,1,diagonal,a);r.event(POINTER_DOWN,2,diagonal,a,b);
            r.event(POINTER_UP,2,diagonal,a,b);r.event(MOVE_EVENT,7,right,a);
            eq(r.poll(),RIGHT|DOWN|A|B,"chord keeps last direction shared by both buttons");
            eq(r.poll(),RIGHT|A,"only A continues");
        });
        test("separate quick taps and genuine overlapping taps"+suffix,()->{
            Rig r=new Rig(w,h);float[] a=r.button(1,0),b=r.button(2,1);
            r.event(DOWN_EVENT,1,a);r.event(UP_EVENT,1,a);
            r.event(DOWN_EVENT,2,b);r.event(UP_EVENT,2,b);
            eq(r.poll(),B,"last independent tap, no false A+B");eq(r.poll(),0,"released");
            r.event(DOWN_EVENT,1,a);r.event(POINTER_DOWN,2,b,a);
            r.event(POINTER_UP,2,a,b);r.event(UP_EVENT,1,a);
            eq(r.poll(),A|B,"true transient chord");eq(r.poll(),0,"chord released");
        });
        test("START never turns into an attack and releases"+suffix,()->{
            Rig r=new Rig(w,h);float[] p=r.start(6);
            r.event(DOWN_EVENT,6,p);eq(r.poll(),START,"start down");
            r.event(MOVE_EVENT,6,r.button(6,0));eq(r.poll(),0,"start role");
            r.event(MOVE_EVENT,6,p);eq(r.poll(),START,"start return");
            r.event(UP_EVENT,6,p);eq(r.poll(),0,"start release");
        });
        test("cancel, pause reset and resize clear held and pending keys"+suffix,()->{
            Rig r=new Rig(w,h);
            for(int mode=0;mode<3;mode++){
                r.event(DOWN_EVENT,7,r.stick(7,45,.8));
                r.event(POINTER_DOWN,2,r.stick(7,45,.8),r.button(2,0));
                if(mode==0)r.event(CANCEL_EVENT,0);
                else if(mode==1)r.t.reset();else r.t.configure(new PadLayout(w,h));
                eq(r.poll(),0,"reset "+mode);eq(r.t.held(),0,"held reset");
                r.event(MOVE_EVENT,7,r.stick(7,45,.8));eq(r.poll(),0,"old pointer not revived");
            }
        });
        test("visual knob follows and clamps; random circles never oppose"+suffix,()->{
            Rig r=new Rig(w,h);Random random=new Random(98);
            r.event(DOWN_EVENT,7,r.stick(7,0,.7));r.poll();
            for(int i=0;i<10000;i++){
                double a=random.nextDouble()*360,radius=.3+random.nextDouble()*3;
                r.event(MOVE_EVENT,7,r.stick(7,a,radius));
                TouchInput.Snapshot s=r.t.poll();double length=Math.hypot(s.x,s.y);
                check(length<=1.00001,"knob unbounded");
                check(Math.abs(length-Math.min(radius,1))<.00001,"knob not following finger");
                check((s.keys&(LEFT|RIGHT))!=(LEFT|RIGHT),"opposite horizontal");
                check((s.keys&(UP|DOWN))!=(UP|DOWN),"opposite vertical");
                check((s.keys&DIRECTIONS)!=0,"outer radius dropped direction");
            }
        });
    }
    public static void main(String[] args){
        for(int[] size:SIZES)replaySize(size[0],size[1]);
        System.out.println("RESULT cases="+cases+" assertions="+checks+" failures="+failures);
        System.out.println("Scope: production Java touch replay on host; Android event delivery and subjective device latency are not measured.");
        if(failures!=0)System.exit(1);
    }
}
