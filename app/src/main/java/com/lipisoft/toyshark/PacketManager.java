package com.lipisoft.toyshark;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public enum PacketManager {
    INSTANCE;

    public static final int PACKET = 0;
    @NonNull private final List<Packet> list = new ArrayList<>();
    private PacketListAdapter adapter;
    @NonNull private final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            if (msg != null) {
                if (msg.what == PacketManager.PACKET) {
                    adapter.notifyDataSetChanged();
                }
            }
            super.handleMessage(msg);
        }
    };

    public boolean add(@NonNull final Packet packet) {
        return list.add(packet);
    }

    @NonNull public List<Packet> getList() {
        return list;
    }

    public void setAdapter(@NonNull PacketListAdapter adapter) {
        this.adapter = adapter;
    }

    @NonNull public Handler getHandler() {
        return handler;
    }
}
