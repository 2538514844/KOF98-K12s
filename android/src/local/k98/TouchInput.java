package local.k98;

import java.util.Arrays;
import static local.k98.PadLayout.*;

/** One joystick owner per gesture, tracked by pointer ID rather than pointer index. */
final class TouchInput {
    static final int DOWN_EVENT=0, UP_EVENT=1, MOVE_EVENT=2, CANCEL_EVENT=3;
    static final int POINTER_DOWN=5, POINTER_UP=6;
    private static final int IGNORE=1, STICK=2, FIRE=3, GAME_START=4, INSERT_COIN=5;
    private static final int[] SECTORS={RIGHT,RIGHT|DOWN,DOWN,DOWN|LEFT,LEFT,LEFT|UP,UP,UP|RIGHT};
    private static final double STEP=Math.PI/4, HOLD_ANGLE=Math.PI/8+Math.toRadians(3);
    private final int[] roles=new int[32],buttonsById=new int[32];
    private PadLayout layout;
    private int owner=-1,sector=-1,direction,buttons,pendingPresses;
    private float thumbX,thumbY;

    static final class Snapshot {
        final int keys;
        final float x,y;
        Snapshot(int keys,float x,float y){this.keys=keys;this.x=x;this.y=y;}
    }
    synchronized void configure(PadLayout next){reset();layout=next;}
    synchronized void reset() {clearContacts();pendingPresses=0;}
    private void clearContacts() {
        Arrays.fill(roles,0);Arrays.fill(buttonsById,0);
        owner=sector=-1;direction=buttons=0;thumbX=thumbY=0;
    }
    synchronized Snapshot poll() {
        Snapshot s=new Snapshot(direction|buttons|pendingPresses,thumbX,thumbY);
        pendingPresses=0;
        return s;
    }
    synchronized int held(){return direction|buttons;}

    /** Processes the whole event atomically so a frame cannot see half an update. */
    synchronized void event(int action,int changedId,int count,int[] ids,float[] xs,float[] ys) {
        if(action==CANCEL_EVENT){reset();return;}
        if(layout==null)return;
        // A second gesture may begin before the emulation thread consumes a tap.
        // Keep pending presses until poll(); only CANCEL/lifecycle resets discard them.
        if(action==DOWN_EVENT)clearContacts();
        boolean adding=action==DOWN_EVENT||action==POINTER_DOWN;
        boolean removing=action==UP_EVENT||action==POINTER_UP;
        for(int i=0;i<count;i++) {
            int id=ids[i];if(id<0||id>=roles.length)continue;
            if(adding&&id==changedId)begin(id,xs[i],ys[i]);
            if(removing&&id==changedId)end(id);
            else move(id,xs[i],ys[i]);
        }
        int next=0;
        for(int value:buttonsById)next|=value;
        pendingPresses|=next&~buttons;
        buttons=next;
    }
    private void begin(int id,float x,float y) {
        // Assign roles only on DOWN. Crossing another control must not change roles.
        if(layout.startsStick(x,y)) {
            if(owner==-1){owner=id;sector=-1;roles[id]=STICK;}
            else roles[id]=IGNORE;
        }else if(layout.insertsCoin(x,y))roles[id]=INSERT_COIN;
        else if(layout.startsGame(x,y))roles[id]=GAME_START;
        else if(layout.fireAt(x,y)!=0)roles[id]=FIRE;
        else roles[id]=IGNORE;
    }
    private void end(int id) {
        roles[id]=buttonsById[id]=0;
        if(owner==id){owner=sector=-1;direction=0;thumbX=thumbY=0;}
    }
    private void move(int id,float x,float y) {
        if(roles[id]==STICK&&owner==id)stick(x,y);
        else if(roles[id]==FIRE)buttonsById[id]=layout.fireAt(x,y);
        else if(roles[id]==GAME_START)buttonsById[id]=layout.startsGame(x,y)?START:0;
        else if(roles[id]==INSERT_COIN)buttonsById[id]=layout.insertsCoin(x,y)?COIN:0;
    }
    private void stick(float x,float y) {
        float vx=(x-layout.dx)/layout.dr,vy=(y-layout.dy)/layout.dr;
        double length=Math.hypot(vx,vy);
        // Only the inner dead zone releases the stick. Its outer circle is visual.
        if(length<(sector<0?.16:.11)) {
            sector=-1;direction=0;thumbX=thumbY=0;return;
        }
        double angle=Math.atan2(vy,vx);
        if(sector>=0) {
            double delta=Math.IEEEremainder(angle-sector*STEP,2*Math.PI);
            if(Math.abs(delta)>HOLD_ANGLE)sector=((int)Math.round(angle/STEP))&7;
        }else sector=((int)Math.round(angle/STEP))&7;
        direction=SECTORS[sector];
        double clamp=Math.max(1,length);thumbX=(float)(vx/clamp);thumbY=(float)(vy/clamp);
    }
}
