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

package com.lipisoft.toyshark.transport.tcp;

import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

import com.lipisoft.toyshark.MainActivity;
import com.lipisoft.toyshark.Packet;
import com.lipisoft.toyshark.network.ip.IPPacketFactory;
import com.lipisoft.toyshark.network.ip.IPv4Header;
import com.lipisoft.toyshark.util.PacketUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Random;

/**
 * class to create IPv4 Header, TCP header, and packet data.
 * @author Borey Sao
 * Date: May 8, 2014
 */
public class TCPPacketFactory {
	public static final String TAG = "TCPPacketFactory";
	
	private static TCPHeader copyTCPHeader(TCPHeader tcpheader){
		TCPHeader tcp = new TCPHeader(tcpheader.getSourcePort(),
				tcpheader.getDestinationPort(), tcpheader.getSequenceNumber(),
				tcpheader.getDataOffset(), tcpheader.isNS(),
				tcpheader.getTcpFlags(), tcpheader.getWindowSize(),
				tcpheader.getChecksum(), tcpheader.getUrgentPointer(),
				tcpheader.getOptions(), tcpheader.getAckNumber());

		tcp.setMaxSegmentSize(65535);//tcpheader.getMaxSegmentSize());
		tcp.setWindowScale(tcpheader.getWindowScale());
		tcp.setSelectiveAckPermitted(tcpheader.isSelectiveAckPermitted());
		tcp.setTimeStampSender(tcpheader.getTimeStampSender());
		tcp.setTimeStampReplyTo(tcpheader.getTimeStampReplyTo());
		return tcp;
	}

	/**
	 * create FIN-ACK for sending to client
	 * @param iPv4Header IP Header
	 * @param tcpHeader TCP Header
	 * @param ackToClient acknowledge
	 * @param seqToClient sequence
	 * @return byte[]
	 */
	public static byte[] createFinAckData(IPv4Header iPv4Header, TCPHeader tcpHeader,
								   long ackToClient, long seqToClient,
								   boolean isFin, boolean isAck){
		IPv4Header ip = IPPacketFactory.copyIPv4Header(iPv4Header);
		TCPHeader tcp = copyTCPHeader(tcpHeader);
		
		//flip IP from source to dest and vice-versa
		int sourceIp = ip.getDestinationIP();
		int destIp = ip.getSourceIP();
		int sourcePort = tcp.getDestinationPort();
		int destPort = tcp.getSourcePort();
		
		ip.setDestinationIP(destIp);
		ip.setSourceIP(sourceIp);
		tcp.setDestinationPort(destPort);
		tcp.setSourcePort(sourcePort);
		
		tcp.setAckNumber(ackToClient);
		tcp.setSequenceNumber(seqToClient);
		
		ip.setIdentification(PacketUtil.getPacketId());
		
		//ACK
		tcp.setIsACK(isAck);
		tcp.setIsSYN(false);
		tcp.setIsPSH(false);
		tcp.setIsFIN(isFin);
		
		//set response timestamps in options fields
		tcp.setTimeStampReplyTo(tcp.getTimeStampSender());
		Date currentDate = new Date();
		int senderTimestamp = (int)currentDate.getTime();
		tcp.setTimeStampSender(senderTimestamp);
		
		//recalculate IP length
		int totalLength = ip.getIPHeaderLength() + tcp.getTCPHeaderLength();
		
		ip.setTotalLength(totalLength);
		
		return createPacketData(ip, tcp, null);
	}

