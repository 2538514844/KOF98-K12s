package local.k98;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.EditText;
import java.io.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.*;
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
public class InputDebugTest {
    private ActivityController<Main> activity;
    private Main main;private GameScreen game;
    @Before public void prepare(){
        activity=Robolectric.buildActivity(Main.class).setup();main=activity.get();
        ReflectionHelpers.callInstanceMethod(main,"releaseLoading");
        game=new GameScreen(main,main.getFilesDir(),NeoMode.AES_EUR,DisplayOptions.DEFAULT);
        ReflectionHelpers.setField(main,"game",game);
    }
    @After public void close(){activity.close();}
    private AlertDialog panel(){main.showInputDebug();return ShadowAlertDialog.getLatestAlertDialog();}
    private void start(){SettingsUITest.click(panel(),"开始记录");assertTrue(main.inputTrace.recording());}
    @Test public void startResumeMarkStopAndKeepAnExportableRecording(){
        AlertDialog dialog=panel();assertFalse(ReflectionHelpers.<Boolean>getField(game,"foreground"));
        assertFalse(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
        SettingsUITest.click(dialog,"开始记录");assertTrue(main.inputTrace.recording());assertTrue(ReflectionHelpers.<Boolean>getField(game,"foreground"));
        SettingsUITest.click(panel(),"标记刚才没出招");assertTrue(String.join("\n",main.inputTrace.snapshot()).contains("failed_attempt"));
        dialog=panel();SettingsUITest.click(dialog,"停止记录");assertFalse(main.inputTrace.recording());assertTrue(main.inputTrace.hasData());
        assertTrue(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());SettingsUITest.click(dialog,"返回");assertTrue(ReflectionHelpers.<Boolean>getField(game,"foreground"));
    }
    @Test public void cancelingTheDocumentPickerKeepsDataAndAllowsRetry(){
        start();SettingsUITest.click(panel(),"停止并导出 ZIP");
        Intent intent=shadowOf(main).getNextStartedActivityForResult().intent;
        assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.getAction());assertEquals("application/zip",intent.getType());
        assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE).endsWith(".zip"));assertFalse(main.inputTrace.recording());
        int count=main.inputTrace.size();main.onActivityResult(InputDebug.EXPORT,Activity.RESULT_CANCELED,null);
        assertEquals(count,main.inputTrace.size());assertTrue(ReflectionHelpers.<Boolean>getField(game,"foreground"));
        SettingsUITest.click(panel(),"导出 ZIP");assertNotNull(shadowOf(main).getNextStartedActivityForResult());
    }
    @Test public void exportWritesParseableZipAndChineseNotesThroughTheDocumentUri()throws Exception{
        start();AlertDialog dialog=panel();
        SettingsUITest.find(SettingsUITest.decor(dialog),EditText.class,null).setText("右侧草薙京，↓↙←↓↙←＋A 没出招");
        SettingsUITest.click(dialog,"停止并导出 ZIP");
        Uri uri=Uri.parse("content://input-test/capture.zip");CountDownLatch closed=new CountDownLatch(1);
        ByteArrayOutputStream stream=new ByteArrayOutputStream(){@Override public void close()throws IOException{super.close();closed.countDown();}};
        shadowOf(main.getContentResolver()).registerOutputStream(uri,stream);
        main.onActivityResult(InputDebug.EXPORT,Activity.RESULT_OK,new Intent().setData(uri));
        assertTrue("background export finished",closed.await(5,TimeUnit.SECONDS));
        shadowOf(Looper.getMainLooper()).idle();Map<String,String> files=InputTraceTest.unzip(stream.toByteArray());
        assertTrue(files.get("note.txt").contains("右侧草薙京"));assertTrue(files.get("trace.jsonl").contains("\"type\":\"session\""));
        for(String line:files.get("trace.jsonl").split("\n"))new org.json.JSONObject(line);
        assertTrue(main.inputTrace.hasData());assertFalse(main.inputTrace.recording());
    }
    @Test public void durationLimitStopsEvenWhileTheGameIsPaused(){
        start();panel();shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(InputTrace.MAX_DURATION_MS+1));
        assertFalse(main.inputTrace.recording());assertEquals("duration_limit",main.inputTrace.reason());assertTrue(main.inputTrace.hasData());
    }
    @Test public void pauseAndSettingsEntriesReturnToGameplayAfterStarting(){
        main.pauseMenu();SettingsUITest.click(ShadowAlertDialog.getLatestAlertDialog(),"输入调试");
        AlertDialog dialog=ShadowAlertDialog.getLatestAlertDialog();SettingsUITest.click(dialog,"开始记录");
        assertTrue(main.inputTrace.recording());assertTrue(ReflectionHelpers.<Boolean>getField(game,"foreground"));
        ReflectionHelpers.callInstanceMethod(main,"openSettings");SettingsUITest.click(ShadowAlertDialog.getLatestAlertDialog(),"输入调试");
        assertFalse(ReflectionHelpers.<Boolean>getField(game,"foreground"));
        SettingsUITest.click(ShadowAlertDialog.getLatestAlertDialog(),"继续记录");assertTrue(ReflectionHelpers.<Boolean>getField(game,"foreground"));
    }
    @Test public void failedDocumentWriteRetainsTheOriginalDataForRetry()throws Exception{
        start();SettingsUITest.click(panel(),"停止并导出 ZIP");int count=main.inputTrace.size();
        Uri uri=Uri.parse("content://input-test/failure.zip");CountDownLatch closed=new CountDownLatch(1);
        OutputStream stream=new OutputStream(){public void write(int b)throws IOException{throw new IOException("test write failure");}public void close(){closed.countDown();}};
        shadowOf(main.getContentResolver()).registerOutputStream(uri,stream);
        main.onActivityResult(InputDebug.EXPORT,Activity.RESULT_OK,new Intent().setData(uri));
        assertTrue(closed.await(5,TimeUnit.SECONDS));
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        InputDebug debug=ReflectionHelpers.getField(main,"inputDebug");
        while(ReflectionHelpers.<Boolean>getField(debug,"writing")&&System.nanoTime()<until){Thread.sleep(10);shadowOf(Looper.getMainLooper()).idle();}
        assertFalse(ReflectionHelpers.<Boolean>getField(debug,"writing"));assertEquals(count,main.inputTrace.size());
        assertTrue(org.robolectric.shadows.ShadowToast.getTextOfLatestToast().contains("导出失败"));
        assertTrue(panel().getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
    }
    @Test public void recordingCanBeExportedFromTheLauncherAfterTheGameEnds(){
        start();game.shutdown();ReflectionHelpers.setField(main,"game",null);
        AlertDialog dialog=panel();assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertTrue(dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled());
        assertEquals("game_end",main.inputTrace.reason());SettingsUITest.click(dialog,"导出 ZIP");assertNotNull(shadowOf(main).getNextStartedActivityForResult());
    }
}
