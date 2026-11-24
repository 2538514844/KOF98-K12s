package local.k98;

import android.graphics.*;

final class Controls extends PadLayout {
    static final String[] LABEL={"A","B","C","D"};
    static final int[] COLORS={0xffed6158,0xffe7bc53,0xff60b88d,0xff6da7df};
    final RectF video,pause,start,coin;
    private final RectF leftBounds,rightBounds;
    final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private static final int INK=0xff121517,EDGE=0xff475054,TYPE=0xffdce2de;
    Controls(int w,int h,boolean arcade,DisplayOptions options,int left,int top,int right,int bottom){
        this(w,h,arcade,options,left,top,right,bottom,Math.round(h*.16f));
    }
    Controls(int w,int h,boolean arcade,DisplayOptions options,int left,int top,int right,int bottom,int gearExtent){
        super(w,h,arcade,options,left,top,right,bottom,gearExtent);
        video=new RectF(videoLeft,videoTop,videoRight,videoBottom);
        pause=rect(ControlProfile.PAUSE);start=rect(ControlProfile.START);coin=rect(ControlProfile.COIN);
        float margin=Math.max(4,br*.06f);
        leftBounds=new RectF(pause);
        leftBounds.union(dx-dr,dy-dr,dx+dr,dy+dr);
        if(arcade)leftBounds.union(coin);
        rightBounds=new RectF(start);
        for(int i=0;i<4;i++)rightBounds.union(rect(i+1));
        rightBounds.union(rect(ControlProfile.MAX_KEY));
        leftBounds.inset(-margin,-margin);
        rightBounds.inset(-margin,-margin);
        p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD));
    }
    private void fill(int color){p.setStyle(Paint.Style.FILL);p.setColor(color);}
    private RectF rect(int id){return new RectF(cx[id]-rx[id],cy[id]-ry[id],cx[id]+rx[id],cy[id]+ry[id]);}
    private void ring(Canvas c,float x,float y,float r,int stroke,int color){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setColor(color);c.drawCircle(x,y,r,p);p.setStyle(Paint.Style.FILL);}
    private void text(Canvas c,String s,float x,float y,float sz,int color){fill(color);p.setTextSize(sz);c.drawText(s,x,y-(p.ascent()+p.descent())*.5f,p);}
    void draw(Canvas c,int keys,float thumbX,float thumbY,int alpha){
        if(alpha==0)return;
        // Blend each control group as a unit so overlapping strokes have uniform opacity.
        if(alpha==255){drawLeft(c,keys,thumbX,thumbY);drawRight(c,keys);return;}
        int layer=c.saveLayerAlpha(leftBounds,alpha);
        drawLeft(c,keys,thumbX,thumbY);
        c.restoreToCount(layer);
        layer=c.saveLayerAlpha(rightBounds,alpha);
        drawRight(c,keys);
        c.restoreToCount(layer);
    }
    // The guide needs the user's stick and four action keys, not unrelated pause/START/coin icons.
    void drawGuide(Canvas c,int keys,float x,float y){
        int l=c.saveLayerAlpha(leftBounds,(keys&DIRECTIONS)==0?145:225);drawLeft(c,keys,x,y,false);c.restoreToCount(l);
        int r=c.saveLayerAlpha(rightBounds,(keys&(A|B|C|D))==0?168:238);drawRight(c,keys,false);c.restoreToCount(r);
    }
    private void drawLeft(Canvas c,int keys,float thumbX,float thumbY){drawLeft(c,keys,thumbX,thumbY,true);}
    private void drawLeft(Canvas c,int keys,float thumbX,float thumbY,boolean menu){
        fill(0xff202528);c.drawCircle(dx,dy,dr,p);ring(c,dx,dy,dr,STICK_STROKE,EDGE);
        float thick=dr*.27f;fill(0xff31383b);c.drawRoundRect(dx-thick,dy-dr*.89f,dx+thick,dy+dr*.89f,thick*.32f,thick*.32f,p);c.drawRoundRect(dx-dr*.89f,dy-thick,dx+dr*.89f,dy+thick,thick*.32f,thick*.32f,p);
        text(c,"▲",dx,dy-dr*.63f,thick,(keys&UP)!=0?0xffffd663:TYPE);text(c,"▼",dx,dy+dr*.63f,thick,(keys&DOWN)!=0?0xffffd663:TYPE);text(c,"◀",dx-dr*.63f,dy,thick,(keys&LEFT)!=0?0xffffd663:TYPE);text(c,"▶",dx+dr*.63f,dy,thick,(keys&RIGHT)!=0?0xffffd663:TYPE);
        fill((keys&DIRECTIONS)!=0?0xffe3c461:0xff596266);c.drawCircle(dx+thumbX*dr*.52f,dy+thumbY*dr*.52f,dr*.22f,p);
        if(!menu)return;
        fill(0xff282d2f);c.drawRoundRect(pause,pause.height()*.18f,pause.height()*.18f,p);float r=pause.height()*.2f,x=pause.centerX(),y=pause.centerY();fill(TYPE);c.drawRect(x-r*.7f,y-r,x-r*.16f,y+r,p);c.drawRect(x+r*.16f,y-r,x+r*.7f,y+r,p);
        if(arcade){
            boolean held=(keys&COIN)!=0;
            fill(held?0xfff8d567:0xff35342d);
            c.drawRoundRect(coin,coin.height()*.18f,coin.height()*.18f,p);
            text(c,"投币",coin.centerX(),coin.centerY(),coin.height()*.44f,held?INK:0xfff5d476);
        }
    }
    private void drawRight(Canvas c,int keys){drawRight(c,keys,true);}
    private void drawRight(Canvas c,int keys,boolean menu){
        for(int i=0;i<4;i++){float r=rx[i+1];boolean held=(keys&BITS[i])!=0;fill(held?COLORS[i]:0xff242a2c);c.drawCircle(bx[i],by[i],r,p);ring(c,bx[i],by[i],r,keyStroke(r),COLORS[i]);text(c,LABEL[i],bx[i],by[i],r*.96f,held?INK:COLORS[i]);}
        if(menu){
            int id=ControlProfile.MAX_KEY;float x=cx[id],y=cy[id],r=rx[id];boolean held=(keys&MAX_KEYS)==MAX_KEYS;
            fill(held?0xfff8d567:0xff35342d);c.drawCircle(x,y,r,p);ring(c,x,y,r,keyStroke(r),0xfff5d476);
            text(c,"MAX",x,y,r*.60f,held?INK:0xfff5d476);
        }
        if(menu){fill((keys&START)!=0?0xfff8d567:0xff35342d);c.drawRoundRect(start,start.height()*.18f,start.height()*.18f,p);text(c,"START",start.centerX(),start.centerY(),start.height()*.4f,(keys&START)!=0?INK:0xfff5d476);}
    }
}
