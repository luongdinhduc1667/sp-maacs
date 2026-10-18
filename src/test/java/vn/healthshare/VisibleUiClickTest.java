package vn.healthshare;

import vn.healthshare.ui.SimulatorPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Run explicitly on an interactive desktop; clicks real Swing controls with AWT Robot. */
public final class VisibleUiClickTest {
    private static JFrame frame;
    private static SimulatorPanel panel;
    private static Robot robot;

    public static void main(String[] args) {
        int exitCode=0;
        try {
            if(GraphicsEnvironment.isHeadless())throw new AssertionError("This test requires a visible desktop.");
            Path csv=Path.of("output/paper-figure3-517.csv").toAbsolutePath();
            if(!Files.isRegularFile(csv))throw new AssertionError("Missing measured CSV: "+csv);
            Files.createDirectories(Path.of("output/screenshots"));
            robot=new Robot();robot.setAutoDelay(80);
            edt(()->{
                panel=new SimulatorPanel();
                frame=new JFrame("SP-MAACS — visible click regression");
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.setContentPane(panel);
                Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
                frame.setSize(Math.min(1380,screen.width),Math.min(940,screen.height));
                frame.setLocationRelativeTo(null);frame.setVisible(true);frame.toFront();frame.requestFocus();
                return null;
            });
            await("visible production window",Duration.ofSeconds(10),()->frame.isShowing());
            edt(()->{
                require(!panel.isTaskRunning(),"Default startup must not start crypto automatically.");
                require(!panel.hasCryptoSession(),"Startup must be a lazy session.");
                require(button("Nạp dữ liệu mẫu").isEnabled(),"Sample button must be available immediately.");
                require(button("Nạp CSV đã đo").isEnabled(),"CSV button must be available immediately.");
                require(button("Xem cấu trúc DNF").isEnabled(),"DNF preview must be available immediately.");
                return null;
            });
            screenshot(Path.of("output/screenshots/08-visible-startup.png"));

            clickTab("Mã hóa hồ sơ");
            clickButton("Xem cấu trúc DNF");
            await("physical DNF preview click",Duration.ofSeconds(5),()->descendants(panel,JLabel.class).stream()
                    .anyMatch(label->label.getText()!=null&&label.getText().startsWith("3 nhánh · ")));
            System.out.println("PASS real default-517 window opens immediately; physical tab and DNF clicks work.");

            clickTab("Hiệu năng");
            clickButton("Nạp CSV đã đo");
            await("physical CSV click opens a modal file chooser",Duration.ofSeconds(10),()->visibleChooser()!=null);
            edt(()->{
                JFileChooser chooser=visibleChooser();
                require(chooser!=null,"CSV click must open a real chooser.");
                Window owner=SwingUtilities.getWindowAncestor(chooser);
                require(owner instanceof JDialog dialog&&dialog.isModal()&&dialog.isShowing(),"CSV chooser must be a visible modal dialog.");
                chooser.setSelectedFile(csv.toFile());chooser.approveSelection();return null;
            });
            await("CSV table populated from visible chooser",Duration.ofSeconds(10),()->{
                JTabbedPane tabs=tabs();
                return descendants(tabs.getSelectedComponent(),JTable.class).stream().anyMatch(table->table.getRowCount()==5);
            });
            edt(()->{require(!panel.hasCryptoSession(),"Viewing measured CSV must not initialize crypto.");return null;});
            System.out.println("PASS physical CSV button opens modal chooser and loads five measured rows without crypto setup.");

            clickButton("Nạp dữ liệu mẫu");
            await("sample click starts background initialization",Duration.ofSeconds(5),()->panel.isTaskRunning());
            long pulseStarted=System.nanoTime();
            edt(()->{
                require(panel.isTaskRunning(),"Initialization must still run asynchronously.");
                require(button("Nạp CSV đã đo").isEnabled(),"CSV must remain enabled during initialization.");
                require(button("Hủy tác vụ").isEnabled(),"Cancel must be enabled during initialization.");
                return null;
            });
            require(System.nanoTime()-pulseStarted<Duration.ofSeconds(2).toNanos(),"The EDT must process input during real 517-bit initialization.");
            clickButton("Hủy tác vụ");
            await("cancelled worker exits safely",Duration.ofSeconds(90),()->!panel.isTaskRunning());
            edt(()->{
                require(!panel.hasCryptoSession(),"Cancelled initialization must not publish a crypto session.");
                require(button("Nạp dữ liệu mẫu").isEnabled(),"Sample button must recover after cancellation.");
                require(button("Nạp CSV đã đo").isEnabled(),"CSV button must remain available after cancellation.");
                require(descendants(panel,JLabel.class).stream().anyMatch(label->label.getText()!=null&&label.getText().startsWith("Đã hủy tác vụ.")),"Cancelled status must be visible.");
                return null;
            });
            screenshot(Path.of("output/screenshots/09-visible-cancelled.png"));
            System.out.println("PASS physical sample/cancel clicks at 517 bits; EDT responsive, CSV stays enabled, no cancelled session published.");
        }catch(Throwable failure){
            exitCode=1;failure.printStackTrace(System.err);
        }finally{
            try{edt(()->{for(Window window:Window.getWindows())window.dispose();return null;});}
            catch(Exception cleanup){exitCode=1;cleanup.printStackTrace(System.err);}
        }
        System.exit(exitCode);
    }

