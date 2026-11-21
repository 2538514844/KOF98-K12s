package local.k98;

import java.io.File;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;

public final class ModeBootTest {
    private static native int byteAt(int offset);
    private static final int[] video = new int[640*512], geometry = new int[2];
    private static final short[] audio = new short[8192];
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static void runFrames(int keys,int count) {
        for (int i=0;i<count;i++) require(Bridge.frame(keys,video,audio,geometry)>=0,"frame");
        require(geometry[0]==304 && geometry[1]==224,"display geometry");
    }
    private static int actualRegion() { return byteAt(0xfd83); }
    private static int actualMachine() { return byteAt(0xfd82); }
    private static int touchCoin(boolean arcade) {
        PadLayout pad=new PadLayout(2392,1080,arcade);
        TouchInput touch=new TouchInput();
        touch.configure(pad);
        int[] finger={0};
        float[] x={pad.dx},y={(pad.coinTop+pad.coinBottom)*.5f};
        touch.event(TouchInput.DOWN_EVENT,0,1,finger,x,y);
        touch.event(TouchInput.UP_EVENT,0,1,finger,x,y);
        int pressed=touch.poll().keys;
        require(pressed==(arcade?PadLayout.COIN:0) && touch.poll().keys==0,"coin button single tap");
        return pressed;
    }
    public static void main(String[] args) throws Exception {
        File root=new File(args[0]).getCanonicalFile();
        require(NeoMode.fromName("bad")==NeoMode.AES_EUR && NeoMode.fromName(null)==NeoMode.AES_EUR,"default");
        require(NeoMode.AES_EUR.resumeFile(root).equals(new File(root,"save/resume.bin")),"legacy resume path");
        Set<File> saves=new HashSet<>();
        File legacy=NeoMode.AES_EUR.resumeFile(root);
        Files.write(legacy.toPath(),new byte[]{1,3,5,7});
        for(NeoMode mode:NeoMode.values()){
            require(saves.add(mode.resumeFile(root)),"shared save path");
            require(mode.canResume(root)==(mode==NeoMode.AES_EUR),"mode read another resume");
            File system=ModeFiles.prepare(root,mode);
            if(mode==NeoMode.AES_JAP){
                File overlay=new File(system,"fbneo/patched/neogeo.zip");
                long modified=overlay.lastModified();
                require(ModeFiles.prepare(root,mode).equals(system) && overlay.lastModified()==modified,"cache reuse");
                System.out.println("AES_JAP local compatibility cache bytes="+overlay.length());
            }
            int rc=Bridge.openMode(root.getPath(),system.getPath(),mode.saveDirectory(root).getPath(),mode.name());
            try {
                require(rc==0,"open "+mode+" returned "+rc);
                int wanted=mode.name().endsWith("JAP")?0:mode.name().endsWith("USA")?1:2;
                int lastRegion=-1,lastMachine=-1;
                boolean bootedRegion=false;
                for(int tick=0;tick<950;tick++){
                    require(Bridge.frame(0,video,audio,geometry)>=0,"boot frame");
                    int r=actualRegion(), m=actualMachine();
                    if(r!=lastRegion||m!=lastMachine){
                        System.out.printf("%s frame=%d machine=%02x region=%02x%n",mode,tick,m,r);
                        lastRegion=r;lastMachine=m;
                    }
                    if(tick>5 && r==wanted && m==(mode.arcade?0x80:0))bootedRegion=true;
                }
                int machine=actualMachine(),region=actualRegion();
                System.out.printf("%s machine=0x%02x region=%d user=%d p1=%d%n",mode,machine,region,byteAt(0xfdaf),byteAt(0xfdb6));
                require(machine==(mode.arcade?0x80:0) && bootedRegion && region==wanted,
                        "wrong machine/region after self-test for "+mode);
                int coin=touchCoin(mode.arcade);
                if(mode.arcade){
                    runFrames(PadLayout.START,4);runFrames(0,45);
                    require(byteAt(0xfdb6)==0,"start worked without credit");
                    runFrames(coin,1);runFrames(0,75);runFrames(PadLayout.START,4);runFrames(0,100);
                    System.out.printf("coin/start -> user=%d p1=%d%n",byteAt(0xfdaf),byteAt(0xfdb6));
                    require(byteAt(0xfdaf)==2 && byteAt(0xfdb6)==1,"coin/start failed: "+mode);
                    require(actualRegion()==wanted,"region changed during play: "+mode);
                }
                byte[] saved=new byte[Bridge.stateSize()];
                require(saved.length>8192 && Bridge.state(saved,true),"save "+mode);
                runFrames(0,29);
                require(Bridge.state(saved,false) && actualMachine()==machine && actualRegion()==region,"restore "+mode);
            } finally { Bridge.close(); }
        }
        require(java.util.Arrays.equals(Files.readAllBytes(legacy.toPath()),new byte[]{1,3,5,7}),"legacy touched");
        require(Bridge.openMode(root.getPath(),new File(root,"system").getPath(),new File(root,"save").getPath(),"INVALID")==4,"bad native ID");
        System.out.println("PASS five hardware modes, three coin/start sequences, default and isolated saves.");
    }
}
