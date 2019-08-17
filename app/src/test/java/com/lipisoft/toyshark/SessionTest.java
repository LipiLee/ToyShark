package com.lipisoft.toyshark;

import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class SessionTest {
    @Test
    public void setSendingData() {
        final Session session = new Session(1, 2, 3, 4);
        final byte[] test = "Hello, World.".getBytes();

        final ByteBuffer buffer = ByteBuffer.wrap(test);

        // Move position to 5
        for (int i = 0; i < 5; i++)
            buffer.get();

        session.setSendingData(buffer);

        final byte[] actual = session.getSendingData();
        final byte[] result = ", World.".getBytes();
        assertArrayEquals(result, actual);
    }
}