    private static void clickTab(String title)throws Exception {
        Point target=edt(()->{
            JTabbedPane tabs=tabs();
            for(int i=0;i<tabs.getTabCount();i++)if(tabs.getTitleAt(i).contains(title)){
                Rectangle tab=tabs.getBoundsAt(i);Point origin=tabs.getLocationOnScreen();
                return new Point(origin.x+tab.x+tab.width/2,origin.y+tab.y+tab.height/2);
            }
            throw new AssertionError("Missing tab: "+title);
        });
        click(target);
        await("tab "+title,Duration.ofSeconds(5),()->tabs().getTitleAt(tabs().getSelectedIndex()).contains(title));
    }
    private static void clickButton(String text)throws Exception {
        edt(()->{JButton b=button(text);b.scrollRectToVisible(new Rectangle(0,0,b.getWidth(),b.getHeight()));return null;});
        Point target=edt(()->{
            JButton button=button(text);
            require(button.isEnabled(),"Disabled button: "+text);require(button.isShowing(),"Button not visible: "+text);
            Point origin=button.getLocationOnScreen();Point p=new Point(origin.x+button.getWidth()/2,origin.y+button.getHeight()/2);require(frame.getBounds().contains(p),"Button outside window: "+text);return p;
        });
        click(target);
    }
    private static void click(Point target){
        robot.mouseMove(target.x,target.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(100);
    }
    private static void screenshot(Path target)throws Exception {
        robot.delay(120);
        Rectangle bounds=edt(()->frame.getBounds());
        require(ImageIO.write(robot.createScreenCapture(bounds),"png",target.toFile()),"PNG writer unavailable.");
    }
    /** Condition is always evaluated on the EDT; polling and timeout wait occur on the main thread. */
    private static void await(String description,Duration timeout,Callable<Boolean> condition)throws Exception {
        long deadline=System.nanoTime()+timeout.toNanos();
        do{if(edt(condition))return;Thread.sleep(40);}while(System.nanoTime()<deadline);
        throw new AssertionError("Timed out: "+description);
    }
    private static <T> T edt(Callable<T> action)throws Exception {
        if(SwingUtilities.isEventDispatchThread())return action.call();
        FutureTask<T> task=new FutureTask<>(action);SwingUtilities.invokeLater(task);return task.get(5,TimeUnit.SECONDS);
    }
    private static JFileChooser visibleChooser(){
        for(Window window:Window.getWindows())if(window.isShowing())
            for(JFileChooser chooser:descendants(window,JFileChooser.class))if(chooser.isShowing())return chooser;
        return null;
    }
    private static JTabbedPane tabs(){return descendants(panel,JTabbedPane.class).getFirst();}
    private static JButton button(String text){return descendants(panel,JButton.class).stream().filter(button->text.equals(button.getText())).findFirst().orElseThrow(()->new AssertionError("Missing button: "+text));}
    private static <T extends Component> List<T> descendants(Component root,Class<T> type){
        List<T> out=new ArrayList<>();if(type.isInstance(root))out.add(type.cast(root));
        if(root instanceof Container container)for(Component child:container.getComponents())out.addAll(descendants(child,type));return out;
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
