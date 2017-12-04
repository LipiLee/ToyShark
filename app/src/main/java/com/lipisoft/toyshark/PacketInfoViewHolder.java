package com.lipisoft.toyshark;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

public class PacketInfoViewHolder extends RecyclerView.ViewHolder {
    private final TextView time;
    private final TextView protocol;
    private final TextView address;
    private final TextView port;

    PacketInfoViewHolder(View itemView) {
        super(itemView);
        time = itemView.findViewById(R.id.time);
        protocol = itemView.findViewById(R.id.protocol);
        address = itemView.findViewById(R.id.address);
        port = itemView.findViewById(R.id.port);
    }

    public TextView getTime() {
        return time;
    }

    public TextView getProtocol() {
        return protocol;
    }

    public TextView getAddress() {
        return address;
    }

    public TextView getPort() {
        return port;
    }
}
