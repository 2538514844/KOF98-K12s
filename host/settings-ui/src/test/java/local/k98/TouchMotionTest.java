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
        assertEquals(DOWN,input.poll(144).keys);
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

    @Test public void historicalFingerPositionsDoNotCreateAPressBeforePointerDown() {
        float r=pad.dr*.65f;
        send(single(100,MotionEvent.ACTION_DOWN,pad.dx+r,pad.dy));
        MotionEvent.PointerCoords diagonal=coords(pad.dx+r,pad.dy+r);
        MotionEvent.PointerCoords down=coords(pad.dx,pad.dy+r);
        MotionEvent.PointerCoords a=coords(pad.bx[0],pad.by[0]);
        MotionEvent.PointerCoords outside=coords(960,100);
        MotionEvent edge=two(118,MotionEvent.ACTION_MOVE,diagonal,a);
        edge.addBatch(138,new MotionEvent.PointerCoords[]{down,outside},0);
        edge.setAction(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT));
        send(edge);
        assertEquals(RIGHT|DOWN,input.poll(120).keys);
        assertEquals(DOWN,input.poll(145).keys);
    }

    @Test public void punchBeatsOverdueDiagonalAndTurnEndingTapLastsTwoSamples() {
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
        assertEquals(DOWN|A,input.poll(127).keys);
        assertEquals(DOWN|A,input.poll(144).keys);
        assertEquals(DOWN,input.poll(161).keys);
        send(single(162,MotionEvent.ACTION_CANCEL,pad.dx,pad.dy+r));
        assertEquals(0,input.poll(177).keys);
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
        assertEquals(RIGHT|A,input.poll(144).keys);
        assertEquals(RIGHT|DOWN,input.poll(160).keys);
    }
}
