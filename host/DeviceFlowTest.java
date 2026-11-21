package local.k98;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class DeviceFlowTest {
    static void check(boolean a,String why){if(!a)throw new AssertionError(why);}
    static byte[] sampleZip(String name,byte[] body) throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(b)){z.putNextEntry(new ZipEntry(name));z.write(body);z.closeEntry();}return b.toByteArray();}
    static void reject(File home,byte[] file) throws IOException{boolean bad=false;try{DataDepot.install(home,new ByteArrayInputStream(file),null);}catch(IOException expected){bad=true;}check(bad,"invalid ZIP accepted");}
    static int keys(int f){return (f>=610&&f<1400&&(f%75)<6?8:0)|(f>720&&(f%39)<5?1:0);}
    static int digest(int[] rgb,int[] wh){int h=1;for(int i=0;i<wh[0]*wh[1];i+=17)h=h*31+rgb[i];return h;}
    public static void main(String[] args)throws Exception{File data=new File(args[0]),home=new File(args[1]);Files.createDirectories(home.toPath());int[] pct={0};
        try(InputStream in=new FileInputStream(data)){DataDepot.install(home,in,n->pct[0]=n);}check(pct[0]==100&&DataDepot.ready(home),"import incomplete");
        File root=DataDepot.root(home),sentinel=new File(root,"save/keep-test.txt");Files.write(sentinel.toPath(),new byte[]{32});
        reject(home,sampleZip("../outside.zip",new byte[]{42}));reject(home,sampleZip("roms/kof98.zip",new byte[]{42}));reject(home,sampleZip("roms/neogeo.zip",new byte[1094595]));
        check(!new File(home,"outside.zip").exists()&&DataDepot.ready(home)&&Files.readAllBytes(sentinel.toPath())[0]==32,"negative tests disturbed good install");
        System.out.println("IMPORT OK: exact release ZIP; wrong path, wrong length and wrong SHA rejected; save retained");System.out.flush();
        int rc=Bridge.open(root.getCanonicalPath());System.out.println("JNI open="+rc);try {check(rc==0,"core failed to open");double fps=Bridge.fps();int rate=Bridge.rate();check(fps>59&&fps<60&&rate>=32000,"invalid AV info");
            int[] rgb=new int[640*512],wh=new int[2];short[] pcm=new short[8192];Set<Integer> screens=new HashSet<>();long audible=0;
            for(int f=0;f<3200;f++){int n=Bridge.frame(keys(f),rgb,pcm,wh);check(n>=0&&2*n<=pcm.length,"audio buffer");for(int i=0;i<n*2;i+=2)if(pcm[i]!=0||pcm[i+1]!=0)audible++;if(f%59==0)screens.add(digest(rgb,wh));}
            check(wh[0]==304&&wh[1]==224&&screens.size()>14&&audible>20000,"silent or static gameplay");System.out.printf(Locale.ROOT,"JNI frames=3200, geometry=%dx%d, fps=%.3f, rate=%d, changing-screens=%d, non-silent stereo frames=%d%n",wh[0],wh[1],fps,rate,screens.size(),audible);
            int sz=Bridge.stateSize();check(sz>8192,"state not supported");byte[] state=new byte[sz];check(Bridge.state(state,true),"serialize");
            for(int i=0;i<37;i++)Bridge.frame(1,rgb,pcm,wh);int oracle=digest(rgb,wh);
            check(Bridge.state(state,false),"unserialize");for(int i=0;i<37;i++)Bridge.frame(1,rgb,pcm,wh);int replay=digest(rgb,wh);check(oracle==replay,"savestate replay frame differs: "+oracle+" vs "+replay);
            System.out.println("JNI SAVE/LOAD OK: "+sz+" bytes; same rendered frame after 37 replayed frames");
        }finally{Bridge.close();}System.out.println("HOST TEST PASS (desktop core; not an Android device)");
    }
}
