// Copyright (c) 2015 D1SM.net

package net.fs.rudp;

import java.net.DatagramPacket;

public interface MessageInterface {
    int getVer();

    int getSType();

    DatagramPacket getDatagramPacket();
}
  
