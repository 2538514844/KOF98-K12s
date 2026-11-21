package local.k98;

import java.util.Random;

/** UI-thread state machine; tokens reject callbacks from released decoders. */
final class FightPlayback {
    interface Driver {
        void prepare(int clip,int position,long token);
        void start();
        int position();
        void release();
        void reveal();
    }
    private static final int IDLE=0,PREPARING=1,PLAYING=2;
    private final Driver driver;
    private final Random random;
    private final int[] order={0,1,2,3,4,5};
    private int index,state,resumeAt,failures;
    private boolean active,surface,disposed;
    private long generation;
    FightPlayback(Driver driver,Random random){this.driver=driver;this.random=random;shuffle(-1);}
    private void shuffle(int previous){
        for(int i=order.length-1;i>0;i--){int j=random.nextInt(i+1),v=order[i];order[i]=order[j];order[j]=v;}
        if(order[0]==previous){int v=order[0];order[0]=order[1];order[1]=v;}
        index=0;
    }
    private void next(){int previous=order[index];if(++index==order.length)shuffle(previous);resumeAt=0;}
    void active(boolean value){if(disposed||active==value)return;active=value;if(value)open();else stop(true);}
    void surface(boolean value){if(disposed||surface==value)return;surface=value;if(value)open();else stop(true);}
    void dispose(){if(disposed)return;disposed=true;active=false;stop(false);}
    private boolean current(long token){return !disposed&&active&&surface&&state!=IDLE&&token==generation;}
    private void open(){
        if(disposed||!active||!surface||state!=IDLE||failures>=order.length)return;
        state=PREPARING;long token=++generation;
        try{driver.prepare(order[index],resumeAt,token);}catch(RuntimeException ex){failed(token);}
    }
    private void stop(boolean remember){
        if(remember&&state==PLAYING){try{resumeAt=Math.max(0,driver.position());}catch(RuntimeException ignored){}}
        state=IDLE;++generation;
        try{driver.release();}catch(RuntimeException ignored){}
    }
    void prepared(long token){
        if(!current(token)||state!=PREPARING)return;
        state=PLAYING;try{driver.start();}catch(RuntimeException ex){failed(token);}
    }
    void firstFrame(long token){if(!current(token)||state!=PLAYING)return;failures=0;driver.reveal();}
    void completed(long token){if(!current(token)||state!=PLAYING)return;stop(false);next();open();}
    void failed(long token){if(!current(token))return;stop(false);failures++;next();open();}
}
