package local.k98;
import android.app.Dialog;
import android.view.View;

/** Keep a child attached to its settings screen without restarting the underlying game. */
final class SettingsWindows {
    static void tie(View anchor,Dialog child){
        View decor=child.getWindow().getDecorView();
        View.OnAttachStateChangeListener owner=new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){anchor.removeOnAttachStateChangeListener(this);if(child.isShowing())child.dismiss();}
        };anchor.addOnAttachStateChangeListener(owner);
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){anchor.removeOnAttachStateChangeListener(owner);v.removeOnAttachStateChangeListener(this);}});
    }
}

