package local.k98;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Bounded, opt-in recording. Gameplay never performs file I/O. Times use Android uptime. */
final class InputTrace {
    static final long MAX_DURATION_MS=120_000;
    private static final int MAX_RECORDS=60_000, MAX_CHARS=12*1024*1024;
    private final int maxRecords,maxChars;
    private final long maxDuration;
    private final ArrayList<String> lines=new ArrayList<>();
    private volatile boolean recording;
    private long startedMs,startedWallMs,endedMs,serial,firstId,lastTouchId;
    private int chars;
    private String stopReason="";

    InputTrace(){this(MAX_RECORDS,MAX_CHARS,MAX_DURATION_MS);}
    InputTrace(int maxRecords,int maxChars,long maxDuration){
        this.maxRecords=maxRecords;this.maxChars=maxChars;this.maxDuration=maxDuration;
    }
    boolean recording(){return recording;}
    synchronized boolean hasData(){return !lines.isEmpty();}
    synchronized String reason(){return stopReason;}
    synchronized int size(){return lines.size();}
    synchronized long wallTime(){return startedWallMs;}
    synchronized long elapsed(long now){return lines.isEmpty()?0:Math.max(0,(recording?now:endedMs)-startedMs);}
    synchronized void start(long now,long wallMs,String metadata){
        lines.clear();chars=0;startedMs=now;startedWallMs=wallMs;endedMs=0;
        firstId=serial+1;lastTouchId=0;stopReason="";recording=true;
        append("session",now,"\"schema\":1,\"wallTimeMs\":"+wallMs+(metadata.isEmpty()?"":","+metadata)
                +",\"clock\":\"uptimeMillis\",\"coordinates\":\"GameScreen pixels; x right, y down\""
                +",\"masks\":{\"A\":1,\"B\":256,\"C\":2,\"D\":512,\"START\":8,\"COIN\":4,\"UP\":16,\"DOWN\":32,\"LEFT\":64,\"RIGHT\":128}");
    }
    synchronized void stop(String reason,long now){
        if(!recording)return;
        recording=false;endedMs=now;stopReason=reason;
        // Reserve the end record even when the data cap was reached. Never discard old samples.
        append("end",now,"\"reason\":"+quote(reason)+",\"dataRecords\":"+lines.size());
    }
    private boolean accept(long now){
        if(!recording)return false;
        if(now-startedMs>=maxDuration){stop("duration_limit",now);return false;}
        if(lines.size()>=maxRecords||chars>=maxChars){stop("size_limit",now);return false;}
        return true;
    }
    private long append(String type,long now,String fields){
        long id=++serial;
        String line="{\"id\":"+id+",\"type\":"+quote(type)+",\"atMs\":"+now
                +(fields.isEmpty()?"":","+fields)+"}";
        lines.add(line);chars+=line.length();return id;
    }
    synchronized boolean marker(String name,long now){
        if(!accept(now))return false;
        append("marker",now,"\"name\":"+quote(name));return true;
    }
    synchronized void layout(PadLayout p,long now){
        if(p==null||!accept(now))return;
        StringBuilder fields=new StringBuilder("\"width\":").append(p.width).append(",\"height\":").append(p.height)
                .append(",\"safeX\":").append(p.safeX).append(",\"safeY\":").append(p.safeY)
                .append(",\"safeWidth\":").append(p.safeWidth).append(",\"safeHeight\":").append(p.safeHeight)
                .append(",\"stickX\":").append(p.dx).append(",\"stickY\":").append(p.dy)
                .append(",\"stickRadius\":").append(p.dr).append(",\"slots\":[");
        for(int i=0;i<p.cx.length;i++){
            if(i>0)fields.append(',');
            fields.append("{\"slot\":").append(i).append(",\"x\":").append(p.cx[i])
                    .append(",\"y\":").append(p.cy[i]).append(",\"rx\":").append(p.rx[i])
                    .append(",\"ry\":").append(p.ry[i]).append(",\"visible\":").append(p.visible(i)).append('}');
        }
        append("layout",now,fields.append(']').toString());
    }
    synchronized void touch(long batch,boolean history,int action,int changedId,int count,int[] ids,float[] xs,float[] ys,
                            long sampleMs,long receivedMs,String state){
        if(!accept(receivedMs))return;
        StringBuilder fields=new StringBuilder("\"batch\":").append(batch).append(",\"historical\":").append(history)
                .append(",\"sampleMs\":").append(sampleMs).append(",\"action\":").append(action)
                .append(",\"changedId\":").append(changedId).append(",\"pointers\":[");
        for(int i=0;i<count;i++){
            if(i>0)fields.append(',');
            fields.append("{\"id\":").append(ids[i]).append(",\"x\":").append(number(xs[i]))
                    .append(",\"y\":").append(number(ys[i])).append('}');
        }
        fields.append("],\"stick\":").append(state);
        lastTouchId=append("touch",receivedMs,fields.toString());
    }
    synchronized long poll(int frame,long cutoffMs,long now,int touchMask,int hardwareMask,String state){
        if(!accept(now))return 0;
        return append("poll",now,"\"frame\":"+frame+",\"cutoffMs\":"+cutoffMs+",\"lastTouchId\":"+lastTouchId
                +",\"touchMask\":"+touchMask+",\"hardwareMask\":"+hardwareMask+",\"localMask\":"+(touchMask|hardwareMask)
                +",\"stick\":"+state);
    }
    synchronized void core(int frame,long pollId,long now,int coreMask){
        // A delayed Bluetooth exchange must not append an old session's frame to a new recording.
        if(pollId<firstId||pollId==0||!accept(now))return;
        int p1=coreMask&0xffff,p2=(coreMask>>>16)&0xffff;
        append("core",now,"\"frame\":"+frame+",\"pollId\":"+pollId+",\"coreMask\":"+coreMask
                +",\"p1Mask\":"+p1+",\"p2Mask\":"+p2+",\"p1Direction\":"+quote(direction(p1))
                +",\"p2Direction\":"+quote(direction(p2)));
    }
    synchronized String[] snapshot(){return lines.toArray(new String[0]);}
    static void writeZip(OutputStream out,String[] records)throws IOException{
        writeZip(out,records,"");
    }
    static void writeZip(OutputStream out,String[] records,String note)throws IOException{
        try(ZipOutputStream zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){
            zip.putNextEntry(new ZipEntry("trace.jsonl"));
            for(String line:records){zip.write(line.getBytes(StandardCharsets.UTF_8));zip.write('\n');}
            zip.closeEntry();zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write(("KOF98 input trace, schema 1\n"
                    +"session: app/device/mode, input tuning, clock and key masks.\n"
                    +"layout: actual GameScreen pixel geometry. Pointer x/y are local pixels, not screen or normalized coordinates.\n"
                    +"touch: gameplay samples (including MotionEvent history), pointer IDs/action, and decoded stick state after it.\n"
                    +"stick: owner, sector, heldMask, normalized knob, queued turns (mask + original time), pending attack.\n"
                    +"poll: emulator input sampling, lastTouchId, touch/hardware/local masks and queue after sampling.\n"
                    +"core: mask passed to Bridge.frame, after Bluetooth exchange, linked to pollId; low 16 bits = P1, high 16 = P2.\n"
                    +"These are submitted inputs, not a claim that the game's move parser accepted a special move.\n"
                    +"marker: lifecycle/reset or failed-attempt marker. end: user/duration_limit/size_limit.\n"
                    +"All atMs/sampleMs/cutoffMs/turn timeMs use uptimeMillis. wallTimeMs is only for calendar time.\n"
                    +"Directions use numpad notation in screen coordinates: 2 down, 1 down-left, 4 left, 3 down-right, 6 right, 5 neutral.\n"
                    +"ACTION_DOWN=0 UP=1 MOVE=2 CANCEL=3 POINTER_DOWN=5 POINTER_UP=6.\n")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("note.txt"));
            zip.write(("测试备注：\n"+(note==null?"":note)+"\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }
    static String direction(int mask){
        switch(mask&PadLayout.DIRECTIONS){
            case 0:return "5";case PadLayout.UP:return "8";case PadLayout.DOWN:return "2";
            case PadLayout.LEFT:return "4";case PadLayout.RIGHT:return "6";
            case PadLayout.UP|PadLayout.LEFT:return "7";case PadLayout.UP|PadLayout.RIGHT:return "9";
            case PadLayout.DOWN|PadLayout.LEFT:return "1";case PadLayout.DOWN|PadLayout.RIGHT:return "3";
            default:return "conflict";
        }
    }
    private static String number(float value){return Float.isNaN(value)||Float.isInfinite(value)?"null":Float.toString(value);}
    static String quote(String value){
        if(value==null)return "null";
        StringBuilder s=new StringBuilder("\"");
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c=='"'||c=='\\')s.append('\\').append(c);
            else if(c<32){s.append("\\u00");s.append(Character.forDigit(c>>4,16)).append(Character.forDigit(c&15,16));}
            else s.append(c);
        }
        return s.append('"').toString();
    }
}
