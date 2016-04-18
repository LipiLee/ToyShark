package com.lipisoft.toyshark.protocol.network;

import java.net.InetAddress;

/**
 * Created by Lipi on 16. 4. 18..
 */
public class IP {
    private InetAddress source;
    private InetAddress destination;

    public IP(InetAddress source, InetAddress destination) {
        this.source = source;
        this.destination = destination;
    }
}
