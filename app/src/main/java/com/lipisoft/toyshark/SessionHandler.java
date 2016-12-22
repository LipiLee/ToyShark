/*
 *  Copyright 2014 AT&T
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.lipisoft.toyshark;

import java.io.IOException;
import java.util.Date;

import com.lipisoft.toyshark.ip.IPPacketFactory;
import com.lipisoft.toyshark.ip.IPv4Header;
import com.lipisoft.toyshark.socket.SocketData;
import com.lipisoft.toyshark.tcp.PacketHeaderException;
import com.lipisoft.toyshark.tcp.TCPHeader;
import com.lipisoft.toyshark.tcp.TCPPacketFactory;
import com.lipisoft.toyshark.udp.UDPHeader;
import com.lipisoft.toyshark.udp.UDPPacketFactory;
import com.lipisoft.toyshark.util.PacketUtil;

import android.os.Message;
import android.util.Log;

/**
 * handle VPN client request and response. it create a new session for each VPN client.
 * @author Borey Sao
 * Date: May 22, 2014
 */
class SessionHandler {
	public static final String TAG = "SessionHandler";
	
	private static final Object synObject = new Object();
	private static volatile SessionHandler handler = null;
	private SessionManager sessionManager;
	private IClientPacketWriter writer;
	private TCPPacketFactory factory;
	private UDPPacketFactory udpFactory;
	private SocketData packetData = null;

	static SessionHandler getInstance() throws IOException{
		if(handler == null){
			synchronized (synObject){
				if(handler == null){
					handler = new SessionHandler();
				}
			}
		}
		return handler;
	}

	private SessionHandler() throws IOException {
		sessionManager = SessionManager.getInstance();
		factory = new TCPPacketFactory();
		udpFactory = new UDPPacketFactory();
		packetData = SocketData.getInstance();
	}

	void setWriter(IClientPacketWriter writer){
		this.writer = writer;
	}

	private void handleUDPPacket(byte[] clientPacketData, IPv4Header ipHeader, UDPHeader udpheader){
		Session session = sessionManager.getSession(ipHeader.getDestinationIP(), udpheader.getDestinationPort(),
				ipHeader.getSourceIP(), udpheader.getSourcePort());

		if(session == null){
			session = sessionManager.createNewUDPSession(ipHeader.getDestinationIP(), udpheader.getDestinationPort(),
					ipHeader.getSourceIP(), udpheader.getSourcePort());
		}

		if(session == null){
			return;
		}

		session.setLastIpHeader(ipHeader);
		session.setLastUdpHeader(udpheader);
		int len = sessionManager.addClientUDPData(ipHeader, udpheader, clientPacketData, session);
		session.setDataForSendingReady(true);
		Log.d(TAG,"added UDP data for bg worker to send: "+len);
		sessionManager.keepSessionAlive(session);
	}

