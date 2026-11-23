package local.k98;

import java.io.*;
import java.util.Arrays;
import java.util.zip.CRC32;

/** Run a fresh emulator process per peer with the production bridge and the same opening snapshot. */
public final class DuelCoreTest {
    public static void main(String[] args)throws Exception{
        File game=new File(args[0]).getCanonicalFile(),out=new File(args[1]).getCanonicalFile();
        boolean team=args[2].equals("team"),host=args[3].equals("host");
        File saves=new File(out,args[2]+"-"+args[3]);saves.mkdirs();
        if(Bridge.openMode(game.getPath(),new File(game,"system").getPath(),saves.getPath(),"AES_EUR")!=0)throw new AssertionError("core open");
        int[] rgb=new int[640*512],wh=new int[2];short[] pcm=new short[8192];
        File snapshot=new File(out,args[2]+".state.gz"),frames=new File(out,args[2]+".frames");
        try{
            if(host)for(int i=0;i<1080;i++)Bridge.frame(DuelRound.menuKeys(i,team),rgb,pcm,wh);
            byte[] state=new byte[Bridge.stateSize()];
            if(host){
                if(!Bridge.state(state,true))throw new AssertionError("save");
                java.nio.file.Files.write(snapshot.toPath(),DuelRound.pack(state));
            }
            DuelRound.unpack(java.nio.file.Files.readAllBytes(snapshot.toPath()),state);
            if(!Bridge.state(state,false))throw new AssertionError("restore");
            CRC32 crc=new CRC32();StringBuilder result=new StringBuilder();
            for(int f=0;f<2100;f++){
                int p1=f%100<4?PadLayout.A:0;
                int p2=f%89<4?PadLayout.A:(f%133==9?PadLayout.RIGHT:0);
                if(Bridge.frame(p1|(p2<<16),rgb,pcm,wh)<0)throw new AssertionError("frame");
                if(f%30==29){
                    int n=Arrays.hashCode(Arrays.copyOf(rgb,wh[0]*wh[1]));
                    for(int k=0;k<4;k++)crc.update(n>>>(8*k));
                    result.append(crc.getValue()).append('\n');
                }
            }
            byte[] actual=result.toString().getBytes("US-ASCII");
            if(host)java.nio.file.Files.write(frames.toPath(),actual);
            else if(!Arrays.equals(actual,java.nio.file.Files.readAllBytes(frames.toPath())))throw new AssertionError("video diverged");
            System.out.println("PASS "+args[2]+" "+args[3]+" 2100 frames; pixel checksum "+crc.getValue());
        }finally{Bridge.close();}
    }
}
