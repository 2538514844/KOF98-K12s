package local.k98;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Exercise the production protocol over two independent streams, including a second round. */
public final class DuelLinkTest {
    static final class Peer implements DuelLink.Events {
        volatile int lobbies;
        public void lobby(){lobbies++;}
        public void playing(DuelRound round){}
        public void closed(String reason){}
    }
    static void until(BooleanSupplier condition)throws Exception{
        long end=System.nanoTime()+3_000_000_000L;
        while(!condition.getAsBoolean()){if(System.nanoTime()>end)throw new AssertionError("timeout");Thread.sleep(2);}
    }
    static Thread reader(DuelLink link){
        Thread t=new Thread(()->{try{link.run();}catch(IOException e){link.fail(e.getMessage());}});t.setDaemon(true);t.start();return t;
    }
    static void play(DuelRound round,boolean owner)throws Exception{
        for(int i=0;i<750;i++){
            int input=owner?(i%17):((i+1)%13);
            int result=round.exchange(i,input);
            int tick=i-DuelRound.DELAY;
            int expected=tick<0?0:((tick%17)<<16)|((tick+1)%13);
            if(result!=expected)throw new AssertionError("input mismatch at "+i);
        }
    }
    public static void main(String[] args)throws Exception{
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
            Socket a=new Socket(server.getInetAddress(),server.getLocalPort());Socket b=server.accept()){
            a.setTcpNoDelay(true);b.setTcpNoDelay(true);
            Peer host=new Peer(),guest=new Peer();
            try(DuelLink left=new DuelLink(a.getInputStream(),a.getOutputStream(),a,true,true,host);
                DuelLink right=new DuelLink(b.getInputStream(),b.getOutputStream(),b,false,false,guest)){
                reader(left);reader(right);until(()->host.lobbies>0&&guest.lobbies>0);
                if(!right.team||right.localP1())throw new AssertionError("incorrect initial seats or mode");
                right.swap();until(()->left.offer==2);left.answer(false);until(()->right.offer==0);
                left.swap();until(()->right.offer==1);right.answer(true);until(()->right.localP1()&&left.offer==0);
                for(int n=0;n<2;n++){
                    left.start();until(()->right.round!=null);
                    Future<?> other=executor.submit(()->{play(right.round,false);return null;});
                    play(left.round,true);other.get(5,TimeUnit.SECONDS);
                    right.returnToRoom();until(()->left.round==null);
                }
                byte[] data={0,1,4,7};byte[] copy=new byte[4];DuelRound.unpack(DuelRound.pack(data),copy);
                if(!java.util.Arrays.equals(data,copy))throw new AssertionError("state transport");
                System.out.println("PASS seat request/decline/accept, opposite input ports, 1500 frames and rematch");
            }
        }finally{executor.shutdownNow();}
    }
}
