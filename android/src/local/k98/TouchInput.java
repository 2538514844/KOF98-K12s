package local.k98;

import java.util.Arrays;
import static local.k98.PadLayout.*;

/** One joystick owner per gesture, tracked by pointer ID rather than pointer index. */
final class TouchInput {
    static final int DOWN_EVENT=0, UP_EVENT=1, MOVE_EVENT=2, CANCEL_EVENT=3;
    static final int POINTER_DOWN=5, POINTER_UP=6;
    private static final int IGNORE=1, STICK=2, FIRE=3, GAME_START=4, INSERT_COIN=5, MAX_FIRE=6;
    private static final int[] SECTORS={RIGHT,RIGHT|DOWN,DOWN,DOWN|LEFT,LEFT,LEFT|UP,UP,UP|RIGHT};
    private static final double STEP=Math.PI/4, HOLD_ANGLE=Math.PI/8+Math.toRadians(1);
    private static final double ENGAGE_RADIUS=.10, RELEASE_RADIUS=.05;
    // The core must sample each diagonal twice, even after a long-held cardinal direction.
    // Time spent on the previous direction does not tell us how briefly the diagonal will last.
    private static final int MIN_DIAGONAL_SAMPLES=2;
    private final int[] roles=new int[32],buttonsById=new int[32];
    private PadLayout layout;
    private int owner=-1,sector=-1,direction,buttons;
    private float thumbX,thumbY;
    // Keep turn order so one port read cannot skip a fast sweep; cap catch-up at 16 turns.
    private final long[] turnTimes=new long[16];
    private final int[] turns=new int[turnTimes.length],turnButtons=new int[turnTimes.length];
    private int firstTurn,turnCount,sampledDirection;
    private Snapshot press;
    private int previousKeys,turnAttack;

    static final class Snapshot {
        final int keys;
        final float x,y;
        Snapshot(int keys,float x,float y){this.keys=keys;this.x=x;this.y=y;}
    }
    synchronized void configure(PadLayout next){reset();layout=next;}
    synchronized void reset() {
        clearContacts();
        firstTurn=turnCount=sampledDirection=0;
        previousKeys=turnAttack=0;
        press=null;
    }
    private void clearContacts() {
        Arrays.fill(roles,0);Arrays.fill(buttonsById,0);
        buttons=0;
        releaseStick();
    }
    private void releaseStick() {
        owner=sector=-1;
        direction=0;
        thumbX=thumbY=0;
    }
    Snapshot poll() {return poll(Long.MAX_VALUE);}
    Snapshot poll(long frameTimeMs) {return poll(frameTimeMs,0);}
    synchronized Snapshot poll(long frameTimeMs,int hardwareKeys) {
        int secondSample=turnAttack;
        turnAttack=0;
        Snapshot result;
        if(press!=null) {
            result=press;
            press=null;
            int dir=result.keys&DIRECTIONS,previousDir=previousKeys&DIRECTIONS;
            int key=result.keys&~DIRECTIONS;
            // KOF98 can see the edge but miss the cancel if a finishing tap lasts one frame.
            // Keep one more attack sample only for a single button arriving with a real turn.
            if(hardwareKeys==0 && dir!=0 && previousDir!=0 && dir!=previousDir
                    && (previousKeys&key)==0 && (key&(A|B|C|D))!=0 && Integer.bitCount(key)==1)
                turnAttack=result.keys;
        } else if(turnCount>0 && turnTimes[firstTurn]<=frameTimeMs) {
            int slot=firstTurn;
            sampledDirection=turns[slot];
            int keys=sampledDirection|turnButtons[slot];
            firstTurn=(firstTurn+1)%turns.length;
            turnCount--;
            int dir=keys&DIRECTIONS,previousDir=previousKeys&DIRECTIONS;
            int key=keys&~DIRECTIONS;
            // Keep a one-frame finishing tap visible after its queued direction turn.
            if(hardwareKeys==0 && dir!=0 && previousDir!=0 && dir!=previousDir
                    && (previousKeys&key)==0 && (key&(A|B|C|D))!=0 && Integer.bitCount(key)==1)
                turnAttack=keys;
            if(secondSample!=0 && hardwareKeys==0 && buttons==0
                    && (sampledDirection==0 || sampledDirection==(secondSample&DIRECTIONS)))
                keys|=secondSample&~DIRECTIONS;
            result=new Snapshot(keys,thumbX,thumbY);
        } else {
            int keys=sampledDirection|buttons;
            if(secondSample!=0 && hardwareKeys==0 && buttons==0
                    && (sampledDirection==0 || sampledDirection==(secondSample&DIRECTIONS)))
                keys|=secondSample&~DIRECTIONS;
            result=new Snapshot(keys,thumbX,thumbY);
        }
        previousKeys=hardwareKeys==0?result.keys:0;
        return result;
    }
    synchronized int held(){return direction|buttons;}

