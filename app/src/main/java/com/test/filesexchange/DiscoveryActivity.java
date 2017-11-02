package com.test.filesexchange;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.nearby.connection.Payload;

import java.io.FileNotFoundException;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static com.test.filesexchange.ShareService.STATE_CONNECTED;
import static com.test.filesexchange.ShareService.STATE_READY_SEND_DATA;


public class DiscoveryActivity extends AppCompatActivity
        implements ServiceConnection, ServiceListener {

    private static final String TAG = "DiscoveryActivity";

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.tv_status)
    TextView tvStatus;
    @BindView(R.id.bt_discovery)
    ImageView btDiscovery;

    private ShareService service;
    //    private Content content;
    private List<Endpoint> list;
    private Uri uri;

    public static void start(Context context) {
        Intent starter = new Intent(context, DiscoveryActivity.class);
        context.startActivity(starter);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_discovery);
        ButterKnife.bind(this);

        setTitle(R.string.activity_title_discovery);

        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();

        if (actionBar != null) {
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

//        Intent intent = getIntent();
//        if (intent.hasExtra("content")) {
//            content = intent.getParcelableExtra("content");
//        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (service != null) {
            service.stopDiscovering();
        }
    }

    @OnClick(R.id.bt_discovery)
    void onDiscoveryClicked() {
        Log.d(TAG, "onDiscoveryClicked: ");
        bindService();
    }

    @OnClick(R.id.bt_choose_file)
    void onChooseFile() {
        Log.d(TAG, "onChooseFile: ");
        showImageChooser();
    }



    private void bindService() {
        Log.d(TAG, "bindService");
        Intent bindIntent = new Intent(this, ShareService.class);
        bindService(bindIntent, this, Context.BIND_AUTO_CREATE);
    }

    private void unBindService() {
        Log.d(TAG, "unBindService");
        unbindService(this);
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

    private static final int READ_REQUEST_CODE = 42;

    /**
     * Fires an intent to spin up the file chooser UI and select an image for
     * sending to endpointId.
     */
    private void showImageChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {

            if (intent != null) {
                // The URI of the file selected by the user.
                uri = intent.getData();

            }
        }
    }

    @Override
    public void onStateUpdate(int state) {
        if (state == STATE_CONNECTED) {
            service.startDiscovering();
        } else if (state == STATE_READY_SEND_DATA) {
            service.sendFile(uri);
        }
    }
}
