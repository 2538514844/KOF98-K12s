package local.k98;

import android.os.SystemClock;
import android.view.MotionEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static local.k98.PadLayout.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE)
public class InputTraceTest {
    private static JSONObject json(String line)throws Exception{return new JSONObject(line);}
    static Map<String,String> unzip(byte[] data)throws IOException{
        Map<String,String> files=new LinkedHashMap<>();
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(data),StandardCharsets.UTF_8)){
            ZipEntry entry;byte[] buffer=new byte[2048];
            while((entry=zip.getNextEntry())!=null){ByteArrayOutputStream out=new ByteArrayOutputStream();int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);files.put(entry.getName(),out.toString("UTF-8"));}
        }
        return files;
    }
    @Test public void disabledCaptureHasNoRecords(){
        InputTrace trace=new InputTrace();
        trace.touch(1,false,0,0,1,new int[]{0},new float[]{1},new float[]{2},100,100,"{}");
        assertEquals(0,trace.poll(0,100,100,DOWN,0,"{}"));
        trace.core(0,0,100,DOWN);trace.marker("pause",100);trace.stop("user",100);
        assertFalse(trace.hasData());assertFalse(trace.recording());assertEquals(0,trace.size());
    }
    @Test public void exportedZipLinksCoordinatesQueuePollAndBothPlayerPorts()throws Exception{
        InputTrace trace=new InputTrace();PadLayout pad=new PadLayout(1920,1080);TouchInput input=new TouchInput();input.configure(pad);
        trace.start(100,1_800_000_000_000L,"\"appVersion\":\"test\",\"inputConfig\":"+TouchInput.debugConfig());trace.layout(pad,100);
        int[] ids={9};float[] xs={pad.dx-pad.dr*.6f},ys={pad.dy+pad.dr*.6f};
        input.event(TouchInput.DOWN_EVENT,9,1,ids,xs,ys,110);
        trace.touch(7,false,0,9,1,ids,xs,ys,110,114,input.debugState());
        int local=input.poll(116).keys;
        long poll=trace.poll(42,116,117,local,A,input.debugState());
        int p1=local|A,p2=RIGHT|B;
        trace.core(42,poll,130,p1|(p2<<16));trace.marker("failed_attempt",140);trace.stop("export",150);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();InputTrace.writeZip(bytes,trace.snapshot(),"草薙京\n右侧：↓↙←↓↙←＋A");
        Map<String,String> files=unzip(bytes.toByteArray());assertEquals(3,files.size());assertTrue(files.get("note.txt").contains("↓↙←↓↙←＋A"));
        String[] lines=files.get("trace.jsonl").trim().split("\n");
        long previous=0;for(String line:lines){JSONObject o=json(line);assertTrue(o.getLong("id")>previous);previous=o.getLong("id");}
        JSONObject touch=json(lines[2]),sample=json(lines[3]),core=json(lines[4]);
        assertEquals(110,touch.getLong("sampleMs"));assertEquals(114,touch.getLong("atMs"));
        assertEquals(9,touch.getJSONArray("pointers").getJSONObject(0).getInt("id"));
        assertEquals(xs[0],touch.getJSONArray("pointers").getJSONObject(0).getDouble("x"),.001);
        assertEquals(LEFT|DOWN,touch.getJSONObject("stick").getInt("heldMask"));
        assertEquals(touch.getLong("id"),sample.getLong("lastTouchId"));assertEquals(poll,core.getLong("pollId"));
        assertEquals(LEFT|DOWN,sample.getInt("touchMask"));assertEquals(A,sample.getInt("hardwareMask"));assertEquals(p1,sample.getInt("localMask"));
        assertEquals(p1,core.getInt("p1Mask"));assertEquals(p2,core.getInt("p2Mask"));assertEquals("1",core.getString("p1Direction"));assertEquals("6",core.getString("p2Direction"));
        assertEquals("end",json(lines[lines.length-1]).getString("type"));
    }
    @Test public void limitsKeepEarlierEvidenceAndEndWithAReason()throws Exception{
        InputTrace timed=new InputTrace(100,100000,50);timed.start(100,0,"");timed.marker("before",149);timed.marker("too_late",150);
        assertEquals("duration_limit",timed.reason());assertEquals(3,timed.size());assertFalse(timed.recording());
        InputTrace capped=new InputTrace(3,100000,1000);capped.start(100,0,"");capped.marker("one",101);capped.marker("two",102);capped.marker("three",103);
        assertEquals("size_limit",capped.reason());assertEquals(4,capped.size());assertTrue(capped.snapshot()[1].contains("one"));
        int count=capped.size();capped.marker("later",104);capped.stop("user",105);assertEquals(count,capped.size());
        InputTrace bytes=new InputTrace(100,1,1000);bytes.start(100,0,"");bytes.marker("overflow",101);assertEquals("size_limit",bytes.reason());
        for(InputTrace t:new InputTrace[]{timed,capped,bytes})for(String line:t.snapshot())json(line);
    }
    @Test public void delayedBluetoothFrameCannotContaminateANewRecording()throws Exception{
        InputTrace trace=new InputTrace();trace.start(100,0,"");long old=trace.poll(4,110,110,LEFT,0,"{}");trace.stop("user",120);
        String[] frozen=trace.snapshot();trace.start(200,0,"");trace.core(4,old,220,LEFT);assertEquals(1,trace.size());
        long next=trace.poll(5,230,230,RIGHT,0,"{}");trace.core(5,next,240,RIGHT);assertEquals(3,trace.size());
        assertEquals(3,frozen.length);assertTrue(frozen[1].contains("\"frame\":4"));assertEquals("core",json(trace.snapshot()[2]).getString("type"));
    }
    @Test public void stringsAndNonFiniteTouchValuesStillExportAsValidJson()throws Exception{
        String value="引号\"反斜杠\\\n\r\t\u0001";
        assertEquals(value,new JSONObject("{\"value\":"+InputTrace.quote(value)+"}").getString("value"));
        InputTrace trace=new InputTrace();trace.start(100,0,"");
        trace.touch(1,false,2,-1,1,new int[]{0},new float[]{Float.NaN},new float[]{Float.POSITIVE_INFINITY},100,100,"{}");
        JSONObject point=json(trace.snapshot()[1]).getJSONArray("pointers").getJSONObject(0);assertTrue(point.isNull("x"));assertTrue(point.isNull("y"));
    }
    private static void both(TouchMotion plain,TouchMotion observed,MotionEvent event){
        try{plain.accept(event);observed.accept(event);}finally{event.recycle();}
    }
    @Test public void recordingPreservesLeftAndRightDoubleSwipeAndHistoricalSamples()throws Exception{
        for(int side:new int[]{-1,1}){
            PadLayout pad=new PadLayout(1920,1080);TouchInput plain=new TouchInput(),observed=new TouchInput();plain.configure(pad);observed.configure(pad);
            InputTrace trace=new InputTrace();trace.start(SystemClock.uptimeMillis(),0,"");
            TouchMotion a=new TouchMotion(plain),b=new TouchMotion(observed,trace);float r=pad.dr*.6f;
            both(a,b,MotionEvent.obtain(100,100,MotionEvent.ACTION_DOWN,pad.dx,pad.dy+r,0));
            for(int turn=0;turn<2;turn++){
                long t=120+turn*65;
                MotionEvent move=MotionEvent.obtain(100,t,MotionEvent.ACTION_MOVE,pad.dx+side*r,pad.dy+r,0);
                move.addBatch(t+18,pad.dx+side*r,pad.dy,1,1,0);both(a,b,move);
                if(turn==0)both(a,b,MotionEvent.obtain(100,t+35,MotionEvent.ACTION_MOVE,pad.dx,pad.dy+r,0));
                for(int f=0;f<4;f++){
                    long cutoff=t+f*17;int expected=plain.poll(cutoff).keys,actual=observed.poll(cutoff).keys;
                    assertEquals(expected,actual);assertEquals(plain.debugState(),observed.debugState());
                    long id=trace.poll(turn*4+f,cutoff,SystemClock.uptimeMillis(),actual,0,observed.debugState());
                    trace.core(turn*4+f,id,SystemClock.uptimeMillis(),actual);
                }
            }
            both(a,b,MotionEvent.obtain(100,230,MotionEvent.ACTION_CANCEL,pad.dx,pad.dy,0));
            assertEquals(0,observed.poll(250).keys);assertEquals(plain.debugState(),observed.debugState());
            int historical=0,core=0;boolean cancel=false;
            for(String line:trace.snapshot()){
                JSONObject record=json(line);String type=record.getString("type");
                if(type.equals("touch")){if(record.getBoolean("historical"))historical++;if(record.getInt("action")==3){cancel=true;assertEquals(0,record.getJSONObject("stick").getInt("queueDepth"));}}
                if(type.equals("core"))core++;
            }
            assertEquals(2,historical);assertEquals(8,core);assertTrue(cancel);
        }
    }
}
