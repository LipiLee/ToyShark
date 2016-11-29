package com.lipisoft.toyshark;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import com.lipisoft.toyshark.tcp.TCPHeader;
import com.lipisoft.toyshark.util.PacketUtil;

import java.net.NetworkInterface;
import java.text.DateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
//    private static final String TAG = "ToyShark";
//    public static final int PACKET = 0;
//    public static Handler mHandler;
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
//
//        Log.v(TAG, "onCreate is called.");
//        Intent intent = VpnService.prepare(this);
//        if (intent != null)
//            startActivityForResult(intent, 0);
//        else
//            onActivityResult(0, RESULT_OK, null);
//
//    }
//
//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        //super.onActivityResult(requestCode, resultCode, data);
//        Log.v(TAG, "onActivityResult is called.");
//        if (resultCode == RESULT_OK) {
//            Intent intent = new Intent(this, ToySharkVPNService.class);
//            startService(intent);
//        } else
//            // TODO Seriously alert popup to user
//            ;
//
//    }

    private static String TAG = "AroCollectorActivity";
    public static final int PACKET = 0;
    private Intent captureVpnServiceIntent;
    private BroadcastReceiver analyzerCloseCmdReceiver = null;
    public static Handler mHandler;
    private TableLayout tableLayout;
    private int i = 0;

    /**
     * Received broadcast adb shell am broadcast -a arodatacollector.home.activity.close
     */
    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            Log.d(TAG, "received analyzer close cmd intent at " + System.currentTimeMillis());
            boolean rez = stopService(captureVpnServiceIntent);
            Log.d(TAG, "stopService result=" + rez);
            unregisterReceiver(broadcastReceiver);
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate(...)");
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.splash);
        setContentView(R.layout.activity_main);

        if (networkAndAirplaneModeCheck()) {
            startVPN();
        }

