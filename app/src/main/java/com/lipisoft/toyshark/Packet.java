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

import android.support.annotation.Nullable;
import android.util.Log;

import com.lipisoft.toyshark.ip.IPv4Header;
import com.lipisoft.toyshark.tcp.TCPHeader;
import com.lipisoft.toyshark.udp.UDPHeader;

/**
 * Data structure that encapsulate both IPv4Header and TCPHeader
 * @author Borey Sao
 * Date: May 27, 2014
 */
public class Packet {
	private static final String TAG = "PACKET";
	private static final int UDP_HEADER_LENGTH = 8;
	private IPv4Header ipHeader;
	private TCPHeader tcpheader;
	private UDPHeader udpHeader;
	private byte[] buffer;

	public UDPHeader getUdpHeader() {
		return udpHeader;
	}

	public void setUdpHeader(UDPHeader udpHeader) {
		this.udpHeader = udpHeader;
	}

	public IPv4Header getIpHeader() {
		return ipHeader;
	}

	public void setIpHeader(IPv4Header ipHeader) {
		this.ipHeader = ipHeader;
	}

	public TCPHeader getTcpHeader() {
		return tcpheader;
	}

	public void setTcpHeader(TCPHeader tcpheader) {
		this.tcpheader = tcpheader;
	}

	/**
	 * the whole packet data as an array of byte
	 * @return byte[]
	 */
	public byte[] getBuffer() {
		return buffer;
	}

	public void setBuffer(byte[] buffer) {
		this.buffer = buffer;
	}
}
