// Copyright (c) 2015 D1SM.net

package net.fs.client;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import net.fs.rudp.Route;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;

public class PortMapManager {

    MapClient mapClient;

    ArrayList<MapRule> mapList = new ArrayList<MapRule>();

    HashMap<Integer, MapRule> mapRuleTable = new HashMap<Integer, MapRule>();

    String configFilePath = new File(URLDecoder.decode(this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath(), "UTF-8")).getParent() + System.getProperty("file.separator") + "port_map.json";

    PortMapManager(MapClient mapClient) throws UnsupportedEncodingException {
        this.mapClient = mapClient;
        //listenPort();
        loadMapRule();
    }

    public static String readFileUtf8(String path) throws Exception {
        String str = null;
        FileInputStream fis = null;
        DataInputStream dis = null;
        try {
            File file = new File(path);

            int length = (int) file.length();
            byte[] data = new byte[length];

            fis = new FileInputStream(file);
            dis = new DataInputStream(fis);
            dis.readFully(data);
            str = new String(data, StandardCharsets.UTF_8);

        } catch (Exception e) {
            //e.printStackTrace();
            throw e;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (dis != null) {
                try {
                    dis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return str;
    }

    void addMapRule(MapRule mapRule) throws Exception {
        if (getMapRule(mapRule.getName()) != null) {
            throw new Exception("映射 " + mapRule.getName() + " 已存在,请修改名称!");
        }
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(mapRule.getListenPort());
            listen(serverSocket);
            mapList.add(mapRule);
            mapRuleTable.put(mapRule.getListenPort(), mapRule);
            saveMapRule();
        } catch (IOException e2) {
            //e2.printStackTrace();
            throw new Exception("端口 " + mapRule.getListenPort() + " 已经被占用!");
        }
    }

    void removeMapRule(String name) {
        MapRule mapRule = getMapRule(name);
        if (mapRule != null) {
            mapList.remove(mapRule);
            mapRuleTable.remove(mapRule.getListenPort());
            if (mapRule.getServerSocket() != null) {
                try {
                    mapRule.getServerSocket().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            try {
                saveMapRule();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void updateMapRule(MapRule mapRule_origin, MapRule mapRule_new) throws Exception {
        if (getMapRule(mapRule_new.getName()) != null && !mapRule_origin.getName().equals(mapRule_new.getName())) {
            throw new Exception("映射 " + mapRule_new.getName() + " 已存在,请修改名称!");
        }
        ServerSocket serverSocket;
        if (mapRule_origin.getListenPort() != mapRule_new.getListenPort()) {
            try {
                serverSocket = new ServerSocket(mapRule_new.getListenPort());
                listen(serverSocket);
                if (mapRule_origin.getServerSocket() != null) {
                    mapRule_origin.getServerSocket().close();
                }
                mapRule_origin.setServerSocket(serverSocket);
                mapRuleTable.remove(mapRule_origin.getListenPort());
                mapRuleTable.put(mapRule_new.getListenPort(), mapRule_new);
            } catch (IOException e2) {
                //e2.printStackTrace();
                throw new Exception("端口 " + mapRule_new.getListenPort() + " 已经被占用!");
            }
        }
        mapRule_origin.setName(mapRule_new.getName());
        mapRule_origin.setDstAddress(mapRule_new.getDstAddress());
        mapRule_origin.setListenPort(mapRule_new.getListenPort());
        mapRule_origin.setDstPort(mapRule_new.getDstPort());
        saveMapRule();

    }

    void saveMapRule() throws Exception {
        JSONObject json = new JSONObject();
        JSONArray json_map_list = new JSONArray();
        json.put(MapRule.MAP_LIST_KEY, json_map_list);
        for (MapRule r : mapList) {
            JSONObject json_rule = new JSONObject();
            json_rule.put(MapRule.NAME_KEY, r.getName());
            json_rule.put(MapRule.DST_ADDRESS_KEY, r.getDstAddress());
            json_rule.put(MapRule.LISTEN_PORT_KEY, r.getListenPort());
            json_rule.put(MapRule.DST_PORT_KEY, r.getDstPort());
            json_map_list.add(json_rule);
        }
        try {
            saveFile(json.toJSONString().getBytes(StandardCharsets.UTF_8), configFilePath);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
            throw new Exception("保存失败!");
        }
    }

    void loadMapRule() {
        String content;
        JSONObject json = null;
        try {
            content = readFileUtf8(configFilePath);
            json = JSONObject.parseObject(content);
        } catch (Exception e) {
            //e.printStackTrace();
        }
        if (json != null && json.containsKey(MapRule.MAP_LIST_KEY)) {
            JSONArray json_map_list = json.getJSONArray(MapRule.MAP_LIST_KEY);
            for (Object o : json_map_list) {
                JSONObject json_rule = (JSONObject) o;
                MapRule mapRule = new MapRule();
                mapRule.setName(json_rule.getString(MapRule.NAME_KEY));
                mapRule.setDstAddress(json_rule.getString(MapRule.DST_ADDRESS_KEY));
                mapRule.setListenPort(json_rule.getIntValue(MapRule.LISTEN_PORT_KEY));
                mapRule.setDstPort(json_rule.getIntValue(MapRule.DST_PORT_KEY));
                mapList.add(mapRule);
                ServerSocket serverSocket;
                try {
                    serverSocket = new ServerSocket(mapRule.getListenPort());
                    listen(serverSocket);
                    mapRule.setServerSocket(serverSocket);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mapRuleTable.put(mapRule.getListenPort(), mapRule);
            }
        }

    }

    MapRule getMapRule(String name) {
        MapRule rule = null;
        for (MapRule r : mapList) {
            if (r.getName().equals(name)) {
                rule = r;
                break;
            }
        }
        return rule;
    }

    public ArrayList<MapRule> getMapList() {
        return mapList;
    }

    public void setMapList(ArrayList<MapRule> mapList) {
        this.mapList = mapList;
    }

    void listen(final ServerSocket serverSocket) {
        Route.es.execute(new Runnable() {

            @Override
            public void run() {
                while (true) {
                    try {
                        final Socket socket = serverSocket.accept();
                        Route.es.execute(new Runnable() {

                            @Override
                            public void run() {
                                int listenPort = serverSocket.getLocalPort();
                                MapRule mapRule = mapRuleTable.get(listenPort);
                                if (mapRule != null) {
                                    Route route = null;
                                    if (mapClient.isUseTcp()) {
                                        route = mapClient.route_tcp;
                                    } else {
                                        route = mapClient.route_udp;
                                    }
                                    PortMapProcess process = new PortMapProcess(mapClient, route, socket, mapClient.serverAddress, mapClient.serverPort, null,
                                            mapRule.getDstAddress(), mapRule.getDstPort());
                                }
                            }

                        });

                    } catch (IOException e) {
                        e.printStackTrace();
                        break;
                    }
                }
            }
        });
    }

    void saveFile(byte[] data, String path) throws Exception {
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(path);
            fos.write(data);
        } catch (Exception e) {
            throw e;
        } finally {
            if (fos != null) {
                fos.close();
            }
        }
    }
}
