package com.lipisoft.toyshark;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import com.lipisoft.toyshark.packetRebuild.PCapFileWriter;
import com.lipisoft.toyshark.socket.IProtectSocket;
import com.lipisoft.toyshark.socket.IReceivePacket;
import com.lipisoft.toyshark.socket.SocketDataPublisher;
import com.lipisoft.toyshark.socket.SocketNIODataService;
import com.lipisoft.toyshark.socket.SocketProtector;
import com.lipisoft.toyshark.tcp.PacketHeaderException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

/**
 * Created by Lipi on 16. 4. 7..
 */
public class ToySharkVPNService extends VpnService implements Handler.Callback, Runnable, IProtectSocket, IReceivePacket{
    private static final String TAG = "ToySharkVPNService";
    private Handler mHandler;
    private Thread mThread;
    private ParcelFileDescriptor mInterface;
    private boolean serviceValid;
    private PendingIntent mConfigureIntent;
    private SocketNIODataService dataservice;
    private Thread dataServiceThread;
    private SocketDataPublisher packetbgWriter;
    private Thread packetqueueThread;
    private File traceDir;
    private PCapFileWriter pcapOutput;
    private FileOutputStream timeStream;
    private FileOutputStream appNameStream;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d(TAG, "onStartCommand");
        loadExtras(intent);

        try {
            initTraceFiles();
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
            return START_STICKY_COMPATIBILITY;
        }

        // The handler is only used to show messages.
        if (mHandler == null) {
            mHandler = new Handler(this);
        }

