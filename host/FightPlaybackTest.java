package local.k98;

import java.util.*;

public final class FightPlaybackTest {
    static int assertions,cases;
    static void check(boolean yes,String message){assertions++;if(!yes)throw new AssertionError(message);}
    static final class Fake implements FightPlayback.Driver {
        long token;int clip,seek,prepares,starts,releases,reveals;boolean live,playing,failPrepare,failStart;
        public void prepare(int c,int p,long t){check(!live,"two decoders allocated");live=true;playing=false;clip=c;seek=p;token=t;prepares++;if(failPrepare)throw new IllegalStateException("asset unavailable");}
        public void start(){check(live&&!playing,"start in invalid state");if(failStart)throw new IllegalStateException("decoder start failed");playing=true;starts++;}
        public int position(){check(live&&playing,"position read outside playback");return 4321;}
        public void release(){if(live){live=false;playing=false;releases++;}}
        public void reveal(){check(live&&playing,"stale decoder revealed");reveals++;}
    }
    static FightPlayback model(Fake d){return new FightPlayback(d,new Random(98));}
    static void open(FightPlayback p){p.active(true);p.surface(true);}
    static void finish(String name){cases++;System.out.println("PASS "+name);}
    public static void main(String[] args){
        Fake d=new Fake();FightPlayback p=model(d);p.surface(true);check(d.prepares==0,"started offscreen");p.active(true);check(d.prepares==1&&d.starts==0,"must prepare async");p.prepared(d.token);p.prepared(d.token);check(d.starts==1,"double prepared callback");p.dispose();check(!d.live,"dispose leaked");finish("visibility-gated async preparation");

        d=new Fake();p=model(d);open(p);long old=d.token;p.active(false);p.prepared(old);p.firstFrame(old);p.completed(old);p.failed(old);check(d.starts==0&&d.prepares==1&&!d.live,"late paused callback");p.active(true);check(d.token!=old&&d.prepares==2,"generation did not change");p.prepared(old);check(d.starts==0,"old callback started replacement");p.prepared(d.token);check(d.starts==1,"replacement did not start");p.dispose();finish("pause during prepare / stale callbacks");

        d=new Fake();p=model(d);open(p);p.prepared(d.token);int clip=d.clip;p.active(false);check(!d.live,"background decoder retained");p.active(true);check(d.clip==clip&&d.seek==4321,"resume lost current clip/time");p.prepared(d.token);p.surface(false);check(!d.live,"surface loss retained player");p.surface(true);check(d.clip==clip&&d.seek==4321,"surface recreation lost position");p.dispose();finish("pause and surface recreation preserve position");

        d=new Fake();p=model(d);open(p);int previous=-1;
        for(int round=0;round<30;round++){Set<Integer> unique=new HashSet<>();for(int i=0;i<6;i++){check(d.clip!=previous,"adjacent repeat");unique.add(d.clip);previous=d.clip;p.prepared(d.token);p.firstFrame(d.token);old=d.token;p.completed(old);p.completed(old);check(d.seek==0,"next clip resumed wrong time");}check(unique.size()==6,"missing clip in shuffle round");}
        p.dispose();finish("all six clips in each shuffled round / no boundary repeat");

        d=new Fake();p=model(d);open(p);for(int i=0;i<6;i++)p.failed(d.token);check(d.prepares==6&&!d.live,"failed assets retry forever");p.active(false);p.active(true);check(d.prepares==6,"fallback retries without bound");p.dispose();finish("six decoder failures fall back without loop");

        d=new Fake();d.failPrepare=true;p=model(d);open(p);check(d.prepares==6&&d.releases==6&&!d.live,"synchronous setup failure leaked/recurred");p.dispose();finish("synchronous preparation exception cleanup");

        d=new Fake();d.failStart=true;p=model(d);open(p);for(int i=0;i<6;i++)p.prepared(d.token);check(d.prepares==6&&d.starts==0&&!d.live,"start failure cleanup");p.dispose();finish("start exception cleanup");

        d=new Fake();p=model(d);open(p);for(int i=0;i<5;i++)p.failed(d.token);p.prepared(d.token);p.firstFrame(d.token);p.completed(d.token);check(d.prepares==7&&d.live,"success did not reset error budget");p.dispose();int count=d.prepares;old=d.token;p.active(true);p.surface(true);p.prepared(old);p.firstFrame(old);p.failed(old);p.completed(old);check(d.prepares==count&&!d.live,"disposed controller reopened");finish("success resets failures / terminal disposal");

        Random r=new Random(20261121);
        for(int n=0;n<200;n++){
            d=new Fake();p=new FightPlayback(d,new Random(n));open(p);
            for(int i=0;i<300;i++){
                long token=r.nextBoolean()?d.token:d.token-2;
                switch(r.nextInt(8)){case 0:p.active(false);break;case 1:p.active(true);break;case 2:p.surface(false);break;case 3:p.surface(true);break;case 4:p.prepared(token);break;case 5:p.firstFrame(token);break;case 6:p.completed(token);break;case 7:p.failed(token);break;}
                check(d.prepares-d.releases==(d.live?1:0),"resource balance");
            }
            p.dispose();check(d.prepares==d.releases,"fuzz leaked player");
        }
        finish("60000 reordered lifecycle/callback events");
        System.out.println("PLAYBACK PASS cases="+cases+" assertions="+assertions);
    }
}
