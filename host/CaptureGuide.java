package local.k98;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Offline authoring only. Uses controller input on the unmodified host core in its own practice home. */
public final class CaptureGuide {
    static final int W=304,H=168,TOP=44, A=PadLayout.A,START=PadLayout.START;
    static final int[] rgb=new int[640*512], wh=new int[2]; static final short[] pcm=new short[8192];
    static void tick(int keys){if(Bridge.frame(keys,rgb,pcm,wh)<0)throw new IllegalStateException("emulation stopped");}
    static void run(int keys,int n){while(n-->0)tick(keys);}
    static void press(int k){run(k,4);run(0,6);}
    static byte[] save(){byte[] s=new byte[Bridge.stateSize()];if(!Bridge.state(s,true))throw new IllegalStateException("capture state");return s;}
    static void restore(byte[] s){if(!Bridge.state(s,false))throw new IllegalStateException("restore state");}
    static BufferedImage picture(boolean full){int h=full?224:H;BufferedImage b=new BufferedImage(W,h,BufferedImage.TYPE_INT_RGB);b.setRGB(0,0,W,h,rgb,full?0:W*TOP,W);return b;}
    static byte[] menu(){for(int f=0;f<1115;f++)tick(f>=615&&f<621?START:(f>=720&&f<726||f>=737&&f<743)?PadLayout.UP:(f>=768&&f<774||f>=884&&f<889||f>=1000&&f<1006||f>=1070&&f<1076)?A:0);return save();}
    // Grid positions: 0..35 are four rows of nine; 36/37 are the two portraits in the bottom row.
    static void select(int cell,boolean alternate){int x=cell<36?cell%9:cell==36?3:5, y=cell<36?cell/9:4;
        for(int i=0;i<x;i++)press(PadLayout.RIGHT);for(int i=0;i<y;i++)press(PadLayout.DOWN);
        int mod=alternate?START:0;run(mod,8);run(mod|A,4);run(mod,4);run(0,74);press(A);run(0,68);press(A);run(0,450);
        // Confirm START in the training options, then wait for the HUD and controllable fighters.
        press(A);run(0,720); // Character intros have different lengths; always start after GO.
    }
    static final int[] CELLS={0,1,2,3,4,5,6,7,8,9,11,10,12,13,14,16,17,15,18,19,20,25,26,24,21,22,23,33,35,34,30,31,32,27,29,28,37,36,0,3,4,5,16,26,6,7,8,21,22,23,36};
    static int directions(int d){int x=MoveBook.X[d],y=MoveBook.Y[d];return (x<0?PadLayout.LEFT:x>0?PadLayout.RIGHT:0)|(y<0?PadLayout.UP:y>0?PadLayout.DOWN:0);}
    static String id(int n){return String.format(Locale.ROOT,"%02d",n);}
    static final byte[] frame=new byte[W*H*3];
    static void writeFrame(OutputStream dst)throws IOException{for(int i=0,j=0;i<W*H;i++){int v=rgb[TOP*W+i];frame[j++]=(byte)(v>>16);frame[j++]=(byte)(v>>8);frame[j++]=(byte)v;}dst.write(frame);}
    static void record(File dir,int fid,MoveBook.Fighter f,byte[] start,PrintWriter index)throws IOException{
        String poster="p"+id(fid==0?1:fid);index.println("@\t"+f.name+"\t"+poster);
        for(int j=0;j<f.moves.size();j++){MoveBook.Move m=f.moves.get(j);String clip="f"+id(fid)+"m"+id(j);
            boolean chain=m.note.equals("喷射反击命中后追加");
            restore(start);if(m.note.contains("近身")){run(PadLayout.RIGHT,94);run(0,8);}if(m.note.contains("空中")&&!m.note.contains("也可")){run(PadLayout.UP,6);run(0,2);}
            try(OutputStream out=new BufferedOutputStream(new FileOutputStream(new File(dir,clip+".rgb")),256*1024)){
                // Include the preceding move and hit window, rather than showing this follow-up from neutral.
                if(chain){run(PadLayout.RIGHT,18);run(0,6);for(char ch:"41236".toCharArray()){run(directions(ch-'0'),2);writeFrame(out);}run(PadLayout.RIGHT|A,2);writeFrame(out);for(int k=0;k<6;k++){run(0,2);writeFrame(out);}}
                List<MoveBook.Move.Beat> beats=m.beats(false);int t=0,samples=0,lastKeys=0;
                for(MoveBook.Move.Beat b:beats){t+=b.ms;int count=Math.max(1,Math.round(t/50f)-samples);samples+=count;
                    int ticks=b.ms>=800?68:b.keys!=0?(m.note.contains("连按")?3:13):beats.size()==1?16:b.dir==5?2:3;
                    int input=directions(b.dir)|b.keys;for(int it=1;it<=count;it++){run(input,(it*ticks)/count-((it-1)*ticks)/count);writeFrame(out);}if(b.keys!=0)lastKeys=b.keys;
                }
                boolean mash=m.note.contains("连续按键"),hold=m.note.contains("按住攻击键");int after=m.type.equals("超必杀")?68:40;
                for(int i=0;i<after;i++){run((mash&&i<16&&i%3==0)||(hold&&i<18)?lastKeys:0,3);writeFrame(out);}
            }index.println(m.name+"\t"+clip+(chain?"\t600":""));
        }index.flush();
    }
    static void contact(File dir,String name,List<BufferedImage> pics,List<String> labels,int cols,int cellW,int cellH)throws Exception{int rows=(pics.size()+cols-1)/cols;BufferedImage sheet=new BufferedImage(cols*cellW,rows*cellH,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setFont(new Font("Dialog",Font.PLAIN,13));for(int i=0;i<pics.size();i++){int x=(i%cols)*cellW,y=(i/cols)*cellH;g.drawImage(pics.get(i),x,y,cellW,cellH-16,null);g.setColor(Color.WHITE);g.drawString(labels.get(i),x+2,y+cellH-3);}g.dispose();ImageIO.write(sheet,"png",new File(dir,name));}
    public static void main(String[] args)throws Exception{File out=new File(args[1]);out.mkdirs();List<MoveBook.Fighter> fighters=MoveBook.read(new FileInputStream(args[2]));int limit=args.length>4?Integer.parseInt(args[4]):CELLS.length;
        try{if(Bridge.open(new File(args[0]).getCanonicalPath())!=0)throw new IOException("could not open isolated practice home");byte[] roster=menu();List<BufferedImage> pics=new ArrayList<>();List<String> labels=new ArrayList<>();
            try(PrintWriter index=new PrintWriter(new File(out,"media.tsv"),"UTF-8")){for(int i=0;i<limit;i++){restore(roster);select(CELLS[i],i>=38);if(wh[0]!=W||wh[1]!=224)throw new IOException("unexpected emulation geometry");run(PadLayout.RIGHT,48);run(0,10);BufferedImage full=picture(true);ImageIO.write(full,"png",new File(out,String.format(Locale.ROOT,"role-%02d.png",i+1)));pics.add(full);labels.add((i+1)+" "+fighters.get(i+1).name+" ("+CELLS[i]+")");
                if(args[3].equals("capture")){ImageIO.write(picture(false),"png",new File(out,"p"+id(i+1)+".png"));byte[] start=save();if(i==0)record(out,0,fighters.get(0),start,index);record(out,i+1,fighters.get(i+1),start,index);}
                if(i%6==0||i+1==limit)System.out.println("practice role "+(i+1)+"/"+limit);
            }}contact(out,"practice-roster.png",pics,labels,5,210,170);
        }finally{Bridge.close();}
    }
}
