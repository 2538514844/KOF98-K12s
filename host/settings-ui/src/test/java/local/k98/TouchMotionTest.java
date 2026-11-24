package local.k98;

import android.view.InputDevice;
import android.view.MotionEvent;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static local.k98.PadLayout.*;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class TouchMotionTest {
    private final PadLayout pad=new PadLayout(1920,1080);
    private final TouchInput input=new TouchInput();
    private final TouchMotion motion=new TouchMotion(input);

    @Before public void setup(){input.configure(pad);}

    private void send(MotionEvent e) {
        try{motion.accept(e);}finally{e.recycle();}
    }
    private MotionEvent single(long time,int action,float x,float y) {
        return MotionEvent.obtain(100,time,action,x,y,0);
    }
    private static MotionEvent.PointerCoords coords(float x,float y) {
        MotionEvent.PointerCoords p=new MotionEvent.PointerCoords();
        p.x=x;p.y=y;p.pressure=1;p.size=1;
        return p;
    }
    private static MotionEvent.PointerProperties finger(int id) {
        MotionEvent.PointerProperties p=new MotionEvent.PointerProperties();
        p.id=id;p.toolType=MotionEvent.TOOL_TYPE_FINGER;
        return p;
    }
    private MotionEvent two(long time,int action,MotionEvent.PointerCoords... points) {
        return MotionEvent.obtain(100,time,action,2,
                new MotionEvent.PointerProperties[]{finger(0),finger(9)},points,
                0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
    }

    private MotionEvent fingers(long time,int action,int[] ids,MotionEvent.PointerCoords... points) {
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[ids.length];
        for(int i=0;i<ids.length;i++)properties[i]=finger(ids[i]);
        return MotionEvent.obtain(100,time,action,ids.length,properties,points,
                0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
    }

    @Test public void singleThumbRimPressReachesGameAsBothButtons() {
        for(int first:new int[]{0,2}){
            input.reset();int chord=BITS[first]|BITS[first+1];
            float x=(pad.bx[first]+pad.bx[first+1])*.5f,y=(pad.by[first]+pad.by[first+1])*.5f;
            send(single(100,MotionEvent.ACTION_DOWN,x,y));
            assertEquals(chord,input.poll(110).keys);
            send(single(112,MotionEvent.ACTION_MOVE,pad.bx[first],pad.by[first]));
            assertEquals(BITS[first],input.poll(127).keys);
            send(single(130,MotionEvent.ACTION_UP,pad.bx[first],pad.by[first]));
            assertEquals(0,input.poll(144).keys);
            send(single(150,MotionEvent.ACTION_DOWN,x,y));send(single(154,MotionEvent.ACTION_UP,x,y));
            assertEquals(chord,input.poll(161).keys);assertEquals(0,input.poll(178).keys);
        }
    }

    @Test public void fiveFingersCombineAndReleaseByIdAfterIndexReordering() {
        MotionEvent.PointerCoords s=coords(pad.dx+pad.dr*.8f,pad.dy);
        MotionEvent.PointerCoords a=coords(pad.bx[0],pad.by[0]),b=coords(pad.bx[1],pad.by[1]);
        MotionEvent.PointerCoords c=coords(pad.bx[2],pad.by[2]),d=coords(pad.bx[3],pad.by[3]);
        send(fingers(100,MotionEvent.ACTION_DOWN,new int[]{31},s));assertEquals(RIGHT,input.poll(110).keys);
        int down=MotionEvent.ACTION_POINTER_DOWN,shift=MotionEvent.ACTION_POINTER_INDEX_SHIFT;
        send(fingers(111,down|(1<<shift),new int[]{31,7},s,a));
        send(fingers(113,down|(2<<shift),new int[]{31,7,2},s,a,b));
        send(fingers(115,down|(3<<shift),new int[]{31,7,2,19},s,a,b,c));
        send(fingers(117,down|(4<<shift),new int[]{31,7,2,19,13},s,a,b,c,d));
        assertEquals(RIGHT|A|B|C|D,input.poll(127).keys);
        send(fingers(129,MotionEvent.ACTION_POINTER_UP|(1<<shift),new int[]{13,2,7,19,31},d,b,a,c,s));
        assertEquals(RIGHT|A|C|D,input.poll(144).keys);
        send(fingers(146,MotionEvent.ACTION_CANCEL,new int[]{31,7,19,13},s,a,c,d));
        assertEquals(0,input.poll(161).keys);assertEquals(0,input.poll(178).keys);
    }

    @Test public void maxButtonSendsABCTogetherAndKeepsTheStickWhenReleased() {
        int max=ControlProfile.MAX_KEY;
        MotionEvent.PointerCoords s=coords(pad.dx-pad.dr*.8f,pad.dy),m=coords(pad.cx[max],pad.cy[max]);
        send(single(100,MotionEvent.ACTION_DOWN,s.x,s.y));assertEquals(LEFT,input.poll(110).keys);
        send(two(112,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),s,m));
        assertEquals(LEFT|MAX_KEYS,input.poll(127).keys);
        send(two(130,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),s,m));
        assertEquals(LEFT,input.poll(144).keys);
        send(single(150,MotionEvent.ACTION_UP,s.x,s.y));assertEquals(0,input.poll(161).keys);
        send(single(170,MotionEvent.ACTION_DOWN,m.x,m.y));send(single(173,MotionEvent.ACTION_UP,m.x,m.y));
        assertEquals(MAX_KEYS,input.poll(178).keys);assertEquals(0,input.poll(195).keys);
    }

    @Test public void batchedMovePreservesDiagonalForOutstandingFrame() {
        float r=pad.dr*.65f;
        send(single(100,MotionEvent.ACTION_DOWN,pad.dx+r,pad.dy));
        assertEquals(RIGHT,input.poll(110).keys);
        MotionEvent move=single(118,MotionEvent.ACTION_MOVE,pad.dx+r,pad.dy+r);
        move.addBatch(138,pad.dx,pad.dy+r,1,1,0);
        assertEquals(1,move.getHistorySize());
        send(move);
        assertEquals(RIGHT,input.poll(117).keys);
        assertEquals(RIGHT|DOWN,input.poll(127).keys);
        assertEquals(RIGHT|DOWN,input.poll(144).keys); // v0.21 keeps fast diagonals for two samples.
        assertEquals(DOWN,input.poll(161).keys);
    }

    private MotionEvent.PointerCoords angle(int degrees) {
        double radians=Math.toRadians(degrees);
        float radius=pad.dr*.8f;
        return coords(pad.dx+radius*(float)Math.cos(radians),pad.dy+radius*(float)Math.sin(radians));
    }

    @Test public void slowSwipeEnteringDiagonalThenSettlingAtItsEdge() {
        MotionEvent.PointerCoords right=angle(0),inside=angle(30),edge=angle(24);
        send(single(100,MotionEvent.ACTION_DOWN,right.x,right.y));
        assertEquals(RIGHT,input.poll(115).keys);
        MotionEvent move=single(120,MotionEvent.ACTION_MOVE,inside.x,inside.y);
        move.addBatch(128,edge.x,edge.y,1,1,0);
        send(move);
        assertEquals(RIGHT|DOWN,input.poll(132).keys);

        // Reproduce the former current-coordinate-only path with the identical endpoint.
        input.reset();
        send(single(100,MotionEvent.ACTION_DOWN,right.x,right.y));
        send(single(128,MotionEvent.ACTION_MOVE,edge.x,edge.y));
        assertEquals(RIGHT,input.poll(132).keys);
    }

    @Test public void shortBatchedDiagonalAfterHeldDownReachesTwoFramesOnEitherSide() {
        for(int side:new int[]{LEFT,RIGHT}){
            input.reset();float r=pad.dr*.65f;float x=pad.dx+(side==LEFT?-r:r);
            send(single(100,MotionEvent.ACTION_DOWN,pad.dx,pad.dy+r));
            for(int time=110;time<=144;time+=17)assertEquals(DOWN,input.poll(time).keys);
            // Real capture: down was held for 58 ms, then the diagonal lasted only 11 ms.
            MotionEvent move=single(158,MotionEvent.ACTION_MOVE,x,pad.dy+r);
            move.addBatch(169,x,pad.dy,1,1,0);send(move);
            MotionEvent.PointerCoords horizontal=coords(x,pad.dy),a=coords(pad.bx[0],pad.by[0]);
            send(two(174,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),horizontal,a));
            send(two(180,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),horizontal,a));
            assertEquals(DOWN|side,input.poll(177).keys);
            assertEquals(DOWN|side,input.poll(194).keys);
            assertEquals(side|A,input.poll(211).keys);
            assertEquals(side|A,input.poll(228).keys);
            assertEquals(side,input.poll(245).keys);
        }
    }

    @Test public void historicalFingerPositionsDoNotCreateAPressBeforePointerDown() {
        float r=pad.dr*.65f;
        send(single(100,MotionEvent.ACTION_DOWN,pad.dx+r,pad.dy));
        assertEquals(RIGHT,input.poll(110).keys);
        MotionEvent.PointerCoords diagonal=coords(pad.dx+r,pad.dy+r);
        MotionEvent.PointerCoords down=coords(pad.dx,pad.dy+r);
        MotionEvent.PointerCoords a=coords(pad.bx[0],pad.by[0]);
        MotionEvent.PointerCoords outside=coords(960,100);
        MotionEvent edge=two(118,MotionEvent.ACTION_MOVE,diagonal,a);
        edge.addBatch(138,new MotionEvent.PointerCoords[]{down,outside},0);
        edge.setAction(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        send(edge);
        assertEquals(RIGHT|DOWN,input.poll(120).keys);
        assertEquals(RIGHT|DOWN,input.poll(137).keys);
        assertEquals(DOWN,input.poll(154).keys);
    }

    @Test public void punchWaitsForQueuedDirectionsAndTurnEndingTapLastsTwoSamples() {
        float r=pad.dr*.65f;
        send(single(100,MotionEvent.ACTION_DOWN,pad.dx+r,pad.dy));
        assertEquals(RIGHT,input.poll(110).keys);
        MotionEvent.PointerCoords diagonal=coords(pad.dx+r,pad.dy+r);
        MotionEvent.PointerCoords down=coords(pad.dx,pad.dy+r);
        MotionEvent.PointerCoords a=coords(pad.bx[0],pad.by[0]);
        MotionEvent edge=two(118,MotionEvent.ACTION_MOVE,diagonal,a);
        edge.addBatch(138,new MotionEvent.PointerCoords[]{down,a},0);
        edge.setAction(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        send(edge);
        send(two(140,MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),down,a));
        assertEquals(RIGHT|DOWN,input.poll(127).keys);
        assertEquals(RIGHT|DOWN,input.poll(144).keys);
        assertEquals(DOWN|A,input.poll(161).keys);
        assertEquals(DOWN|A,input.poll(178).keys);
        assertEquals(DOWN,input.poll(195).keys);
        send(single(196,MotionEvent.ACTION_CANCEL,pad.dx,pad.dy+r));
        assertEquals(0,input.poll(212).keys);
    }

    @Test public void finalDirectionInBatchedMoveBeforePunchReleaseReachesNextPoll() {
        MotionEvent.PointerCoords down=angle(90),diagonal=angle(45),right=angle(0);
        MotionEvent.PointerCoords a=coords(pad.bx[0],pad.by[0]);
        send(single(100,MotionEvent.ACTION_DOWN,down.x,down.y));
        assertEquals(DOWN,input.poll(112).keys);
        send(single(116,MotionEvent.ACTION_MOVE,diagonal.x,diagonal.y));
        assertEquals(DOWN|RIGHT,input.poll(128).keys);
        send(two(130,MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),diagonal,a));
        MotionEvent release=two(135,MotionEvent.ACTION_MOVE,right,a);
        release.addBatch(138,new MotionEvent.PointerCoords[]{diagonal,a},0);
        release.setAction(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        send(release);
        assertEquals(RIGHT|DOWN|A,input.poll(144).keys);
        assertEquals(RIGHT|A,input.poll(160).keys);
        assertEquals(RIGHT|DOWN,input.poll(176).keys);
        assertEquals(RIGHT|DOWN,input.poll(192).keys);
    }
}