	public static byte[] createFinData(IPv4Header ip, TCPHeader tcp, long ackNumber, long seqNumber, int timeSender, int timeReplyto){
		//flip IP from source to dest and vice-versa
		int sourceIp = ip.getDestinationIP();
		int destIp = ip.getSourceIP();
		int sourcePort = tcp.getDestinationPort();
		int destPort = tcp.getSourcePort();
		
		tcp.setAckNumber(ackNumber);
		tcp.setSequenceNumber(seqNumber);
		
		tcp.setTimeStampReplyTo(timeReplyto);
		tcp.setTimeStampSender(timeSender);
		
		ip.setDestinationIP(destIp);
		ip.setSourceIP(sourceIp);
		tcp.setDestinationPort(destPort);
		tcp.setSourcePort(sourcePort);
		
		ip.setIdentification(PacketUtil.getPacketId());
		
		tcp.setIsRST(false);
		tcp.setIsACK(false);
		tcp.setIsSYN(false);
		tcp.setIsPSH(false);
		tcp.setIsCWR(false);
		tcp.setIsECE(false);
		tcp.setIsFIN(true);
		tcp.setIsNS(false);
		tcp.setIsURG(false);
		
		//remove any option field
//		byte[] options = new byte[0];
//		tcp.setOptions(options);
		tcp.setOptions(null);

		//window size should be zero
		tcp.setWindowSize(0);
		
		//recalculate IP length
		int totalLength = ip.getIPHeaderLength() + tcp.getTCPHeaderLength();
		
		ip.setTotalLength(totalLength);
		
		return createPacketData(ip, tcp, null);
	}

	/**
	 * create packet with RST flag for sending to client when reset is required.
	 * @param ipheader IP Header
	 * @param tcpheader TCP Header
	 * @param datalength Data Length
	 * @return byte[]
	 */
	public static byte[] createRstData(IPv4Header ipheader, TCPHeader tcpheader, int datalength){
		IPv4Header ip = IPPacketFactory.copyIPv4Header(ipheader);
		TCPHeader tcp = copyTCPHeader(tcpheader);
		
		//flip IP from source to dest and vice-versa
		int sourceIp = ip.getDestinationIP();
		int destIp = ip.getSourceIP();
		int sourcePort = tcp.getDestinationPort();
		int destPort = tcp.getSourcePort();
		
		long ackNumber = 0;
		long seqNumber = 0;
		
		if(tcp.getAckNumber() > 0){
			seqNumber = tcp.getAckNumber();
		}else{
			ackNumber = tcp.getSequenceNumber() + datalength;
		}
		tcp.setAckNumber(ackNumber);
		tcp.setSequenceNumber(seqNumber);
		
		ip.setDestinationIP(destIp);
		ip.setSourceIP(sourceIp);
		tcp.setDestinationPort(destPort);
		tcp.setSourcePort(sourcePort);
		
		ip.setIdentification(0);
		
		tcp.setIsRST(true);
		tcp.setIsACK(false);
		tcp.setIsSYN(false);
		tcp.setIsPSH(false);
		tcp.setIsCWR(false);
		tcp.setIsECE(false);
		tcp.setIsFIN(false);
		tcp.setIsNS(false);
		tcp.setIsURG(false);
		
		//remove any option field
//		byte[] options = new byte[0];
//		tcp.setOptions(options);
		tcp.setOptions(null);

		//window size should be zero
		tcp.setWindowSize(0);
		
		//recalculate IP length
		int totalLength = ip.getIPHeaderLength() + tcp.getTCPHeaderLength();
		
		ip.setTotalLength(totalLength);
		
		return createPacketData(ip, tcp, null);
	}

	/**
	 * Acknowledgment to client that server has received request.
	 * @param ipheader IP Header
	 * @param tcpheader TCP Header
	 * @param ackToClient Acknowledge
	 * @return byte[]
	 */
	public static byte[] createResponseAckData(IPv4Header ipheader, TCPHeader tcpheader, long ackToClient){
		IPv4Header ip = IPPacketFactory.copyIPv4Header(ipheader);
		TCPHeader tcp = copyTCPHeader(tcpheader);
		
		//flip IP from source to dest and vice-versa
		int sourceIp = ip.getDestinationIP();
		int destIp = ip.getSourceIP();
		int sourcePort = tcp.getDestinationPort();
		int destPort = tcp.getSourcePort();
		
		long seqNumber = tcp.getAckNumber();
		
		ip.setDestinationIP(destIp);
		ip.setSourceIP(sourceIp);
		tcp.setDestinationPort(destPort);
		tcp.setSourcePort(sourcePort);
		
		tcp.setAckNumber(ackToClient);
		tcp.setSequenceNumber(seqNumber);
		
		ip.setIdentification(PacketUtil.getPacketId());
		
		//ACK
		tcp.setIsACK(true);
		tcp.setIsSYN(false);
		tcp.setIsPSH(false);
		
		//set response timestamps in options fields
		tcp.setTimeStampReplyTo(tcp.getTimeStampSender());
		Date currentdate = new Date();
		int sendertimestamp = (int)currentdate.getTime();
		tcp.setTimeStampSender(sendertimestamp);
		
		//recalculate IP length
		int totalLength = ip.getIPHeaderLength() + tcp.getTCPHeaderLength();
		
		ip.setTotalLength(totalLength);
		
		return createPacketData(ip, tcp, null);
	}

