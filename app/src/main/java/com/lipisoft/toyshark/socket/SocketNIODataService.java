package com.lipisoft.toyshark.socket;

import android.util.Log;

import com.lipisoft.toyshark.IClientPacketWriter;
import com.lipisoft.toyshark.Session;
import com.lipisoft.toyshark.SessionManager;
import com.lipisoft.toyshark.util.PacketUtil;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class SocketNIODataService implements Runnable {
	private static final String TAG = "SocketNIODataService";
	public static final Object syncSelector = new Object();
	public static final Object syncSelector2 = new Object();

	private SessionManager sessionManager;
	private static IClientPacketWriter writer;
	private volatile boolean shutdown = false;
	private Selector selector;
	//create thread pool for reading/writing data to socket
	private ThreadPoolExecutor workerPool;
	
	public SocketNIODataService(IClientPacketWriter iClientPacketWriter) {
		final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();
		workerPool = new ThreadPoolExecutor(8, 100, 10, TimeUnit.SECONDS, taskQueue);
		writer = iClientPacketWriter;
	}

	@Override
	public void run() {
		Log.d(TAG,"SocketDataService starting in background...");
		sessionManager = SessionManager.getInstance();
		selector = sessionManager.getSelector();
		runTask();
	}
	/**
	 * notify long running task to shutdown
	 * @param isshutdown
	 */
	public void setShutdown(boolean isshutdown){
		this.shutdown = isshutdown;
		this.sessionManager.getSelector().wakeup();
	}

	void runTask(){
		Log.d(TAG, "Selector is running...");
		
		while(!shutdown){
			try {
				synchronized(syncSelector){
					selector.select();
				}
			} catch (IOException e) {
				Log.e(TAG,"Error in Selector.select(): " + e.getMessage());
				try {
					Thread.sleep(100);
				} catch (InterruptedException ex) {
					Log.e(TAG, e.toString());
				}
				continue;
			}
			if(shutdown){
				break;
			}
			synchronized(syncSelector2){
				Iterator<SelectionKey> iter = selector.selectedKeys().iterator();
				while(iter.hasNext()){
					SelectionKey key = iter.next();
					SelectableChannel selectableChannel = key.channel();
					if(selectableChannel instanceof SocketChannel) {
						try {
							processTCPSelectionKey(key);
						} catch (IOException e) {
							key.cancel();
						}
					} else if (selectableChannel instanceof DatagramChannel) {
						processUDPSelectionKey(key);
					}
					iter.remove();
					if(shutdown){
						break;
					}
				}
			}
		}
	}

	void processUDPSelectionKey(SelectionKey key){
		if(!key.isValid()){
			Log.d(TAG,"Invalid SelectionKey for UDP");
			return;
		}
		DatagramChannel channel = (DatagramChannel)key.channel();
		Session sess = sessionManager.getSessionByDatagramChannel(channel);
		if(sess == null){
			return;
		}
		
		if(!sess.isConnected() && key.isConnectable()){
			String ips = PacketUtil.intToIPAddress(sess.getDestAddress());
			int port = sess.getDestPort();
			SocketAddress addr = new InetSocketAddress(ips,port);
			try {
				Log.d(TAG,"selector: connecting to remote UDP server: "+ips+":"+port);
				try{
					channel = channel.connect(addr);
					sess.setUdpChannel(channel);
					sess.setConnected(channel.isConnected());
					
				}catch(ClosedChannelException ex){
					sess.setAbortingConnection(true);
				}catch(UnresolvedAddressException ex2){
					sess.setAbortingConnection(true);
				}catch(UnsupportedAddressTypeException ex3){
					sess.setAbortingConnection(true);
				}catch(SecurityException ex4){
					sess.setAbortingConnection(true);
				}
				
			}catch(ClosedChannelException ex1){
				Log.e(TAG,"failed to connect to closed udp: "+ex1.getMessage());
				sess.setAbortingConnection(true);
			} catch (IOException e) {
				Log.e(TAG,"failed to connect to udp: "+e.getMessage());
				e.printStackTrace();
				sess.setAbortingConnection(true);
			}
		}
		if(channel.isConnected()){
			processSelector(key, sess);
		}
	}
	void processTCPSelectionKey(SelectionKey key) throws IOException{
		if(!key.isValid()){
			Log.d(TAG,"Invalid SelectionKey for TCP");
			return;
		}
		SocketChannel channel = (SocketChannel)key.channel();
		Session sess = sessionManager.getSessionByChannel(channel);
		if(sess == null){
			return;
		}
		
		if(!sess.isConnected() && key.isConnectable()){
			String ips = PacketUtil.intToIPAddress(sess.getDestAddress());
			int port = sess.getDestPort();
			SocketAddress addr = new InetSocketAddress(ips,port);
			Log.d(TAG,"connecting to remote tcp server: "+ips+":"+port);
			boolean connected = false;
			if(!channel.isConnected() && !channel.isConnectionPending()){
				try{
					connected = channel.connect(addr);
				}catch(ClosedChannelException ex){
					sess.setAbortingConnection(true);
				}catch(UnresolvedAddressException ex2){
					sess.setAbortingConnection(true);
				}catch(UnsupportedAddressTypeException ex3){
					sess.setAbortingConnection(true);
				}catch(SecurityException ex4){
					sess.setAbortingConnection(true);
				}catch(IOException ex5){
					sess.setAbortingConnection(true);
				}
			}
			
			if(connected){
				sess.setConnected(connected);
				Log.d(TAG,"connected immediately to remote tcp server: "+ips+":"+port);
			}else{
				if(channel.isConnectionPending()){
					connected = channel.finishConnect();
					sess.setConnected(connected);
					Log.d(TAG,"connected to remote tcp server: "+ips+":"+port);
				}
			}
		}
		if(channel.isConnected()){
			processSelector(key, sess);
		}
	}

	private void processSelector(SelectionKey selectionKey, Session session){
		String sessionKey = sessionManager.createKey(session.getDestAddress(),
				session.getDestPort(), session.getSourceIp(),
				session.getSourcePort());
		//tcp has PSH flag when data is ready for sending, UDP does not have this
		if(selectionKey.isValid() && selectionKey.isWritable()
				&& !session.isBusywrite() && session.hasDataToSend()
				&& session.isDataForSendingReady())
		{
			session.setBusywrite(true);
			final SocketDataWriterWorker worker =
					new SocketDataWriterWorker(writer, sessionKey);
			workerPool.execute(worker);
		}
		if(selectionKey.isValid() && selectionKey.isReadable()
				&& !session.isBusyread())
		{
			session.setBusyread(true);
			final SocketDataReaderWorker worker =
					new SocketDataReaderWorker(writer, sessionKey);
			workerPool.execute(worker);
		}
	}
}
