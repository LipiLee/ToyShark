package com.lipisoft.toyshark.socket;

import android.support.annotation.NonNull;
import android.util.Log;

import com.lipisoft.toyshark.IClientPacketWriter;
import com.lipisoft.toyshark.Session;
import com.lipisoft.toyshark.SessionManager;
import com.lipisoft.toyshark.tcp.TCPPacketFactory;
import com.lipisoft.toyshark.util.PacketUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.util.Date;

public class SocketDataWriterWorker implements Runnable {
	private static final String TAG = "SocketDataWriterWorker";

	private static IClientPacketWriter writer;
	@NonNull private String sessionKey;

	SocketDataWriterWorker(IClientPacketWriter writer, @NonNull String sessionKey) {
		this.writer = writer;
		this.sessionKey = sessionKey;
	}

	@Override
	public void run() {
		final SessionManager sessionManager = SessionManager.getInstance();
		final Session session = sessionManager.getSessionByKey(sessionKey);
		if(session == null) {
			Log.d(TAG, "No session related to " + sessionKey + "for write");
			return;
		}

		session.setBusywrite(true);
		if(session.getSocketChannel() != null){
			writeTCP(session);
		}else if(session.getUdpChannel() != null){
			writeUDP(session);
		}
		session.setBusywrite(false);

		if(session.isAbortingConnection()){
			Log.d(TAG,"removing aborted connection -> " + sessionKey);
			session.getSelectionkey().cancel();
			if(session.getSocketChannel() != null &&
					session.getSocketChannel().isConnected()){
				try {
					session.getSocketChannel().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else if(session.getUdpChannel() != null &&
					session.getUdpChannel().isConnected()){
				try {
					session.getUdpChannel().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			sessionManager.closeSession(session);
		}
	}
	private void writeUDP(Session session){
		if(!session.hasDataToSend()){
			return;
		}
		DatagramChannel channel = session.getUdpChannel();
		String name = PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()+
				"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort();
		byte[] data = session.getSendingData();
		ByteBuffer buffer = ByteBuffer.allocate(data.length);
		buffer.put(data);
		buffer.flip();
		try {
			String str = new String(data);
			Log.d(TAG,"****** data write to server ********");
			Log.d(TAG,str);
			Log.d(TAG,"***** end writing to server *******");
			Log.d(TAG,"writing data to remote UDP: "+name);
			channel.write(buffer);
			Date dt = new Date();
			session.connectionStartTime = dt.getTime();
		}catch(NotYetConnectedException ex2){
			session.setAbortingConnection(true);
			Log.e(TAG,"Error writing to unconnected-UDP server, will abort current connection: "+ex2.getMessage());
		} catch (IOException e) {
			session.setAbortingConnection(true);
			e.printStackTrace();
			Log.e(TAG,"Error writing to UDP server, will abort connection: "+e.getMessage());
		}
	}
	
	private void writeTCP(Session session){
		SocketChannel channel = session.getSocketChannel();

		String name = PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()+
				"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort();
		
		byte[] data = session.getSendingData();
		ByteBuffer buffer = ByteBuffer.allocate(data.length);
		buffer.put(data);
		buffer.flip();
		
		try {
			Log.d(TAG,"writing TCP data to: "+name);
			channel.write(buffer);
			//Log.d(TAG,"finished writing data to: "+name);
		}catch(NotYetConnectedException ex){
			Log.e(TAG,"failed to write to unconnected socket: "+ex.getMessage());
		} catch (IOException e) {
			Log.e(TAG,"Error writing to server: "+e.getMessage());
			
			//close connection with vpn client
			byte[] rstData = TCPPacketFactory.createRstData(
					session.getLastIpHeader(), session.getLastTcpHeader(), 0);
			try {
				writer.write(rstData);
				SocketData socketData = SocketData.getInstance();
				socketData.addData(rstData);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			//remove session
			Log.e(TAG,"failed to write to remote socket, aborting connection");
			session.setAbortingConnection(true);
		}
	}
}
