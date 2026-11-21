package local.k98;

/** Reproduces the reported 4:3 offset when landscape insets differ between sides. */
public final class CenteringRegressionTest {
    private static int cases;
    private static void near(float actual,float expected,String label) {
        if(Math.abs(actual-expected)>.002f)throw new AssertionError(label+": "+actual+" vs "+expected);
    }
    public static void main(String[] args) {
        PadLayout reported=new PadLayout(2392,1080,false,DisplayOptions.DEFAULT,0,0,54,0);
        System.out.println("right inset=54: video center="+(reported.videoLeft+reported.videoRight)/2+", screen center=1196");
        int[][] screens={{2392,1080},{2400,1080},{1920,1080},{1440,1080},{1280,800},{800,360},{640,360},{480,270},{2393,1081}};
        int[][] insets={{0,0,0,0},{54,0,0,8},{0,6,54,0},{84,0,24,0},{24,0,84,0},{0,30,0,4}};
        for(int[] size:screens)for(int[] inset:insets)for(boolean full:new boolean[]{false,true}) {
            PadLayout p=new PadLayout(size[0],size[1],true,new DisplayOptions(full,true,50),inset[0],inset[1],inset[2],inset[3]);
            near((p.videoLeft+p.videoRight)/2,size[0]/2f,"horizontal center "+java.util.Arrays.toString(inset));
            near((p.videoTop+p.videoBottom)/2,size[1]/2f,"vertical center");
            near((p.videoRight-p.videoLeft)/(p.videoBottom-p.videoTop),4f/3,"4:3 ratio");
            if(p.videoLeft<0||p.videoTop<0||p.videoRight>size[0]||p.videoBottom>size[1])throw new AssertionError("outside screen");
            if(!full&&(p.videoLeft<inset[0]||p.videoRight>size[0]-inset[2]||p.videoTop<inset[1]||p.videoBottom>size[1]-inset[3]))throw new AssertionError("classic picture overlaps unsafe area");
            PadLayout mirrored=new PadLayout(size[0],size[1],true,new DisplayOptions(full,true,50),inset[2],inset[3],inset[0],inset[1]);
            near(p.videoLeft,mirrored.videoLeft,"reverse landscape X");
            near(p.videoTop,mirrored.videoTop,"reverse landscape Y");
            cases++;
        }
        System.out.println("CENTERING PASS: "+cases+" asymmetric-inset/display cases");
    }
}
