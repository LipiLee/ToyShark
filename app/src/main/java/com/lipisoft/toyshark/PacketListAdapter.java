package com.lipisoft.toyshark;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.lipisoft.toyshark.util.PacketUtil;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PacketListAdapter extends RecyclerView.Adapter<PacketInfoViewHolder> {
    private static final byte TCP = 6;
    private static final byte UDP = 17;

    @NonNull private final List<Packet> list;

    PacketListAdapter(@NonNull final List<Packet> list) {
        this.list = list;
    }

    @Override
    public PacketInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (parent != null) {
            final View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.packet_info, parent, false);
            return new PacketInfoViewHolder(view);
        }
        return null;
    }

    @Override
    public void onBindViewHolder(PacketInfoViewHolder holder, int position) {
        if (holder != null) {
            final Packet packet = list.get(position);
            final TextView time = holder.getTime();
            final TextView protocol = holder.getProtocol();
            final TextView address = holder.getAddress();
            final TextView port = holder.getPort();

            time.setText(new Date().toString());
            final byte protocolType = packet.getProtocol();
            if (protocolType == TCP) {
                protocol.setText(R.string.tcp);
            } else if (protocolType == UDP) {
                protocol.setText(R.string.udp);
            }
            address.setText(PacketUtil.intToIPAddress(packet.getIpHeader().getDestinationIP()));
            port.setText(String.format(Locale.getDefault(), "%d", packet.getDestinationPort()));
        }
    }

    @Override
    public int getItemCount() {
        return list.size();
    }
}