	private void handleTCPPacket(byte[] clientPacketData, IPv4Header ipHeader, TCPHeader tcpheader){
		int length = clientPacketData.length;
        int dataLength = length - ipHeader.getIPHeaderLength() - tcpheader.getTCPHeaderLength();
        // for debugging purpose
        if(PacketUtil.isEnabledDebugLog()) {
	        String str = PacketUtil.getOutput(ipHeader, tcpheader, clientPacketData);
	        Log.d(TAG,">>>>>>>> Received from client <<<<<<<<<<");
	        Log.d(TAG,str);
	        Log.d(TAG,">>>>>>>>>>>>>>>>>>>end receiving from client>>>>>>>>>>>>>>>>>>>>>");
	        Log.d(TAG,"handlePacket(length) => "+length);
	        str = PacketUtil.bytesToStringArray(clientPacketData);
	        Log.d(TAG,str);
        }

        if(tcpheader.isSYN()) {
        	
        	//3-way handshake + create new session
        	//set windows size and scale, set reply time in options
        	replySynAck(ipHeader,tcpheader);
        	
        } else if(tcpheader.isACK()) {
			String key = sessionManager.createKey(ipHeader.getDestinationIP(), tcpheader.getDestinationPort(),
					ipHeader.getSourceIP(), tcpheader.getSourcePort());
        	Session session = sessionManager.getSessionByKey(key);

        	if(session == null) {
        		Log.d(TAG,"**** ==> Session not found: " + key);
        		if(!tcpheader.isRST() && !tcpheader.isFIN()){
        			this.sendRstPacket(ipHeader, tcpheader, dataLength);
        		}
    			return;
        		/*
        		if(datalength > 0){
        			//lost session? auto establish it
        			this.recreateSession(ipheader, tcpheader);
        			Log.d(TAG,"re-establish session");
        	        
        		}else{
        			//ACK to some unknown => tell client to reset
        			this.sendRstPacket(ipheader, tcpheader, datalength);
        			return;
        		}
        		*/
        	}
        	//any data from client?
        	if(dataLength > 0){
        		//accumulate data from client
        		int totalAdded = sessionManager.addClientData(ipHeader, tcpheader, clientPacketData);
        		if(totalAdded > 0){
	        		/*
	        		byte[] clientdata = new byte[totalAdded];
	        		int offset = ipheader.getIPHeaderLength() + tcpheader.getTCPHeaderLength();
	        		System.arraycopy(clientpacketdata, offset, clientdata, 0, totalAdded);
	        		Log.d(TAG,"@@@@@@ Data from Client @@@@@@@@@@@@");
	        		String svpn = new String(clientdata);
	        		Log.d(TAG,svpn);
	        		Log.d(TAG,"@@@@@@ End Data from Client @@@@@@@@@@@@");
	        		*/
        			//send ack to client only if new data was added
        			sendAck(ipHeader,tcpheader,totalAdded, session);
        		}
        	}else{
        		//an ack from client for previously sent data
        		acceptAck(ipHeader,tcpheader, session);
        		
        		if(session.isClosingConnection()){
        			sendFinAck(ipHeader, tcpheader, session);
        		}else if(session.isAckedToFin() && !tcpheader.isFIN()){
        			//the last ACK from client after FIN-ACK flag was sent
        			sessionManager.closeSession(ipHeader.getDestinationIP(), tcpheader.getDestinationPort(),
        					ipHeader.getSourceIP(), tcpheader.getSourcePort());
        			Log.d(TAG,"got last ACK after FIN, session is now closed.");
        		}
        	}
        	//received the last segment of data from vpn client
        	if(tcpheader.isPSH()){
        		//push data to destination here. Background thread will receive data and fill session's buffer.
        		//Background thread will send packet to client
        		pushDataToDestination(session, ipHeader, tcpheader);
        	}else if(tcpheader.isFIN()){
        		//fin from vpn client is the last packet
        		//ack it
        		Log.d(TAG,"FIN from vpn client, will ack it.");
        		ackFinAck(ipHeader, tcpheader, session);
        	}else if(tcpheader.isRST()){
        		resetConnection(ipHeader, tcpheader);
        	}
        	if(!session.isClientWindowFull() && !session.isAbortingConnection()){
        		sessionManager.keepSessionAlive(session);
        	}
        }else if(tcpheader.isFIN()){
        	//case client sent FIN without ACK
        	Session session = sessionManager.getSession(ipHeader.getDestinationIP(), tcpheader.getDestinationPort(),
    				ipHeader.getSourceIP(), tcpheader.getSourcePort());
        	if(session == null)
        		ackFinAck(ipHeader, tcpheader, null);
        	else
        		sessionManager.keepSessionAlive(session);

        }else if(tcpheader.isRST()){
        	Log.d(TAG,"**** Reset client connection for dest: "+PacketUtil.intToIPAddress(ipHeader.getDestinationIP())+":"+tcpheader.getDestinationPort()
        			+"-"+PacketUtil.intToIPAddress(ipHeader.getSourceIP())+":"+tcpheader.getSourcePort());
        	resetConnection(ipHeader, tcpheader);
        }else{
        	Log.d(TAG,"unknown TCP flag");
        	String str1 = PacketUtil.getOutput(ipHeader, tcpheader, clientPacketData);
            Log.d(TAG,">>>>>>>> Received from client <<<<<<<<<<");
            Log.d(TAG,str1);
            Log.d(TAG,">>>>>>>>>>>>>>>>>>>end receiving from client>>>>>>>>>>>>>>>>>>>>>");
        }
	}
	/**
	 * handle each packet from each vpn client
	 * @param data packet data
	 * @param length packet length
	 * @throws PacketHeaderException
	 */
	void handlePacket(byte[] data, int length) throws PacketHeaderException {
		byte[] clientPacketData = new byte[length];
		System.arraycopy(data, 0, clientPacketData, 0, length);
		packetData.addData(clientPacketData);
		IPv4Header ipHeader = IPPacketFactory.createIPv4Header(clientPacketData, 0);

		if(ipHeader.getIpVersion() != 4) {
			Log.e(TAG, "********===> Unsupported IP Version: " + ipHeader.getIpVersion());
			return;
		}

		UDPHeader udpheader = null;
		TCPHeader tcpheader = null;
		Packet packet = new Packet();
		packet.setIpHeader(ipHeader);
		if(ipHeader.getProtocol() == 6) {
			tcpheader = factory.createTCPHeader(clientPacketData, ipHeader.getIPHeaderLength());
			packet.setTcpHeader(tcpheader);
			packet.setBuffer(clientPacketData);
		} else if(ipHeader.getProtocol() == 17)
			udpheader = udpFactory.createUDPHeader(clientPacketData, ipHeader.getIPHeaderLength());
		else {
			Log.e(TAG, "******===> Unsupported protocol: " + ipHeader.getProtocol());
			return;
		}

		Message message = MainActivity.mHandler.obtainMessage(MainActivity.PACKET, packet);
		message.sendToTarget();

        if(tcpheader != null){
        	handleTCPPacket(clientPacketData, ipHeader, tcpheader);
        }else if(udpheader != null){
//        	Log.d(TAG,"-------- UDP packet from client ---------");
//        	String str = PacketUtil.getUDPoutput(ipheader, udpheader);
//        	Log.d(TAG,str);
//        	Log.d(TAG,"------ end UDP packet from client -------");
//        	str = PacketUtil.bytesToStringArray(clientpacketdata);
//        	Log.d(TAG,str);
        	handleUDPPacket(clientPacketData, ipHeader, udpheader);
        }
	}

