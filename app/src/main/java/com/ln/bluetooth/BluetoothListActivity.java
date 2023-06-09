package com.ln.bluetooth;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

/**
 * @author SZLY(COPYRIGHT 2018 - 2020 SZLY. All rights reserved.)
 * @abstract 蓝牙扫描、连接和数据交互
 * @version V1.0.0
 * @date  2020/09/01
 */
public class BluetoothListActivity extends Activity {
    private static final boolean D = true;
    private static final String TAG = "BluetoothChat";
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
    private static final int REQUEST_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    public static final int MESSAGE_DEVICE_NAME = 1;
    public static final int MESSAGE_TOAST_FAIL = 2;
    public static final int MESSAGE_TOAST_LOST = 3;
    public static final int MESSAGE_BLUETOOTH_DATA = 4;

    private EditText sendEditView;
    private Button scanButton;
    private Button sendButton;
    private ListView receiveListView;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothService mChatService;

    /**
     * 已经配对蓝牙设备
     */
    private ArrayAdapter<String> mPairedDevicesArrayAdapter;
    /**
     * 扫描到的蓝牙设备
     */
    private ArrayAdapter<String> mNewDevicesArrayAdapter;
    /**
     * 蓝牙接收到的数据
     */
    private ArrayAdapter<String> mBluetoothReceiveData;

    /**
     * @method onCreate方法
     * @param savedInstanceState 用户按到home键，退出界面，用户再次打开时使用该参数恢复原来状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bluetooth_list);

        scanButton = (Button)findViewById(R.id.btn_scan);
        sendEditView = (EditText)findViewById(R.id.edit_send);
        sendButton = (Button)findViewById(R.id.btn_send);
        receiveListView = (ListView) findViewById(R.id.lv_receive);

        mPairedDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

        mNewDevicesArrayAdapter = new ArrayAdapter<>(this, R.layout.device_name);

        mBluetoothReceiveData = new ArrayAdapter<String>(this, R.layout.device_name);

        receiveListView.setAdapter(mBluetoothReceiveData);

        //已配对列表
        ListView pairedListView = (ListView) findViewById(R.id.lv_paired_devices);
        //列表格式
        pairedListView.setAdapter(mPairedDevicesArrayAdapter);
        //按下监听
        pairedListView.setOnItemClickListener(mDeviceClickListener);

        ListView newDevicesListView = (ListView) findViewById(R.id.lv_new_devices);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        //当找到设备后注册广播
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        this.registerReceiver(mReceiver, filter);

        //当蓝牙扫描结束后注册广播
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        this.registerReceiver(mReceiver, filter);

        filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        this.registerReceiver(mReceiver, filter);
     
        //获取本地蓝牙设备
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        //判断是否打开蓝牙
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            //Otherwise, setup the chat session
        } else {
            if (mChatService == null) {
                mChatService = new BluetoothService(this, mHandler);

                //将每个已配对蓝牙显示出来
                Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

                if (pairedDevices.size() > 0) {
                    findViewById(R.id.text_paired_devices).setVisibility(View.VISIBLE);

                    for (BluetoothDevice device : pairedDevices) {
                        mPairedDevicesArrayAdapter.add(device.getName() +
                                "\n" + device.getAddress());
                    }
                } else {
                    String noDevices = getResources().getText(R.string.none_paired).toString();
                    mPairedDevicesArrayAdapter.add(noDevices);
                }
            }
        }

        scanButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doDiscovery();
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //获取待发送内容，发送字节流
                byte[] sendBuffer;

                if (sendEditView.getText() != null) {
                    String[] stringBuffer = sendEditView.getText().toString().split(" ");
                    sendBuffer = new byte[stringBuffer.length];
                    for (int i = 0; i < stringBuffer.length; i++) {
                        sendBuffer[i] = (byte) Integer.parseInt(stringBuffer[i], 16);
                    }
                    if (mChatService.getState() == 3) {
                        mChatService.write(sendBuffer);
                    }
                }
            }
        });
    }

    /**
     * @method 扫描蓝牙设备
     */
    private void doDiscovery() {
        if (D) {
            Log.d(TAG, "doDiscovery()");
        }
        setTitle(R.string.scanning);
        //使可用蓝牙设备文本框可见
        findViewById(R.id.text_new_devices).setVisibility(View.VISIBLE);

        //已经扫描完成，则停止扫描
        if (mBluetoothAdapter.isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
        mNewDevicesArrayAdapter.clear();
        //开始扫描蓝牙设备
        //判断蓝牙权限是否打开，否则请求权限
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.M)
        {
            int permissionCheck = 0;
            permissionCheck = this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionCheck += this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permissionCheck != 2) {
                this.requestPermissions( // 请求授权
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
            }
            else {//开始搜索设备
                mBluetoothAdapter.startDiscovery();
            }
        }
        else {//开始搜索设备
            mBluetoothAdapter.startDiscovery();
        }
    }

