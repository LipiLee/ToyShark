package com.lipisoft.toyshark;

import androidx.annotation.NonNull;

import com.lipisoft.toyshark.socket.IProtectSocket;
import com.lipisoft.toyshark.socket.SocketProtector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.spi.AbstractSelectableChannel;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class SessionManagerTest {
    @Before
    public void setUp() {
        IProtectSocket protectSocket = new IProtectSocket() {
            @Override
            public void protectSocket(Socket socket) {

            }

            @Override
            public void protectSocket(int socket) {

            }

            @Override
            public void protectSocket(DatagramSocket socket) {

            }
        };
        SocketProtector.getInstance().setProtector(protectSocket);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testGetSelector() {
    }

    @Test
    public void testKeepSessionAlive() {
        final Session session = new Session(1, 2, 3, 4);
        SessionManager.INSTANCE.keepSessionAlive(session);

        final int destinationAddress = session.getDestIp();
        final int destinationPort = session.getDestPort();
        final int sourceAddress = session.getSourceIp();
        final int sourcePort = session.getSourcePort();
        final String key = SessionManager.INSTANCE.createKey(destinationAddress, destinationPort, sourceAddress, sourcePort);
        final Session result = SessionManager.INSTANCE.getSessionByKey(key);
        assertEquals(session, result);

        // Release prevents other unit tests from being interrupted.
        SessionManager.INSTANCE.closeSession(session);
    }

    @Test
    public void testAddClientData() {
        final Session tcpSession = makeNewTcpSession();
        final String hello = "Hello";
        byte[] byteBuffer = hello.getBytes();
        final ByteBuffer buffer = ByteBuffer.wrap(byteBuffer);
        SessionManager.INSTANCE.addClientData(buffer, tcpSession);
        final byte[] result = tcpSession.getSendingData();
        assertArrayEquals(byteBuffer, result);

        // Release prevents other unit tests from being interrupted.
        SessionManager.INSTANCE.closeSession(tcpSession);

    }

    @Test
    public void testGetSession() {
        final Session tcpSession = makeNewTcpSession();
        final int destinationAddress = tcpSession.getDestIp();
        final int destinationPort = tcpSession.getDestPort();
        final int sourceAddress = tcpSession.getSourceIp();
        final int sourcePort = tcpSession.getSourcePort();

        final Session session = SessionManager.INSTANCE.getSession(destinationAddress, destinationPort, sourceAddress, sourcePort);
        assertEquals(session, tcpSession);

        // Release prevents other unit tests from being interrupted.
        SessionManager.INSTANCE.closeSession(tcpSession);
    }

    @Test
    public void testGetSessionByKey() {
        final Session tcpSession = makeNewTcpSession();
        final int destinationAddress = tcpSession.getDestIp();
        final int destinationPort = tcpSession.getDestPort();
        final int sourceAddress = tcpSession.getSourceIp();
        final int sourcePort = tcpSession.getSourcePort();
        final String key = SessionManager.INSTANCE.createKey(destinationAddress, destinationPort, sourceAddress, sourcePort);
        final Session session = SessionManager.INSTANCE.getSessionByKey(key);
        assertEquals(session, tcpSession);

        // Release prevents other unit tests from being interrupted.
        SessionManager.INSTANCE.closeSession(tcpSession);
    }

    @Test
    public void testGetSessionByChannel() {
        final Session tcpSession = makeNewTcpSession();
        final AbstractSelectableChannel channel = tcpSession.getChannel();

        final Session session = SessionManager.INSTANCE.getSessionByChannel(channel);
        assertEquals(tcpSession, session);

        // Release prevents other unit tests from being interrupted.
        SessionManager.INSTANCE.closeSession(tcpSession);
    }

    @Test
    public void testCloseSessionByInt() {
        final Session session = makeNewTcpSession();
        final int destinationAddress = session.getDestIp();
        final int destinationPort = session.getDestPort();
        final int sourceAddress = session.getSourceIp();
        final int sourcePort = session.getSourcePort();

        SessionManager.INSTANCE.closeSession(destinationAddress, destinationPort, sourceAddress, sourcePort);
        final Session result = SessionManager.INSTANCE.getSession(destinationAddress, destinationPort, sourceAddress, sourcePort);
        assertNull(result);
    }

    @Test
    public void testCloseSessionBySession() {
        // UDP connection to Google Public DNS Server(8.8.8.8) for TEST
        final Session session = SessionManager.INSTANCE.createNewUDPSession(0x08080808, 53, 0, 1);
        assertNotNull(session);
        SessionManager.INSTANCE.closeSession(session);
        assertNull(SessionManager.INSTANCE.getSession(0x08080808, 53, 0, 1));
    }

    @Test
    public void testCreateNewUDPSession() {
        // UDP connection to Google Public DNS Server(8.8.8.8) for TEST
        final Session session = SessionManager.INSTANCE.createNewUDPSession(0x08080808, 53, 0, 1);
        assertNotNull(session);
        assertEquals(session, SessionManager.INSTANCE.getSession(0x08080808, 53, 0, 1));

        // Release prevents other unit tests from being interrupted.
        SessionManager.INSTANCE.closeSession(session);
    }

    private int getTcpAddress() {
        int destinationAddress = 0;
        try {
            final InetAddress address = InetAddress.getByName("www.google.com");
            // convert it to int address
            final byte[] bytesAddress = address.getAddress();
            destinationAddress = ((bytesAddress[0] & 0xff) << 24) + ((bytesAddress[1] & 0xff) << 16) +
                    ((bytesAddress[2] & 0xff) << 8) + (bytesAddress[3] & 0xff);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return destinationAddress;
    }

    @NonNull
    private Session makeNewTcpSession() {
        final int destinationAddress = getTcpAddress();
        final Session session = SessionManager.INSTANCE.createNewSession(destinationAddress, 443, 0, 1);
        assertNotNull(session);
        assertEquals(session, SessionManager.INSTANCE.getSession(destinationAddress, 443, 0, 1));
        return session;
    }

    @Test
    public void testCreateNewSession() {
        final Session session = makeNewTcpSession();

        // Release prevents other unit tests from being interrupted.
        SessionManager.INSTANCE.closeSession(session);
    }

    @Test
    public void testCreateKey() {
        final String key = SessionManager.INSTANCE.createKey(1, 2, 3, 4);
        assertEquals(key, "0.0.0.3:4-0.0.0.1:2");
    }
}