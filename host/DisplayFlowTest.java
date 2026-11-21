package local.k98;

/** Exercise the same geometry, transparency and touch implementation used by the APK. */
public final class DisplayFlowTest {
    private static int cases, checks;
    private static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, String name) {
        check(Math.abs(actual - expected) < .002f, name + " " + actual + " vs " + expected);
    }
    private static void touches(PadLayout pad) {
        TouchInput touch = new TouchInput();
        touch.configure(pad);
        int[] fingers = {3, 9};
        float[] x = {pad.dx + pad.dr * .72f, pad.bx[0]};
        float[] y = {pad.dy, pad.by[0]};
        touch.event(TouchInput.DOWN_EVENT, 3, 1, fingers, x, y);
        touch.event(TouchInput.POINTER_DOWN, 9, 2, fingers, x, y);
        check(touch.poll().keys == (PadLayout.RIGHT | PadLayout.A), "direction and A");
        touch.event(TouchInput.CANCEL_EVENT, 3, 2, fingers, x, y);
        check(touch.poll().keys == 0, "release after dialog");
        check(pad.insertsCoin(pad.dx, (pad.coinTop + pad.coinBottom) / 2) == pad.arcade, "coin");
        check(pad.startsGame(pad.startX,(pad.menuTop + pad.menuBottom) / 2), "start");
        for (int i=0; i<4; i++) check(pad.fireAt(pad.bx[i],pad.by[i]) == PadLayout.BITS[i], "action key");
    }
    private static void screen(int w, int h, int left, int top, int right, int bottom) {
        for(boolean arcade : new boolean[]{false,true}) {
            for (boolean full : new boolean[]{false,true}) for(boolean keep : new boolean[]{false,true}) {
                for(int transparency : new int[]{0,50,99,100}) {
                    cases++;
                    DisplayOptions options = new DisplayOptions(full,keep,transparency);
                    PadLayout pad = new PadLayout(w,h,arcade,options,left,top,right,bottom);
                    float width = pad.videoRight-pad.videoLeft, height = pad.videoBottom-pad.videoTop;
                    if(full && !keep) {
                        near(pad.videoLeft,0,"fill left"); near(pad.videoTop,0,"fill top");
                        near(pad.videoRight,w,"fill right"); near(pad.videoBottom,h,"fill bottom");
                    } else {
                        near(width/height,4f/3,"aspect");
                        near(pad.videoLeft,w-pad.videoRight,"center X");
                        near(pad.videoTop,h-pad.videoBottom,"center Y");
                        check(Math.abs(width-w)<.002f || Math.abs(height-h)<.002f,"largest 4:3");
                        if(w*3>=h*4){near(pad.videoTop,0,"4:3 reaches top");near(pad.videoBottom,h,"4:3 reaches bottom");}
                    }
                    check(pad.dx-pad.dr>=left && pad.startX+pad.half<=w-right, "safe controls X");
                    check(pad.menuTop>=top && pad.dy+pad.dr<=h-bottom, "safe controls Y");
                    touches(pad);
                }
            }
        }
    }
    public static void main(String[] args) {
        check(!DisplayOptions.DEFAULT.fullscreen && DisplayOptions.DEFAULT.transparency==50, "defaults");
        int previous=256;
        for(int t=-2; t<=102; t++) {
            DisplayOptions full=new DisplayOptions(true,false,t);
            int clamped=Math.max(0,Math.min(100,t));
            near(full.controlAlpha(),(255*(100-clamped)+50)/100,"opacity");
            check(full.controlAlpha()<=previous,"slider direction"); previous=full.controlAlpha();
            check(new DisplayOptions(false,false,t).controlAlpha()==255,"classic opacity");
        }
        int[][] sizes={{2392,1080},{2400,1080},{1600,720},{1920,1080},{1440,1080},{1280,800},{800,360},{640,360}};
        for(int[] size:sizes) {
            screen(size[0],size[1],0,0,0,0);
            screen(size[0],size[1],54,0,0,8);
            screen(size[0],size[1],0,6,54,0);
        }
        System.out.println("DISPLAY PASS sizes="+sizes.length+" layout cases="+cases+" assertions="+checks);
    }
}
