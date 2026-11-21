package local.k98;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.zip.*;

final class DataDepot {
    private static final String[] names={"roms/kof98.zip","roms/neogeo.zip","system/fbneo/patched/neogeo.zip"};
    private static final int[] lengths={41697980,1094595,38056};
    private static final String[] hashes={"a291a96ac94e9777497daed69cd186bc4b84f9b7d4ab0ce45b77791d1002b50b","213bbfacbace63c57e631ac33cc129816d1649bb4020e01c7f2fec686fcadd18","e421b42c3744e11725d08ce6cca01d86c3a1ae5e844fa6082c5e31582c42b4a9"};
    interface Monitor { void progress(int percent); }
    static File root(File home){return new File(home,"game");}
    static boolean ready(File home){File base=root(home);for(int i=0;i<names.length;i++){File f=new File(base,names[i]);if(!f.isFile()||f.length()!=lengths[i])return false;}return true;}
    private static void mkdir(File f) throws IOException {if(!f.isDirectory()&&!f.mkdirs())throw new IOException("无法建立游戏目录，请检查剩余空间。");}
    private static void clear(File f) throws IOException {if(!f.exists())return;if(f.isDirectory()){File[] all=f.listFiles();if(all==null)throw new IOException("无法读取临时目录。");for(File c:all)clear(c);}if(!f.delete())throw new IOException("无法清理临时文件。");}
    private static MessageDigest digest(){try{return MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private static String hex(byte[] b){char[] digits="0123456789abcdef".toCharArray(),s=new char[2*b.length];for(int j=0;j<b.length;j++){int n=b[j]&255;s[2*j]=digits[n>>>4];s[2*j+1]=digits[n&15];}return new String(s);}
    static synchronized void install(File home,InputStream source,Monitor monitor) throws IOException {
        File stage=new File(home,"importing"),base=root(home);boolean[] seen=new boolean[names.length];int total=0,last=-1;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(source,65536))){
            clear(stage);mkdir(stage);byte[] buf=new byte[65536];ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){int i=Arrays.asList(names).indexOf(entry.getName());
                if(i<0||seen[i]||entry.isDirectory())throw new IOException("请选择完整的 KOF98-data.zip，不要选择内层 ROM 或其他压缩包。");
                if(entry.getSize()>=0&&entry.getSize()!=lengths[i])throw new IOException("数据长度不正确："+names[i]);
                File dest=new File(stage,names[i]);mkdir(dest.getParentFile());MessageDigest md=digest();int bytes=0,n;
                try(FileOutputStream out=new FileOutputStream(dest)){while((n=zip.read(buf))!=-1){if(n==0)continue;if(n>lengths[i]-bytes)throw new IOException("游戏文件超出预期大小。");out.write(buf,0,n);md.update(buf,0,n);bytes+=n;total+=n;int pct=(int)(100L*total/42830631L);if(last!=pct&&monitor!=null){monitor.progress(pct);last=pct;}}out.getFD().sync();}
                if(bytes!=lengths[i]||!hashes[i].equals(hex(md.digest())))throw new IOException("内容校验失败："+names[i]+"。请重新传输数据包。");
                seen[i]=true;zip.closeEntry();
            }
            for(boolean ok:seen)if(!ok)throw new IOException("数据包不完整。请选择 KOF98-data.zip。");
            mkdir(base);mkdir(new File(base,"save"));
            for(String name:names){File out=new File(base,name);mkdir(out.getParentFile());Files.move(new File(stage,name).toPath(),out.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        }finally{clear(stage);}
    }
}