    /**
     * @method 请求蓝牙权限界面关闭后
     * @param requestCode 标识请求的来源
     * @param permissions 具体权限
     * @param grantResults 授权结果
     */
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_LOCATION:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //开始搜索设备
                    mBluetoothAdapter.startDiscovery();
                } else {
                    Toast.makeText(getApplicationContext(), "蓝牙权限申请失败，无法搜索设备", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }


    /**
     * @method 注册广播
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice device;
            //当扫描到可用蓝牙设备
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //获取可用蓝牙设备
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                //若为已配对，则不添加到可用蓝牙设备列表
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    mNewDevicesArrayAdapter.add(device.getName() + "\n"
                            + device.getAddress());
                }

                //当扫描完成
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if (mNewDevicesArrayAdapter.getCount() == 0) {
                    String noDevices = getResources().getText(R.string.none_found).toString();
                    //mNewDevicesArrayAdapter.add(noDevices);
                }
            } else if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                //当扫描完成，则停止扫描
                if (mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING: //正在配对
                        Toast.makeText(getApplicationContext(),
                                "正在配对 ",
                                Toast.LENGTH_SHORT).show();
                        setTitle(R.string.pairing_device);
                        break;
                    case BluetoothDevice.BOND_BONDED: //配对成功
                        setTitle(R.string.device_already_paired);
                        Toast.makeText(getApplicationContext(),
                                "完成配对 ",
                                Toast.LENGTH_SHORT).show();
                        mNewDevicesArrayAdapter.remove(device.getName() + "\n"
                                + device.getAddress());
                        mPairedDevicesArrayAdapter.add(device.getName() + "\n" +
                                device.getAddress());
                        mChatService.connect(device);
                        setTitle(R.string.connecting);
                        break;
                    case BluetoothDevice.BOND_NONE: //取消配对或未配对
                        Toast.makeText(getApplicationContext(),
                                "配对失败或取消 ",
                                Toast.LENGTH_SHORT).show();
                        setTitle(R.string.fail_to_pair);
                        break;
                    default:
                        break;
                }
            }
        }
    };

    /**
     * @method 按下可用蓝牙设备列表中的蓝牙设备后连接蓝牙设备
     */
    private AdapterView.OnItemClickListener mDeviceClickListener
            = new AdapterView.OnItemClickListener() {
        //选项点击事件
        @Override
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            //停止扫描蓝牙设备
            mBluetoothAdapter.cancelDiscovery();
            //获取MAC地址
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            Log.e(TAG, "address"+ address);
            //根据地址获取蓝牙设备
            BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
            //作为客户端连接蓝牙设备
            if(device.getBondState() == BluetoothDevice.BOND_BONDED) {
                //作为客户端连接蓝牙设备
                setTitle(R.string.connecting);
                mChatService.connect(device);
            } else{
                try{
                    Method createBond = BluetoothDevice.class.getMethod("createBond");
                    createBond.invoke(device);
                } catch (NoSuchMethodException | IllegalAccessException
                        | InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    /**
     * 处理BluetoothService发送过来的消息
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // 接收到的数据
                case MESSAGE_BLUETOOTH_DATA:
                    String readMessage = (String) msg.obj;
                    Log.d(TAG,readMessage);

                    mBluetoothReceiveData.add(readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    String mConnectedDeviceName
                            = msg.getData().getString(DEVICE_NAME);
                    setTitle(R.string.device_already_connected);
                    Toast.makeText(getApplicationContext(),
                            "Connected to " + mConnectedDeviceName,
                            Toast.LENGTH_SHORT).show();
                    break;
                case MESSAGE_TOAST_FAIL:
                    //客户端连接失败
                    setTitle(R.string.wait_for_connecting);
                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
                            .show();
                    break;
                case MESSAGE_TOAST_LOST:
                    //连接失败或断掉信息
                    setTitle(R.string.device_lost);
                    Toast.makeText(getApplicationContext(),
                            msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
                            .show();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * @method 从另一Activity回到本Activity的操作
     * @param requestCode 请求码
     * @param resultCode 返回码
     * @param data 传递的数据
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            //打开蓝牙设备
            case REQUEST_ENABLE_BT:
                if (resultCode == Activity.RESULT_OK) {
                    mChatService = new BluetoothService(this, mHandler);
                    //将每个已配对蓝牙设备显示出来
                    Set<BluetoothDevice> pairedDevices
                            = mBluetoothAdapter.getBondedDevices();

                    if (pairedDevices.size() > 0) {
                        findViewById(R.id.text_paired_devices).setVisibility(View.VISIBLE);

                        for (BluetoothDevice device : pairedDevices) {
                            mPairedDevicesArrayAdapter.add(device.getName() +
                                    "\n" + device.getAddress());
                        }
                    } else {
                        String noDevices
                                = getResources().getText(R.string.none_paired).toString();
                        mPairedDevicesArrayAdapter.add(noDevices);
                    }
                } else {
                    if (D) {
                        Log.d(TAG, "BT not enabled");
                    }
                    //用户不允许打开蓝牙设备，退出界面
                    Toast.makeText(this, R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            default:
                break;
        }
    }
    /**
     * @method 关闭BluetoothListAcitity触发
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mChatService.stop();
        mHandler.removeCallbacksAndMessages(null);
        this.unregisterReceiver(mReceiver);
    }

    /**
     * @method 手机返回键
     */
    @Override
    public void onBackPressed() {
        finish();
    }

}
