// Copyright (c) 2015 D1SM.net

package net.fs.client;

import java.io.Serializable;
import java.net.ServerSocket;

public class MapRule implements Serializable {

    public static final String DST_PORT_KEY = "dst_port";
    public static final String DST_ADDRESS_KEY = "dst_address";
    static final String MAP_LIST_KEY = "map_list";
    static final String NAME_KEY = "name";
    static final String LISTEN_PORT_KEY = "listen_port";

    private static final long serialVersionUID = -3504577683070928480L;
    private int listenPort;

    private int dstPort;

    private String dstAddress;

    private ServerSocket serverSocket;

    private String name;

    public int getDstPort() {
        return dstPort;
    }

    void setDstPort(int dstPort) {
        this.dstPort = dstPort;
    }

    String getDstAddress() {
        return dstAddress;
    }

    void setDstAddress(String dstAddress) {
        this.dstAddress = dstAddress;
    }

    ServerSocket getServerSocket() {
        return serverSocket;
    }

    void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    int getListenPort() {
        return listenPort;
    }

    void setListenPort(int listenPort) {
        this.listenPort = listenPort;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
