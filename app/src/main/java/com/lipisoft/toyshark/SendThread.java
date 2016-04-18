package com.lipisoft.toyshark;

import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by Lipi on 16. 4. 7..
 */
public class SendThread implements Runnable {
    private static final String TAG = "SendThread";
    private ParcelFileDescriptor mVPNInterface;

    public SendThread(ParcelFileDescriptor mVPNInterface) {
        this.mVPNInterface = mVPNInterface;
    }

    @Override
    public void run() {
        FileInputStream in = new FileInputStream(mVPNInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(65535);
        int length;
        try {
            while ((length = in.read(packet.array())) >= 0) {
                if (length > 0) {
                    // TODO extract header and send out
                    // check IP version
                    // get source and destination address
                    // check whether TCP or UDP
                    // if TCP, check control flag (seq, fin, reset, etc) and port number
                    // else UDP, get source and destination port number
                    // check new session and add it if new session
                    // and make new socket

                    // if it exists, send payload to the socket
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }
}
