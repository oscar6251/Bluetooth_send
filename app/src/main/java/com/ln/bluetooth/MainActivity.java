package com.ln.bluetooth;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * @author SZLY(COPYRIGHT 2018 - 2020 SZLY. All rights reserved.)
 * @abstract 首页按键监听
 * @version V1.0.0
 * @date 2020/09/01
 */

public class MainActivity extends Activity {
    private Button bluetoothButton;
    /**
     * @method onCreate方法
     * @param savedInstanceState 用户按到home键，退出界面，用户再次打开时使用该参数恢复原来状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothButton = (Button) findViewById(R.id.btn_bluetooth);

        bluetoothButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, BluetoothListActivity.class);
                startActivity(intent);
            }
        });
    }
}
