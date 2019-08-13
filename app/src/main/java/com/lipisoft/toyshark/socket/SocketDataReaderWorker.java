package com.lipisoft.toyshark.socket;

import androidx.annotation.NonNull;
import android.util.Log;

import com.lipisoft.toyshark.IClientPacketWriter;
import com.lipisoft.toyshark.Session;
import com.lipisoft.toyshark.SessionManager;
import com.lipisoft.toyshark.network.ip.IPPacketFactory;
import com.lipisoft.toyshark.network.ip.IPv4Header;
import com.lipisoft.toyshark.packetRebuild.PCapFileWriter;
import com.lipisoft.toyshark.transport.tcp.PacketHeaderException;
import com.lipisoft.toyshark.transport.tcp.TCPHeader;
import com.lipisoft.toyshark.transport.tcp.TCPPacketFactory;
import com.lipisoft.toyshark.transport.udp.UDPHeader;
import com.lipisoft.toyshark.transport.udp.UDPPacketFactory;
import com.lipisoft.toyshark.util.PacketUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.Date;

/**
 * background task for reading data from remote server and write data to vpn client
 * @author Borey Sao
 * Date: July 30, 2014
 */
class SocketDataReaderWorker implements Runnable {
	private static final String TAG = "SocketDataReaderWorker";
	private IClientPacketWriter writer;
	private String sessionKey;
	private SocketData pData;

	SocketDataReaderWorker(IClientPacketWriter writer, String sessionKey) {
		pData = SocketData.getInstance();
		this.writer = writer;
		this.sessionKey = sessionKey;
	}

