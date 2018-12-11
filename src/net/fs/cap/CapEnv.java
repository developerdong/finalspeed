// Copyright (c) 2015 D1SM.net

package net.fs.cap;

import net.fs.rudp.Route;
import net.fs.utils.ByteShortConvert;
import net.fs.utils.MLog;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.*;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.packet.*;
import org.pcap4j.packet.EthernetPacket.EthernetHeader;
import org.pcap4j.packet.IpV4Packet.IpV4Header;
import org.pcap4j.packet.TcpPacket.TcpHeader;
import org.pcap4j.util.MacAddress;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;


public class CapEnv {

    private final int COUNT = -1;
    private final int READ_TIMEOUT = 1;
    private final int SNAP_LENGTH = 10 * 1024;
    MacAddress gateway_mac;
    MacAddress local_mac;
    PcapHandle sendHandle;
    public boolean tcpEnable = false;
    boolean client;
    Inet4Address local_ipv4;
    VDatagramSocket vDatagramSocket;
    TunManager tcpManager;
    private boolean fwSuccess;
    private final String TCP_TEST_IP = "114.114.114.114";
    private final int TCP_TEST_PORT = 53;
    private String selectedInterfaceName = null;
    private String selectedInterfaceDes = "";
    short listenPort;
    private PcapNetworkInterface nif;
    private CapEnv capEnv;
    private boolean ppp = false;

    {
        capEnv = this;
    }

    public CapEnv(boolean isClient, boolean fwSuccess) {
        this.client = isClient;
        this.fwSuccess = fwSuccess;
        tcpManager = new TunManager(this);
    }

    public static int toUnsigned(short s) {
        return s & 0x0FFFF;
    }

