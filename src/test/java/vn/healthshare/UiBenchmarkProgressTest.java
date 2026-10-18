package vn.healthshare;

import org.junit.Test;
import vn.healthshare.ui.SimulatorPanel;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/** Exercise the complete default benchmark through Swing using real, small test pairings. */
public final class UiBenchmarkProgressTest {
    @Test(timeout=100_000) public void tenRunsPerPointFinishOnceWithGlobalProgressAndIncrementalResults()throws Exception{
        AtomicReference<SimulatorPanel> panelRef=new AtomicReference<>();
        AtomicReference<Throwable> callbackFailure=new AtomicReference<>();
        AtomicBoolean sawFirstPointBeforeCompletion=new AtomicBoolean(),sawSecondPoint=new AtomicBoolean();
        AtomicInteger finishTransitions=new AtomicInteger();
        CountDownLatch finished=new CountDownLatch(1);
        List<Integer> progressValues=new ArrayList<>();

        SwingUtilities.invokeAndWait(()->{
            SimulatorPanel panel=new SimulatorPanel(64,false,false);panelRef.set(panel);
            JTabbedPane tabs=descendants(panel,JTabbedPane.class).getFirst();tabs.setSelectedIndex(5);
            panel.setSize(1380,940);layout(panel);
            Component page=tabs.getComponentAt(5);
            JButton run=button(page,"Chạy benchmark");
            JProgressBar progress=named(page,JProgressBar.class,"benchmarkProgress");
            JLabel detail=named(page,JLabel.class,"benchmarkDetail");
            JTextArea plan=named(page,JTextArea.class,"benchmarkPlan");
            JTable table=descendants(page,JTable.class).getFirst();
            assertEquals("Default is ten measured runs at each point",10,descendants(page,JSpinner.class).getFirst().getValue());
            assertTrue("Plan explains all fifty measured runs",plan.getText().contains("50"));
            assertTrue("Plan includes the five warm-ups",plan.getText().contains("5 warm-up"));

            progress.addChangeListener(e->{
                try{
                    int value=progress.getValue();
                    if(!progressValues.isEmpty())assertTrue("Global progress must not restart at the next point",value>=progressValues.getLast());
                    progressValues.add(value);
                }catch(Throwable failure){callbackFailure.compareAndSet(null,failure);}
            });
            table.getModel().addTableModelListener(e->{
                if(table.getRowCount()==1&&progress.getValue()<=11){
                    try{
                        assertTrue("The first result must appear while the benchmark is still running",panel.isTaskRunning());
                        sawFirstPointBeforeCompletion.set(true);
                    }catch(Throwable failure){callbackFailure.compareAndSet(null,failure);}
                }
            });
            Pattern pointTwo=Pattern.compile("(?iu).*điểm\\s+2\\s*/\\s*5.*",Pattern.DOTALL);
            detail.addPropertyChangeListener("text",e->{
                if(pointTwo.matcher(detail.getText()).matches()&&progress.getValue()<55&&sawSecondPoint.compareAndSet(false,true)){
                    try{render(panel,Path.of("output/screenshots/10-benchmark-progress.png"));}
                    catch(Exception failure){callbackFailure.compareAndSet(null,failure);}
                }
            });
            run.addPropertyChangeListener("enabled",e->{
                if(Boolean.TRUE.equals(e.getNewValue())){finishTransitions.incrementAndGet();finished.countDown();}
            });

            run.doClick();
            assertTrue("Benchmark must run in the background",panel.isTaskRunning());
            assertFalse("A second benchmark cannot start concurrently",run.isEnabled());
            assertFalse("The global benchmark counter must be determinate",progress.isIndeterminate());
            assertEquals("Fifty measurements plus five warm-ups",55,progress.getMaximum());
        });

        assertTrue("The finite 64-bit test benchmark did not finish within ninety seconds",finished.await(90,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(()->{
            SimulatorPanel panel=panelRef.get();
            Component page=descendants(panel,JTabbedPane.class).getFirst().getComponentAt(5);
            JProgressBar progress=named(page,JProgressBar.class,"benchmarkProgress");
            JLabel detail=named(page,JLabel.class,"benchmarkDetail");
            assertNull("Swing progress callback failed",callbackFailure.get());
            assertTrue("Publish the first completed point before the other points finish",sawFirstPointBeforeCompletion.get());
            assertTrue("Explain that point two follows point one",sawSecondPoint.get());
            assertFalse(panel.isTaskRunning());
            assertFalse("Benchmark needs no sample healthcare session",panel.hasCryptoSession());
            assertTrue(button(page,"Chạy benchmark").isEnabled());
            assertEquals("Only one completion transition is allowed",1,finishTransitions.get());
            assertEquals("Exactly five completed measurement points",5,descendants(page,JTable.class).getFirst().getRowCount());
            assertEquals(55,progress.getMaximum());assertEquals(55,progress.getValue());
            assertTrue("Final label counts all measured runs",progress.getString().contains("50/50"));
            assertTrue("The visible detail explicitly finishes",detail.getText().contains("Hoàn tất"));
            assertTrue("Progress must advance during the run",progressValues.stream().anyMatch(value->value>0&&value<55));
            try{render(panel,Path.of("output/screenshots/11-benchmark-completed.png"));}
            catch(Exception failure){throw new AssertionError("Could not render completed benchmark",failure);}
        });
    }

    private static JButton button(Component root,String text){return descendants(root,JButton.class).stream().filter(b->text.equals(b.getText())).findFirst().orElseThrow();}
    private static <T extends Component>T named(Component root,Class<T> type,String name){return descendants(root,type).stream().filter(c->name.equals(c.getName())).findFirst().orElseThrow(()->new AssertionError("Missing component "+name));}
    private static <T extends Component>List<T> descendants(Component root,Class<T> type){List<T> out=new ArrayList<>();if(type.isInstance(root))out.add(type.cast(root));if(root instanceof Container container)for(Component child:container.getComponents())out.addAll(descendants(child,type));return out;}
    private static void layout(Component root){if(root instanceof Container container){container.doLayout();for(Component child:container.getComponents())layout(child);}}
    private static void render(JPanel panel,Path path)throws Exception{
        Files.createDirectories(path.getParent());layout(panel);
        BufferedImage image=new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();
        try{graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);panel.printAll(graphics);}
        finally{graphics.dispose();}
        ImageIO.write(image,"png",path.toFile());
    }
}
