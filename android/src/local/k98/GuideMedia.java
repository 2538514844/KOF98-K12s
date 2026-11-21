package local.k98;

import android.content.res.AssetManager;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Match by names, not by list position: sorting/filtering the guide cannot silently change the recording. */
final class GuideMedia {
    static final String DIR="guide/scenes/";
    static final class Role {
        final String poster;final Map<String,String> moves=new HashMap<>();final Map<String,Integer> lead=new HashMap<>();
        Role(String id){poster=DIR+id+".webp";}
    }
    private final Map<String,Role> roles=new HashMap<>();
    GuideMedia(AssetManager assets)throws IOException{
        try(BufferedReader r=new BufferedReader(new InputStreamReader(assets.open("guide/media.tsv"),StandardCharsets.UTF_8))){
            String line;Role role=null;while((line=r.readLine())!=null){String[] v=line.split("\t",-1);
                if(v.length==3&&v[0].equals("@")&&v[2].matches("p[0-9]{2}")){role=new Role(v[2]);roles.put(v[1],role);}
                else if(role!=null&&(v.length==2||v.length==3)&&v[1].matches("f[0-9]{2}m[0-9]{2}")){
                    role.moves.put(v[0],DIR+v[1]+".mp4");if(v.length==3){if(!v[2].matches("[0-9]{1,4}"))throw new IOException("回放时间错误");role.lead.put(v[0],Integer.parseInt(v[2]));}}
                else throw new IOException("练习回放索引格式错误");
            }
        }
    }
    Role get(MoveBook.Fighter f){return roles.get(f.name);}
}
