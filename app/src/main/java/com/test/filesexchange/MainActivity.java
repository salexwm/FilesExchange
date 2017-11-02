package com.test.filesexchange;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.Button;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
    }

    @OnClick(R.id.bt_start_sender)
    void openSender() {
        DiscoveryActivity.start(this);
    }

    @OnClick(R.id.bt_start_reciever)
    void openReciever() {
        AdvertiseShareActivity.start(this);
    }

}