	/**
	 * create packet data for sending back to client
	 * @param ip IP Header
	 * @param tcp TCP Header
	 * @param packetdata Packet Data
	 * @return byte[]
	 */
	public static byte[] createResponsePacketData(IPv4Header ip, TCPHeader tcp, byte[] packetdata, boolean ispsh,
			long ackNumber, long seqNumber, int timeSender, int timeReplyto){
		IPv4Header ipheader = IPPacketFactory.copyIPv4Header(ip);
		TCPHeader tcpheader = copyTCPHeader(tcp);
		
		//flip IP from source to dest and vice-versa
		int sourceIp = ipheader.getDestinationIP();
		int destIp = ipheader.getSourceIP();
		int sourcePort = tcpheader.getDestinationPort();
		int destPort = tcpheader.getSourcePort();
		
		
		ipheader.setDestinationIP(destIp);
		ipheader.setSourceIP(sourceIp);
		tcpheader.setDestinationPort(destPort);
		tcpheader.setSourcePort(sourcePort);
		
		tcpheader.setAckNumber(ackNumber);
		tcpheader.setSequenceNumber(seqNumber);
		
		ipheader.setIdentification(PacketUtil.getPacketId());
		
		//ACK is always sent
		tcpheader.setIsACK(true);
		tcpheader.setIsSYN(false);
		tcpheader.setIsPSH(ispsh);
		tcpheader.setIsFIN(false);
		
		tcpheader.setTimeStampSender(timeSender);
		tcpheader.setTimeStampReplyTo(timeReplyto);
		//recalculate IP length
		int totalLength = ipheader.getIPHeaderLength() + tcpheader.getTCPHeaderLength();
		if(packetdata != null){
			totalLength += packetdata.length;
		}
		ipheader.setTotalLength(totalLength);
		
		return createPacketData(ipheader, tcpheader, packetdata);
	}

	/**
	 * create SYN-ACK packet data from writing back to client stream
	 * @param ip IP Header
	 * @param tcp TCP Header
	 * @return class Packet
	 */
	public static Packet createSynAckPacketData(IPv4Header ip, TCPHeader tcp){
		IPv4Header ipheader = IPPacketFactory.copyIPv4Header(ip);
		TCPHeader tcpheader = copyTCPHeader(tcp);
		
		//flip IP from source to dest and vice-versa
		int sourceIp = ipheader.getDestinationIP();
		int destIp = ipheader.getSourceIP();
		int sourcePort = tcpheader.getDestinationPort();
		int destPort = tcpheader.getSourcePort();
		long ackNumber = tcpheader.getSequenceNumber() + 1;
		long seqNumber;
		Random random = new Random();
		seqNumber = random.nextInt();
		if(seqNumber < 0){
			seqNumber = seqNumber * -1;
		}
		ipheader.setDestinationIP(destIp);
		ipheader.setSourceIP(sourceIp);
		tcpheader.setDestinationPort(destPort);
		tcpheader.setSourcePort(sourcePort);
		
		//ack = received sequence + 1
		tcpheader.setAckNumber(ackNumber);
		
		//initial sequence number generated by server
		tcpheader.setSequenceNumber(seqNumber);
		Log.d(TAG,"Set Initial Sequence number: "+seqNumber);
		
		//SYN-ACK
		tcpheader.setIsACK(true);
		tcpheader.setIsSYN(true);
		
		//timestamp in options fields
		tcpheader.setTimeStampReplyTo(tcpheader.getTimeStampSender());
		Date currentdate = new Date();
		int sendertimestamp = (int)currentdate.getTime();
		tcpheader.setTimeStampSender(sendertimestamp);
		
		return new Packet(ipheader, tcpheader, createPacketData(ipheader, tcpheader, null));
	}

