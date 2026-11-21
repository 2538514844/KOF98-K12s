package local.k98;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Captures local gameplay for the separate loading-screen design preview. */
public final class CapturePreview {
    public static void main(String[] args)throws Exception{
        File output=new File(args[1]);output.mkdirs();
        int[] rgb=new int[640*512],wh=new int[2];short[] pcm=new short[8192];
        final int first=1680,count=240,stride=2,cols=12;
        BufferedImage atlas=new BufferedImage(304*cols,224*((count+cols-1)/cols),BufferedImage.TYPE_INT_RGB);
        try{
            int rc=Bridge.open(new File(args[0]).getCanonicalPath());
            if(rc!=0)throw new IllegalStateException("open="+rc);
            for(int f=0;f<first+count*stride;f++){
                if(Bridge.frame(DeviceFlowTest.keys(f),rgb,pcm,wh)<0)throw new IllegalStateException("frame");
                if(f>=first&&(f-first)%stride==0){
                    if(wh[0]!=304||wh[1]!=224)throw new IllegalStateException("unexpected geometry");
                    int i=(f-first)/stride;
                    atlas.setRGB((i%cols)*304,(i/cols)*224,304,224,rgb,0,304);
                }
            }
            ImageIO.write(atlas,"png",new File(output,"fight-atlas.png"));
            System.out.println("Captured "+count+" real gameplay frames; "+(count*stride/Bridge.fps())+" seconds; "+cols+" columns; 304x224 tiles");
        }finally{Bridge.close();}
    }
}
