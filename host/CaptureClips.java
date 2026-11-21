package local.k98;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Real local-core frames used by the bundled loading animation. */
public final class CaptureClips {
    static final int WIDTH=304, TOP=32, HEIGHT=168, COUNT=300, STRIDE=2;
    static int[] pixels=new int[640*512],wh=new int[2];
    static short[] audio=new short[8192];
    static void frame(int keys)throws Exception {
        if(Bridge.frame(keys,pixels,audio,wh)<0)throw new IOException("Core stopped");
    }
    static BufferedImage shot(){
        BufferedImage b=new BufferedImage(304,224,BufferedImage.TYPE_INT_RGB);
        b.setRGB(0,0,304,224,pixels,0,304);return b;
    }
    static int keys(int f,int variant){
        if(f<880)return DeviceFlowTest.keys(f);
        int[][] moves={ {},{128},{128,128,128},{128,128,128,128,128,128},{32,128,128,128},{32,32,32} };
        if(f<990){int n=(f-900)/8;return f>=900&&n<moves[variant].length&&(f-900)%8<4?moves[variant][n]:0;}
        if(f<1100)return ((f>=1000&&f<1005)||(f>=1040&&f<1045)||(f>=1080&&f<1085))?1:0;
        if(f<1820)return f%39<5?1:0;
        int t=f-1820;
        if(t<36)return 128;
        int cycle=t%180;
        if(cycle<26)return 128;
        if(cycle<36)return 32;
        if(cycle<46)return 32|128;
        if(cycle<58)return 128|256;
        if(cycle<72)return 0;
        if(cycle<96)return 16|128;
        return (t%18<7?new int[]{1,2,256,512}[(t/18+variant)%4]:0);
    }
    public static void main(String[] args)throws Exception {
        File output=new File(args[1]);output.mkdirs();
        try {
            if(Bridge.open(new File(args[0]).getCanonicalPath())!=0)throw new IOException("Core open failed");
            if(args[2].equals("scout")){
                BufferedImage sheet=new BufferedImage(304*4,248*6,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=sheet.createGraphics();g.setFont(new Font("SansSerif",Font.PLAIN,15));
                int sample=0;
                for(int f=0;f<=1980;f++){
                    frame(DeviceFlowTest.keys(f));
                    if(f>=600&&f%60==0){int x=(sample%4)*304,y=(sample/4)*248;g.drawImage(shot(),x,y,null);g.setColor(Color.WHITE);g.drawString("frame "+f,x+4,y+242);sample++;}
                }
                g.dispose();ImageIO.write(sheet,"png",new File(output,"selection-timeline.png"));
                System.out.println("Scouted "+sample+" frames");
            } else {
                boolean capture=args[2].equals("capture");
                BufferedImage sheet=new BufferedImage(304*6,248*3,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=sheet.createGraphics();g.setFont(new Font("SansSerif",Font.PLAIN,15));
                for(int v=0;v<6;v++){
                    if(v>0){Bridge.close();if(Bridge.open(new File(args[0]).getCanonicalPath())!=0)throw new IOException("Reopen failed");}
                    OutputStream raw=capture?new BufferedOutputStream(new FileOutputStream(new File(output,"fight-"+(v+1)+".rgb")),1024*1024):null;
                    byte[] row=new byte[WIDTH*HEIGHT*3];
                    int first=1900;
                    try {
                        for(int f=0;f<first+COUNT*STRIDE;f++){
                            frame(keys(f,v));
                            int s=f==1020?0:f==1900?1:f==2140?2:-1;
                            if(s>=0){g.drawImage(shot(),v*304,s*248,null);g.setColor(Color.WHITE);g.drawString("clip "+(v+1)+" / frame "+f,v*304+4,s*248+242);}
                            if(raw!=null&&f>=first&&(f-first)%STRIDE==0){
                                if(wh[0]!=WIDTH||wh[1]!=224)throw new IOException("Geometry changed");
                                for(int i=0;i<WIDTH*HEIGHT;i++){int rgb=pixels[TOP*WIDTH+i];row[i*3]=(byte)(rgb>>16);row[i*3+1]=(byte)(rgb>>8);row[i*3+2]=(byte)rgb;}
                                raw.write(row);
                            }
                        }
                    }finally{if(raw!=null)raw.close();}
                    System.out.println("clip "+(v+1)+": "+COUNT+" samples, sourceFPS="+Bridge.fps()+", stride="+STRIDE);
                }
                g.dispose();ImageIO.write(sheet,"png",new File(output,"six-fights-contact.png"));
            }
        } finally {Bridge.close();}
    }
}
