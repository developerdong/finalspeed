// Copyright (c) 2015 D1SM.net

package net.fs.client;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class AddMapFrame extends JDialog {

    private static final long serialVersionUID = -3248779355079724594L;

    ClientUI ui;

    private final JTextField remoteAddressTextField;
    private final JTextField remotePortTextField;
    private final JTextField localPortTextField;
    private final JTextField nameTextField;

    int downloadSpeed, uploadSpeed;

    MapRule maprule_origin;

    boolean edit = false;

    AddMapFrame(final ClientUI ui, JFrame parent, final MapRule maprule_origin, final boolean edit) {
        super(parent, Dialog.ModalityType.APPLICATION_MODAL);
        this.ui = ui;
        this.edit = edit;
        this.maprule_origin = maprule_origin;
        setTitle("增加映射");
        //setSize(size);
        if (edit) {
            setTitle("编辑映射");
        }

        JPanel panel = (JPanel) getContentPane();
        panel.setLayout(new MigLayout("alignx center,aligny center,insets 10 10 10 10"));


        JPanel p3 = new JPanel();
        panel.add(p3, "wrap");
        p3.setBorder(BorderFactory.createEtchedBorder());
        p3.setLayout(new MigLayout("inset 5 5 5 5"));

        p3.add(new JLabel("名称:"));
        nameTextField = new JTextField();
        p3.add(nameTextField, "width :100: ,wrap");

        p3.add(new JLabel("加速地址:"));
        remoteAddressTextField = new JTextField("");
        p3.add(remoteAddressTextField, "width :100:,wrap");
        remoteAddressTextField.setToolTipText("需要加速的主机地址");

        p3.add(new JLabel("加速端口:"));
        remotePortTextField = new JTextField("");
        p3.add(remotePortTextField, "width :50:,wrap");
        remotePortTextField.setToolTipText("需要加速的端口号");

        p3.add(new JLabel("本地端口:	"));
        localPortTextField = new JTextField();
        p3.add(localPortTextField, "width :50: ,wrap");

        JPanel p6 = new JPanel();
        panel.add(p6, "align center,wrap");
        p6.setLayout(new MigLayout("align center"));

        JButton button_ok = createButton("确定");
        p6.add(button_ok);
        button_ok.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    checkName(nameTextField.getText());
                    checkPort(localPortTextField.getText());
                    checkPort(remotePortTextField.getText());
                    checkDstAddress(remoteAddressTextField.getText());
                    String name = nameTextField.getText();
                    String address = remoteAddressTextField.getText();
                    int listen_port = Integer.parseInt(localPortTextField.getText());
                    int dst_port = Integer.parseInt(remotePortTextField.getText());
                    MapRule mapRule_new = new MapRule();
                    mapRule_new.setName(name);
                    mapRule_new.setDstAddress(address);
                    mapRule_new.setListenPort(listen_port);
                    mapRule_new.setDstPort(dst_port);
                    if (!edit) {
                        ui.mapClient.portMapManager.addMapRule(mapRule_new);
                    } else {
                        ui.mapClient.portMapManager.updateMapRule(maprule_origin, mapRule_new);
                    }
                    ui.loadMapRule();
                    ui.select(mapRule_new.getName());
                    setVisible(false);
                } catch (Exception e1) {
                    //e2.printStackTrace();
                    JOptionPane.showMessageDialog(ui.mainFrame, e1.getMessage(), "消息", JOptionPane.WARNING_MESSAGE);
                }
            }
        });

        p6.add(new JLabel(" "));

        JButton button_cancel = createButton("取消");
        p6.add(button_cancel);
        button_cancel.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                setVisible(false);
            }
        });


        if (edit) {
            nameTextField.setText(maprule_origin.getName());
            remoteAddressTextField.setText(maprule_origin.getDstAddress());
            localPortTextField.setText(maprule_origin.getListenPort() + "");
            remotePortTextField.setText(maprule_origin.getDstPort() + "");
        }

        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    void checkName(String s) throws Exception {
        if (s.trim().equals("")) {
            throw new Exception("请输入名称");
        }
    }

    void checkDstAddress(String s) throws Exception {
        if (s.trim().equals("")) {
            throw new Exception("请输入目标地址");
        }
    }

    void checkPort(String s) throws Exception {
        int port = 0;
        try {
            port = Integer.parseInt(s);
        } catch (Exception e1) {
            throw new Exception("请输入正确端口号");
        }
        if (port < 1 | port > 256 * 256) {
            throw new Exception("请输入正确端口号");
        }
    }

    JButton createButton(String name) {
        JButton button = new JButton(name);
        button.setMargin(new Insets(0, 5, 0, 5));
        button.setFocusPainted(false);
        return button;
    }


}
