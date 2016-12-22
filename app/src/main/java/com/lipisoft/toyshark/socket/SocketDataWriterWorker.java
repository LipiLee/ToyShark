package com.lipisoft.toyshark.socket;

import android.util.Log;

import com.lipisoft.toyshark.IClientPacketWriter;
import com.lipisoft.toyshark.Session;
import com.lipisoft.toyshark.SessionManager;
import com.lipisoft.toyshark.tcp.TCPPacketFactory;
import com.lipisoft.toyshark.udp.UDPPacketFactory;
import com.lipisoft.toyshark.util.PacketUtil;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.util.Date;

class SocketDataWriterWorker implements Runnable{
	public static final String TAG = "SocketDataWriterWorker";
	private IClientPacketWriter writer;
	private TCPPacketFactory tcpPacketFactory;
	private UDPPacketFactory udpPacketFactory;
	private SessionManager sessionManager;
	private String sessionKey = "";
	private SocketData pdata;

	SocketDataWriterWorker(TCPPacketFactory tcpPacketFactory, UDPPacketFactory udpPacketFactory, IClientPacketWriter writer){
		sessionManager = SessionManager.getInstance();
		pdata = SocketData.getInstance();
		this.tcpPacketFactory = tcpPacketFactory;
		this.udpPacketFactory = udpPacketFactory;
		this.writer = writer;
	}

	public String getSessionKey() {
		return sessionKey;
	}

	void setSessionKey(String sessionKey) {
		this.sessionKey = sessionKey;
	}

	@Override
	public void run() {
		Session sess = sessionManager.getSessionByKey(sessionKey);
		if(sess == null){
			return;
		}
		sess.setBusywrite(true);
		if(sess.getSocketChannel() != null){
			writeTCP(sess);
		}else if(sess.getUdpChannel() != null){
			writeUDP(sess);
		}
		sess.setBusywrite(false);
		if(sess.isAbortingConnection()){
			Log.d(TAG,"removing aborted connection -> "+
					PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()
					+"-"+PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort());
			sess.getSelectionkey().cancel();
			if(sess.getSocketChannel() != null && sess.getSocketChannel().isConnected()){
				try {
					sess.getSocketChannel().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else if(sess.getUdpChannel() != null && sess.getUdpChannel().isConnected()){
				try {
					sess.getUdpChannel().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			sessionManager.closeSession(sess);
		}

	}
	private void writeUDP(Session sess){
		if(!sess.hasDataToSend()){
			return;
		}
		DatagramChannel channel = sess.getUdpChannel();
		String name = PacketUtil.intToIPAddress(sess.getDestAddress())+":"+sess.getDestPort()+
				"-"+PacketUtil.intToIPAddress(sess.getSourceIp())+":"+sess.getSourcePort();
		byte[] data = sess.getSendingData();
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
			sess.connectionStartTime = dt.getTime();
		}catch(NotYetConnectedException ex2){
			sess.setAbortingConnection(true);
			Log.e(TAG,"Error writing to unconnected-UDP server, will abort current connection: "+ex2.getMessage());
		} catch (IOException e) {
			sess.setAbortingConnection(true);
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
			byte[] rstdata = tcpPacketFactory.createRstData(session.getLastIpHeader(), session.getLastTcpHeader(), 0);
			try {
				writer.write(rstdata);
				pdata.addData(rstdata);
			} catch (IOException ex) {
				ex.printStackTrace();
			}
			//remove session
			Log.e(TAG,"failed to write to remote socket, aborting connection");
			session.setAbortingConnection(true);
		}
		
	}

}
