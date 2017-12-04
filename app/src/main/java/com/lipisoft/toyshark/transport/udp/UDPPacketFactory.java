package com.lipisoft.toyshark.transport.udp;

import android.os.Message;

import com.lipisoft.toyshark.MainActivity;
import com.lipisoft.toyshark.Packet;
import com.lipisoft.toyshark.PacketManager;
import com.lipisoft.toyshark.network.ip.IPPacketFactory;
import com.lipisoft.toyshark.network.ip.IPv4Header;
import com.lipisoft.toyshark.transport.tcp.PacketHeaderException;
import com.lipisoft.toyshark.util.PacketUtil;

public class UDPPacketFactory {
	private static final String TAG = "UDPPacketFactory";

	public static UDPHeader createUDPHeader(byte[] buffer, int start) throws PacketHeaderException{
		if((buffer.length - start) < 8){
			throw new PacketHeaderException("Minimum UDP header is 8 bytes.");
		}
		int srcPort = PacketUtil.getNetworkInt(buffer, start, 2);
		int destPort = PacketUtil.getNetworkInt(buffer, start + 2, 2);
		int length = PacketUtil.getNetworkInt(buffer, start + 4,2);
		int checksum = PacketUtil.getNetworkInt(buffer, start + 6,2);

		return new UDPHeader(srcPort, destPort, length, checksum);
	}

	public static UDPHeader copyHeader(UDPHeader header){
		return new UDPHeader(header.getSourcePort(), header.getDestinationPort(),
				header.getLength(), header.getChecksum());
	}
	/**
	 * create packet data for responding to vpn client
	 * @param ip IPv4Header sent from VPN client, will be used as the template for response
	 * @param udp UDPHeader sent from VPN client
	 * @param packetData packet data to be sent to client
	 * @return array of byte
	 */
	public static byte[] createResponsePacket(IPv4Header ip, UDPHeader udp, byte[] packetData){
		byte[] buffer;
		int udpLen = 8;
		if(packetData != null){
			udpLen += packetData.length;
		}
		int srcPort = udp.getDestinationPort();
		int destPort = udp.getSourcePort();
		short checksum = 0;
		
		IPv4Header ipHeader = IPPacketFactory.copyIPv4Header(ip);

		int srcIp = ip.getDestinationIP();
		int destIp = ip.getSourceIP();
		ipHeader.setMayFragment(false);
		ipHeader.setSourceIP(srcIp);
		ipHeader.setDestinationIP(destIp);
		ipHeader.setIdentification(PacketUtil.getPacketId());
		
		//ip's length is the length of the entire packet => IP header length + UDP header length (8) + UDP body length
		int totalLength = ipHeader.getIPHeaderLength() + udpLen;
		
		ipHeader.setTotalLength(totalLength);
		buffer = new byte[totalLength];
		byte[] ipData = IPPacketFactory.createIPv4HeaderData(ipHeader);
		//calculate checksum for IP header
		byte[] ipChecksum = PacketUtil.calculateChecksum(ipData, 0, ipData.length);
		//write result of checksum back to buffer
		System.arraycopy(ipChecksum, 0, ipData, 10, 2);
		System.arraycopy(ipData, 0, buffer, 0, ipData.length);
		
		//copy UDP header to buffer
		int start = ipData.length;
		byte[] intContainer = new byte[4];
		PacketUtil.writeIntToBytes(srcPort, intContainer, 0);
		//extract the last two bytes of int value
		System.arraycopy(intContainer,2,buffer,start,2);
		start += 2;
		
		PacketUtil.writeIntToBytes(destPort, intContainer, 0);
		System.arraycopy(intContainer, 2, buffer, start, 2);
		start += 2;
		
		PacketUtil.writeIntToBytes(udpLen, intContainer, 0);
		System.arraycopy(intContainer, 2, buffer, start, 2);
		start += 2;
		
		PacketUtil.writeIntToBytes(checksum, intContainer, 0);
		System.arraycopy(intContainer, 2, buffer, start, 2);
		start += 2;
		
		//now copy udp data
		if (packetData != null)
		System.arraycopy(packetData, 0, buffer, start, packetData.length);

		UDPHeader udpHeader = new UDPHeader(srcPort, destPort, udpLen, checksum);

		PacketManager.INSTANCE.add(new Packet(ipHeader, udpHeader, buffer));
		PacketManager.INSTANCE.getHandler().obtainMessage(PacketManager.PACKET).sendToTarget();

		return buffer;
	}
	
}//end
