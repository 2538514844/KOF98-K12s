package local.k98;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline, original Neo Geo instructions. The two readings describe identical inputs. */
final class MoveBook {
    static final String[] DIR={"","↙","↓","↘","←","","→","↖","↑","↗"};
    static final String[] WORD={"","左下","下","右下","左","","右","左上","上","右上"};
    private static final int[] MIRROR={0,3,2,1,6,5,4,9,8,7};
    static final int[] X={0,-1,0,1,-1,0,1,-1,0,1}, Y={0,1,1,1,0,0,0,-1,-1,-1};
    static final class Fighter {
        final String group,name;final List<Move> moves=new ArrayList<>();
        Fighter(String g,String n){group=g;name=n;}
        public String toString(){return group+" · "+name;}
    }
    static final class Move {
        final String type,name,dirs,keys,note;
        Move(String[] row){type=row[0];name=row[1];dirs=row[2];keys=row[3];note=row[4];}
        int mapped(char c,boolean left){int n=c-'0';return left?MIRROR[n]:n;}
        String format(boolean left,boolean words){
            StringBuilder s=new StringBuilder();
            for(int i=0;i<dirs.length();i++){
                char c=dirs.charAt(i);if(c=='~'){s.append(words?"（按住蓄力）":"〔蓄〕");continue;}
                if(words&&i>0)s.append("、");int n=mapped(c,left);s.append(words?WORD[n]:DIR[n]);
            }
            if(!keys.equals("-")){
                if(s.length()>0)s.append(words?"，同时按 ":" ＋ ");else if(words)s.append("按 ");
                s.append(words?keys.replace("/"," 或 ").replace("+"," 和 "):keys.replace("+","＋"));
            }
            return s.toString();
        }
        static final class Beat {
            final int dir,keys,ms;Beat(int d,int k,int t){dir=d;keys=k;ms=t;}
        }
        List<Beat> beats(boolean left){
            List<Beat> a=new ArrayList<>();int last=5,k=0;
            for(int i=0;i<dirs.length();i++)if(dirs.charAt(i)!='~'){
                int n=mapped(dirs.charAt(i),left);if(n==last)a.add(new Beat(5,0,140));
                a.add(new Beat(n,0,(i+1<dirs.length()&&dirs.charAt(i+1)=='~')?820:285));last=n;
            }
            if(!keys.equals("-")){
                String example=keys.split("/",2)[0];for(int i=0;i<example.length();i++){
                    int key=example.charAt(i)-'A';if(key>=0&&key<4)k|=PadLayout.BITS[key];
                }
                a.add(new Beat(last,k,370));
                if(note.contains("连续按键")||note.contains("连按两次")){
                    a.add(new Beat(last,0,155));a.add(new Beat(last,k,300));
                }
            }
            return a;
        }
    }
    static List<Fighter> read(InputStream in)throws IOException {
        List<Fighter> fighters=new ArrayList<>();Fighter current=null;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){
            String line;int n=0;while((line=reader.readLine())!=null){n++;if(line.isEmpty())continue;
                String[] p=line.split("\t",-1);
                if(p.length==3&&p[0].equals("@")&&!p[1].isEmpty()&&!p[2].isEmpty()){current=new Fighter(p[1],p[2]);fighters.add(current);}
                else if(current!=null&&p.length==5&&!p[0].isEmpty()&&!p[1].isEmpty()&&p[2].matches("[12346789]*(~[12346789]+)?")&&p[3].matches("-|[ABCD]([/+][ABCD])*"))current.moves.add(new Move(p));
                else throw new IOException("出招表第 "+n+" 行格式错误");
            }
        }
        if(fighters.isEmpty())throw new IOException("出招表为空");for(Fighter f:fighters)if(f.moves.isEmpty())throw new IOException("角色没有招式："+f.name);
        return fighters;
    }
}

