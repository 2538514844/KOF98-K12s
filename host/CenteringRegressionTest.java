package local.k98;

/** The game stays centred and fills the height; safe insets belong to controls. */
public final class CenteringRegressionTest {
    private static int cases;
    private static void near(float actual,float expected,String label) {
        if(Math.abs(actual-expected)>.002f)throw new AssertionError(label+": "+actual+" vs "+expected);
    }
    public static void main(String[] args) {
        PadLayout reported=new PadLayout(2392,1080,false,DisplayOptions.DEFAULT,0,0,54,0);
        System.out.println("right inset=54: video center="+(reported.videoLeft+reported.videoRight)/2+", screen center=1196");
        PadLayout bottomInset=new PadLayout(2392,1080,false,DisplayOptions.DEFAULT,0,0,54,24);
        System.out.println("bottom inset=24: picture top="+bottomInset.videoTop+", bottom="+bottomInset.videoBottom+", expected 0..1080");
        PadLayout standard=new PadLayout(1920,1080,false);
        System.out.println("1920x1080 without insets: picture top="+standard.videoTop+", bottom="+standard.videoBottom+", expected 0..1080");
        int[][] screens={{2392,1080},{2400,1080},{1920,1080},{1440,1080},{1280,800},{800,360},{640,360},{480,270},{2393,1081},{1921,1080},{1024,768},{800,800}};
        int[][] insets={{0,0,0,0},{54,0,0,8},{0,6,54,0},{84,0,24,0},{24,0,84,0},{0,30,0,4}};
        for(int[] size:screens)for(int[] inset:insets)for(boolean full:new boolean[]{false,true})for(boolean keep:new boolean[]{false,true}) {
            DisplayOptions options=new DisplayOptions(full,keep,50);
            PadLayout p=new PadLayout(size[0],size[1],true,options,inset[0],inset[1],inset[2],inset[3]);
            near((p.videoLeft+p.videoRight)/2,size[0]/2f,"horizontal center "+java.util.Arrays.toString(inset));
            near((p.videoTop+p.videoBottom)/2,size[1]/2f,"vertical center");
            if(!full||keep)near((p.videoRight-p.videoLeft)/(p.videoBottom-p.videoTop),4f/3,"4:3 ratio");
            if(p.videoLeft<0||p.videoTop<0||p.videoRight>size[0]||p.videoBottom>size[1])throw new AssertionError("outside screen");
            if(full&&!keep||size[0]*3>=size[1]*4){
                near(p.videoTop,0,"picture reaches top, full="+full+", insets="+java.util.Arrays.toString(inset));
                near(p.videoBottom,size[1],"picture reaches bottom");
            }
            if(full&&!keep||size[0]*3<=size[1]*4){
                near(p.videoLeft,0,"width-limited picture reaches left");
                near(p.videoRight,size[0],"width-limited picture reaches right");
            }
            PadLayout noInsets=new PadLayout(size[0],size[1],true,options,0,0,0,0);
            near(p.videoLeft,noInsets.videoLeft,"insets must not shrink picture width");
            near(p.videoTop,noInsets.videoTop,"insets must not shrink picture height");
            PadLayout mirrored=new PadLayout(size[0],size[1],true,options,inset[2],inset[3],inset[0],inset[1]);
            near(p.videoLeft,mirrored.videoLeft,"reverse landscape X");
            near(p.videoTop,mirrored.videoTop,"reverse landscape Y");
            if(!full||keep){
                float leftGap=p.videoLeft-p.dx-p.dr-PadLayout.STICK_STROKE*.5f;
                float rightGap=Float.MAX_VALUE;
                for(int i=0;i<4;i++){
                    float reach=p.rx[i+1]+PadLayout.keyStroke(p.rx[i+1])*.5f;
                    rightGap=Math.min(rightGap,p.bx[i]-reach-p.videoRight);
                    if(p.bx[i]+reach>size[0]-inset[2]+.002f)throw new AssertionError("key outside safe bounds");
                }
                if(p.dx-p.dr-PadLayout.STICK_STROKE*.5f<inset[0]-.002f)throw new AssertionError("stick outside safe bounds");
                near(leftGap,rightGap,"painted edges must be equally distant");
                near(p.dx,mirrored.dx,"gap unchanged after rotation");
            }
            cases++;
        }
        System.out.println("CENTERING & FULL HEIGHT PASS: "+cases+" asymmetric-inset/display cases");
    }
}