	private void sendRstPacket(IPv4Header ip, TCPHeader tcp, int datalength){
		byte[] data = factory.createRstData(ip, tcp, datalength);
		try {
			writer.write(data);
			packetData.addData(data);
			Log.d(TAG,"Sent RST Packet to client with dest => "+PacketUtil.intToIPAddress(ip.getDestinationIP())+":"+tcp.getDestinationPort());
		} catch (IOException e) {
			Log.e(TAG,"failed to send RST packet: "+e.getMessage());
		}
	}
	private void ackFinAck(IPv4Header ip, TCPHeader tcp, Session session){
		//TODO: check if client only sent FIN without ACK
		int ack = tcp.getSequenceNumber() + 1;
		int seq = tcp.getAckNumber();
		byte[] data = factory.createFinAckData(ip, tcp, ack, seq, true, true);
		try {
			writer.write(data);
			packetData.addData(data);
			if(session != null){
				session.getSelectionkey().cancel();
				sessionManager.closeSession(session);
				Log.d(TAG,"ACK to client's FIN and close session => "+PacketUtil.intToIPAddress(ip.getDestinationIP())+":"+tcp.getDestinationPort()
						+"-"+PacketUtil.intToIPAddress(ip.getSourceIP())+":"+tcp.getSourcePort());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private void sendFinAck(IPv4Header ip, TCPHeader tcp, Session session){
		int ack = tcp.getSequenceNumber();
		int seq = tcp.getAckNumber();
		byte[] data = factory.createFinAckData(ip, tcp, ack, seq,true,false);
		try {
			writer.write(data);
			packetData.addData(data);
			Log.d(TAG,"00000000000 FIN-ACK packet data to vpn client 000000000000");
			IPv4Header vpnip = null;
			try {
				vpnip = IPPacketFactory.createIPv4Header(data, 0);
			} catch (PacketHeaderException e) {
				e.printStackTrace();
			}
			TCPHeader vpntcp = null;
			try {
				if (vpnip != null)
					vpntcp = factory.createTCPHeader(data, vpnip.getIPHeaderLength());
			} catch (PacketHeaderException e) {
				e.printStackTrace();
			}
			if(vpnip != null && vpntcp != null){
				String sout = PacketUtil.getOutput(vpnip, vpntcp, data);
				Log.d(TAG,sout);
			}
			Log.d(TAG,"0000000000000 finished sending FIN-ACK packet to vpn client 000000000000");
		} catch (IOException e) {
			Log.e(TAG,"Failed to send ACK packet: "+e.getMessage());
		}
		session.setSendNext(seq + 1);
		//avoid re-sending it, from here client should take care the rest
		session.setClosingConnection(false);
	}
	private void pushDataToDestination(Session session, IPv4Header ip, TCPHeader tcp){
		session.setDataForSendingReady(true);
		session.setLastIpHeader(ip);
		session.setLastTcpHeader(tcp);
		session.setTimestampReplyto(tcp.getTimeStampSender());
		Date dt = new Date();
		int timestampSender = (int)dt.getTime();
		session.setTimestampSender(timestampSender);
		Log.d(TAG,"set data ready for sending to dest, bg will do it. data size: "+session.getSendingDataSize());
	}
	
	/**
	 * send acknowledgment packet to VPN client
	 * @param ipheader IP Header
	 * @param tcpheader TCP Header
	 * @param acceptedDataLength Data Length
	 * @param session Session
	 */
	private void sendAck(IPv4Header ipheader, TCPHeader tcpheader, int acceptedDataLength, Session session){
		int acknumber = session.getRecSequence() + acceptedDataLength;
		Log.d(TAG,"sent ack, ack# "+session.getRecSequence()+" + "+acceptedDataLength+" = "+acknumber);
		session.setRecSequence(acknumber);
		byte[] data = factory.createResponseAckData(ipheader, tcpheader, acknumber);
		try {
			writer.write(data);
			packetData.addData(data);
			/* for debugging purpose
			Log.d(TAG,"&&&&&&&&&&&&& ACK packet data to vpn client &&&&&&&&&&&&&&");
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
			Log.d(TAG,"&&&&&&&&&&&& finished sending ACK packet to vpn client &&&&&&&&&&&&&&&&");
			*/
		} catch (IOException e) {
			Log.e(TAG,"Failed to send ACK packet: "+e.getMessage());
		}
	}
	/**
	 * acknowledge a packet and adjust the receiving window to avoid congestion.
	 * @param ipHeader IP Header
	 * @param tcpHeader TCP Header
	 * @param session Session
	 */
	private void acceptAck(IPv4Header ipHeader, TCPHeader tcpHeader, Session session){
		boolean isCorrupted = PacketUtil.isPacketCorrupted(tcpHeader);
		session.setPacketCorrupted(isCorrupted);
		if(isCorrupted){
			Log.e(TAG,"prev packet was corrupted, last ack# " + tcpHeader.getAckNumber());
		}
		if(tcpHeader.getAckNumber() > session.getSendUnack() ||
				tcpHeader.getAckNumber() == session.getSendNext()){
			session.setAcked(true);
			//Log.d(TAG,"Accepted ack from client, ack# "+tcpheader.getAckNumber());
			
			if(tcpHeader.getWindowSize() > 0){
				session.setSendWindowSizeAndScale(tcpHeader.getWindowSize(), session.getSendWindowScale());
			}
			int byteReceived = tcpHeader.getAckNumber() - session.getSendUnack();
			if(byteReceived > 0){
				session.decreaseAmountSentSinceLastAck(byteReceived);
			}
			if(session.isClientWindowFull()){
				Log.d(TAG,"window: "+session.getSendWindow()+" is full? "+session.isClientWindowFull() + " for "+PacketUtil.intToIPAddress(ipHeader.getDestinationIP())
					+":"+tcpHeader.getDestinationPort()+"-"+PacketUtil.intToIPAddress(ipHeader.getSourceIP())+":"+tcpHeader.getSourcePort());
			}
			session.setSendUnack(tcpHeader.getAckNumber());
			session.setRecSequence(tcpHeader.getSequenceNumber());
			session.setTimestampReplyto(tcpHeader.getTimeStampSender());
			Date dt = new Date();
			int timestampSender = (int)dt.getTime();
			session.setTimestampSender(timestampSender);
		}else{
			Log.d(TAG,"Not Accepting ack# "+tcpHeader.getAckNumber() +" , it should be: "+session.getSendNext());
			Log.d(TAG,"Prev sendUnack: "+session.getSendUnack());
			session.setAcked(false);
		}
	}
	/**
	 * set connection as aborting so that background worker will close it.
	 * @param ip IP
	 * @param tcp TCP
	 */
	private void resetConnection(IPv4Header ip, TCPHeader tcp){
		Session session = sessionManager.getSession(ip.getDestinationIP(), tcp.getDestinationPort(),
				ip.getSourceIP(), tcp.getSourcePort());
		if(session != null){
			session.setAbortingConnection(true);
		}
	}

	/**
	 * create a new client's session and SYN-ACK packet data to respond to client
	 * @param ip IP
	 * @param tcp TCP
	 */
	private void replySynAck(IPv4Header ip, TCPHeader tcp){
		ip.setIdentification(0);
		Packet packet = factory.createSynAckPacketData(ip, tcp);
		
		TCPHeader tcpheader = packet.getTcpHeader();
		
		Session session = sessionManager.createNewSession(ip.getDestinationIP(), tcp.getDestinationPort(),
													ip.getSourceIP(), tcp.getSourcePort());
		if(session == null)
			return;
		
    	int windowScaleFactor = (int) Math.pow(2,tcpheader.getWindowScale());
    	//Log.d(TAG,"window scale: Math.power(2,"+tcpheader.getWindowScale()+") is "+windowScaleFactor);
    	session.setSendWindowSizeAndScale(tcpheader.getWindowSize(), windowScaleFactor);
    	Log.d(TAG,"send-window size: " + session.getSendWindow());
    	session.setMaxSegmentSize(tcpheader.getMaxSegmentSize());
    	session.setSendUnack(tcpheader.getSequenceNumber());
    	session.setSendNext(tcpheader.getSequenceNumber() + 1);
    	//client initial sequence has been incremented by 1 and set to ack
    	session.setRecSequence(tcpheader.getAckNumber());
    	
    	try {
			writer.write(packet.getBuffer());
			packetData.addData(packet.getBuffer());
			Log.d(TAG,"Send SYN-ACK to client");
		} catch (IOException e) {
			Log.e(TAG,"Error sending data to client: "+e.getMessage());
		}
	}
}//end class