        // Stop the previous session by interrupting the thread.
        if (mThread != null) {
            mThread.interrupt();
            int reps = 0;
            while(mThread.isAlive()){
                Log.i(TAG, "Waiting to exit " + ++reps);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        // Start a new session by creating a new thread.
        mThread = new Thread(this, "CaptureVpnThread");
        mThread.start();
        return START_STICKY;
    }

    private void loadExtras(Intent intent) {
        Log.i(TAG, "loadExtras");
        String traceDirStr = intent.getStringExtra("TRACE_DIR");
        traceDir = new File(traceDirStr);
    }

    private void unregisterAnalyzerCloseCmdReceiver() {
        Log.d(TAG, "inside unregisterAnalyzerCloseCmdReceiver()");
        try {
            if (serviceCloseCmdReceiver != null) {
                unregisterReceiver(serviceCloseCmdReceiver);
                serviceCloseCmdReceiver = null;

                Log.d(TAG, "successfully unregistered serviceCloseCmdReceiver");
            }
        } catch (Exception e) {
            Log.d(TAG, "Ignoring exception in serviceCloseCmdReceiver", e);
        }
    }

    @Override
    public void onRevoke() {
        Log.i(TAG, "revoked!, user has turned off VPN");
        super.onRevoke();
    }
    /**
     * receive message to trigger termination of collection
     */
    private BroadcastReceiver serviceCloseCmdReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            Log.d(TAG, "received service close cmd intent at " + System.currentTimeMillis());
            unregisterAnalyzerCloseCmdReceiver();
            serviceValid = false;
            stopSelf();
        }
    };

    @Override
    public ComponentName startService(Intent service) {
        Log.i(TAG, "startService(...)");
        return super.startService(service);
    }


    @Override
    public boolean stopService(Intent name) {
        Log.i(TAG, "stopService(...)");

        serviceValid = false;
        //	closeTraceFiles();
        return super.stopService(name);
    }

    @Override
    public void protectSocket(Socket socket) {
        this.protect(socket);
    }

    @Override
    public void protectSocket(int socket) {
        this.protect(socket);
    }

    /**
     * called back from background thread when new packet arrived
     */
    @Override
    public void receive(byte[] packet) {
        if (pcapOutput != null) {
            try {
                pcapOutput.addPacket(packet, 0, packet.length, System.currentTimeMillis() * 1000000);
            } catch (IOException e) {
                Log.e(TAG, "pcapOutput.addPacket IOException :" + e.getMessage());
                e.printStackTrace();
            }
        }else{
            Log.e(TAG, "overrun from capture: length:"+packet.length);
        }

    }

    /**
     * Close the packet trace file
     */
    private void closePcapTrace() {
        Log.i(TAG, "closePcapTrace()");
        if (pcapOutput != null) {
            pcapOutput.close();
            pcapOutput = null;
            Log.i(TAG, "closePcapTrace() closed");
        }
    }

    /**
     * onDestroy is invoked when user disconnects the VPN
     */
    @Override
    public void onDestroy() {

        Log.i(TAG, "onDestroy()");
        serviceValid = false;

        unregisterAnalyzerCloseCmdReceiver();

        if (dataservice !=  null)
            dataservice.setShutdown(true);

        if (packetbgWriter != null)
            packetbgWriter.setShuttingDown(true);

        //	closeTraceFiles();

        if(dataServiceThread != null){
            dataServiceThread.interrupt();
        }
        if(packetqueueThread != null){
            packetqueueThread.interrupt();
        }

        try {
            if (mInterface != null) {
                Log.i(TAG, "mInterface.close()");
                mInterface.close();
            }
        } catch (IOException e) {
            Log.d(TAG, "mInterface.close():" + e.getMessage());
            e.printStackTrace();
        }

        // Stop the previous session by interrupting the thread.
        if (mThread != null) {
            mThread.interrupt();
            int reps = 0;
            while(mThread.isAlive()){
                Log.i(TAG, "Waiting to exit " + ++reps);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(reps > 5){
                    break;
                }
            }
            mThread = null;
        }

    }

    @Override
    public void run() {
        Log.i(TAG, "running vpnService");
        SocketProtector protector = SocketProtector.getInstance();
        protector.setProtector(this);

        try {
            if (startVpnService()) {
                try {
                    startCapture();
                    Log.i(TAG, "Capture completed");
                } catch (IOException e) {
                    Log.e(TAG,e.getMessage());
                }
            } else {
                Log.e(TAG,"Failed to start VPN Service!");
            }
        } catch (IOException e) {
            Log.e(TAG,e.getMessage());
        }
        Log.i(TAG, "Closing Capture files");
        closeTraceFiles();
    }

    // Trace files


    /**
     * create, open, initialize trace files
     */
    private void initTraceFiles() throws IOException{
        Log.i(TAG, "initTraceFiles()");
        instanciatePcapFile();
        instanciateTimeFile();
        instanciateAppNameFile();
    }

    /**
     * close the trace files
     */
    private void closeTraceFiles() {
        Log.i(TAG, "closeTraceFiles()");
        closePcapTrace();
        closeTimeFile();
        closeAppNameFile();
    }


    private void instanciateAppNameFile() throws IOException {
        if (!traceDir.exists())
            if (!traceDir.mkdirs())
                Log.e(TAG, "CANNOT make " + traceDir.toString());

        // gen & open pcap file
        String sFileName = "appname";
        File appNameFile = new File(traceDir, sFileName);
        appNameStream = new FileOutputStream(appNameFile);

        try {
            appNameStream.write("adb\n".getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        recordCurrentApps();
    }

    /**
     * Retrieve List of active apps and output to stream
     */
    private void recordCurrentApps() {

        if (appNameStream != null) {
            Log.i(TAG, "Current Active Apps");
            final ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            final List<RunningTaskInfo> recentTasks = activityManager.getRunningTasks(Integer.MAX_VALUE);

            for (int i = 0; i < recentTasks.size(); i++) {
                try {
                    String activeApp = recentTasks.get(i).baseActivity.getPackageName() + " " + recentTasks.get(i).id + "\n";
                    appNameStream.write(activeApp.getBytes());
                    Log.i(TAG, activeApp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void closeAppNameFile(){
        Log.i(TAG, "closeAppNameFile()");
        if (appNameStream != null) {
            try {
                appNameStream.write(".\n".getBytes());
                appNameStream.flush();
                appNameStream.close();
                Log.i(TAG, "...closed");
            } catch (IOException e) {
                Log.e(TAG, "IOException:" + e.getMessage());
            }
        }
    }

    /**
     * Create and leave open, the pcap file
     * @throws IOException
     */
    private void instanciatePcapFile() throws IOException {
        if (!traceDir.exists())
            if (!traceDir.mkdirs())
                Log.e(TAG, "CANNOT make " + traceDir.toString());

        // gen & open pcap file
        String sFileName = "traffic.pcap";
        File pcapFile = new File(traceDir, sFileName);
        pcapOutput = new PCapFileWriter(pcapFile);
    }

    /**
     * Create and leave open, the time file
     * time file format
     * line 1: header
     * line 2: pcap start time
     * line 3: eventtime or uptime (doesn't appear to be used)
     * line 4: pcap stop time
     * line 5: time zone offset
     */
    private void instanciateTimeFile() throws IOException {

        if (!traceDir.exists())
            if (!traceDir.mkdirs())
                Log.e(TAG, "CANNOT make " + traceDir.toString());

        // gen & open pcap file
        String sFileName = "time";
        File timeFile = new File(traceDir, sFileName);
        timeStream = new FileOutputStream(timeFile);

        String str = String.format(Locale.ENGLISH, "%s\n%.3f\n%d\n"
                , "Synchronized timestamps"
                , ((double)System.currentTimeMillis())/1000.0
                , SystemClock.uptimeMillis()
        );

        try {
            timeStream.write(str.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * update and close the time file
     */
    private void closeTimeFile() {
        Log.i(TAG, "closeTimeFile()");
        if (timeStream != null) {
            String str = String.format(Locale.ENGLISH, "%.3f\n", ((double) System.currentTimeMillis()) / 1000.0);
            try {
                timeStream.write(str.getBytes());
                timeStream.flush();
                timeStream.close();
                Log.i(TAG, "...closed");
            } catch (IOException e) {
                Log.e(TAG, "IOException:" + e.getMessage());
            }
        }
    }

    /**
     * setup VPN interface.
     * @return boolean
     * @throws IOException
     */
    boolean startVpnService() throws IOException{
        // If the old interface has exactly the same parameters, use it!
        if (mInterface != null) {
            Log.i(TAG, "Using the previous interface");
            return false;
        }

        Log.i(TAG, "startVpnService => create builder");
        // Configure a builder while parsing the parameters.
        Builder builder = new Builder()
                .addAddress("10.120.0.1", 32)
                .addRoute("0.0.0.0", 0)
                .setSession("AROCollector")
                .setConfigureIntent(mConfigureIntent);
        if (mInterface != null) {
            try {
                mInterface.close();
            } catch (Exception e) {
                Log.e(TAG, "Exception when closing mInterface:" + e.getMessage());
            }
        }

        Log.i(TAG, "startVpnService => builder.establish()");
        mInterface = builder.establish();

        if(mInterface != null){
            Log.i(TAG, "VPN Established:interface = " + mInterface.getFd());
            return true;
        } else {
            Log.d(TAG,"mInterface is null");
            return false;
        }
    }

    /**
     * start background thread to handle client's socket, handle incoming and outgoing packet from VPN interface
     * @throws IOException
     */
    void startCapture() throws IOException{

        Log.i(TAG, "startCapture() :capture starting");

        // Packets to be sent are queued in this input stream.
        FileInputStream clientreader = new FileInputStream(mInterface.getFileDescriptor());

        // Packets received need to be written to this output stream.
        FileOutputStream clientwriter = new FileOutputStream(mInterface.getFileDescriptor());

        // Allocate the buffer for a single packet.
        ByteBuffer packet = ByteBuffer.allocate(4092);
        IClientPacketWriter clientpacketwriter = new ClientPacketWriterImpl(clientwriter);

        SessionHandler handler = SessionHandler.getInstance();
        handler.setWriter(clientpacketwriter);

        //background task for non-blocking socket
        dataservice = new SocketNIODataService();
        dataservice.setWriter(clientpacketwriter);
        dataServiceThread = new Thread(dataservice);
        dataServiceThread.start();

        //background task for writing packet data to pcap file
        packetbgWriter = new SocketDataPublisher();
        packetbgWriter.subscribe(this);
        packetqueueThread = new Thread(packetbgWriter);
        packetqueueThread.start();

        byte[] data;
        int length;
        serviceValid = true;
        while (serviceValid) {
            //read packet from vpn client
            data = packet.array();
            length = clientreader.read(data);
            if(length > 0){
                //Log.d(TAG, "received packet from vpn client: "+length);
                try {
                    handler.handlePacket(data, length);
                } catch (PacketHeaderException e) {
                    Log.e(TAG,e.getMessage());
                }

                packet.clear();
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Log.d(TAG,"Failed to sleep: "+ e.getMessage());
                }
            }
        }
        Log.i(TAG, "capture finished: serviceValid = "+serviceValid);
    }

    @Override
    public boolean handleMessage(Message message) {
        if (message != null) {
            Log.d(TAG, "handleMessage:" + getString(message.what));
            Toast.makeText(this.getApplicationContext(), message.what, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    @Override
    public void protectSocket(DatagramSocket socket) {
        this.protect(socket);
    }

}