//        { // test code
//            PackageInfo packageInfo = null;
//            try {
//                packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
//            } catch (NameNotFoundException e) {
//                e.printStackTrace();
//            }
//            boolean value = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
//            String display = "version: " + packageInfo.versionName + " (" + (value ? "Debug" : "Production") + ")";
//            TextView textView = (TextView) findViewById(R.id.version);
//            textView.setText(display);
//        }

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

    void updateTableLayout(Packet packet) {
        TableRow tableRow = new TableRow(this);

        TextView numberTextView = new TextView(this);
        numberTextView.setText(String.valueOf(++i));
        numberTextView.setGravity(Gravity.END);
        numberTextView.setPadding(10, 0, 10, 0);
        tableRow.addView(numberTextView);

        TextView timeTextView = new TextView(this);
        timeTextView.setPadding(10, 0, 10, 0);
        timeTextView.setText(DateFormat.getDateTimeInstance().format(new Date()));
        tableRow.addView(timeTextView);

        TextView sourceTextView = new TextView(this);
        sourceTextView.setPadding(10, 0, 10, 0);
        sourceTextView.setText(PacketUtil.intToIPAddress(packet.getIpheader().getSourceIP()));
        tableRow.addView(sourceTextView);

        TextView destinationTextView = new TextView(this);
        destinationTextView.setPadding(10, 0, 10, 0);
        destinationTextView.setText(PacketUtil.intToIPAddress(packet.getIpheader().getDestinationIP()));
        tableRow.addView(destinationTextView);

        TextView protocolTextView = new TextView(this);
        protocolTextView.setPadding(10, 0, 10, 0);
        if(packet.getIpheader().getProtocol() == 6)
            protocolTextView.setText(R.string.tcp);
        else
            protocolTextView.setText(R.string.udp);
        tableRow.addView(protocolTextView);

        TextView lengthTextView = new TextView(this);
        lengthTextView.setPadding(10, 0, 10, 0);
        lengthTextView.setText(String.valueOf(packet.getBuffer().length));
        tableRow.addView(lengthTextView);

        TextView infoTextView = new TextView(this);
        infoTextView.setPadding(10, 0, 10, 0);
        String info = makeInfo(packet);
        if (info != null)
            infoTextView.setText(info);
        tableRow.addView(infoTextView);

        tableLayout.addView(tableRow);
    }

    private String makeTCPinfo(Packet packet) {
        TCPHeader tcpHeader = packet.getTcpheader();
        StringBuilder stringBuilder = new StringBuilder(tcpHeader.getSourcePort() + "->" + tcpHeader.getDestinationPort() + " [");
        if (tcpHeader.isSYN())
            stringBuilder.append("SYN");
        if (tcpHeader.isACK())
            stringBuilder.append("ACK");
        if (tcpHeader.isFIN())
            stringBuilder.append("FIN");
        if (tcpHeader.isRST())
            stringBuilder.append("RST");

        stringBuilder.append("] ");

        if (tcpHeader.isSYN())
            stringBuilder.append("Seq=" + tcpHeader.getSequenceNumber() + " ");
        if (tcpHeader.isACK())
            stringBuilder.append("Ack=" + tcpHeader.getAckNumber() + " ");

        stringBuilder.append("Win=" + tcpHeader.getWindowSize() + " ");
        int length = packet.getBuffer().length - packet.getIpheader().getIPHeaderLength() - packet.getTcpheader().getTCPHeaderLength();
        stringBuilder.append("Len=" + length);

        return stringBuilder.toString();
    }

    private String makeInfo(Packet packet) {
        int protocol = packet.getIpheader().getProtocol();
        if (protocol == 6) return makeTCPinfo(packet);

        return null;
    }

    /**
     * Launch intent for user approval of VPN connection
     */
    private void startVPN() {
        Log.i(TAG, "startVPN()");

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
        for (NetworkInterface intf : interfaces) {
            if (intf.getName().equals(networkInterfaceName)) {
                return intf.isUp();
            }
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult(... resultCode{" + resultCode + "} ...)");
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
        Log.d(TAG, "onDestroy()");
        if (analyzerCloseCmdReceiver != null) {
            Log.d(TAG, "calling unregisterAnalyzerCloseCmdReceiver inside onDestroy()");
            unregisterAnalyzerCloseCmdReceiver();
        }

        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause()");
        if (analyzerCloseCmdReceiver != null) {
            Log.d(TAG, "calling unregisterAnalyzerCloseCmdReceiver inside onPause()");
            unregisterAnalyzerCloseCmdReceiver();
        }
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.i(TAG, "onStop()");
        // TODO Auto-generated method stub
        super.onStop();
    }

    /**
     * do not need broadcastReceiver anymore so unregister it!
     */
    private void unregisterAnalyzerCloseCmdReceiver() {
        Log.d(TAG, "inside unregisterAnalyzerCloseCmdReceiver");
        try {
            if (analyzerCloseCmdReceiver != null) {
                unregisterReceiver(analyzerCloseCmdReceiver);
                analyzerCloseCmdReceiver = null;

                Log.d(TAG, "successfully unregistered analyzerCloseCmdReceiver");
            }
        } catch (Exception e) {
            Log.d(TAG, "Ignoring exception in analyzerCloseCmdReceiver", e);
        }
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

    /**
     * @return boolean
     */
    private boolean isConnectedToInternet() {
        ConnectivityManager connectivity = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity != null) {
            NetworkInfo[] networkInfos = connectivity.getAllNetworkInfo();
            if (networkInfos != null)
                for (NetworkInfo networkInfo: networkInfos) {
                    Log.i(TAG, "NETWORK CONNECTION : " + networkInfo.getState() + " Connected STATE :" + NetworkInfo.State.CONNECTED);
                    if (networkInfo.getState().equals(NetworkInfo.State.CONNECTED)) {
                        return true;
                    }
                }

        }
        return false;
    }

    private boolean networkAndAirplaneModeCheck() {
        String title = "ARO";

        if (!isConnectedToInternet()) {
            String message = "No network connection in your phone, Connect to network and start again";
            //popup dialog
            showInfoDialog(title, message);
            return false;
        }

        return true;

    }

}
