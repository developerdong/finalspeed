// Copyright (c) 2015 D1SM.net

package net.fs.cap;

import net.fs.rudp.CopiedIterator;
import net.fs.utils.MLog;

import java.util.HashMap;
import java.util.Iterator;

public class TunManager {

    static TunManager tunManager;
    HashMap<String, TCPTun> connTable = new HashMap<String, TCPTun>();
    TCPTun defaultTcpTun;
    Thread scanThread;
    Object syn_scan = new Object();
    CapEnv capEnv;

    {
        tunManager = this;
    }

    {
        scanThread = new Thread() {
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    scan();
                }
            }
        };
        scanThread.start();
    }

    TunManager(CapEnv capEnv) {
        this.capEnv = capEnv;
    }

    public static TunManager get() {
        return tunManager;
    }

    void scan() {
        Iterator<String> it = getConnTableIterator();
        while (it.hasNext()) {
            String key = it.next();
            TCPTun tun = connTable.get(key);
            if (tun != null) {
                if (tun.preDataReady) {
                    //无数据超时
                    long t = System.currentTimeMillis() - tun.lastReceiveDataTime;
                    if (t > 6000) {
                        connTable.remove(key);
                        if (capEnv.client) {
                            defaultTcpTun = null;
                            MLog.println("tcp隧道超时");
                        }
                    }
                } else {
                    //连接中超时
                    if (System.currentTimeMillis() - tun.createTime > 5000) {
                        connTable.remove(key);
                    }
                }
            }
        }
    }

    public void removeTun(TCPTun tun) {
        connTable.remove(tun.key);
    }

    Iterator<String> getConnTableIterator() {
        Iterator<String> it = null;
        synchronized (syn_scan) {
            it = new CopiedIterator(connTable.keySet().iterator());
        }
        return it;
    }

    public TCPTun getTcpConnection_Client(String remoteAddress, short remotePort, short localPort) {
        return connTable.get(remoteAddress + ":" + remotePort + ":" + localPort);
    }

    public void addConnection_Client(TCPTun conn) {
        synchronized (syn_scan) {
            String key = conn.remoteAddress.getHostAddress() + ":" + conn.remotePort + ":" + conn.localPort;
            //MLog.println("addConnection "+key);
            conn.setKey(key);
            connTable.put(key, conn);
        }
    }

    public TCPTun getTcpConnection_Server(String remoteAddress, short remotePort) {
        return connTable.get(remoteAddress + ":" + remotePort);
    }

    public void addConnection_Server(TCPTun conn) {
        synchronized (syn_scan) {
            String key = conn.remoteAddress.getHostAddress() + ":" + conn.remotePort;
            //MLog.println("addConnection "+key);
            conn.setKey(key);
            connTable.put(key, conn);
        }
    }

    public TCPTun getDefaultTcpTun() {
        return defaultTcpTun;
    }

    public void setDefaultTcpTun(TCPTun defaultTcpTun) {
        this.defaultTcpTun = defaultTcpTun;
    }

}
