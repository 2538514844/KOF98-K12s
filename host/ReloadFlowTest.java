package local.k98;

import java.io.File;
import java.nio.file.*;
import java.util.Arrays;

/** Exercises actual production JNI close/open, playback and serialization across reloads. */
public final class ReloadFlowTest {
    private static final int[] rgb=new int[640*512],wh=new int[2];
    private static final short[] pcm=new short[8192];
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static int frame(int keys){
        int n=Bridge.frame(keys,rgb,pcm,wh);check(n>=0&&n*2<=pcm.length,"frame failed");
        return Arrays.hashCode(Arrays.copyOf(rgb,wh[0]*wh[1]));
    }
    private static int[] startup(){
        int[] frames=new int[20];
        for(int i=0;i<600;i++){int hash=frame(0);if(i%30==29)frames[i/30]=hash;}
        check(wh[0]==304&&wh[1]==224,"unexpected video geometry");return frames;
    }
    private static byte[] state(){
        byte[] data=new byte[Bridge.stateSize()];check(data.length>0&&Bridge.state(data,true),"save");return data;
    }
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);Path sentinel=root.toPath().resolve("save/keep-test.txt");
        byte[] original=Files.readAllBytes(sentinel);
        try{
            check(Bridge.open(root.getCanonicalPath())==0,"initial open");
            double fps=Bridge.fps();int rate=Bridge.rate();int[] baseline=startup();
            for(int round=0;round<3;round++){
                for(int i=0;i<2100;i++)frame(DeviceFlowTest.keys(i+600));
                byte[] match=state();check(match.length>8192,"incomplete match state");
                Bridge.close();
                check(Bridge.open(root.getCanonicalPath())==0,"reload open "+round);
                check(Bridge.fps()==fps&&Bridge.rate()==rate,"AV changed after reload");
                int[] reload=startup();check(Arrays.equals(baseline,reload),"reload did not return to identical startup "+round);
                byte[] fresh=state();check(!Arrays.equals(match,fresh),"reload incorrectly restored match");
                for(int i=0;i<37;i++)frame(0);int expected=frame(0);
                check(Bridge.state(fresh,false),"new-session restore");
                for(int i=0;i<37;i++)frame(0);check(frame(0)==expected,"fresh savestate replay mismatch");
                System.out.println("PASS reload "+(round+1)+": identical 600-frame startup, old match discarded, new save restores");
            }
            check(DataDepot.ready(root.getParentFile()),"valid data preflight");
            check(!DataDepot.ready(new File(root,"missing-data")),"missing data must fail preflight");
            System.out.println("PASS data preflight rejects missing data before closing active core");
            Bridge.close();char[] tooLong=new char[1100];Arrays.fill(tooLong,'x');
            int failure=Bridge.open(new String(tooLong));
            check(failure==1,"overlong path not rejected");Bridge.close();
            check(Bridge.open(root.getCanonicalPath())==0,"recovery after failed reload");
            check(Arrays.equals(baseline,startup()),"recovery startup differs");
            check(Arrays.equals(original,Files.readAllBytes(sentinel)),"unrelated save modified");
            System.out.println("PASS failed reload cleanly recovers; unrelated save retained");
            System.out.println("RELOAD HOST PASS: actual production JNI and desktop core; phone UI not exercised");
        }finally{Bridge.close();}
    }
}
