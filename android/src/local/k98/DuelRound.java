package local.k98;

import java.io.*;
import java.util.zip.*;

/** One round's ordered input window. Socket reader and emulation thread meet here. */
final class DuelRound {
    interface Wire {
        void input(int round,int frame,int mask) throws IOException;
        void snapshot(int round,byte[] compressed) throws IOException;
    }
    static final int MAX_STATE=32*1024*1024, DELAY=2;
    final int id;
    final boolean team,localP1,host;
    private final Wire wire;
    private final int[] local=new int[16],remote=new int[16];
    private int consumed=DELAY,received=DELAY;
    private byte[] snapshot;
    private boolean ended;
    DuelRound(int id,boolean team,boolean localP1,boolean host,Wire wire){
        this.id=id;this.team=team;this.localP1=localP1;this.host=host;this.wire=wire;
    }
    synchronized void end(){ended=true;notifyAll();}
    synchronized void receive(int frame,int mask)throws IOException{
        if(ended)return;
        if(frame!=received || received-consumed>=remote.length)throw new IOException("蓝牙输入帧不同步");
        remote[received++%remote.length]=mask;notifyAll();
    }
    synchronized void receiveState(byte[] bytes)throws IOException{
        if(host || snapshot!=null)throw new IOException("开局数据顺序错误");
        snapshot=bytes;notifyAll();
    }
    private void waitForData(long until)throws IOException{
        long ms=(until-System.nanoTime())/1000000L;
        if(ended || ms<=0)throw new IOException(ended?"对局已结束":"等待对方超时");
        try{wait(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException(e);}
    }
    static byte[] pack(byte[] raw)throws IOException{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(GZIPOutputStream zip=new GZIPOutputStream(bytes)){zip.write(raw);}
        return bytes.toByteArray();
    }
    static void unpack(byte[] compressed,byte[] raw)throws IOException{
        try(DataInputStream stream=new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))){
            stream.readFully(raw);if(stream.read()!=-1)throw new IOException("开局状态大小不同");
        }
    }
    void synchronizeCore()throws IOException{
        int size=Bridge.stateSize();
        if(size<=0||size>MAX_STATE)throw new IOException("核心不支持联机存档");
        byte[] raw=new byte[size];
        if(host){
            if(!Bridge.state(raw,true))throw new IOException("开局状态生成失败");
            wire.snapshot(id,pack(raw));
        }else{
            byte[] bytes;
            synchronized(this){long until=System.nanoTime()+30000000000L;while(snapshot==null)waitForData(until);bytes=snapshot;snapshot=null;}
            unpack(bytes,raw);
        }
        if(!Bridge.state(raw,false))throw new IOException("无法载入联机开局");
    }
    int exchange(int frame,int mask)throws IOException{
        int target=frame+DELAY;
        wire.input(id,target,mask);
        local[target%local.length]=mask;
        if(frame<DELAY)return 0;
        synchronized(this){
            long until=System.nanoTime()+12000000000L;
            while(received<=frame)waitForData(until);
            if(ended)throw new IOException("对局已结束");
            int a=local[frame%local.length],b=remote[frame%remote.length];consumed=frame+1;
            return localP1?a|(b<<16):b|(a<<16);
        }
    }
    static int menuKeys(int frame,boolean team){
        if(frame>=615&&frame<621)return PadLayout.START;
        int step=frame-720;
        if(step>=0&&step<(team?1:3)*14 && step%14<5)return PadLayout.DOWN;
        if(frame>=830&&frame<836 || frame>=980&&frame<986)return PadLayout.A;
        return 0;
    }
}
