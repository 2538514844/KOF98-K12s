package local.k98;
final class LegacyControls extends PadLayout {
LegacyControls(int w,int h){super(w,h);}
    int hit(float x,float y){if(startsGame(x,y))return START;float vx=x-dx,vy=y-dy;float r=(float)Math.sqrt(vx*vx+vy*vy);
        if(r<=dr*1.23f){if(r<dr*.18f)return 0;int bits=0;if(Math.abs(vx)>.39f*r)bits|=vx<0?LEFT:RIGHT;if(Math.abs(vy)>.39f*r)bits|=vy<0?UP:DOWN;return bits;}
        int pick=-1;float best=br*br*1.39f;for(int i=0;i<4;i++){vx=x-bx[i];vy=y-by[i];float dist=vx*vx+vy*vy;if(dist<best){best=dist;pick=i;}}return pick<0?0:BITS[pick];
    }
}
