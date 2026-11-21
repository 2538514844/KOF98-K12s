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
        final PadLayout l; final TouchInput t=new TouchInput();
        Rig(int w,int h){l=new PadLayout(w,h);t.configure(l);}
        void event(int action,int changed,float[]... contacts){
            at(0,action,changed,contacts);
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
                r.event(UP_EVENT,4,r.stick(4,45*i,.8));eq(r.poll(),0,"release");
            }
            int[][] paths={{2,1,0},{0,1,2},{2,3,4},{4,3,2},{0,2,1}};
            for(int[] path:paths){
                r.event(DOWN_EVENT,4,r.stick(4,45*path[0],.8));
                for(int i:path){r.event(MOVE_EVENT,4,r.stick(4,45*i,.8));eq(r.poll(),EIGHT[i],"motion");}
                r.event(UP_EVENT,4,r.stick(4,0,0));eq(r.poll(),0,"motion release");
            }
        });
        test("diagonal boundary hysteresis"+suffix,()->{
            Rig r=new Rig(w,h);LegacyControls old=new LegacyControls(w,h);
            r.event(DOWN_EVENT,4,r.stick(4,0,.7));
            for(int i=0;i<40;i++){r.event(MOVE_EVENT,4,r.stick(4,i%2==0?22.5:24,.7));eq(r.poll(),RIGHT,"boundary hold");}
            r.event(MOVE_EVENT,4,r.stick(4,27,.7));eq(r.poll(),RIGHT|DOWN,"intentional diagonal");
            r.event(MOVE_EVENT,4,r.stick(4,22,.7));eq(r.poll(),RIGHT|DOWN,"diagonal hold");
            r.event(MOVE_EVENT,4,r.stick(4,18,.7));eq(r.poll(),RIGHT,"intentional cardinal");
            float[] p=r.stick(4,22.5,.7),q=r.stick(4,24,.7);
            check(old.hit(p[1],p[2])!=old.hit(q[1],q[2]),"legacy boundary jitter reproduction");
        });
        test("dead zone starts, holds, recenters and switches"+suffix,()->{
            Rig r=new Rig(w,h);r.event(DOWN_EVENT,0,r.stick(0,0,0));eq(r.poll(),0,"center");
            r.event(MOVE_EVENT,0,r.stick(0,0,.14));eq(r.poll(),0,"entry dead zone");
            r.event(MOVE_EVENT,0,r.stick(0,0,.17));eq(r.poll(),RIGHT,"leave center");
            r.event(MOVE_EVENT,0,r.stick(0,0,.13));eq(r.poll(),RIGHT,"radial hysteresis");
            r.event(MOVE_EVENT,0,r.stick(0,0,.1));eq(r.poll(),0,"return to center");
            r.event(MOVE_EVENT,0,r.stick(0,180,.17));eq(r.poll(),LEFT,"reverse");
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
                int[] path=left?new int[]{4,3,2,4,3,2}:new int[]{0,1,2,0,1,2};
                for(int i=0;i<path.length;i++){
                    long start=(long)i*dwell*17;
                    float[] s=r.stick(7,45*path[i],.8);
                    if(i==5)r.at(start,POINTER_DOWN,2,s,r.button(2,0));
                    else r.at(start,i==0?DOWN_EVENT:MOVE_EVENT,7,s);
                    for(int f=0;f<dwell;f++)eq(r.t.poll(start+17L*f).keys,
                            EIGHT[path[i]]|(i==5?A:0),"dwell="+dwell+" step="+i+" frame="+f);
                }
            }
        });
        test("overdue frames sample turns by timestamp; no replay into later frames"+suffix,()->{
            Rig r=new Rig(w,h);
            r.at(5,DOWN_EVENT,7,r.stick(7,0,.8));
            eq(r.t.poll(16).keys,RIGHT,"first frame");
            r.at(22,MOVE_EVENT,7,r.stick(7,45,.8));
            r.at(40,MOVE_EVENT,7,r.stick(7,90,.8));
            eq(r.t.poll(33).keys,RIGHT|DOWN,"diagonal at outstanding frame");
            eq(r.t.poll(50).keys,DOWN,"last direction");
            r.at(52,MOVE_EVENT,7,r.stick(7,45,.8));
            r.at(54,MOVE_EVENT,7,r.stick(7,0,.8));
            eq(r.t.poll(67).keys,RIGHT,"two turns inside one frame");
            eq(r.t.poll(84).keys,RIGHT,"no direction backlog");
        });
        test("punch uses its own direction at the next poll, including during catch-up"+suffix,()->{
            Rig r=new Rig(w,h);float[] down=r.stick(7,90,.8),right=r.stick(7,0,.8),a=r.button(1,0);
            r.at(2,DOWN_EVENT,7,right);eq(r.t.poll(16).keys,RIGHT,"initial");
            r.at(28,MOVE_EVENT,7,r.stick(7,45,.8));
            r.at(43,POINTER_DOWN,1,down,a);
            r.at(45,POINTER_UP,1,right,a);
            eq(r.t.poll(33).keys,DOWN|A,"press immediately, sample the direction at touch-down");
            eq(r.t.poll(50).keys,RIGHT,"released and latest direction");
            eq(r.t.poll(67).keys,RIGHT,"no stale diagonal or second punch");
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
                eq(r.t.poll(50).keys,order==2?RIGHT|DOWN|A:RIGHT|A,"first poll after punch, order="+order);
                int held=order==0?RIGHT|A:order==3?A:RIGHT;
                eq(r.t.poll(67).keys,order==1?RIGHT|A:held,"second sample, order="+order);
                eq(r.t.poll(84).keys,held,"no replay, order="+order);
            }
        });
        test("short turn-ending attack releases and yields to real controls"+suffix,()->{
            for(int next=0;next<5;next++){
                Rig r=new Rig(w,h);
                float[] diagonal=r.stick(7,45,.8),right=r.stick(7,0,.8),left=r.stick(7,180,.8);
                float[] a=r.button(1,0),b=r.button(2,1);
                r.event(DOWN_EVENT,7,diagonal);r.poll();
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
                    eq(r.poll(),RIGHT,"an intervening turn ends the tap");
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
