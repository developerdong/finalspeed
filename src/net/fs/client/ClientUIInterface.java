// Copyright (c) 2015 D1SM.net

package net.fs.client;

public interface ClientUIInterface {


    void setMessage(String message);

    void updateUISpeed(int downSpeed, int upSpeed);

    boolean login();

    boolean updateNode(boolean testSpeed);

    boolean isOsx_fw_pf();

    boolean isOsx_fw_ipfw();

}