    public void init() throws Exception {
        initInterface();

        Thread systemSleepScanThread = new Thread(() -> {
            long t = System.currentTimeMillis();
            while (true) {
                if (System.currentTimeMillis() - t > 5 * 1000) {
                    MLog.info("休眠恢复...");
                    networkRecover();
                }
                t = System.currentTimeMillis();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        systemSleepScanThread.start();
    }

    private void processPacket(Packet packet) throws Exception {
        EthernetPacket packet_eth = (EthernetPacket) packet;
        EthernetHeader head_eth = packet_eth.getHeader();

        IpV4Packet ipV4Packet = null;
        if (ppp) {
            ipV4Packet = getIpV4Packet_PPPoE(packet_eth);
        } else {
            if (packet_eth.getPayload() instanceof IpV4Packet) {
                ipV4Packet = (IpV4Packet) packet_eth.getPayload();
            }
        }
        if (ipV4Packet != null) {
            IpV4Header ipV4Header = ipV4Packet.getHeader();
            if (ipV4Packet.getPayload() instanceof TcpPacket) {
                TcpPacket tcpPacket = (TcpPacket) ipV4Packet.getPayload();
                TcpHeader tcpHeader = tcpPacket.getHeader();
                if (client) {
                    TCPTun conn = tcpManager.getTcpConnection_Client(ipV4Header.getSrcAddr().getHostAddress(), tcpHeader.getSrcPort().value(), tcpHeader.getDstPort().value());
                    if (conn != null) {
                        conn.process_client(capEnv, packet, head_eth, ipV4Header, tcpPacket, false);
                    }
                } else {
                    TCPTun conn;
                    conn = tcpManager.getTcpConnection_Server(ipV4Header.getSrcAddr().getHostAddress(), tcpHeader.getSrcPort().value());
                    if (
                            tcpHeader.getDstPort().value() == listenPort) {
                        if (tcpHeader.getSyn() && !tcpHeader.getAck() && conn == null) {
                            conn = new TCPTun(capEnv, ipV4Header.getSrcAddr(), tcpHeader.getSrcPort().value());
                            tcpManager.addConnection_Server(conn);
                        }
                        conn = tcpManager.getTcpConnection_Server(ipV4Header.getSrcAddr().getHostAddress(), tcpHeader.getSrcPort().value());
                        if (conn != null) {
                            conn.process_server(packet, head_eth, ipV4Header, tcpPacket, true);
                        }
                    }
                }
            } else if (packet_eth.getPayload() instanceof IllegalPacket) {
                MLog.println("IllegalPacket!!!");
            }
        }

    }

    private PromiscuousMode getMode(PcapNetworkInterface pi) {
        PromiscuousMode mode;
        String string = (pi.getDescription() + ":" + pi.getName()).toLowerCase();
        if (string.contains("wireless")) {
            mode = PromiscuousMode.NONPROMISCUOUS;
        } else {
            mode = PromiscuousMode.PROMISCUOUS;
        }
        return mode;
    }

    boolean initInterface() throws Exception {
        while (!isNetworkConnected()) {
            MLog.info("网络未连通");
            Thread.sleep(1000);
        }
        boolean success = false;
        detectInterface();
        List<PcapNetworkInterface> allDevices = Pcaps.findAllDevs();
        MLog.println("Network Interface List: ");
        for (PcapNetworkInterface pi : allDevices) {
            String desString = "";
            if (pi.getDescription() != null) {
                desString = pi.getDescription();
            }
            MLog.info("  " + desString + "   " + pi.getName());
            if (pi.getName().equals(selectedInterfaceName)
                    && desString.equals(selectedInterfaceDes)) {
                nif = pi;
                //break;
            }
        }
        if (nif != null) {
            String desString = "";
            if (nif.getDescription() != null) {
                desString = nif.getDescription();
            }
            success = true;
            MLog.info("Selected Network Interface:\n" + "  " + desString + "   " + nif.getName());
            if (fwSuccess) {
                tcpEnable = true;
            }
        } else {
            tcpEnable = false;
            MLog.info("Select Network Interface failed,can't use TCP protocol!\n");
        }
        if (tcpEnable) {

            if (sendHandle != null && sendHandle.isOpen()) {
                sendHandle.breakLoop();
                sendHandle.close();
            }

            sendHandle = nif.openLive(SNAP_LENGTH, getMode(nif), READ_TIMEOUT);

            String filter;
            if (!client) {
                //服务端
                filter = "tcp dst port " + toUnsigned(listenPort);
            } else {
                //客户端
                filter = "tcp";
            }
            sendHandle.setFilter(filter, BpfCompileMode.OPTIMIZE);

            final PacketListener listener = packet -> {

                try {
                    if (packet instanceof EthernetPacket) {
                        processPacket(packet);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            };

            Thread thread = new Thread(() -> {
                try {
                    sendHandle.loop(COUNT, listener);
                    sendHandle.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            thread.start();
        }

        if (!client) {
            MLog.info("FinalSpeed server start success.");
        }
        return success;

    }

    private void detectInterface() {
        List<PcapNetworkInterface> allDevices;
        HashMap<PcapNetworkInterface, PcapHandle> handleTable = new HashMap<>();
        try {
            allDevices = Pcaps.findAllDevs();
        } catch (PcapNativeException e1) {
            e1.printStackTrace();
            return;
        }
        for (final PcapNetworkInterface pi : allDevices) {
            try {
                final PcapHandle handle = pi.openLive(SNAP_LENGTH, getMode(pi), READ_TIMEOUT);
                handleTable.put(pi, handle);
                final PacketListener listener = packet -> {

                    try {
                        if (packet instanceof EthernetPacket) {
                            EthernetPacket packet_eth = (EthernetPacket) packet;
                            EthernetHeader head_eth = packet_eth.getHeader();

                            if (head_eth.getType().value() == 0xffff8864) {
                                ppp = true;
                                PacketUtils.ppp = true;
                            }

                            IpV4Packet ipV4Packet = null;
                            IpV4Header ipV4Header;

                            if (ppp) {
                                ipV4Packet = getIpV4Packet_PPPoE(packet_eth);
                            } else {
                                if (packet_eth.getPayload() instanceof IpV4Packet) {
                                    ipV4Packet = (IpV4Packet) packet_eth.getPayload();
                                }
                            }
                            if (ipV4Packet != null) {
                                ipV4Header = ipV4Packet.getHeader();

                                if (ipV4Header.getSrcAddr().getHostAddress().equals(TCP_TEST_IP)) {
                                    local_mac = head_eth.getDstAddr();
                                    gateway_mac = head_eth.getSrcAddr();
                                    local_ipv4 = ipV4Header.getDstAddr();
                                    selectedInterfaceName = pi.getName();
                                    if (pi.getDescription() != null) {
                                        selectedInterfaceDes = pi.getDescription();
                                    }
                                    //MLog.println("local_mac_tcp1 "+gateway_mac+" gateway_mac "+gateway_mac+" local_ipv4 "+local_ipv4);
                                }
                                if (ipV4Header.getDstAddr().getHostAddress().equals(TCP_TEST_IP)) {
                                    local_mac = head_eth.getSrcAddr();
                                    gateway_mac = head_eth.getDstAddr();
                                    local_ipv4 = ipV4Header.getSrcAddr();
                                    selectedInterfaceName = pi.getName();
                                    if (pi.getDescription() != null) {
                                        selectedInterfaceDes = pi.getDescription();
                                    }
                                    //MLog.println("local_mac_tcp2 local_mac "+local_mac+" gateway_mac "+gateway_mac+" local_ipv4 "+local_ipv4);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                };

                Thread thread = new Thread(() -> {
                    try {
                        handle.loop(COUNT, listener);
                        handle.close();
                    } catch (Exception e) {
                        //e.printStackTrace();
                    }
                });
                thread.start();
            } catch (PcapNativeException e1) {
                e1.printStackTrace();
            }

        }

        detectMac_tcp();


        for (PcapNetworkInterface pi : handleTable.keySet()) {
            PcapHandle handle = handleTable.get(pi);
            try {
                handle.breakLoop();
            } catch (NotOpenException e) {
                e.printStackTrace();
            }
            //handle.close();//linux下会阻塞
        }
    }

    private IpV4Packet getIpV4Packet_PPPoE(EthernetPacket packet_eth) throws IllegalRawDataException {
        IpV4Packet ipV4Packet = null;
        byte[] pppData = packet_eth.getPayload().getRawData();
        if (pppData.length > 8 && pppData[8] == 0x45) {
            byte[] b2 = new byte[2];
            System.arraycopy(pppData, 4, b2, 0, 2);
            short len = ByteShortConvert.toShort(b2, 0);
            int ipLength = toUnsigned(len) - 2;
            byte[] ipData = new byte[ipLength];
            //设置ppp参数
            PacketUtils.pppHead_static[2] = pppData[2];
            PacketUtils.pppHead_static[3] = pppData[3];
            if (ipLength == (pppData.length - 8)) {
                System.arraycopy(pppData, 8, ipData, 0, ipLength);
                ipV4Packet = IpV4Packet.newPacket(ipData, 0, ipData.length);
            } else {
                MLog.println("长度不符!");
            }
        }
        return ipV4Packet;
    }

    void createTcpTun_Client(String dstAddress, short dstPort) throws Exception {
        Inet4Address serverAddress = (Inet4Address) Inet4Address.getByName(dstAddress);
        TCPTun conn = new TCPTun(this, serverAddress, dstPort, local_mac, gateway_mac);
        tcpManager.addConnection_Client(conn);
        boolean success = false;
        for (int i = 0; i < 6; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (conn.preDataReady) {
                success = true;
                break;
            }
        }
        if (success) {
            tcpManager.setDefaultTcpTun(conn);
        } else {
            tcpManager.removeTun(conn);
            tcpManager.setDefaultTcpTun(null);
            MLog.info("创建隧道失败!");
            networkRecover();
        }
    }

    private void detectMac_tcp() {
        for (int i = 0; i < 5; i++) {
            try {
                Route.es.execute(() -> {
                    try {
                        Socket socket = new Socket(TCP_TEST_IP, TCP_TEST_PORT);
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
                Thread.sleep(500);
                if (local_mac != null) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }
        }
    }

    public void setListenPort(short listenPort) {
        this.listenPort = listenPort;
        if (!client) {
            MLog.info("Listen tcp port: " + toUnsigned(listenPort));
        }
    }

    private boolean isNetworkConnected() {
        try {
            Socket socket = new Socket(TCP_TEST_IP, TCP_TEST_PORT);
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private void networkRecover() {
        MLog.info("网络恢复...重新绑定网卡");
        try {
            if (initInterface()) {
                MLog.info("网络恢复成功");
            }
        } catch (Exception initInterfaceException) {
            initInterfaceException.printStackTrace();
        }
    }
}
