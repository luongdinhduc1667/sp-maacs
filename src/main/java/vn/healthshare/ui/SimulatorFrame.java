package vn.healthshare.ui;

import javax.swing.*;
import java.awt.*;

public final class SimulatorFrame extends JFrame {
    public SimulatorFrame() {
        super("SP-MAACS | Mô phỏng chia sẻ hồ sơ y tế");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setContentPane(new SimulatorPanel());
        setMinimumSize(new Dimension(1040,740)); setSize(1380,940); setLocationRelativeTo(null);
    }
}
