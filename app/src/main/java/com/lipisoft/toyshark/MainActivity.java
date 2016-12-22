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
import android.net.Network;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.annotation.NonNull;
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

import com.lipisoft.toyshark.ip.IPv4Header;
import com.lipisoft.toyshark.tcp.TCPHeader;
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
    public static final int PACKET = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE = 0;
    Intent captureVpnServiceIntent;
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
    }

    void checkRuntimePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                // TODO inform the user to ask runtime permission
                Log.d(TAG, "explains permission is needed.");
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{ Manifest.permission.WRITE_EXTERNAL_STORAGE },
                        REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        } else
            if (networkAndAirplaneModeCheck())
                startVPN();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (networkAndAirplaneModeCheck()) {
                        startVPN();
                    }
                }
        }
    }

    void updateTableLayout(final Packet packet) {
        int color;
        packets.add(packet);
        if (packet.getIpHeader().getProtocol() == 6)
            if (packet.getTcpHeader().getDestinationPort() == 80 ||
                    packet.getTcpHeader().getSourcePort() == 80)
                color = 0xFFE4FFC7;
            else
                color = 0xFFE7E6FF;
        else
            color = 0xFFDAEEFF;

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
        if(packet.getIpHeader().getProtocol() == 6)
            protocolTextView.setText(R.string.tcp);
        else
            protocolTextView.setText(R.string.udp);
        protocolTextView.setBackgroundColor(color);
        protocolTextView.setTextColor(0xFF000000);
        tableRow.addView(protocolTextView);

        TextView lengthTextView = new TextView(this);
        lengthTextView.setPadding(10, 0, 10, 0);
        if (packet.getBuffer() == null)
            lengthTextView.setText("0");
        else
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
                Packet packet = packets.get(index-1);
                updatePacketDetailView(packet);
            }
        });

        tableLayout.addView(tableRow);
    }

    private void updatePacketDetailView(Packet packet) {

        List<Map<String, String>> groupList = new ArrayList<>();
        List<List<Map<String, String>>> childList = new ArrayList<>();

        IPv4Header iPv4Header = packet.getIpHeader();
        String src = PacketUtil.intToIPAddress(iPv4Header.getSourceIP());
        String dst = PacketUtil.intToIPAddress(iPv4Header.getDestinationIP());
        Map<String, String> ipGroupMap = new Hashtable<>();
        ipGroupMap.put(NAME, "Internet Protocol Version 4, Src: " + src + ", Dst: " + dst);
        groupList.add(ipGroupMap);

        TCPHeader tcpHeader = packet.getTcpHeader();
        int len = packet.getBuffer().length - iPv4Header.getIPHeaderLength() - tcpHeader.getTCPHeaderLength();
        Map<String, String> tcpGroupMap = new Hashtable<>();
        tcpGroupMap.put(NAME, "Transmission Control Protocol, Src Port: " +
                tcpHeader.getSourcePort() + ", Dst Port: " + tcpHeader.getDestinationPort() +
                ", Seq: " + tcpHeader.getSequenceNumber() + ", Ack: " + tcpHeader.getAckNumber() +
                ", Len: " + len);
        groupList.add(tcpGroupMap);

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
        totalLengthMap.put(NAME, "Total Length" + iPv4Header.getTotalLength());
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
        sourceMap.put(NAME, "Source: " + src);
        ipChildList.add(sourceMap);

        Map<String, String> destinationMap = new HashMap<>();
        destinationMap.put(NAME, "Destination" + dst);
        ipChildList.add(destinationMap);

        List<Map<String, String>> tcpChildList = new ArrayList<>();

        Map<String, String> sourcePortMap = new HashMap<>();
        sourcePortMap.put(NAME, "Source Port: " + tcpHeader.getSourcePort());
        tcpChildList.add(sourcePortMap);

        Map<String, String> destinationPortMap =  new HashMap<>();
        destinationPortMap.put(NAME, "Destination Port: " + tcpHeader.getDestinationPort());
        tcpChildList.add(destinationPortMap);

        childList.add(ipChildList);
        childList.add(tcpChildList);

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

    private String makeTcpInfo(Packet packet) {
        TCPHeader tcpHeader = packet.getTcpHeader();
        StringBuilder tcpInfo = new StringBuilder(tcpHeader.getSourcePort());
        tcpInfo.append("->").append(tcpHeader.getDestinationPort()).append(" [");
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
        int length = packet.getBuffer().length - packet.getIpHeader().getIPHeaderLength() - packet.getTcpHeader().getTCPHeaderLength();
        tcpInfo.append("Len=").append(length);

        return tcpInfo.toString();
    }

    private String makeInfo(Packet packet) {
        int protocol = packet.getIpHeader().getProtocol();
        if (protocol == 6) return makeTcpInfo(packet);

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
     * @throws Exception
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
        Log.i(TAG, "onActivityResult(resultCode:  "+ resultCode + ")");
        if (resultCode == RESULT_OK) {
            captureVpnServiceIntent = new Intent(getApplicationContext(), ToySharkVPNService.class);
            captureVpnServiceIntent.putExtra("TRACE_DIR", Environment.getExternalStorageDirectory().getPath() + "/ARO");
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
                .setMessage("You must trust the ToyShark\nIn order to run a VPN based trace")
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
            for(Network network : connectivity.getAllNetworks()) {
                NetworkInfo networkInfo = connectivity.getNetworkInfo(network);
                if (networkInfo.getState().equals(NetworkInfo.State.CONNECTED)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean networkAndAirplaneModeCheck() {
        String title = "ToyShark";

        if (!isConnectedToInternet()) {
            String message = "No network connection in your phone, Connect to network and start again";
            showInfoDialog(title, message);
            return false;
        }
        return true;
    }
}
