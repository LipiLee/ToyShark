package com.lipisoft.toyshark.protocol.transport;

/**
 * Created by Lipi on 16. 4. 8..
 */
public class TCP {
    int source;
    int destination;
    int sequence;
    int acknowledge;
    int state;

    public TCP(int source, int destination) {
        this.source = source;
        this.destination = destination;
    }
}
