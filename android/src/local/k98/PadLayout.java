package local.k98;

/** Geometry shared by drawing and touch input; independent of Android for replay tests. */
class PadLayout {
    static final int A=1, B=1<<8, C=1<<1, D=1<<9, START=1<<3, COIN=1<<2;
    static final int UP=1<<4, DOWN=1<<5, LEFT=1<<6, RIGHT=1<<7;
    static final int DIRECTIONS=UP|DOWN|LEFT|RIGHT;
    static final int MAX_KEYS=A|B|C;
    static final int[] BITS={A,B,C,D};
    final int width,height;
    final float videoLeft,videoTop,videoRight,videoBottom;
    final float dx,dy,dr,br,half,menuTop,menuBottom,startX;
    final boolean arcade;
    final float coinTop,coinBottom;
    final float[] bx=new float[4],by=new float[4];
    final float[] cx=new float[ControlProfile.COUNT],cy=cx.clone(),rx=cx.clone(),ry=cx.clone();
    final int safeX,safeY,safeWidth,safeHeight;
    final float gearLeft,gearBottom;
    static final int STICK_STROKE=2;
    private static final float KEY_HIT_SQUARED=1.39f, CHORD_HIT_SQUARED=2.25f, SINGLE_INNER_SQUARED=.5625f;
    static int keyStroke(float radius){return Math.max(2,(int)(radius*.07f));}

