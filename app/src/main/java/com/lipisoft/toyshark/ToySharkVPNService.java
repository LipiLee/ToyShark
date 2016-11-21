package com.lipisoft.toyshark;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;
import java.util.List;

/**
 * Created by Lipi on 16. 4. 7..
 */
public class ToySharkVPNService extends VpnService {
    private static final String TAG = "ToyShark";
    private ParcelFileDescriptor mVPNInterface;
    List<SocketChannel> socketChannelList;
    List<DatagramChannel> datagramChannelList;

    @Override
    public void onRevoke() {
        //super.onRevoke();
        Log.v(TAG, "onRevoke is called.");
    }

    @Override
    public void onCreate() {
        //super.onCreate();
        Log.v(TAG, "onCreate is called.");
    }

    @Override
    public void onDestroy() {
        //super.onDestroy();
        Log.v(TAG, "onDestroy is called.");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //return super.onStartCommand(intent, flags, startId);
        Log.v(TAG, "onStartCommand is called.");
        Builder builder = new Builder();
        // TODO check and add necessary options to builder
        mVPNInterface = builder.establish();

        Thread sendThread = new Thread(new SendThread(mVPNInterface), "SendThread");
        Thread receiveThread = new Thread(new ReceiveThread(mVPNInterface), "ReceiveThread");
        sendThread.start();
        receiveThread.start();

        return START_STICKY;
    }
}