	/**
	 * create packet data from IP Header, TCP header and data
	 * @param ipHeader IPv4Header object
	 * @param tcpheader TCPHeader object
	 * @param data array of byte (packet body)
	 * @return array of byte
	 */
    private static byte[] createPacketData(IPv4Header ipHeader, TCPHeader tcpheader, @Nullable byte[] data){
		int dataLength = 0;
		if(data != null){
			dataLength = data.length;
		}
		byte[] buffer = new byte[ipHeader.getIPHeaderLength() + tcpheader.getTCPHeaderLength() + dataLength];
		byte[] ipBuffer = IPPacketFactory.createIPv4HeaderData(ipHeader);
		byte[] tcpBuffer = createTCPHeaderData(tcpheader);
		
		System.arraycopy(ipBuffer, 0, buffer, 0, ipBuffer.length);
		System.arraycopy(tcpBuffer, 0, buffer, ipBuffer.length, tcpBuffer.length);
		if(dataLength > 0){
			int offset = ipBuffer.length + tcpBuffer.length;
			System.arraycopy(data, 0, buffer, offset, dataLength);
		}
		//calculate checksum for both IP and TCP header
		byte[] ipChecksum = PacketUtil.calculateChecksum(buffer, 0, ipBuffer.length);
		//write result of checksum back to buffer
		System.arraycopy(ipChecksum, 0, buffer, 10, 2);
		
		//zero out TCP header checksum first
		int tcpStart = ipBuffer.length;
		byte[] tcpChecksum = PacketUtil.calculateTCPHeaderChecksum(buffer, tcpStart, tcpBuffer.length + dataLength ,
				ipHeader.getDestinationIP(), ipHeader.getSourceIP());
		
		//write new checksum back to array
		System.arraycopy(tcpChecksum, 0, buffer,tcpStart + 16, 2);

		Message message = MainActivity.mHandler.obtainMessage(MainActivity.PACKET, new Packet(ipHeader, tcpheader, buffer));
		message.sendToTarget();

		return buffer;
	}
	
