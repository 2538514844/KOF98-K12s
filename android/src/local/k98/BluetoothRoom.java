package local.k98;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;

final class BluetoothRoom {
    private static final UUID SERVICE=UUID.fromString("02130698-f854-427c-a482-13c1c14e0098");
    static final int PERMISSION=410, ENABLE=411, VISIBLE=412;
    final Main main;
    private final BluetoothAdapter adapter;
    private AlertDialog dialog;
    private boolean active,scanning,registered,team;
    private volatile int generation;
    private volatile BluetoothServerSocket server;
    private volatile BluetoothSocket socket;
    private volatile DuelLink link;
    private String peer;
    private final Set<String> listed=new HashSet<>();
    private LinearLayout roomList;
    private TextView scanStatus;
    BluetoothRoom(Main main){
        this.main=main;BluetoothManager manager=main.getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
    }
    void open(){
        close();active=true;
        if(adapter==null){error("这台设备没有蓝牙适配器");return;}
        String[] permissions=Build.VERSION.SDK_INT>=31?new String[]{Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_ADVERTISE}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        for(String permission:permissions)if(main.checkSelfPermission(permission)!=PackageManager.PERMISSION_GRANTED){
            main.requestPermissions(permissions,PERMISSION);return;
        }
        enabled();
    }
    void permissions(int[] grants){
        if(!active)return;
        if(Build.VERSION.SDK_INT>=31&&!granted(Manifest.permission.BLUETOOTH_CONNECT)){error("请在系统设置中允许附近设备权限");return;}
        enabled();
    }
    private boolean granted(String permission){return main.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED;}
    private boolean canScan(){return Build.VERSION.SDK_INT>=31?granted(Manifest.permission.BLUETOOTH_SCAN):granted(Manifest.permission.ACCESS_FINE_LOCATION);}
    private void enabled(){
        try{if(adapter.isEnabled())choose();else main.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),ENABLE);}
        catch(RuntimeException e){error("无法启用蓝牙");}
    }
    void result(int request,int result){
        if(!active)return;
        if(request==ENABLE){if(result==Activity.RESULT_OK)choose();else error("蓝牙未开启");}
        if(request==VISIBLE&&server!=null&&link==null)waiting(result<=0);
    }
    private void show(AlertDialog.Builder builder){
        dismiss();dialog=builder.create();dialog.setOnCancelListener(d->close());dialog.show();
    }
    private void dismiss(){if(dialog!=null){dialog.dismiss();dialog=null;}}
    private void choose(){
        show(new AlertDialog.Builder(main).setTitle("蓝牙对战").setItems(new String[]{"创建房间","加入房间"},(d,i)->{
            if(i==1)scan();else show(new AlertDialog.Builder(main).setTitle("创建房间").setItems(
                new String[]{"单挑　1 对 1","团队竞技　3 对 3"},(x,n)->host(n==1)).setNegativeButton("取消",(x,n)->close()));
        }).setNegativeButton("取消",(d,i)->close()));
    }
    private static String name(BluetoothDevice device){
        String label=device.getName();return label==null||label.trim().isEmpty()?"拳皇玩家":label;
    }
    private void waiting(boolean pairedOnly){
        String label=adapter.getName();
        show(new AlertDialog.Builder(main).setTitle(team?"团队竞技":"单挑")
            .setMessage((label==null?"房主":label)+"（我） · 1P 左侧\n"+(pairedOnly?"等待已配对的好友加入…":"等待好友加入…"))
            .setNeutralButton("重新公开",(d,i)->discoverable()).setNegativeButton("关闭房间",(d,i)->close()));
    }
    private void discoverable(){
        if(Build.VERSION.SDK_INT>=31&&!granted(Manifest.permission.BLUETOOTH_ADVERTISE)){waiting(true);return;}
        try{main.startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,300),VISIBLE);}
        catch(RuntimeException e){waiting(true);}
    }
    private void host(boolean teams){
        team=teams;final int token=generation;
        try{server=adapter.listenUsingRfcommWithServiceRecord("KOF98 Duel",SERVICE);}
        catch(IOException|SecurityException e){error("蓝牙房间创建失败");return;}
        BluetoothServerSocket listener=server;
        new Thread(()->{
            try{BluetoothSocket connected=listener.accept();establish(connected,true,token);}
            catch(IOException|RuntimeException e){postError(token,e);}
            finally{quietClose(listener);}
        },"K98-room-host").start();
        discoverable();
    }
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        public void onReceive(Context context,Intent intent){
            if(!scanning)return;
            String action=intent.getAction();BluetoothDevice dev=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if(BluetoothDevice.ACTION_FOUND.equals(action)&&dev!=null){
                BluetoothClass type=dev.getBluetoothClass();
                int major=type==null?BluetoothClass.Device.Major.UNCATEGORIZED:type.getMajorDeviceClass();
                if(major==BluetoothClass.Device.Major.PHONE||major==BluetoothClass.Device.Major.UNCATEGORIZED)
                    addDevice(dev,dev.getBondState()==BluetoothDevice.BOND_BONDED);
            }
            if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)&&!adapter.isDiscovering())
                scanStatus.setText(listed.isEmpty()?"未发现手机，请让房主点「重新公开」后刷新":"请选择已开房的那台手机");
        }
    };
    private void addDevice(BluetoothDevice dev,boolean paired){
        if(dev.getType()==BluetoothDevice.DEVICE_TYPE_LE||!listed.add(dev.getAddress()))return;
        Button row=new Button(main);row.setAllCaps(false);row.setText(name(dev)+(paired?" · 已配对":" · 附近"));
        row.setOnClickListener(v->join(dev));roomList.addView(row);
    }
    private void scan(){
        stopScan();listed.clear();
        ScrollView list=new ScrollView(main);roomList=new LinearLayout(main);roomList.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(main.getResources().getDisplayMetrics().density*20);
        roomList.setPadding(pad,pad/2,pad,pad/2);list.addView(roomList);
        scanStatus=new TextView(main);roomList.addView(scanStatus);
        show(new AlertDialog.Builder(main).setTitle("加入蓝牙房间").setView(list)
            .setNeutralButton("刷新",(d,i)->scan()).setNegativeButton("取消",(d,i)->close()));
        try{
            for(BluetoothDevice dev:adapter.getBondedDevices())addDevice(dev,true);
        }catch(SecurityException e){error("无法读取已配对设备，请允许附近设备权限");return;}
        if(!canScan()){scanUnavailable();return;}
        IntentFilter filter=new IntentFilter(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        try{
            if(Build.VERSION.SDK_INT>=33)main.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else main.registerReceiver(receiver,filter);
            registered=true;
            scanning=true;
            if(adapter.isDiscovering()||adapter.startDiscovery())
                scanStatus.setText(listed.isEmpty()?"正在搜索附近的手机…":"已配对设备可直接选择，正在搜索附近的手机…");
            else scanUnavailable();
        }catch(RuntimeException e){scanUnavailable();}
    }
    private void scanUnavailable(){
        scanStatus.setText(listed.isEmpty()?"未能扫描附近手机。可先在系统蓝牙中配对，再点刷新":"附近扫描不可用，可直接选择已配对的房主手机");
    }
    private void stopScan(){
        if(registered){main.unregisterReceiver(receiver);registered=false;}
        if(scanning){scanning=false;try{if(adapter.isDiscovering())adapter.cancelDiscovery();}catch(SecurityException ignored){}}
    }
    private void join(BluetoothDevice device){
        stopScan();int token=generation;
        show(new AlertDialog.Builder(main).setTitle(name(device)).setMessage("正在连接房主…").setNegativeButton("取消",(d,i)->close()));
        try{socket=device.createRfcommSocketToServiceRecord(SERVICE);}catch(IOException|SecurityException e){error("无法连接房主");return;}
        BluetoothSocket pending=socket;
        new Thread(()->{
            try{pending.connect();}catch(IOException|RuntimeException e){
                ui(token,()->{close();active=true;scan();main.note("未连上「"+name(device)+"」，请确认对方已创建房间并在等待好友");});return;
            }
            try{establish(pending,false,token);}catch(IOException|RuntimeException e){postError(token,e);}
        },"K98-room-guest").start();
    }
    private void establish(BluetoothSocket connected,boolean owner,int token)throws IOException{
        DuelLink next;
        synchronized(this){
            if(generation!=token){quietClose(connected);return;}
            socket=connected;peer=name(connected.getRemoteDevice());quietClose(server);server=null;
            next=new DuelLink(connected.getInputStream(),connected.getOutputStream(),connected,owner,team,new DuelLink.Events(){
                public void lobby(){ui(token,()->{if(link.round==null){main.stopDuel();lobbyPage();}});}
                public void playing(DuelRound round){ui(token,()->{if(link.round==round){dismiss();main.playDuel(round);}});}
                public void closed(String reason){ui(token,()->error(reason));}
            });link=next;
        }
        next.run();
    }
    private void ui(int token,Runnable action){main.runOnUiThread(()->{if(active&&generation==token&&!main.isDestroyed())action.run();});}
    private void postError(int token,Exception e){ui(token,()->error(e.getMessage()==null?"蓝牙连接已断开":"蓝牙连接已断开 · "+e.getMessage()));}
    private void lobbyPage(){
        DuelLink l=link;
        String mine=adapter.getName();if(mine==null)mine="本机";mine+="（我）";
        String text="1P 左侧　"+(l.localP1()?mine:peer)+"\n2P 右侧　"+(l.localP1()?peer:mine);
        AlertDialog.Builder b=new AlertDialog.Builder(main).setTitle(l.team?"团队竞技 · 蓝牙房间":"单挑 · 蓝牙房间")
            .setNegativeButton("离开房间",(d,i)->close());
        if(l.offer==0){
            if(l.host)b.setPositiveButton("开打",(d,i)->l.start());else text+="\n等待房主开打";
            b.setNeutralButton("交换左右",(d,i)->l.swap());
        }else if(l.offer==(l.host?2:1)){
            text+="\n对方想交换左右";b.setPositiveButton("同意",(d,i)->l.answer(true)).setNeutralButton("保留站位",(d,i)->l.answer(false));
        }else text+="\n等待对方确认换边";
        show(b.setMessage(text));
    }
    void gameMenu(){
        DuelLink l=link;if(l==null)return;
        android.widget.Button debug=new android.widget.Button(main);debug.setText("输入调试");
        debug.setOnClickListener(v->{main.showInputDebug();dismiss();});
        dismiss();dialog=new AlertDialog.Builder(main).setTitle(l.round!=null&&l.round.localP1?"你是 1P":"你是 2P")
            .setView(debug)
            .setPositiveButton("继续",(d,i)->{}).setNeutralButton("双方返回房间",(d,i)->l.returnToRoom())
            .setNegativeButton("离开房间",(d,i)->{close();main.stopDuel();}).show();
    }
    void background(){if(link!=null&&link.round!=null)link.returnToRoom();}
    void roundFailed(String msg){if(link!=null)link.returnToRoom();main.note(msg);}
    private void error(String message){close();main.stopDuel();main.note(message);}
    private static void quietClose(Closeable c){if(c!=null)try{c.close();}catch(IOException ignored){}}
    synchronized void close(){
        generation++;active=false;stopScan();dismiss();quietClose(server);server=null;
        if(link!=null){link.close();link=null;}quietClose(socket);socket=null;
    }
}
