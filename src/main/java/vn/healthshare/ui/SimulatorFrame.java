package vn.healthshare.ui;

import javax.swing.*;
import java.awt.*;

public final class SimulatorFrame extends JFrame {
    public SimulatorFrame() {
        super("SP-MAACS | Mô phỏng chia sẻ hồ sơ y tế");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setContentPane(new SimulatorPanel());
        Rectangle available=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setMinimumSize(new Dimension(Math.min(1040,available.width),Math.min(700,available.height)));
        setSize(Math.min(1380,available.width),Math.min(940,available.height));setLocationRelativeTo(null);
    }
}
