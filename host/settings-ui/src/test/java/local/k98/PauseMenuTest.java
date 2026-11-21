package local.k98;

import android.app.AlertDialog;
import android.os.Looper;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,qualifiers="w800dp-h360dp-land-mdpi")
public class PauseMenuTest {
    @Test public void pauseShowsReloadNoticeAndCanBeOpenedAgainAfterContinuing() {
        try(ActivityController<Main> activity=Robolectric.buildActivity(Main.class).setup()){
            Main main=activity.get();
            GameScreen game=new GameScreen(main,main.getFilesDir(),NeoMode.AES_EUR,DisplayOptions.DEFAULT);
            ReflectionHelpers.setField(main,"game",game);
            for(int i=0;i<2;i++){
                main.onBackPressed();
                AlertDialog d=ShadowAlertDialog.getLatestAlertDialog();
                TextView v=d.findViewById(android.R.id.message);
                assertTrue(d.isShowing());
                assertEquals("重载游戏将重新开始，当前对战进度不保留。",v.getText().toString());
                d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertFalse(d.isShowing());
            }
        }
    }
}
