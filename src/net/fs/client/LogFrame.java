package net.fs.client;

import net.fs.utils.LogListener;
import net.fs.utils.LogOutputStream;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;

public class LogFrame extends JFrame implements LogListener {

    private static final long serialVersionUID = 8642892909397273483L;
    ClientUI ui;
    private JTextArea logArea;

    LogFrame(ClientUI ui) {
        super("日志");
        this.ui = ui;
        JPanel panel = (JPanel) getContentPane();
        panel.setLayout(new MigLayout("insets 5 5 5 5"));


        logArea = new JTextArea();

        JScrollPane scroll = new JScrollPane(logArea);

        panel.add(scroll, "width :10240:,height :10240: ,wrap");

        JPanel p3 = new JPanel();
        panel.add(p3, "align center,wrap");
        p3.setLayout(new MigLayout("inset 5 5 5 5"));

        final JCheckBox cb_lock = new JCheckBox("自动滚动", true);
        p3.add(cb_lock, "align center");
        cb_lock.addActionListener(e -> {
            DefaultCaret caret = (DefaultCaret) logArea.getCaret();
            if (cb_lock.isSelected()) {
                caret.setDot(logArea.getText().length());
                caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
            } else {
                caret.setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
            }
        });

        JButton button_clear = createButton("清空");
        p3.add(button_clear);
        button_clear.addActionListener(e -> logArea.setText(""));
    }

    void showText(String text) {
        logArea.append(text);
    }

    @Override
    public void onAppendContent(LogOutputStream los, final String text) {
        SwingUtilities.invokeLater(() -> showText(text));
    }

    JButton createButton(String name) {
        JButton button = new JButton(name);
        button.setMargin(new Insets(0, 5, 0, 5));
        button.setFocusPainted(false);
        return button;
    }


}
