package com.lipisoft.toyshark.socket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SocketChannel;
import java.util.Date;

import android.util.Log;

import com.lipisoft.toyshark.IClientPacketWriter;
import com.lipisoft.toyshark.Session;
import com.lipisoft.toyshark.SessionManager;
import com.lipisoft.toyshark.ip.IPPacketFactory;
import com.lipisoft.toyshark.ip.IPv4Header;
import com.lipisoft.toyshark.tcp.PacketHeaderException;
import com.lipisoft.toyshark.tcp.TCPHeader;
import com.lipisoft.toyshark.tcp.TCPPacketFactory;
import com.lipisoft.toyshark.udp.UDPHeader;
import com.lipisoft.toyshark.udp.UDPPacketFactory;
import com.lipisoft.toyshark.util.PacketUtil;

/**
 * background task for reading data from remote server and write data to vpn client
 * @author Borey Sao
 * Date: July 30, 2014
 */
class SocketDataReaderWorker implements Runnable {
	public static final String TAG = "AROCollector";
	private IClientPacketWriter writer;
	private TCPPacketFactory factory;
	private UDPPacketFactory udpfactory;
	private SessionManager sessionmg;
	private String sessionKey = "";
	private SocketData pdata;
	public SocketDataReaderWorker(){
		sessionmg = SessionManager.getInstance();
		pdata = SocketData.getInstance();
	}
	SocketDataReaderWorker(TCPPacketFactory tcpfactory, UDPPacketFactory udpfactory, IClientPacketWriter writer){
		sessionmg = SessionManager.getInstance();
		pdata = SocketData.getInstance();
		this.factory = tcpfactory;
		this.udpfactory = udpfactory;
		this.writer = writer;
	}
	@Override
	public void run() {
		Session session = sessionmg.getSessionByKey(sessionKey);
		if(session == null){
			return;
		}
		if(session.getSocketchannel() != null) {
			try{
				readTCP(session);
			} catch(Exception ex){
				Log.e(TAG, "error processRead: "+ ex.getMessage());
			}
		} else if(session.getUdpchannel() != null){
			readUDP(session);
		}

			
		if(session.isAbortingConnection()){
			Log.d(TAG,"removing aborted connection -> "+
					PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()
					+"-"+PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort());
			session.getSelectionkey().cancel();
			if(session.getSocketchannel() != null && session.getSocketchannel().isConnected()){
				try {
					session.getSocketchannel().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}else if(session.getUdpchannel() != null && session.getUdpchannel().isConnected()){
				try {
					session.getUdpchannel().close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			sessionmg.closeSession(session);
		}else{
			session.setBusyread(false);
		}

	}
	
	private void readTCP(Session session){
		SocketChannel channel = session.getSocketchannel();
		ByteBuffer buffer = ByteBuffer.allocate(DataConst.MAX_RECEIVE_BUFFER_SIZE);
		int len;
		String name = PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()+"-"+
				PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort();
		try {
			
			do{
				if(session.isAbortingConnection()){
					return;//break;
				}
				
				if(!session.isClientWindowFull()){
					len = channel.read(buffer);
					if(len > 0){//-1 mean it reach the end of stream
						
						//Log.d(TAG,"SocketDataService received "+len+" from remote server: "+name);
						sendToRequester(buffer,channel, len, session);
						buffer.clear();
					}else if(len == -1){
						Log.d(TAG,"====> End of data from remote server, will send FIN to client <====");
						Log.d(TAG,"==> send FIN to: "+name);
						sendFin(session);
						session.setAbortingConnection(true);
					}
				}else{

					Log.e(TAG,"*** client window is full, now pause for "+PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()+"-"+
							PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort());
					break;
				}
			}while(len > 0);
		}catch(NotYetConnectedException e){
			Log.e(TAG,"socket not connected");
		}catch(ClosedByInterruptException e){
			Log.e(TAG,"ClosedByInterruptException reading socketchannel: "+ e.getMessage());
			//sess.setAbortingConnection(true);
		}catch(ClosedChannelException e){
			Log.e(TAG,"ClosedChannelException reading socketchannel: "+ e.getMessage());
			//sess.setAbortingConnection(true);
		} catch (IOException e) {
			Log.e(TAG,"Error reading data from socketchannel: "+ e.getMessage());
			session.setAbortingConnection(true);
		}
	}
	
	private void sendToRequester(ByteBuffer buffer, SocketChannel channel, int datasize, Session sess){
		
		if(sess == null){
			Log.e(TAG,"Session not found for dest. server: "+channel.socket().getInetAddress().getHostAddress());
			return;
		}
		
		//last piece of data is usually smaller than MAX_RECEIVE_BUFFER_SIZE
		if(datasize < DataConst.MAX_RECEIVE_BUFFER_SIZE)
			sess.setHasReceivedLastSegment(true);
		else
			sess.setHasReceivedLastSegment(false);

		buffer.limit(datasize);
		buffer.flip();
		byte[] data = new byte[datasize];
		System.arraycopy(buffer.array(), 0, data, 0, datasize);
		sess.addReceivedData(data);
		//Log.d(TAG,"DataSerice added "+data.length+" to session. session.getReceivedDataSize(): "+session.getReceivedDataSize());
		//pushing all data to vpn client
		while(sess.hasReceivedData()){
			pushDataToClient(sess);
		}
	}
	/**
	 * create packet data and send it to VPN client
	 * @param session Session
	 * @return boolean
	 */
	private boolean pushDataToClient(Session session){
		if(!session.hasReceivedData()){
			//no data to send
			Log.d(TAG,"no data for vpn client");
			return false;
		}
		
		IPv4Header ipheader = session.getLastIPheader();
		TCPHeader tcpheader = session.getLastTCPheader();
		int max = session.getMaxSegmentSize() - 60;
		
		if(max < 1){
			max = 1024;
		}
		byte[] packetbody = session.getReceivedData(max);
		if(packetbody != null && packetbody.length > 0){
			int unack = session.getSendNext();
			int nextUnack = session.getSendNext() + packetbody.length;
			//Log.d(TAG,"sending vpn client body len: "+packetbody.length+", current seq: "+unack+", next seq: "+nextUnack);
			session.setSendNext(nextUnack);
			//we need this data later on for retransmission
			session.setUnackData(packetbody);
			session.setResendPacketCounter(0);
			
			byte[] data = factory.createResponsePacketData(ipheader, tcpheader, packetbody, session.hasReceivedLastSegment(), 
					session.getRecSequence(), unack, session.getTimestampSender(), session.getTimestampReplyto());
			try {
				writer.write(data);
				pdata.addData(data);
				//Log.d(TAG,"finished sending "+data.length+" to vpn client: "+PacketUtil.intToIPAddress(session.getDestAddress())+":"+session.getDestPort()+"-"+
				//		PacketUtil.intToIPAddress(session.getSourceIp())+":"+session.getSourcePort());
				
				/* for debugging purpose 
				Log.d(TAG,"========> BG: packet data to vpn client++++++++");
				IPv4Header vpnip = null;
				try {
					vpnip = factory.createIPv4Header(data, 0);
				} catch (PacketHeaderException e) {
					e.printStackTrace();
				}
				TCPHeader vpntcp = null;
				try {
					vpntcp = factory.createTCPHeader(data, vpnip.getIPHeaderLength());
				} catch (PacketHeaderException e) {
					e.printStackTrace();
				}
				if(vpnip != null && vpntcp != null){
					String sout = PacketUtil.getOutput(vpnip, vpntcp, data);
					Log.d(TAG,sout);
				}
				
				Log.d(TAG,"=======> BG: finished sending packet to vpn client ========");
				if(vpntcp != null){
					int offset = vpntcp.getTCPHeaderLength() + vpnip.getIPHeaderLength();
					int bodysize = data.length - offset;
					byte[] clientdata = new byte[bodysize];
	        		System.arraycopy(data, offset, clientdata, 0, bodysize);
	        		Log.d(TAG,"444444 Packet Data sent to Client 444444");
	        		String svpn = new String(clientdata);
	        		Log.d(TAG,svpn);
	        		Log.d(TAG,"444444 End Data to Client 4444444");
				}
				*/
				
			} catch (IOException e) {
				Log.e(TAG,"Failed to send ACK+Data packet: "+e.getMessage());
				return false;
			}
			return true;
		}
		return false;
	}
	private void sendFin(Session session){
		IPv4Header ipheader = session.getLastIPheader();
		TCPHeader tcpheader = session.getLastTCPheader();
		byte[] data = factory.createFinData(ipheader, tcpheader, session.getSendNext(), session.getRecSequence(), session.getTimestampSender(), session.getTimestampReplyto());
		try {
			writer.write(data);
			pdata.addData(data);
			/* for debugging purpose 
			Log.d(TAG,"========> BG: FIN packet data to vpn client++++++++");
			IPv4Header vpnip = null;
			try {
				vpnip = factory.createIPv4Header(data, 0);
			} catch (PacketHeaderException e) {
				e.printStackTrace();
			}
			TCPHeader vpntcp = null;
			try {
				vpntcp = factory.createTCPHeader(data, vpnip.getIPHeaderLength());
			} catch (PacketHeaderException e) {
				e.printStackTrace();
			}
			if(vpnip != null && vpntcp != null){
				String sout = PacketUtil.getOutput(vpnip, vpntcp, data);
				Log.d(TAG,sout);
			}
			
			Log.d(TAG,"=======> BG: finished sending FIN packet to vpn client ========");
			*/
			
		} catch (IOException e) {
			Log.e(TAG,"Failed to send FIN packet: "+e.getMessage());
			
		}
	}
	private void readUDP(Session sess){
		DatagramChannel channel = sess.getUdpchannel();
		ByteBuffer buffer = ByteBuffer.allocate(DataConst.MAX_RECEIVE_BUFFER_SIZE);
		int len;
		try {
			do{
				if(sess.isAbortingConnection()){
					break;
				}
				len = channel.read(buffer);
				if(len > 0){
					Date dt = new Date();
					long restime = dt.getTime() - sess.connectionStartTime;
					
					buffer.limit(len);
					buffer.flip();
					//create UDP packet
					byte[] data = new byte[len];
					System.arraycopy(buffer.array(),0, data, 0, len);
					byte[] packetdata = udpfactory.createResponsePacket(sess.getLastIPheader(), sess.getLastUDPheader(), data);
					//write to client
					writer.write(packetdata);
					//publish to packet subscriber
					pdata.addData(packetdata);
					Log.d(TAG,"SDR: sent "+len+" bytes to UDP client, packetdata.length: "+packetdata.length);
					buffer.clear();
					
					try {
						IPv4Header ip = IPPacketFactory.createIPv4Header(packetdata, 0);
						UDPHeader udp = udpfactory.createUDPHeader(packetdata, ip.getIPHeaderLength());
						String str = PacketUtil.getUDPoutput(ip, udp);
						Log.d(TAG,"++++++ SD: packet sending to client ++++++++");
						Log.i(TAG,"got response time: "+restime);
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
			Log.e(TAG,"Faild to read from UDP socket, aborting connection");
			sess.setAbortingConnection(true);
		}
	}
	public String getSessionKey() {
		return sessionKey;
	}
	void setSessionKey(String sessionKey) {
		this.sessionKey = sessionKey;
	}
}
