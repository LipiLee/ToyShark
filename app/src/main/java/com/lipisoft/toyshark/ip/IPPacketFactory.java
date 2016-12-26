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

package com.lipisoft.toyshark.ip;

import android.support.annotation.NonNull;

import com.lipisoft.toyshark.tcp.PacketHeaderException;
import com.lipisoft.toyshark.util.PacketUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * class for creating packet data, header etc related to IP
 * @author Borey Sao
 * Date: June 30, 2014
 */
public class IPPacketFactory {
	/**
	 * make new instance of IPv4Header
	 * @param iPv4Header instance of IPv4Header
	 * @return IPv4Header
	 */
	public static IPv4Header copyIPv4Header(@NonNull IPv4Header iPv4Header) {
		return new IPv4Header(iPv4Header.getIpVersion(),
				iPv4Header.getInternetHeaderLength(),
				iPv4Header.getDscpOrTypeOfService(), iPv4Header.getEcn(),
				iPv4Header.getTotalLength(), iPv4Header.getIdentification(),
				iPv4Header.isMayFragment(), iPv4Header.isLastFragment(),
				iPv4Header.getFragmentOffset(), iPv4Header.getTimeToLive(),
				iPv4Header.getProtocol(), iPv4Header.getHeaderChecksum(),
				iPv4Header.getSourceIP(), iPv4Header.getDestinationIP(),
				iPv4Header.getOptionBytes());
	}

	/**
	 * create IPv4 Header array of byte from a given IPv4Header object
	 * @param header instance of IPv4Header
	 * @return array of byte
	 */
	public static byte[] createIPv4HeaderData(@NonNull IPv4Header header){
		final byte[] buffer = new byte[header.getIPHeaderLength()];

		buffer[0] = (byte)((header.getInternetHeaderLength() & 0xF) | 0x40);
		buffer[1] = (byte) ((header.getDscpOrTypeOfService() << 2) &
				(header.getEcn() & 0xFF));
		buffer[2] = (byte)(header.getTotalLength() >> 8);
		buffer[3] = (byte)header.getTotalLength();
		buffer[4] = (byte)(header.getIdentification() >> 8);
		buffer[5] = (byte)header.getIdentification();

		//combine flags and partial fragment offset
		buffer[6] = (byte)(((header.getFragmentOffset() >> 8) & 0x1F) |
				header.getFlag());
		buffer[7] = (byte)header.getFragmentOffset();
		buffer[8] = header.getTimeToLive();
		buffer[9]= header.getProtocol();
		buffer[10] = (byte) (header.getHeaderChecksum() >> 8);
		buffer[11] = (byte)header.getHeaderChecksum();

		final ByteBuffer buf = ByteBuffer.allocate(8);

		buf.order(ByteOrder.BIG_ENDIAN);
		buf.putInt(0,header.getSourceIP());
		buf.putInt(4,header.getDestinationIP());
		
		//source ip address
		System.arraycopy(buf.array(), 0, buffer, 12, 4);
		//destination ip address
		System.arraycopy(buf.array(), 4, buffer, 16, 4);

		final byte[] optionBytes = header.getOptionBytes();
		if (optionBytes != null)
			System.arraycopy(optionBytes, 0, buffer, 20, optionBytes.length);

		return buffer;
	}

	/**
	 * create IPv4 Header from a given array of byte
	 * @param buffer array of byte
	 * @param start position to start extracting data
	 * @return a new instance of IPv4Header
	 * @throws PacketHeaderException
	 */
	public static IPv4Header createIPv4Header(@NonNull byte[] buffer, int start)
			throws PacketHeaderException{
		//avoid Index out of range
		if( (buffer.length - start) < 20) {
			throw new PacketHeaderException("Minimum IPv4 header is 20 bytes. There are less "
					+ "than 20 bytes from start position to the end of array.");
		}

		final byte ipVersion = (byte) (buffer[start] >> 4);
		if (ipVersion != 0x04) {
			throw new PacketHeaderException("Invalid IPv4 header. IP version should be 4.");
		}

		final byte internetHeaderLength = (byte) (buffer[start] & 0x0F);
		if(buffer.length < (start + internetHeaderLength * 4)) {
			throw new PacketHeaderException("Not enough space in array for IP header");
		}

		final byte dscp = (byte) (buffer[start + 1] >> 2);
		final byte ecn = (byte) (buffer[start + 1] & 0x03);
		final int totalLength = PacketUtil.getNetworkInt(buffer, start + 2, 2);
		final int identification = PacketUtil.getNetworkInt(buffer, start + 4, 2);
		final byte flag = buffer[start + 6];
		final boolean mayFragment = (flag & 0x40) > 0x00;
		final boolean lastFragment = (flag & 0x20) > 0x00;
		final short fragmentOffset = (short)
				(PacketUtil.getNetworkInt(buffer, start + 6, 2) & 0x1FFF);
		final byte timeToLive = buffer[start + 8];
		final byte protocol = buffer[start + 9];
		final int checksum = PacketUtil.getNetworkInt(buffer, start + 10, 2);
		final int sourceIp = PacketUtil.getNetworkInt(buffer, start + 12, 4);
		final int desIp = PacketUtil.getNetworkInt(buffer, start + 16, 4);
		byte[] options = null;
		if(internetHeaderLength > 5){
			int optionLength = (internetHeaderLength - 5) * 4;
			options = new byte[optionLength];
			System.arraycopy(buffer, start + 20, options, 0, optionLength);
		}
		return new IPv4Header(ipVersion, internetHeaderLength, dscp, ecn, totalLength, identification,
				mayFragment, lastFragment, fragmentOffset, timeToLive, protocol, checksum, sourceIp, 
				desIp, options);
	}
}
