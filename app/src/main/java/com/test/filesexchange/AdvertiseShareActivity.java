package com.test.filesexchange;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.test.filesexchange.ShareService.STATE_CONNECTED;


public class AdvertiseShareActivity extends AppCompatActivity implements ServiceConnection, ServiceListener {
    private static final String TAG = "AdvertiseShareActivity";

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.tv_status)
    TextView tvStatus;
    @BindView(R.id.bt_advertise)
    ImageView btAdvertise;
    private ShareService service;


    public static void start(Context context) {
        Intent starter = new Intent(context, AdvertiseShareActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_advertise);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        setTitle(R.string.activity_title_advertisement);
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (service != null) {
            service.stopAdvertising();
        }
    }

    @OnClick(R.id.bt_advertise)
    void onAdvertiseClicked() {
        Log.d(TAG, "onAdvertiseClicked: ");
        bindService();
    }

    private void bindService() {
        Log.d(TAG, "bindService");
        Intent bindIntent = new Intent(this, ShareService.class);
        bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    }

    private void unBindService() {
        Log.d(TAG, "unBindService");
        if (service != null) {
            unbindService(this);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        Log.d(TAG, "onServiceConnected");
        service = ((ShareService.LocalBinder) binder).getService();
        service.setListener(this);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Log.d(TAG, "onServiceDisconnected");
        if (service != null) {
            service = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unBindService();
    }

    @Override
    public void onStateUpdate(int state) {
        if (state == STATE_CONNECTED) {
            service.startAdvertising();
        }
    }
}
