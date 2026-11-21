package local.k98;

import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;

/** Transfers a whole Android touch batch to the emulator's input sampler. */
final class TouchMotion {
    private static final String TAG="K98Input";
    private final boolean trace=Log.isLoggable(TAG,Log.DEBUG);
    private final TouchInput input;
    private final int[] ids=new int[32];
    private final float[] xs=new float[ids.length],ys=new float[ids.length];

    TouchMotion(TouchInput input){this.input=input;}

    void accept(MotionEvent event) {
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_CANCEL){input.reset();return;}
        int count=Math.min(event.getPointerCount(),ids.length);
        for(int i=0;i<count;i++)ids[i]=event.getPointerId(i);
        int changedId=event.getPointerId(event.getActionIndex());
        long receivedMs=trace?SystemClock.uptimeMillis():0;
        int history=action==MotionEvent.ACTION_DOWN?0:event.getHistorySize();
        synchronized(input) {
            for(int h=0;h<history;h++) {
                for(int i=0;i<count;i++) {
                    xs[i]=event.getHistoricalX(i,h);
                    ys[i]=event.getHistoricalY(i,h);
                }
                // Only the current sample creates or lifts the changed pointer.
                sample(MotionEvent.ACTION_MOVE,-1,count,event.getHistoricalEventTime(h),receivedMs);
            }
            for(int i=0;i<count;i++){xs[i]=event.getX(i);ys[i]=event.getY(i);}
            sample(action,changedId,count,event.getEventTime(),receivedMs);
        }
    }

    private void sample(int action,int changedId,int count,long timeMs,long receivedMs) {
        input.event(action,changedId,count,ids,xs,ys,timeMs);
        if(trace) {
            int held=input.held();
            Log.d(TAG,"sampleMs="+timeMs+" receivedMs="+receivedMs+" action="+action
                  +" direction="+(held&PadLayout.DIRECTIONS)+" buttons="+(held&~PadLayout.DIRECTIONS));
        }
    }

    void traceFrame(int frame,long cutoffMs,int keys) {
        if(trace)Log.d(TAG,"frame="+frame+" pollMs="+SystemClock.uptimeMillis()
                       +" cutoffMs="+cutoffMs+" coreMask="+keys);
    }
}