    PadLayout(int w,int h) {
        this(w,h,false);
    }
    PadLayout(int w,int h,boolean arcade) {
        this(w,h,arcade,DisplayOptions.DEFAULT,0,0,0,0);
    }
    PadLayout(int w,int h,boolean arcade,DisplayOptions options,
              int insetLeft,int insetTop,int insetRight,int insetBottom) {
        this(w,h,arcade,options,insetLeft,insetTop,insetRight,insetBottom,Math.round(h*.16f));
    }
    PadLayout(int w,int h,boolean arcade,DisplayOptions options,
              int insetLeft,int insetTop,int insetRight,int insetBottom,int gearExtent) {
        this.arcade=arcade;
        width=w;height=h;
        int safeW=Math.max(1,w-insetLeft-insetRight);
        int safeH=Math.max(1,h-insetTop-insetBottom);
        safeX=insetLeft;safeY=insetTop;safeWidth=safeW;safeHeight=safeH;
        gearLeft=insetLeft+safeW-gearExtent;gearBottom=insetTop+gearExtent;
        float classicWidth=Math.min(4f/3*safeH,safeW-2*Math.min(.35f*safeH,.16f*safeW));
        float lane=(safeW-classicWidth)*.5f;
        // Fit video to the whole view. Insets and control lanes must not shrink it.
        // Start from the limiting dimension so full-height edges stay exactly 0 and h.
        float pictureWidth=w,pictureHeight=h;
        if (!options.fullscreen || options.keepAspect) {
            if (w*3L>=h*4L) pictureWidth=h*4f/3;
            else pictureHeight=w*.75f;
        }
        videoLeft=(w-pictureWidth)*.5f;videoTop=(h-pictureHeight)*.5f;
        videoRight=w-videoLeft;videoBottom=h-videoTop;
        float stickX=insetLeft+lane*.5f,stickY=insetTop+safeH*.68f,rightX=w-insetRight-lane*.5f;
        float menuLeftX=stickX,menuRightX=rightX;
        float stickRadius=Math.min(lane*.446f,safeH*.187f);
        float spread=Math.min(lane*.223f,safeH*.098f);br=spread*.87f;
        if(!options.fullscreen || options.keepAspect){
            // Split the spare space on the tighter side. If the controls cannot
            // fit outside the picture, allow equal overlap without shrinking them.
            float stickReach=stickRadius+STICK_STROKE*.5f;
            float keysReach=spread+br+keyStroke(br)*.5f;
            float side=videoLeft-Math.max(insetLeft,insetRight);
            float spare=side-2f*Math.max(stickReach,keysReach);
            float gap=spare>=0?spare*.5f:spare;
            stickX=videoLeft-gap-stickReach;
            rightX=videoRight+gap+keysReach;
        }
        slot(ControlProfile.STICK,stickX,stickY,stickRadius,stickRadius,options.profile);
        dx=cx[0];dy=cy[0];dr=rx[0];
        for(int i=0;i<4;i++) {
            int id=ControlProfile.FIRST_KEY+i;
            slot(id,rightX+((i&1)==0?-spread:spread),stickY+((i&2)==0?spread:-spread)-((i&1)!=0?spread*.16f:0),br,br,options.profile);
            bx[i]=cx[id];by[i]=cy[id];
        }
        float hw=Math.min(lane*.39f,safeH*.126f),hh=safeH*.056f,menuY=insetTop+safeH*.235f;
        slot(ControlProfile.PAUSE,menuLeftX,menuY,hw,hh,options.profile);
        slot(ControlProfile.START,menuRightX,menuY,hw,hh,options.profile);
        slot(ControlProfile.COIN,menuLeftX,insetTop+safeH*.377f,hw,safeH*.055f,options.profile);
        slot(ControlProfile.MAX_KEY,rightX,insetTop+safeH*.40f,br*.68f,br*.68f,options.profile);
        if(!options.profile.positioned(ControlProfile.MAX_KEY))placeDefaultMax();
        int s=ControlProfile.START,k=ControlProfile.COIN;
        half=rx[s];startX=cx[s];menuTop=cy[s]-ry[s];menuBottom=cy[s]+ry[s];coinTop=cy[k]-ry[k];coinBottom=cy[k]+ry[k];
    }
    private void slot(int id,float autoX,float autoY,float baseX,float baseY,ControlProfile profile){
        float factor=profile.size(id)/100f;
        float a=rx[id]=Math.min(baseX*factor,safeWidth*.49f),b=ry[id]=Math.min(baseY*factor,safeHeight*.49f);
        float x=profile.positioned(id)?safeX+profile.x(id)*safeWidth:autoX;
        float y=profile.positioned(id)?safeY+profile.y(id)*safeHeight:autoY;
        x=Math.max(safeX+a,Math.min(safeX+safeWidth-a,x));y=Math.max(safeY+b,Math.min(safeY+safeHeight-b,y));
        // Keep the settings gear reachable, including on displays with a camera cutout.
        if((profile.positioned(id)||profile.size(id)!=100)&&x+a>gearLeft&&y-b<gearBottom){
            if(x+a-gearLeft<gearBottom-(y-b))x=gearLeft-a;else y=gearBottom+b;
        }
        cx[id]=x;cy[id]=y;
    }
    boolean visible(int id){return id!=ControlProfile.COIN||arcade;}
    private void placeDefaultMax(){
        int id=ControlProfile.MAX_KEY;float wantedX=cx[id],wantedY=cy[id],r=rx[id];
        if(maxFits(wantedX,wantedY))return;
        float best=Float.MAX_VALUE;
        // An upgraded custom layout may already occupy the new default location.
        // Find nearby free space without moving any of the user's existing controls.
        for(int row=0;row<=24;row++)for(int col=0;col<=32;col++){
            float x=safeX+r+(safeWidth-2*r)*col/32f,y=safeY+r+(safeHeight-2*r)*row/24f;
            float distance=(x-wantedX)*(x-wantedX)+(y-wantedY)*(y-wantedY);
            if(distance<best&&maxFits(x,y)){best=distance;cx[id]=x;cy[id]=y;}
        }
    }
    private boolean maxFits(float x,float y){
        float r=rx[ControlProfile.MAX_KEY]+br*.12f;
        if(x+r>gearLeft&&y-r<gearBottom)return false;
        for(int id=0;id<ControlProfile.MAX_KEY;id++)if(visible(id)){
            float vx=Math.abs(x-cx[id]),vy=Math.abs(y-cy[id]);
            if(id<ControlProfile.PAUSE){
                float reach=r+rx[id]*(id==ControlProfile.STICK?1.23f:1.5f);
                if(vx*vx+vy*vy<reach*reach)return false;
            }else{
                vx=Math.max(0,vx-rx[id]);vy=Math.max(0,vy-ry[id]);
                if(vx*vx+vy*vy<r*r)return false;
            }
        }
        return true;
    }
    boolean containsSlot(int id,float x,float y){
        float u=Math.abs(x-cx[id])/rx[id],v=Math.abs(y-cy[id])/ry[id];
        return visible(id)&&((id<ControlProfile.PAUSE||id==ControlProfile.MAX_KEY)?u*u+v*v<=1:u<=1&&v<=1);
    }
    int pickSlot(float x,float y){
        int pick=-1;float best=Float.MAX_VALUE;
        for(int i=0;i<cx.length;i++)if(containsSlot(i,x,y)){
            float u=(x-cx[i])/rx[i],v=(y-cy[i])/ry[i],score=u*u+v*v;if(score<best){best=score;pick=i;}
        }return pick;
    }
    // A little overlap at the rims is fine, but never let a control lose its centre to another.
    boolean obstructed(int i){
        if(!visible(i))return false;
        for(int j=0;j<cx.length;j++)if(i!=j&&visible(j)){
            if(containsSlot(i,cx[j],cy[j])||containsSlot(j,cx[i],cy[i]))return true;
            if((i==ControlProfile.STICK&&startsStick(cx[j],cy[j]))||(j==ControlProfile.STICK&&startsStick(cx[i],cy[i])))return true;
        }return false;
    }
    boolean startsStick(float x,float y) {
        float vx=x-dx,vy=y-dy;
        return vx*vx+vy*vy<=dr*dr*1.23f*1.23f;
    }
    boolean startsGame(float x,float y) {
        return x>=startX-half&&x<startX+half&&y>=menuTop&&y<menuBottom;
    }
    boolean insertsCoin(float x,float y) {
        return containsSlot(ControlProfile.COIN,x,y);
    }
    boolean pressesMax(float x,float y) {
        return containsSlot(ControlProfile.MAX_KEY,x,y);
    }
    int fireAt(float x,float y) {
        int chord=0,pick=-1;float best=Float.MAX_VALUE;
        for(int i=0;i<4;i++) {
            float vx=x-bx[i],vy=y-by[i],r=rx[ControlProfile.FIRST_KEY+i],dist=(vx*vx+vy*vy)/(r*r);
            if(dist<CHORD_HIT_SQUARED)chord|=BITS[i];
            if(dist<best){best=dist;pick=i;}
        }
        // The former shared rim was only 2-5 px wide in the default layouts.
        // Broaden chords without enlarging the outside edge of a lone button.
        // The inner 75% radius remains single-key, including unequal custom sizes.
        if(best<=SINGLE_INNER_SQUARED)return BITS[pick];
        if(Integer.bitCount(chord)>=2)return chord;
        return best<KEY_HIT_SQUARED?BITS[pick]:0;
    }
}