	@Override
	public void run() {
		Session session = SessionManager.INSTANCE.getSessionByKey(sessionKey);
		if(session == null) {
			Log.e(TAG, "Session NOT FOUND");
			return;
		}

		AbstractSelectableChannel channel = session.getChannel();

		if(channel instanceof SocketChannel) {
			readTCP(session);
		} else if(channel instanceof DatagramChannel){
			readUDP(session);
		} else {
			return;
		}

		if(session.isAbortingConnection()) {
			Log.d(TAG,"removing aborted connection -> "+ sessionKey);
			session.getSelectionKey().cancel();
			if (channel instanceof SocketChannel){
				try {
					SocketChannel socketChannel = (SocketChannel) channel;
					if (socketChannel.isConnected()) {
						socketChannel.close();
					}
				} catch (IOException e) {
					Log.e(TAG, e.toString());
				}
			} else {
				try {
					DatagramChannel datagramChannel = (DatagramChannel) channel;
					if (datagramChannel.isConnected()) {
						datagramChannel.close();
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			SessionManager.INSTANCE.closeSession(session);
		} else {
			session.setBusyread(false);
		}
	}
	
	private void readTCP(@NonNull Session session) {
		if(session.isAbortingConnection()){
			return;
		}

		SocketChannel channel = (SocketChannel) session.getChannel();
		ByteBuffer buffer = ByteBuffer.allocate(DataConst.MAX_RECEIVE_BUFFER_SIZE);
		int len;

		try {
			do {
				if(!session.isClientWindowFull()) {
					len = channel.read(buffer);
					if(len > 0) { //-1 mean it reach the end of stream
						//Log.d(TAG,"SocketDataService received "+len+" from remote server: "+name);
						sendToRequester(buffer, len, session);
						buffer.clear();
					} else if(len == -1) {
						Log.d(TAG,"End of data from remote server, will send FIN to client");
						Log.d(TAG,"send FIN to: " + sessionKey);
						sendFin(session);
						session.setAbortingConnection(true);
					}
				} else {
					Log.e(TAG,"*** client window is full, now pause for " + sessionKey);
					break;
				}
			} while(len > 0);
		}catch(NotYetConnectedException e){
			Log.e(TAG,"socket not connected");
		}catch(ClosedByInterruptException e){
			Log.e(TAG,"ClosedByInterruptException reading SocketChannel: "+ e.getMessage());
			//session.setAbortingConnection(true);
		}catch(ClosedChannelException e){
			Log.e(TAG,"ClosedChannelException reading SocketChannel: "+ e.getMessage());
			//session.setAbortingConnection(true);
		} catch (IOException e) {
			Log.e(TAG,"Error reading data from SocketChannel: "+ e.getMessage());
			session.setAbortingConnection(true);
		}
	}
	
	private void sendToRequester(ByteBuffer buffer, int dataSize, @NonNull Session session){
		//last piece of data is usually smaller than MAX_RECEIVE_BUFFER_SIZE
		if(dataSize < DataConst.MAX_RECEIVE_BUFFER_SIZE)
			session.setHasReceivedLastSegment(true);
		else
			session.setHasReceivedLastSegment(false);

		buffer.limit(dataSize);
		buffer.flip();
		// TODO should allocate new byte array?
		byte[] data = new byte[dataSize];
		System.arraycopy(buffer.array(), 0, data, 0, dataSize);
		session.addReceivedData(data);
		//Log.d(TAG,"DataService added "+data.length+" to session. session.getReceivedDataSize(): "+session.getReceivedDataSize());
		//pushing all data to vpn client
		while(session.hasReceivedData()){
			pushDataToClient(session);
		}
	}
	/**
	 * create packet data and send it to VPN client
	 * @param session Session
	 */
	private void pushDataToClient(@NonNull Session session){
		if(!session.hasReceivedData()){
			//no data to send
			Log.d(TAG,"no data for vpn client");
		}

		IPv4Header ipHeader = session.getLastIpHeader();
		TCPHeader tcpheader = session.getLastTcpHeader();
		// TODO What does 60 mean?
		int max = session.getMaxSegmentSize() - 60;

		if(max < 1) {
			max = 1024;
		} else if (max > PCapFileWriter.MAX_PACKET_SIZE - 60) {
			max = PCapFileWriter.MAX_PACKET_SIZE - 60;
		}
		byte[] packetBody = session.getReceivedData(max);
		if(packetBody != null && packetBody.length > 0) {
			long unAck = session.getSendNext();
			long nextUnAck = session.getSendNext() + packetBody.length;
			//Log.d(TAG,"sending vpn client body len: "+packetBody.length+", current seq: "+unAck+", next seq: "+nextUnAck);
			session.setSendNext(nextUnAck);
			//we need this data later on for retransmission
			session.setUnackData(packetBody);
			session.setResendPacketCounter(0);

			byte[] data = TCPPacketFactory.createResponsePacketData(ipHeader,
					tcpheader, packetBody, session.hasReceivedLastSegment(),
					session.getRecSequence(), unAck,
					session.getTimestampSender(), session.getTimestampReplyto());
			try {
				writer.write(data);
				pData.addData(data);
			} catch (IOException e) {
				Log.e(TAG,"Failed to send ACK + Data packet: " + e.getMessage());
			}
		}
	}
	private void sendFin(Session session){
		final IPv4Header ipHeader = session.getLastIpHeader();
		final TCPHeader tcpheader = session.getLastTcpHeader();
		final byte[] data = TCPPacketFactory.createFinData(ipHeader, tcpheader,
				session.getSendNext(), session.getRecSequence(),
				session.getTimestampSender(), session.getTimestampReplyto());
		try {
			writer.write(data);
			pData.addData(data);
		} catch (IOException e) {
			Log.e(TAG,"Failed to send FIN packet: " + e.getMessage());
		}
	}
	private void readUDP(Session session){
		DatagramChannel channel = (DatagramChannel) session.getChannel();
		ByteBuffer buffer = ByteBuffer.allocate(DataConst.MAX_RECEIVE_BUFFER_SIZE);
		int len;

		try {
			do{
				if(session.isAbortingConnection()){
					break;
				}
				len = channel.read(buffer);
				if(len > 0){
					Date date = new Date();
					long responseTime = date.getTime() - session.connectionStartTime;
					
					buffer.limit(len);
					buffer.flip();
					//create UDP packet
					byte[] data = new byte[len];
					System.arraycopy(buffer.array(),0, data, 0, len);
					byte[] packetData = UDPPacketFactory.createResponsePacket(
							session.getLastIpHeader(), session.getLastUdpHeader(), data);
					//write to client
					writer.write(packetData);
					//publish to packet subscriber
					pData.addData(packetData);
					Log.d(TAG,"SDR: sent " + len + " bytes to UDP client, packetData.length: "
							+ packetData.length);
					buffer.clear();
					
					try {
						final ByteBuffer stream = ByteBuffer.wrap(packetData);
						IPv4Header ip = IPPacketFactory.createIPv4Header(stream);
						UDPHeader udp = UDPPacketFactory.createUDPHeader(stream);
						String str = PacketUtil.getUDPoutput(ip, udp);
						Log.d(TAG,"++++++ SD: packet sending to client ++++++++");
						Log.i(TAG,"got response time: " + responseTime);
						Log.d(TAG,str);
						Log.d(TAG,"++++++ SD: end sending packet to client ++++");
					} catch (PacketHeaderException e) {
						e.printStackTrace();
					}
				}
			} while(len > 0);
		}catch(NotYetConnectedException ex){
			Log.e(TAG,"failed to read from unconnected UDP socket");
		} catch (IOException e) {
			e.printStackTrace();
			Log.e(TAG,"Failed to read from UDP socket, aborting connection");
			session.setAbortingConnection(true);
		}
	}
}
