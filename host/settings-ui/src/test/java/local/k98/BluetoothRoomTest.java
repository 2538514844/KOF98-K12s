package local.k98;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Looper;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,manifest=Config.NONE,qualifiers="w800dp-h360dp-land-mdpi")
public class BluetoothRoomTest {
    private ActivityController<Main> activity;
    private BluetoothRoom room;
    private BluetoothAdapter adapter;
    private BluetoothDevice phone;
    @Before public void setUp(){
        activity=Robolectric.buildActivity(Main.class).setup();
        room=ReflectionHelpers.getField(activity.get(),"bluetooth");
        adapter=BluetoothAdapter.getDefaultAdapter();
        shadowOf(adapter).setEnabled(true);
        phone=adapter.getRemoteDevice("10:20:30:40:50:60");
        shadowOf(phone).setName("Player Two");
        shadowOf(phone).setType(BluetoothDevice.DEVICE_TYPE_CLASSIC);
        shadowOf(phone).setBondState(BluetoothDevice.BOND_BONDED);
        shadowOf(adapter).setBondedDevices(Collections.singleton(phone));
    }
    @After public void tearDown(){activity.close();}
    private LinearLayout scan(){
        ReflectionHelpers.callInstanceMethod(room,"scan");
        return ReflectionHelpers.getField(room,"roomList");
    }
    @Test public void pairedPhoneShownWithoutDiscoveryOrSdp(){
        shadowOf(activity.get().getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LinearLayout list=scan();
        assertEquals(2,list.getChildCount());
        Button button=(Button)list.getChildAt(1);
        assertEquals("Player Two · 已配对",button.getText());
        assertTrue(button.hasOnClickListeners());
        assertEquals(0,shadowOf(phone).getFetchUuidsWithSdpCount());
    }
    @Test public void deniedScanPermissionKeepsPairedChoice(){
        shadowOf(activity.get().getApplication()).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LinearLayout list=scan();
        assertEquals(2,list.getChildCount());
        assertFalse(adapter.isDiscovering());
        assertTrue(list.getChildAt(1).hasOnClickListeners());
    }
    @Test public void foundPhoneShownImmediatelyWithoutDuplicates(){
        shadowOf(activity.get().getApplication()).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION);
        LinearLayout list=scan();
        BluetoothDevice nearby=adapter.getRemoteDevice("10:20:30:40:50:61");
        shadowOf(nearby).setName("Nearby Player");
        for(BluetoothDevice device:new BluetoothDevice[]{phone,nearby,nearby}){
            activity.get().sendBroadcast(new Intent(BluetoothDevice.ACTION_FOUND).putExtra(BluetoothDevice.EXTRA_DEVICE,device));
        }
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(3,list.getChildCount());
        assertEquals("Nearby Player · 附近",((Button)list.getChildAt(2)).getText());
    }
}
