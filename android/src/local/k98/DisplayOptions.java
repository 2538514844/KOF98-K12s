package local.k98;

final class DisplayOptions {
    static final DisplayOptions DEFAULT = new DisplayOptions(false, false, 50);
    final boolean fullscreen;
    final boolean keepAspect;
    final int transparency;
    final ControlProfile profile;

    DisplayOptions(boolean fullscreen, boolean keepAspect, int transparency) {
        this(fullscreen,keepAspect,transparency,ControlProfile.DEFAULT);
    }
    DisplayOptions(boolean fullscreen, boolean keepAspect, int transparency, ControlProfile profile) {
        this.profile=profile;
        this.fullscreen = fullscreen;
        this.keepAspect = keepAspect;
        this.transparency = Math.max(0, Math.min(100, transparency));
    }

    int controlAlpha() {
        return fullscreen ? Math.round(255 * (100 - transparency) / 100f) : 255;
    }
}
