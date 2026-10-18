package vn.healthshare;

import vn.healthshare.ui.SimulatorPanel;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Renders real Swing components and exercises the main access workflow without opening a window. */
public final class UiSmokeTest {
    public static void main(String[] args)throws Exception{
        Path output=Path.of("output/screenshots");Files.createDirectories(output);
        CountDownLatch benchmarkFinished=new CountDownLatch(1);
        AtomicReference<SimulatorPanel> view=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            try{
                SimulatorPanel panel=new SimulatorPanel();view.set(panel);panel.setSize(1380,940);layout(panel);
                JTabbedPane tabs=descendants(panel,JTabbedPane.class).getFirst();
                button(panel,"Chạy 8 tình huống mẫu").doClick();
                JTable demo=descendants(tabs.getComponentAt(0),JTable.class).getFirst();
                if(demo.getRowCount()!=8)throw new AssertionError("Demo UI row count");
                for(int row=0;row<8;row++)if(!demo.getValueAt(row,3).toString().startsWith("PASS"))throw new AssertionError("Demo UI result "+row);
                render(panel,output.resolve("01-overview.png"));
                tabs.setSelectedIndex(1);layout(panel);render(panel,output.resolve("06-authorities-and-users.png"));
                tabs.setSelectedIndex(2);layout(panel);render(panel,output.resolve("02-encryption.png"));
                button(panel,"Mã hóa & đưa lên cloud").doClick();
                tabs.setSelectedIndex(3);layout(panel);
                List<JComboBox> combos=descendants(tabs.getComponentAt(3),JComboBox.class);
                combos.get(0).setSelectedItem("doctor-an");button(panel,"Giải mã hồ sơ").doClick();
                if(descendants(tabs.getComponentAt(3),JTextArea.class).stream().noneMatch(t->t.getText().contains("Nguyễn An")))throw new AssertionError("Authorized GUI plaintext");
                render(panel,output.resolve("03-authorized-access.png"));
                combos.get(0).setSelectedItem("doctor-binh");button(panel,"Giải mã hồ sơ").doClick();
                if(descendants(tabs.getComponentAt(3),JTextArea.class).stream().anyMatch(t->t.getText().contains("Nguyễn An")))throw new AssertionError("GUI leaked prior plaintext");
                button(panel,"Ghép & thử cả hai K1").doClick();
                if(descendants(tabs.getComponentAt(3),JTextArea.class).stream().noneMatch(t->t.getText().contains("Ghép theo cả hai K1 đều bị từ chối")))throw new AssertionError("GUI collusion workflow");
                render(panel,output.resolve("04-denied-access.png"));
                tabs.setSelectedIndex(4);layout(panel);render(panel,output.resolve("05-cloud.png"));
                tabs.setSelectedIndex(5);layout(panel);
                JButton run=button(panel,"Chạy benchmark");
                run.addPropertyChangeListener("enabled",e->{if(Boolean.TRUE.equals(e.getNewValue()))benchmarkFinished.countDown();});run.doClick();
                System.out.println("PASS UI demo, encrypt, authorized/denied access, collusion and stale plaintext cleared.");
            }catch(Exception e){throw new RuntimeException(e);}
        });
        if(!benchmarkFinished.await(60,TimeUnit.SECONDS))throw new AssertionError("GUI benchmark timed out");
        SwingUtilities.invokeAndWait(()->{
            try{
                SimulatorPanel panel=view.get();JTabbedPane tabs=descendants(panel,JTabbedPane.class).getFirst();
                if(descendants(tabs.getComponentAt(5),JTable.class).getFirst().getRowCount()!=5)throw new AssertionError("GUI benchmark result rows");
                render(panel,output.resolve("07-benchmark.png"));
                System.out.println("PASS UI benchmark background worker, table and chart; 7 screenshots rendered.");
            }catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static JButton button(Component root,String text){return descendants(root,JButton.class).stream().filter(b->b.getText().equals(text)).findFirst().orElseThrow();}
    private static <T extends Component> List<T> descendants(Component root,Class<T> type){List<T> out=new ArrayList<>();if(type.isInstance(root))out.add(type.cast(root));if(root instanceof Container c)for(Component child:c.getComponents())out.addAll(descendants(child,type));return out;}
    private static void layout(Component root){if(root instanceof Container c){c.doLayout();for(Component child:c.getComponents())layout(child);}}
    private static void render(JPanel panel,Path path)throws Exception{layout(panel);BufferedImage im=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=im.createGraphics();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);panel.printAll(g);g.dispose();ImageIO.write(im,"png",path.toFile());}
}
