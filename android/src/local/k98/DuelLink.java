package local.k98;

import java.io.*;
import java.util.concurrent.*;

/** Both peers use one ordered stream. Only the room owner assigns seats and round IDs. */
final class DuelLink implements Closeable,DuelRound.Wire {
    interface Events {
        void lobby();
        void playing(DuelRound round);
        void closed(String reason);
    }
    private static final int HELLO=0x4b391610, START=1, INPUT=2, STATE=3, RETURN=4,
        OFFER=5, ANSWER=6, SEATS=7;
    final boolean host;
    volatile boolean team,hostP1=true;
    volatile int offer; // 1 from host, 2 from guest
    volatile DuelRound round;
    private volatile boolean closed;
    private int serial;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final Closeable socket;
    private final Events events;
    private final ExecutorService writer=Executors.newSingleThreadExecutor();
    DuelLink(InputStream input,OutputStream output,Closeable socket,boolean host,boolean team,Events events){
        in=new DataInputStream(new BufferedInputStream(input));out=new DataOutputStream(new BufferedOutputStream(output));
        this.socket=socket;this.host=host;this.team=team;this.events=events;
    }
    boolean localP1(){return host==hostP1;}
    interface Write {void send()throws IOException;}
    private synchronized void send(Write w){
        if(closed)return;
        writer.execute(()->{try{w.send();out.flush();}catch(IOException e){fail("蓝牙连接中断");}});
    }
    void run()throws IOException{
        send(()->{out.writeInt(HELLO);out.writeBoolean(host);out.writeBoolean(team);});
        if(in.readInt()!=HELLO||in.readBoolean()==host)throw new IOException("请在两台手机安装同一版本");
        boolean peerTeam=in.readBoolean();if(!host)team=peerTeam;
        events.lobby();
        while(!closed){
            int op=in.readUnsignedByte();
            switch(op){
            case START:{
                int id=in.readInt();boolean left=in.readBoolean();
                synchronized(this){if(host||round!=null||id!=serial+1)throw new IOException("对局顺序错误");
                    serial=id;hostP1=left;offer=0;round=new DuelRound(id,team,localP1(),false,this);}
                events.playing(round);break;
            }
            case INPUT:{
                int id=in.readInt(),frame=in.readInt(),mask=in.readUnsignedShort();DuelRound r=round;
                if(r!=null&&r.id==id)r.receive(frame,mask);break;
            }
            case STATE:{
                int id=in.readInt(),size=in.readInt();
                if(host||size<1||size>DuelRound.MAX_STATE)throw new IOException("开局状态无效");
                byte[] bytes=new byte[size];in.readFully(bytes);DuelRound r=round;
                if(r!=null&&r.id==id)r.receiveState(bytes);break;
            }
            case RETURN:{int id=in.readInt();if(clearRound(id))events.lobby();break;}
            case OFFER:{
                synchronized(this){if(round!=null)break;
                    if(host){if(offer!=0)break;offer=2;broadcastSeats();}else offer=1;}
                events.lobby();break;
            }
            case ANSWER:{boolean yes=in.readBoolean();
                synchronized(this){if(!host||offer!=1||round!=null)break;
                    if(yes)hostP1=!hostP1;offer=0;broadcastSeats();}
                events.lobby();break;
            }
            case SEATS:{
                boolean left=in.readBoolean();int pending=in.readUnsignedByte();
                synchronized(this){if(host||pending>2)throw new IOException("座位数据错误");
                    hostP1=left;offer=pending;}
                events.lobby();break;
            }
            default:throw new IOException("联机协议不兼容");
            }
        }
    }
    private void broadcastSeats(){
        final boolean left=hostP1;final int pending=offer;
        send(()->{out.writeByte(SEATS);out.writeBoolean(left);out.writeByte(pending);});
    }
    synchronized void swap(){
        if(round!=null||offer!=0||closed)return;
        if(host){offer=1;broadcastSeats();}else{offer=2;send(()->out.writeByte(OFFER));}
        events.lobby();
    }
    synchronized void answer(boolean yes){
        if(round!=null||offer!=(host?2:1))return;
        if(host){if(yes)hostP1=!hostP1;offer=0;broadcastSeats();}
        else send(()->{out.writeByte(ANSWER);out.writeBoolean(yes);});
        events.lobby();
    }
    synchronized void start(){
        if(!host||round!=null||offer!=0||closed)return;
        int id=++serial;boolean left=hostP1;
        round=new DuelRound(id,team,localP1(),true,this);
        send(()->{out.writeByte(START);out.writeInt(id);out.writeBoolean(left);});
        events.playing(round);
    }
    public void input(int id,int frame,int mask){
        send(()->{out.writeByte(INPUT);out.writeInt(id);out.writeInt(frame);out.writeShort(mask);});
    }
    public void snapshot(int id,byte[] bytes){
        send(()->{out.writeByte(STATE);out.writeInt(id);out.writeInt(bytes.length);out.write(bytes);});
    }
    private synchronized boolean clearRound(int id){
        if(round==null||round.id!=id)return false;
        round.end();round=null;return true;
    }
    synchronized void returnToRoom(){
        if(round==null)return;
        int id=round.id;send(()->{out.writeByte(RETURN);out.writeInt(id);});clearRound(id);events.lobby();
    }
    void fail(String reason){if(dispose())events.closed(reason);}
    private synchronized boolean dispose(){
        if(closed)return false;closed=true;if(round!=null)clearRound(round.id);
        try{socket.close();}catch(IOException ignored){}writer.shutdownNow();return true;
    }
    public void close(){dispose();}
}