    // Observation only: never poll, drain, or change input when collecting diagnostics.
    synchronized String debugState(){
        StringBuilder s=new StringBuilder("{\"owner\":").append(owner).append(",\"sector\":").append(sector)
                .append(",\"heldMask\":").append(direction|buttons).append(",\"x\":").append(thumbX)
                .append(",\"y\":").append(thumbY).append(",\"sampledDirection\":").append(sampledDirection)
                .append(",\"pressMask\":").append(press==null?0:press.keys).append(",\"turnAttack\":").append(turnAttack)
                .append(",\"queueDepth\":").append(turnCount).append(",\"queue\":[");
        for(int i=0;i<turnCount;i++){
            int slot=(firstTurn+i)%turns.length;if(i>0)s.append(',');
            s.append("{\"mask\":").append(turns[slot]|turnButtons[slot]).append(",\"timeMs\":").append(turnTimes[slot]).append('}');
        }
        return s.append("]}").toString();
    }
    static String debugConfig(){
        return "{\"engageRadius\":"+ENGAGE_RADIUS+",\"releaseRadius\":"+RELEASE_RADIUS
                +",\"holdAngleDegrees\":"+Math.toDegrees(HOLD_ANGLE)+",\"minDiagonalSamples\":"+MIN_DIAGONAL_SAMPLES
                +",\"queueCapacity\":16}";
    }

    void event(int action,int changedId,int count,int[] ids,float[] xs,float[] ys) {
        event(action,changedId,count,ids,xs,ys,System.nanoTime()/1_000_000L);
    }
    synchronized void event(int action,int changedId,int count,int[] ids,float[] xs,float[] ys,long timeMs) {
        if(action==CANCEL_EVENT){reset();return;}
        if(layout==null)return;
        int previousDirection=direction;
        int previousSector=action==DOWN_EVENT?-1:sector;
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
        if(direction!=0 && direction!=(turnAttack&DIRECTIONS))turnAttack=0;
        boolean directionChanged=direction!=previousDirection;
        boolean buttonsChanged=next!=buttons;
        boolean buttonPressed=(next&~buttons)!=0;
        if(directionChanged) {
            int skipped=skippedDiagonal(previousSector,sector,previousDirection,direction);
            if(skipped>=0)enqueueTurn(SECTORS[skipped],buttons,timeMs);
            enqueueTurn(direction,next,timeMs);
        }
        if(buttonsChanged&&!directionChanged&&buttonPressed) {
            if(turnCount>0) {
                if(!attachToLastTurn(direction,next))enqueueTurn(direction,next,timeMs);
            } else {
                press=new Snapshot(direction|next,thumbX,thumbY);
                sampledDirection=direction;
            }
        }
        buttons=next;
    }
    private void enqueueTurn(int nextDirection,int nextButtons,long timeMs) {
        int samples=isDiagonal(nextDirection)?MIN_DIAGONAL_SAMPLES:1;
        for(int i=0;i<samples;i++)enqueueOneTurn(nextDirection,nextButtons,timeMs);
    }
    private static boolean isDiagonal(int value) {
        return (value&(LEFT|RIGHT))!=0&&(value&(UP|DOWN))!=0;
    }
    private void enqueueOneTurn(int nextDirection,int nextButtons,long timeMs) {
        if(turnCount==turns.length) {
            firstTurn=(firstTurn+1)%turns.length;
            turnCount--;
        }
        int slot=(firstTurn+turnCount)%turns.length;
        turnTimes[slot]=timeMs;
        turns[slot]=nextDirection;
        turnButtons[slot]=nextButtons;
        turnCount++;
    }
    // Preserve the diagonal crossing when one touch sample jumps between cardinal sectors.
    private static int skippedDiagonal(int from,int to,int oldDirection,int newDirection) {
        if(from<0||to<0||oldDirection!=SECTORS[from]||newDirection!=SECTORS[to])return -1;
        int delta=(to-from+8)&7;
        if(delta==2)return (from+1)&7;
        if(delta==6)return (from+7)&7;
        return -1;
    }
    private boolean attachToLastTurn(int nextDirection,int nextButtons) {
        if(turnCount==0)return false;
        int slot=(firstTurn+turnCount-1)%turns.length;
        if(turns[slot]!=nextDirection)return false;
        turnButtons[slot]=nextButtons;
        return true;
    }
    private void begin(int id,float x,float y) {
        // Assign roles only on DOWN. Crossing another control must not change roles.
        if(layout.startsStick(x,y)) {
            if(owner==-1){owner=id;roles[id]=STICK;}
            else roles[id]=IGNORE;
        }else if(layout.insertsCoin(x,y))roles[id]=INSERT_COIN;
        else if(layout.startsGame(x,y))roles[id]=GAME_START;
        else if(layout.pressesMax(x,y))roles[id]=MAX_FIRE;
        else if(layout.fireAt(x,y)!=0)roles[id]=FIRE;
        else roles[id]=IGNORE;
    }
    private void end(int id) {
        roles[id]=buttonsById[id]=0;
        if(owner==id)releaseStick();
    }
    private void move(int id,float x,float y) {
        if(roles[id]==STICK&&owner==id)stick(x,y);
        else if(roles[id]==FIRE)buttonsById[id]=layout.fireAt(x,y);
        else if(roles[id]==GAME_START)buttonsById[id]=layout.startsGame(x,y)?START:0;
        else if(roles[id]==INSERT_COIN)buttonsById[id]=layout.insertsCoin(x,y)?COIN:0;
        else if(roles[id]==MAX_FIRE)buttonsById[id]=layout.pressesMax(x,y)?MAX_KEYS:0;
    }
    private void stick(float x,float y) {
        float vx=(x-layout.dx)/layout.dr,vy=(y-layout.dy)/layout.dr;
        double length=Math.hypot(vx,vy);
        if(length<(direction==0?ENGAGE_RADIUS:RELEASE_RADIUS)) {
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