	/**
	 * create array of byte from a given TCPHeader object
	 * @param header instance of TCPHeader
	 * @return array of byte
	 */
	private static byte[] createTCPHeaderData(TCPHeader header){
		final byte[] buffer = new byte[header.getTCPHeaderLength()];
		buffer[0] = (byte)(header.getSourcePort() >> 8);
		buffer[1] = (byte)(header.getSourcePort());
		buffer[2] = (byte)(header.getDestinationPort() >> 8);
		buffer[3] = (byte)(header.getDestinationPort());

		final ByteBuffer sequenceNumber = ByteBuffer.allocate(4);
		sequenceNumber.order(ByteOrder.BIG_ENDIAN);
		sequenceNumber.putInt((int)header.getSequenceNumber());
		
		//sequence number
		System.arraycopy(sequenceNumber.array(), 0, buffer, 4, 4);

		final ByteBuffer ackNumber = ByteBuffer.allocate(4);
		ackNumber.order(ByteOrder.BIG_ENDIAN);
		ackNumber.putInt((int)header.getAckNumber());
		System.arraycopy(ackNumber.array(), 0, buffer, 8, 4);
		
		buffer[12] = (byte) (header.isNS() ? (header.getDataOffset() << 4) | 0x1
				: header.getDataOffset() << 4);
		buffer[13] = (byte)header.getTcpFlags();

		buffer[14] = (byte)(header.getWindowSize() >> 8);
		buffer[15] = (byte)header.getWindowSize();

		buffer[16] = (byte)(header.getChecksum() >> 8);
		buffer[17] = (byte)header.getChecksum();

		buffer[18] = (byte)(header.getUrgentPointer() >> 8);
		buffer[19] = (byte)header.getUrgentPointer();

		//set timestamp for both sender and reply to
		final byte[] options = header.getOptions();
		if (options != null) {
			for (int i = 0; i < options.length; i++) {
				final byte kind = options[i];
				if (kind > 1) {
					if (kind == 8) {//timestamp
						i += 2;
						if ((i + 7) < options.length) {
							PacketUtil.writeIntToBytes(header.getTimeStampSender(), options, i);
							i += 4;
							PacketUtil.writeIntToBytes(header.getTimeStampReplyTo(), options, i);
						}
						break;
					} else if ((i + 1) < options.length) {
						final byte len = options[i + 1];
						i = i + len - 1;
					}
				}
			}
			if (options.length > 0) {
				System.arraycopy(options, 0, buffer, 20, options.length);
			}
		}

		return buffer;
	}
	/**
	 * create a TCP Header from a given byte array
	 * @param buffer array of byte
	 * @param start position to start extracting data
	 * @return a new instance of TCPHeader
	 * @throws PacketHeaderException throws PacketHeaderException
	 */
	public static TCPHeader createTCPHeader(byte[] buffer, int start) throws PacketHeaderException{
		if(buffer.length < start + 20){
			throw new PacketHeaderException("There is not enough space for TCP header from provided starting position");
		}
		int sourcePort = PacketUtil.getNetworkInt(buffer, start, 2);
		int destPort = PacketUtil.getNetworkInt(buffer, start + 2, 2);
		long sequenceNumber = PacketUtil.getNetworkLong(buffer, start + 4, 4);
		long ackNumber = PacketUtil.getNetworkLong(buffer, start + 8, 4);
		int dataOffset = (buffer[start + 12] >> 4) & 0x0F;
		if(dataOffset < 5 && buffer.length == 60){
			dataOffset = 10;
		}else if(dataOffset < 5){
			dataOffset = 5;
		}
		if(buffer.length < (start + dataOffset * 4)){
			throw new PacketHeaderException("invalid array size for TCP header from given starting position");
		}
		
		byte nsbyte = buffer[start + 12];
		boolean isNs = (nsbyte & 0x1) > 0x0;
		
		int tcpFlag = PacketUtil.getNetworkInt(buffer, start + 13, 1);
		int windowSize = PacketUtil.getNetworkInt(buffer, start + 14, 2);
		int checksum = PacketUtil.getNetworkInt(buffer, start + 16, 2);
		int urgentPointer = PacketUtil.getNetworkInt(buffer, start + 18, 2);
		byte[] options;
		if(dataOffset > 5){
			int optionLength = (dataOffset - 5) * 4;

			options = new byte[optionLength];
			System.arraycopy(buffer, start + 20, options, 0, optionLength);
		}else{
			options = new byte[0];
		}
		TCPHeader head = new TCPHeader(sourcePort, destPort, sequenceNumber, dataOffset, isNs, tcpFlag, windowSize, checksum, urgentPointer, options, ackNumber);
		extractOptionData(head);
		return head;
	}
	private static void extractOptionData(TCPHeader head){
		final byte[] options = head.getOptions();
		if (options != null) {
			for (int i = 0; i < options.length; i++) {
				final byte kind = options[i];
				if (kind == 2) {
					i += 2;
					int segSize = PacketUtil.getNetworkInt(options, i, 2);
					head.setMaxSegmentSize(segSize);
					i++;
				} else if (kind == 3) {
					i += 2;
					int scale = PacketUtil.getNetworkInt(options, i, 1);
					head.setWindowScale(scale);
				} else if (kind == 4) {
					i++;
					head.setSelectiveAckPermitted(true);
				} else if (kind == 5) {//SACK => selective acknowledgment
					i++;
					int sackLength = PacketUtil.getNetworkInt(options, i, 1);
					i = i + (sackLength - 2);
					//case 10, 18, 26 and 34
					//TODO: handle missing segments
					//rare case => low priority
				} else if (kind == 8) {//timestamp and echo of previous timestamp
					i += 2;
					int timestampSender = PacketUtil.getNetworkInt(options, i, 4);
					i += 4;
					int timestampReplyTo = PacketUtil.getNetworkInt(options, i, 4);
					i += 3;
					head.setTimeStampSender(timestampSender);
					head.setTimeStampReplyTo(timestampReplyTo);
				}
			}
		}
	}
}
