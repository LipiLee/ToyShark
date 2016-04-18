package com.lipisoft.toyshark;

import android.content.Intent;
import android.net.VpnService;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ToyShark";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.v(TAG, "onCreate is called.");
        Intent intent = VpnService.prepare(this);
        if (intent != null)
            startActivityForResult(intent, 0);
        else
            onActivityResult(0, RESULT_OK, null);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        Log.v(TAG, "onActivityResult is called.");
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, ToySharkVPNService.class);
            startService(intent);
        } else
            // TODO Seriously alert popup to user
            ;

    }
}
