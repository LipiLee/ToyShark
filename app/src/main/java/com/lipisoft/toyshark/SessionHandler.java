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

import com.lipisoft.toyshark.network.ip.IPPacketFactory;
import com.lipisoft.toyshark.network.ip.IPv4Header;
import com.lipisoft.toyshark.socket.SocketData;
import com.lipisoft.toyshark.transport.tcp.PacketHeaderException;
import com.lipisoft.toyshark.transport.tcp.TCPHeader;
import com.lipisoft.toyshark.transport.tcp.TCPPacketFactory;
import com.lipisoft.toyshark.transport.ITransportHeader;
import com.lipisoft.toyshark.transport.udp.UDPHeader;
import com.lipisoft.toyshark.transport.udp.UDPPacketFactory;
import com.lipisoft.toyshark.util.PacketUtil;

import android.os.Message;
import android.util.Log;

/**
 * handle VPN client request and response. it create a new session for each VPN client.
 * @author Borey Sao
 * Date: May 22, 2014
 */
class SessionHandler {
	private static final String TAG = "SessionHandler";

	private static final Object synObject = new Object();
	private static volatile SessionHandler handler;
	private SessionManager sessionManager;
	private IClientPacketWriter writer;
	private SocketData packetData;

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
		int sourceIP = ipHeader.getSourceIP();
		int destinationIP = ipHeader.getDestinationIP();
		int sourcePort = tcpheader.getSourcePort();
		int destinationPort = tcpheader.getDestinationPort();

		if(tcpheader.isSYN()) {
			//3-way handshake + create new session
			//set windows size and scale, set reply time in options
			replySynAck(ipHeader,tcpheader);
		} else if(tcpheader.isACK()) {
			String key = SessionManager.createKey(destinationIP, destinationPort, sourceIP, sourcePort);
			Session session = sessionManager.getSessionByKey(key);

			if(session == null) {
				Log.e(TAG,"**** ==> Session not found: " + key);
				if(!tcpheader.isRST() && !tcpheader.isFIN()){
					sendRstPacket(ipHeader, tcpheader, dataLength);
				}
				return;
			}

			//any data from client?
			if(dataLength > 0){
				//accumulate data from client
				int totalAdded = sessionManager.addClientData(ipHeader, tcpheader, clientPacketData);
				if(totalAdded > 0){
					//send ack to client only if new data was added
					sendAck(ipHeader,tcpheader,totalAdded, session);
				}
			} else {
				//an ack from client for previously sent data
				acceptAck(ipHeader,tcpheader, session);

				if(session.isClosingConnection()){
					sendFinAck(ipHeader, tcpheader, session);
				}else if(session.isAckedToFin() && !tcpheader.isFIN()){
					//the last ACK from client after FIN-ACK flag was sent
					sessionManager.closeSession(destinationIP, destinationPort, sourceIP, sourcePort);
					Log.d(TAG,"got last ACK after FIN, session is now closed.");
				}
			}
			//received the last segment of data from vpn client
			if(tcpheader.isPSH()){
				//push data to destination here. Background thread will receive data and fill session's buffer.
				//Background thread will send packet to client
				pushDataToDestination(session, ipHeader, tcpheader);
			} else if(tcpheader.isFIN()){
				//fin from vpn client is the last packet
				//ack it
				Log.d(TAG,"FIN from vpn client, will ack it.");
				ackFinAck(ipHeader, tcpheader, session);
			} else if(tcpheader.isRST()){
				resetConnection(ipHeader, tcpheader);
			}

			if(!session.isClientWindowFull() && !session.isAbortingConnection()){
				sessionManager.keepSessionAlive(session);
			}
		} else if(tcpheader.isFIN()){
			//case client sent FIN without ACK
			Session session = sessionManager.getSession(destinationIP, destinationPort, sourceIP, sourcePort);
			if(session == null)
				ackFinAck(ipHeader, tcpheader, null);
			else
				sessionManager.keepSessionAlive(session);

		} else if(tcpheader.isRST()){
			resetConnection(ipHeader, tcpheader);
		} else{
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
	 * @param length packet length to be read
	 * @throws PacketHeaderException throws PacketHeaderException
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

		final ITransportHeader transportHeader;
		if(ipHeader.getProtocol() == 6) {
			transportHeader = TCPPacketFactory.createTCPHeader(clientPacketData, ipHeader.getIPHeaderLength());
		} else if(ipHeader.getProtocol() == 17) {
			transportHeader = UDPPacketFactory.createUDPHeader(clientPacketData, ipHeader.getIPHeaderLength());
		} else {
			Log.e(TAG, "******===> Unsupported protocol: " + ipHeader.getProtocol());
			return;
		}
		Packet packet = new Packet(ipHeader, transportHeader, clientPacketData);

		Message message = MainActivity.mHandler.obtainMessage(MainActivity.PACKET, packet);
		message.sendToTarget();

		if(transportHeader instanceof TCPHeader){
			handleTCPPacket(clientPacketData, ipHeader, (TCPHeader) transportHeader);
		}else if(ipHeader.getProtocol() == 17){
			handleUDPPacket(clientPacketData, ipHeader, (UDPHeader) transportHeader);
		}
	}

	private void sendRstPacket(IPv4Header ip, TCPHeader tcp, int dataLength){
		byte[] data = TCPPacketFactory.createRstData(ip, tcp, dataLength);
		try {
			writer.write(data);
			packetData.addData(data);
			Log.d(TAG,"Sent RST Packet to client with dest => " +
					PacketUtil.intToIPAddress(ip.getDestinationIP()) + ":" +
					tcp.getDestinationPort());
		} catch (IOException e) {
			Log.e(TAG,"failed to send RST packet: " + e.getMessage());
		}
	}
	private void ackFinAck(IPv4Header ip, TCPHeader tcp, Session session){
		//TODO: check if client only sent FIN without ACK
		long ack = tcp.getSequenceNumber() + 1;
		long seq = tcp.getAckNumber();
		byte[] data = TCPPacketFactory.createFinAckData(ip, tcp, ack, seq, true, true);
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
		long ack = tcp.getSequenceNumber();
		long seq = tcp.getAckNumber();
		byte[] data = TCPPacketFactory.createFinAckData(ip, tcp, ack, seq,true,false);
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
					vpntcp = TCPPacketFactory.createTCPHeader(data, vpnip.getIPHeaderLength());
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
		long acknumber = session.getRecSequence() + acceptedDataLength;
		Log.d(TAG,"sent ack, ack# "+session.getRecSequence()+" + "+acceptedDataLength+" = "+acknumber);
		session.setRecSequence(acknumber);
		byte[] data = TCPPacketFactory.createResponseAckData(ipheader, tcpheader, acknumber);
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
			long byteReceived = tcpHeader.getAckNumber() - session.getSendUnack();
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
		Packet packet = TCPPacketFactory.createSynAckPacketData(ip, tcp);
		
		TCPHeader tcpheader = (TCPHeader) packet.getTransportHeader();
		
		Session session = sessionManager.createNewSession(ip.getDestinationIP(),
				tcp.getDestinationPort(), ip.getSourceIP(), tcp.getSourcePort());
		if(session == null)
			return;
		
		int windowScaleFactor = (int) Math.pow(2, tcpheader.getWindowScale());
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
