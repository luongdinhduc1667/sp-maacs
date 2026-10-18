package vn.healthshare;

import org.junit.Test;
import org.junit.BeforeClass;
import vn.healthshare.crypto.SpMaacs;
import vn.healthshare.ui.SimulatorPanel;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Production startup and task lifecycle regressions: controls must not be globally locked. */
public final class UiLifecycleTest {
    private static Path csvFixture;
    @BeforeClass public static void createSyntheticUiImportFixture()throws Exception{
        // Parser/UI fixture only; it is not experimental evidence and is never packaged.
        csvFixture=Path.of("build/test-fixtures/ui-import.csv");java.nio.file.Files.createDirectories(csvFixture.getParent());
        StringBuilder text=new StringBuilder("backend,prime_bits,authorities,attributes_per_authority,policy_attributes,keygen_attributes,decryption_attributes,clauses,runs,keygen_total_mean_ms,ca_keygen_mean_ms,aa_keygen_mean_ms,abe_encrypt_mean_ms,abe_decrypt_mean_ms,encrypt_population_sd_ms,decrypt_population_sd_ms,encrypt_pairings,decrypt_pairings,abe_element_bytes\n");
        int[] n={5,10,15,20,25},m={3,5,7,10,12};for(int i=0;i<5;i++)text.append(SpMaacs.BACKEND+",517,5,5,"+n[i]+","+m[i]+","+m[i]+",2,10,2,1,1,1,1,0,0,0,"+(2*m[i]+1)+",100\n");
        java.nio.file.Files.writeString(csvFixture,text);
    }
    @Test public void production517StartupIsImmediatelyUsable()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            SimulatorPanel panel=new SimulatorPanel();assertFalse(panel.isTaskRunning());assertFalse(panel.hasCryptoSession());
            for(String name:List.of("Nạp dữ liệu mẫu","Phiên trống","Nạp CSV đã đo","Xem cấu trúc DNF","Chạy benchmark"))assertTrue(name,button(panel,name).isEnabled());
            assertFalse("Session-dependent actions explain their prerequisite",button(panel,"Đăng ký").isEnabled());
            button(panel,"Xem cấu trúc DNF").doClick();
            assertTrue(descendants(panel,JLabel.class).stream().anyMatch(l->l.getText()!=null&&l.getText().contains("3 nhánh")));
            panel.loadBenchmarkCsv(csvFixture);
            JTabbedPane tabs=descendants(panel,JTabbedPane.class).getFirst();tabs.setSelectedIndex(5);
            assertEquals(5,descendants(tabs.getComponentAt(5),JTable.class).getFirst().getRowCount());
        });
    }
    @Test public void cancellingReal517InitializationKeepsCsvAndNavigationUsable()throws Exception{
        AtomicReference<SimulatorPanel> ref=new AtomicReference<>();CountDownLatch finished=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(()->{
            SimulatorPanel panel=new SimulatorPanel();ref.set(panel);JButton start=button(panel,"Nạp dữ liệu mẫu");
            start.addPropertyChangeListener("enabled",e->{if(Boolean.TRUE.equals(e.getNewValue()))finished.countDown();});start.doClick();
            assertTrue(panel.isTaskRunning());assertFalse(start.isEnabled());assertTrue(button(panel,"Nạp CSV đã đo").isEnabled());
            assertTrue(button(panel,"Xem cấu trúc DNF").isEnabled());
            panel.loadBenchmarkCsv(csvFixture);
            button(panel,"Hủy tác vụ").doClick();assertFalse("Do not enable a second crypto job before the first thread exits",start.isEnabled());
        });
        assertTrue("JPBC cancellation did not finish",finished.await(90,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(()->{
            SimulatorPanel panel=ref.get();assertFalse(panel.isTaskRunning());assertFalse(panel.hasCryptoSession());assertTrue(button(panel,"Nạp dữ liệu mẫu").isEnabled());
            assertFalse(button(panel,"Hủy tác vụ").isEnabled());assertTrue(button(panel,"Nạp CSV đã đo").isEnabled());
            var tabs=descendants(panel,JTabbedPane.class).getFirst();assertEquals(5,descendants(tabs.getComponentAt(5),JTable.class).getFirst().getRowCount());
        });
    }
    @Test public void failedBackgroundActionRestoresSessionControls()throws Exception{
        AtomicReference<SimulatorPanel> ref=new AtomicReference<>();CountDownLatch ready=new CountDownLatch(1),failed=new CountDownLatch(1);
        SwingUtilities.invokeAndWait(()->{
            SimulatorPanel panel=new SimulatorPanel(64,false,false);ref.set(panel);JButton start=button(panel,"Nạp dữ liệu mẫu");
            start.addPropertyChangeListener("enabled",e->{if(Boolean.TRUE.equals(e.getNewValue()))ready.countDown();});start.doClick();
        });
        assertTrue(ready.await(60,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(()->{
            SimulatorPanel panel=ref.get();assertTrue(panel.hasCryptoSession());JButton register=button(panel,"Đăng ký");
            register.addPropertyChangeListener("enabled",e->{if(Boolean.TRUE.equals(e.getNewValue()))failed.countDown();});register.doClick();
        });
        assertTrue(failed.await(30,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(()->{assertFalse(ref.get().isTaskRunning());assertTrue(ref.get().hasCryptoSession());assertTrue(button(ref.get(),"Đăng ký").isEnabled());});
    }
    private static JButton button(Component root,String name){return descendants(root,JButton.class).stream().filter(b->name.equals(b.getText())).findFirst().orElseThrow();}
    private static <T extends Component> List<T> descendants(Component root,Class<T> type){List<T> out=new ArrayList<>();if(type.isInstance(root))out.add(type.cast(root));if(root instanceof Container c)for(Component child:c.getComponents())out.addAll(descendants(child,type));return out;}
}
