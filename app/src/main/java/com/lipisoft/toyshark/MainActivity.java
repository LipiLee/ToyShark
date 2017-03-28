/*
 *  Copyright 2016 Lipi C.H. Lee
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

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.lipisoft.toyshark.network.ip.IPv4Header;
import com.lipisoft.toyshark.transport.tcp.TCPHeader;
import com.lipisoft.toyshark.transport.ITransportHeader;
import com.lipisoft.toyshark.transport.udp.UDPHeader;
import com.lipisoft.toyshark.util.PacketUtil;

import java.net.NetworkInterface;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {
	private static String TAG = "MainActivity";
	private static final String NAME = "NAME";
	private static final byte TCP = 6;
	private static final byte UDP = 17;
	public static final int PACKET = 0;
	private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 0;
	public static Handler mHandler;
	private TableLayout tableLayout;
	private int i = 0;
	List<Packet> packets = new ArrayList<>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		checkRuntimePermission();

		mHandler = new Handler(Looper.getMainLooper()) {
			@Override
			public void handleMessage(Message msg) {
				Packet packet = (Packet) msg.obj;
				if(msg.what == PACKET)
					updateTableLayout(packet);
				else
					super.handleMessage(msg);
			}
		};

		tableLayout = (TableLayout) findViewById(R.id.tableLayout);
		makeTableHeader();
	}

	void checkRuntimePermission() {
		int permission = ContextCompat.checkSelfPermission(this,
				Manifest.permission.WRITE_EXTERNAL_STORAGE);
		if (permission != PackageManager.PERMISSION_GRANTED) {
			if (ActivityCompat.shouldShowRequestPermissionRationale(this,
					Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
				// TODO inform the user to ask runtime permission
				Log.d(TAG, "explains permission is needed.");
			} else {
				ActivityCompat.requestPermissions(this,
						new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
						REQUEST_WRITE_EXTERNAL_STORAGE);
			}
		} else {
			if (networkAndAirplaneModeCheck())
				startVPN();
			else {
				showInfoDialog(getResources().getString(R.string.app_name),
						getResources().getString(R.string.no_network_information));
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		switch (requestCode) {
			case REQUEST_WRITE_EXTERNAL_STORAGE:
				if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					if (networkAndAirplaneModeCheck()) {
						startVPN();
					} else {
						showInfoDialog(getResources().getString(R.string.app_name),
								getResources().getString(R.string.no_network_information));
					}
				}
		}
	}

	private void makeTableHeader() {
		TableRow headerTableRow = new TableRow(this);

		TextView noTextView = new TextView(this);
		noTextView.setText(getResources().getText(R.string.number));
		headerTableRow.addView(noTextView);

		TextView timeTextView = new TextView(this);
		timeTextView.setText(getResources().getText(R.string.time));
		headerTableRow.addView(timeTextView);

		TextView sourceTextView = new TextView(this);
		sourceTextView.setText(getResources().getText(R.string.source));
		headerTableRow.addView(sourceTextView);

		TextView destinationTextView = new TextView(this);
		destinationTextView.setText(getResources().getText(R.string.destination));
		headerTableRow.addView(destinationTextView);

		TextView protocolTextView = new TextView(this);
		protocolTextView.setText(getResources().getText(R.string.protocol));
		headerTableRow.addView(protocolTextView);

		TextView lengthTextView = new TextView(this);
		lengthTextView.setText(getResources().getText(R.string.length));
		lengthTextView.setPadding(20, 0, 20, 0);
		headerTableRow.addView(lengthTextView);

		TextView infoTextView = new TextView(this);
		infoTextView.setText(getResources().getText(R.string.info));
		headerTableRow.addView(infoTextView);

		tableLayout.addView(headerTableRow);
	}

	private void updateTableLayout(@NonNull final Packet packet) {
		final int color;
		final byte protocol = packet.getProtocol();

		final int destinationPort = packet.getDestinationPort();
		if (destinationPort == 0) {
			Log.e(TAG, "A Packet instance invalid for destination port");
			return;
		}

		final int sourcePort = packet.getSourcePort();
		if (sourcePort == 0) {
			Log.e(TAG, "A Packet instance invalid for source port");
			return;
		}

		packets.add(packet);

		if (protocol == TCP)
			if (destinationPort == 80 || sourcePort == 80)
				color = 0xFFE4FFC7; // Green
			else
				color = 0xFFE7E6FF;
		else if (protocol == UDP)
			color = 0xFFDAEEFF; //
		else
			color = 0xFF000000;

		TableRow tableRow = new TableRow(this);

		TextView numberTextView = new TextView(this);
		numberTextView.setText(String.valueOf(++i));
		numberTextView.setGravity(Gravity.END);
		numberTextView.setPadding(10, 0, 10, 0);
		numberTextView.setBackgroundColor(color);
		numberTextView.setTextColor(0xFF000000);
		tableRow.addView(numberTextView);

		TextView timeTextView = new TextView(this);
		timeTextView.setPadding(10, 0, 10, 0);
		timeTextView.setText(DateFormat.getDateTimeInstance().format(new Date()));
		timeTextView.setBackgroundColor(color);
		timeTextView.setTextColor(0xFF000000);
		tableRow.addView(timeTextView);

		TextView sourceTextView = new TextView(this);
		sourceTextView.setPadding(10, 0, 10, 0);
		sourceTextView.setText(PacketUtil.intToIPAddress(packet.getIpHeader().getSourceIP()));
		sourceTextView.setBackgroundColor(color);
		sourceTextView.setTextColor(0xFF000000);
		tableRow.addView(sourceTextView);

		TextView destinationTextView = new TextView(this);
		destinationTextView.setPadding(10, 0, 10, 0);
		destinationTextView.setText(PacketUtil.intToIPAddress(packet.getIpHeader().getDestinationIP()));
		destinationTextView.setBackgroundColor(color);
		destinationTextView.setTextColor(0xFF000000);
		tableRow.addView(destinationTextView);

		TextView protocolTextView = new TextView(this);
		protocolTextView.setPadding(10, 0, 10, 0);
		if(protocol == TCP)
			protocolTextView.setText(R.string.tcp);
		else if (protocol == 17)
			protocolTextView.setText(R.string.udp);
		else
			protocolTextView.setText(R.string.unknown);

		protocolTextView.setBackgroundColor(color);
		protocolTextView.setTextColor(0xFF000000);
		tableRow.addView(protocolTextView);

		TextView lengthTextView = new TextView(this);
		lengthTextView.setPadding(10, 0, 10, 0);
		lengthTextView.setText(String.valueOf(packet.getBuffer().length));
		lengthTextView.setBackgroundColor(color);
		lengthTextView.setTextColor(0xFF000000);
		tableRow.addView(lengthTextView);

		TextView infoTextView = new TextView(this);
		infoTextView.setPadding(10, 0, 10, 0);
		String info = makeInfo(packet);
		if (info != null)
			infoTextView.setText(info);
		infoTextView.setBackgroundColor(color);
		infoTextView.setTextColor(0xFF000000);
		tableRow.addView(infoTextView);

		tableRow.setClickable(true);
		tableRow.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				TableRow selectedTableRow = (TableRow) v;
				TextView noTextView = (TextView) selectedTableRow.getChildAt(0);
				int index = Integer.valueOf(noTextView.getText().toString());
				Packet packet = packets.get(index - 1);
				if (packet != null) {
					updatePacketDetailView(packet);
				}
			}
		});

		tableLayout.addView(tableRow);
	}

	private List<Map<String, String>> makeNetworkLayerDetail(@NonNull IPv4Header iPv4Header) {
		List<Map<String, String>> ipChildList = new ArrayList<>();

		Map<String, String> ipVersionMap = new HashMap<>();
		ipVersionMap.put(NAME, "Version: 4");
		ipChildList.add(ipVersionMap);

		Map<String, String> headerLengthMap = new HashMap<>();
		headerLengthMap.put(NAME, "Header Length: " + iPv4Header.getIPHeaderLength());
		ipChildList.add(headerLengthMap);

		byte[] dsf = {iPv4Header.getDscpOrTypeOfService()};
		Map<String, String> differentiatedServiceFieldMap = new HashMap<>();
		differentiatedServiceFieldMap.put(NAME, "Differentiated Service Field: " + PacketUtil.bytesToStringArray(dsf));
		ipChildList.add(differentiatedServiceFieldMap);

		Map<String, String> totalLengthMap = new HashMap<>();
		totalLengthMap.put(NAME, "Total Length: " + iPv4Header.getTotalLength());
		ipChildList.add(totalLengthMap);

		Map<String, String> identificationMap = new HashMap<>();
		identificationMap.put(NAME, "Identification: " + iPv4Header.getIdentification());
		ipChildList.add(identificationMap);

		byte[] flag = {iPv4Header.getFlag()};
		Map<String, String> flagsMap = new HashMap<>();
		flagsMap.put(NAME, "Flags: " + PacketUtil.bytesToStringArray(flag));
		ipChildList.add(flagsMap);

		Map<String, String> fragmentOffsetMap = new HashMap<>();
		fragmentOffsetMap.put(NAME, "Fragment offset: " + iPv4Header.getFragmentOffset());
		ipChildList.add(fragmentOffsetMap);

		Map<String, String> timeToLiveMap = new HashMap<>();
		timeToLiveMap.put(NAME, "Time to live: " + iPv4Header.getTimeToLive());
		ipChildList.add(timeToLiveMap);

		Map<String, String> protocolMap = new HashMap<>();
		if (iPv4Header.getProtocol() == 6) {
			protocolMap.put(NAME, "Protocol: TCP");
		} else {
			protocolMap.put(NAME, "Protocol: UDP");
		}
		ipChildList.add(protocolMap);

//        byte[] checksum = {iPv4Header.getHeaderChecksum()};
//        child.add("Header checksum: " + iPv4Header.getHeaderChecksum());
		Map<String, String> sourceMap = new HashMap<>();
		sourceMap.put(NAME, "Source: " + PacketUtil.intToIPAddress(iPv4Header.getSourceIP()));
		ipChildList.add(sourceMap);

		Map<String, String> destinationMap = new HashMap<>();
		destinationMap.put(NAME, "Destination: " + PacketUtil.intToIPAddress(iPv4Header.getDestinationIP()));
		ipChildList.add(destinationMap);

		return ipChildList;
	}

	private List<Map<String, String>> makeTCPDetail(TCPHeader tcpHeader) {

		List<Map<String, String>> tcpChildList = new ArrayList<>();

		Map<String, String> sourcePortMap = new HashMap<>();
		sourcePortMap.put(NAME, "Source Port: " + tcpHeader.getSourcePort());
		tcpChildList.add(sourcePortMap);

		Map<String, String> destinationPortMap =  new HashMap<>();
		destinationPortMap.put(NAME, "Destination Port: " + tcpHeader.getDestinationPort());
		tcpChildList.add(destinationPortMap);

		Map<String, String> sequenceNumberMap = new HashMap<>();
		sequenceNumberMap.put(NAME, "Sequence number: " + tcpHeader.getSequenceNumber());
		tcpChildList.add(sequenceNumberMap);

		Map<String, String> acknowledgmentNumberMap = new HashMap<>();
		acknowledgmentNumberMap.put(NAME, "Acknowledgement number: " + tcpHeader.getAckNumber());
		tcpChildList.add(acknowledgmentNumberMap);

		Map<String, String> tcpHeaderLengthMap = new HashMap<>();
		tcpHeaderLengthMap.put(NAME, "Header Length: " + tcpHeader.getTCPHeaderLength());
		tcpChildList.add(tcpHeaderLengthMap);

//		Map<String, String> flagsMap = new HashMap<>();
//		flagsMap.put(NAME, "Flags: " + tcpHeader.);
//		tcpChildList.add(flagsMap);

		Map<String, String> windowSizeMap = new HashMap<>();
		windowSizeMap.put(NAME, "Window size value: " + tcpHeader.getWindowSize());
		tcpChildList.add(windowSizeMap);

		Map<String, String> checksumMap = new HashMap<>();
		checksumMap.put(NAME, "Checksum: " + tcpHeader.getChecksum());
		tcpChildList.add(checksumMap);

		Map<String, String> urgentPointerMap = new HashMap<>();
		urgentPointerMap.put(NAME, "Urgent pointer: " + tcpHeader.getUrgentPointer());
		tcpChildList.add(urgentPointerMap);

		return tcpChildList;
	}

	private List<Map<String, String>> makeUDPDetail(UDPHeader udpHeader) {
		List<Map<String, String>> udpChildList = new ArrayList<>();

		Map<String, String> sourcePort = new HashMap<>();
		sourcePort.put(NAME, "Source Port: " + udpHeader.getSourcePort());
		udpChildList.add(sourcePort);

		Map<String, String> destinationPort = new HashMap<>();
		destinationPort.put(NAME, "Destinatioin Port: " + udpHeader.getDestinationPort());
		udpChildList.add(destinationPort);

		Map<String, String> length = new HashMap<>();
		length.put(NAME, "Length: " + udpHeader.getLength());
		udpChildList.add(length);

		Map<String, String> checksum = new HashMap<>();
		checksum.put(NAME, "Checksum: " + udpHeader.getChecksum());
		udpChildList.add(checksum);

		return udpChildList;
	}

	private void updatePacketDetailView(@NonNull Packet packet) {
		List<Map<String, String>> groupList = new ArrayList<>();
		List<List<Map<String, String>>> childList = new ArrayList<>();

		IPv4Header iPv4Header = packet.getIpHeader();
		String src = PacketUtil.intToIPAddress(iPv4Header.getSourceIP());
		String dst = PacketUtil.intToIPAddress(iPv4Header.getDestinationIP());
		Map<String, String> ipGroupMap = new Hashtable<>();
		ipGroupMap.put(NAME, "Internet Protocol Version 4, Src: " + src + ", Dst: " + dst);

		groupList.add(ipGroupMap);

		childList.add(makeNetworkLayerDetail(iPv4Header));

		ITransportHeader transportHeader = packet.getTransportHeader();
		if (transportHeader instanceof TCPHeader) {
			TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();
			int len = packet.getBuffer().length - iPv4Header.getIPHeaderLength() - tcpHeader.getTCPHeaderLength();
			Map<String, String> tcpGroupMap = new Hashtable<>();
			tcpGroupMap.put(NAME, "Transmission Control Protocol, Src Port: " +
					tcpHeader.getSourcePort() + ", Dst Port: " + tcpHeader.getDestinationPort() +
					", Seq: " + tcpHeader.getSequenceNumber() + ", Ack: " + tcpHeader.getAckNumber() +
					", Len: " + len);
			groupList.add(tcpGroupMap);

			childList.add(makeTCPDetail(tcpHeader));

		} else if (transportHeader instanceof UDPHeader) {
			UDPHeader udpHeader = (UDPHeader) packet.getTransportHeader();
			Map<String, String> udpGroupMap = new HashMap<>();
			udpGroupMap.put(NAME, "User Datagram Protocol, Src Port: " + udpHeader.getSourcePort() + ", Dst Port: " + udpHeader.getDestinationPort());
			groupList.add(udpGroupMap);
			childList.add(makeUDPDetail(udpHeader));
		} else {
			Log.e(NAME, "Unknown Transport Protocol");
			return;
		}

		ExpandableListView expandableListView = (ExpandableListView) findViewById(R.id.packetDetailView);
		try {
			expandableListView.setAdapter(new SimpleExpandableListAdapter(
					this,
					groupList,
					android.R.layout.simple_expandable_list_item_1,
					new String[]{NAME},
					new int[]{android.R.id.text1},
					childList,
					android.R.layout.simple_expandable_list_item_2,
					new String[]{NAME},
					new int[]{android.R.id.text1}
			));
		} catch (NullPointerException e) {
			Log.e(TAG, e.toString());
		}
	}

	@Nullable private String makeTcpInfo(@NonNull Packet packet) {
		TCPHeader tcpHeader = (TCPHeader) packet.getTransportHeader();

		StringBuilder tcpInfo = new StringBuilder();
		tcpInfo.append(tcpHeader.getSourcePort()).append("->")
                .append(tcpHeader.getDestinationPort()).append(" [");
		if (tcpHeader.isSYN())
			tcpInfo.append("SYN");
		if (tcpHeader.isACK())
			tcpInfo.append("ACK");
		if (tcpHeader.isFIN())
			tcpInfo.append("FIN");
		if (tcpHeader.isRST())
			tcpInfo.append("RST");

		tcpInfo.append("] ");

		if (tcpHeader.isSYN()) {
			tcpInfo.append("Seq=").append(tcpHeader.getSequenceNumber()).append(" ");
		}
		if (tcpHeader.isACK()) {
			tcpInfo.append("Ack=").append(tcpHeader.getAckNumber()).append(" ");
		}

		tcpInfo.append("Win=").append(tcpHeader.getWindowSize()).append(" ");
		int length = packet.getBuffer().length - packet.getIpHeader().getIPHeaderLength() - tcpHeader.getTCPHeaderLength();
		tcpInfo.append("Len=").append(length);

		return tcpInfo.toString();
	}

	@Nullable String makeUdpInfo(@NonNull Packet packet) {
		final UDPHeader udpHeader = (UDPHeader) packet.getTransportHeader();

		final StringBuilder udpInfo = new StringBuilder();
		final int sourcePort = udpHeader.getSourcePort();
		final int destinationPort = udpHeader.getDestinationPort();
		final int length = udpHeader.getLength();

		udpInfo.append(sourcePort).append("->").append(destinationPort)
				.append(" Length: ").append(length);

		return udpInfo.toString();
	}

	@Nullable private String makeInfo(@NonNull Packet packet) {
		int protocol = packet.getProtocol();

		if (protocol == TCP)
			return makeTcpInfo(packet);
		else if (protocol == UDP)
			return makeUdpInfo(packet);

		return null;
	}

	/**
	 * Launch intent for user approval of VPN connection
	 */
	private void startVPN() {
		// check for VPN already running
		try {
			if (!checkForActiveInterface("tun0")) {

				// get user permission for VPN
				Intent intent = VpnService.prepare(this);
				if (intent != null) {
					Log.d(TAG, "ask user for VPN permission");
					startActivityForResult(intent, 0);
				} else {
					Log.d(TAG, "already have VPN permission");
					onActivityResult(0, RESULT_OK, null);
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Exception checking network interfaces :" + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * check a network interface by name
	 *
	 * @param networkInterfaceName Network interface Name on Linux, for example tun0
	 * @return true if interface exists and is active
	 * @throws Exception throws Exception
	 */
	private boolean checkForActiveInterface(String networkInterfaceName) throws Exception {
		List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
		for (NetworkInterface networkInterface : interfaces) {
			if (networkInterface.getName().equals(networkInterfaceName)) {
				return networkInterface.isUp();
			}
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.i(TAG, "onActivityResult(resultCode:  " + resultCode + ")");
		if (resultCode == RESULT_OK) {
			Intent captureVpnServiceIntent = new Intent(getApplicationContext(), ToySharkVPNService.class);
			captureVpnServiceIntent.putExtra("TRACE_DIR", Environment.getExternalStorageDirectory().getPath() + "/ToyShark");
			startService(captureVpnServiceIntent);
		} else if (resultCode == RESULT_CANCELED) {
			showVPNRefusedDialog();
		}
	}

	/**
	 * Show dialog to educate the user about VPN trust
	 * abort app if user chooses to quit
	 * otherwise relaunch the startVPN()
	 */
	private void showVPNRefusedDialog() {
		new AlertDialog.Builder(this)
				.setTitle("Usage Alert")
				.setMessage("You must trust the ToyShark in order to run a VPN based trace.")
				.setPositiveButton(getString(R.string.try_again), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startVPN();
					}
				})
				.setNegativeButton(getString(R.string.quit), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				})
				.show();

	}

	/**
	 * @param title Title in Dialog
	 * @param message Message in Dialog
	 */
	private void showInfoDialog(String title, String message) {
		new AlertDialog.Builder(this)
				.setTitle(title)
				.setMessage(message)
				.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						//finish();
					}
				})
				.show();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	protected void onPause() {
		super.onPause();
	}

	@Override
	protected void onStop() {
		Log.i(TAG, "onStop()");
		super.onStop();
	}

	@Override
	protected void onResume() {
		Log.i(TAG, "onResume");
		super.onResume();
	}

	@Override
	public void onBackPressed() {
		Log.i(TAG, "onBackPressed");
		super.onBackPressed();
	}

	/** check whether network is connected or not
	 *  @return boolean
	 */
	private boolean isConnectedToInternet() {
		ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		if (connectivity != null) {
			NetworkInfo networkInfo = connectivity.getActiveNetworkInfo();
			if (networkInfo != null && networkInfo.isConnected()) {
				return true;
			}
		}
		return false;
	}

	private boolean networkAndAirplaneModeCheck() {
//		if (!isConnectedToInternet()) {
//			final String title = "ToyShark";
//			final String message = "No network connection in your phone, Connect to network and start again";
//			showInfoDialog(title, message);
//			return false;
//		}
//		return true;
		return isConnectedToInternet();
	}
}
