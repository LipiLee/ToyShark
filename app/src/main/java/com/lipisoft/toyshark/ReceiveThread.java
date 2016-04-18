package com.lipisoft.toyshark;

import android.os.ParcelFileDescriptor;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;

/**
 * Created by Lipi on 16. 4. 7..
 */
public class ReceiveThread implements Runnable {
    private ParcelFileDescriptor mVPNInterface;

    public ReceiveThread(ParcelFileDescriptor mVPNInterface) {
        this.mVPNInterface = mVPNInterface;
    }

    @Override
    public void run() {
        FileOutputStream out = new FileOutputStream(mVPNInterface.getFileDescriptor());
        ByteBuffer packet = ByteBuffer.allocate(65535);
        int length;

        // TODO get the payload and send it to interface

    }
}
