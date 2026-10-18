package vn.healthshare.ui;

import vn.healthshare.crypto.SpMaacs;
import vn.healthshare.crypto.SpMaacs.*;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;
import vn.healthshare.sim.Benchmark;
import vn.healthshare.sim.Simulation;
import vn.healthshare.sim.ExperimentCharts;
import vn.healthshare.math.CompositeGroup;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;

/** Swing desktop UI; all role administration is an explicit local simulation operator action. */
public final class SimulatorPanel extends JPanel {
    static {
        try { UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel"); }
        catch(Exception ignored) { /* The platform look and feel is a usable fallback. */ }
        UIManager.put("control",new Color(242,246,248));
        UIManager.put("nimbusBase",new Color(45,91,105));
        UIManager.put("nimbusSelectionBackground",new Color(0,119,120));
        configureFonts();
    }
    private static final Color INK=new Color(24,47,65), TEAL=new Color(0,119,120), BG=new Color(242,246,248), MUTED=new Color(94,112,124);
    private Simulation sim;
    private final int primeBits;
    private final boolean synchronousTests;
    private boolean busy;
    private volatile boolean cancelRequested;
    private volatile Thread jobThread;
    private boolean discardSessionOnCancel;
    private long jobStarted;
    private String jobLabel="";
    private final List<JButton> sessionButtons=new ArrayList<>(), setupButtons=new ArrayList<>();
    private final JLabel activity=new JLabel("Chưa khởi tạo phiên. Nạp dữ liệu mẫu hoặc mở CSV để bắt đầu.");
    private final JProgressBar jobProgress=new JProgressBar();
    private final JButton cancelButton=button("Hủy tác vụ",this::cancelActiveTask);
    private final javax.swing.Timer jobTimer=new javax.swing.Timer(500,e->updateActivity());
    private final JTabbedPane tabs=new JTabbedPane();
    private final JTextArea log=area("",10), cloudText=area("",18), decrypted=area("Chọn người dùng và hồ sơ, sau đó nhấn Giải mã.",15);
    private final JTextArea policy=area(Simulation.DEFAULT_POLICY,5), ehr=area(Simulation.DEFAULT_EHR,10);
    private final JTextField recordId=new JTextField("EHR-002"), gidInput=new JTextField(), aaInput=new JTextField();
    private final JTextArea catalogInput=area("Profession=Doctor\nProfession=Nurse",4);
    private final JComboBox<String> userPicker=new JComboBox<>(), recordPicker=new JComboBox<>(), grantUser=new JComboBox<>(), cloudPicker=new JComboBox<>(), colluderOne=new JComboBox<>(), colluderTwo=new JComboBox<>();
    private final DefaultListModel<Attribute> catalogModel=new DefaultListModel<>();
    private final JList<Attribute> catalogList=new JList<>(catalogModel);
    private final DefaultTableModel userModel=tableModel("GID","Cơ quan cấp khóa","Thuộc tính đã được AA cấp");
    private final DefaultTableModel demoModel=tableModel("Người dùng","Vai trò / tình huống","Kỳ vọng","Kết quả");
    private final DefaultTableModel benchModel=tableModel("Thuộc tính","Tập thỏa","Cấp khóa (ms)","Mã hóa (ms)","Giải mã (ms)","SD mã hóa","SD giải mã","Pairing giải mã");
    private final JLabel counts=new JLabel(), status=new JLabel("Sẵn sàng."), accessStatus=new JLabel("Chưa có yêu cầu giải mã"), compiled=new JLabel(" ");
    private final BenchmarkPlot plot=new BenchmarkPlot();
    private List<Benchmark.Result> benchmarkRows=List.of();
    private Benchmark.Report benchmarkReport;
    private boolean benchmarkActive;
    private final List<Benchmark.Result> liveBenchmarkRows=new ArrayList<>();
    private final JProgressBar benchmarkProgress=new JProgressBar();
    private final JLabel benchmarkDetail=label("Chưa chạy. Kết quả sẽ xuất hiện sau mỗi điểm đo hoàn tất.",13,false);
    private final JTextArea benchmarkPlan=area("",2);
    private final JButton benchmarkButton=button("Chạy benchmark",null);
    private final JSpinner runs=new JSpinner(new SpinnerNumberModel(10,1,100,1));

    public SimulatorPanel() {
        this(CompositeGroup.PAPER_PRIME_BITS,false,false);
    }
    public SimulatorPanel(int bits,boolean synchronousTests) {
        this(bits,synchronousTests,true);
    }
    public SimulatorPanel(int bits,boolean synchronousTests,boolean autoStart) {
        this.primeBits=bits;this.synchronousTests=synchronousTests;
        configureFonts(); setLayout(new BorderLayout());setBackground(BG);
        log.setEditable(false); cloudText.setEditable(false); decrypted.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); cloudText.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        JPanel north=new JPanel(new BorderLayout());north.add(header(),BorderLayout.NORTH);north.add(activityBar(),BorderLayout.SOUTH);add(north,BorderLayout.NORTH);
        tabs.setBorder(new EmptyBorder(12,20,8,20));tabs.setBackground(BG);
        tabs.addTab("  01  Tổng quan  ",overview());
        tabs.addTab("  02  Cơ quan & người dùng  ",page(identities(),720));
        tabs.addTab("  03  Mã hóa hồ sơ  ",page(encryptPanel(),630));
        tabs.addTab("  04  Truy cập & thông đồng  ",page(accessPanel(),630));
        tabs.addTab("  05  Cloud công khai  ",page(cloudPanel(),580));
        tabs.addTab("  06  Hiệu năng  ",page(benchmarkPanel(),650));
        tabs.addTab("  Nhật ký thuật toán  ",scroll(log));
        add(tabs,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.setBackground(BG);footer.setBorder(new EmptyBorder(6,24,12,24));
        status.setForeground(MUTED);counts.setForeground(MUTED);footer.add(status,BorderLayout.CENTER);footer.add(counts,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        cloudPicker.addActionListener(e->showCloud());
        userPicker.addActionListener(e->clearDecryption()); recordPicker.addActionListener(e->clearDecryption());
        counts.setText("Chưa có phiên mật mã");setBusy(false);
        if(autoStart)startSession(true);
    }
    private static void configureFonts() {
        Font f=new Font("Segoe UI",Font.PLAIN,14);
        for(String key:new String[]{"Label.font","Button.font","TextField.font","TextArea.font","ComboBox.font","List.font","Table.font","TableHeader.font","TabbedPane.font","Spinner.font","OptionPane.messageFont","OptionPane.buttonFont"}) UIManager.put(key,f);
    }
    private JPanel header() {
        JPanel p=new JPanel(new BorderLayout(20,0));p.setBackground(INK);p.setBorder(new EmptyBorder(22,26,20,26));
        JLabel title=new JLabel("SP-MAACS   /   Healthcare Data Sharing");title.setForeground(Color.WHITE);title.setFont(title.getFont().deriveFont(Font.BOLD,23));
        JLabel subtitle=new JLabel("JPBC Type A1 · 3 × "+primeBits+" bit · BLS01/SHA-256 · Chính sách ẩn giá trị");subtitle.setForeground(new Color(179,208,219));
        JPanel labels=new JPanel(new GridLayout(2,1,0,8));labels.setOpaque(false);labels.add(title);labels.add(subtitle);p.add(labels,BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(2,1,0,5));actions.setOpaque(false);
        actions.add(setupButton("Nạp dữ liệu mẫu",()->reset(true)));actions.add(setupButton("Phiên trống",()->reset(false)));p.add(actions,BorderLayout.EAST);return p;
    }
    private JPanel activityBar(){
        JPanel bar=new JPanel(new BorderLayout(12,0));bar.setBackground(new Color(228,239,241));bar.setBorder(new EmptyBorder(8,24,8,24));
        jobProgress.setPreferredSize(new Dimension(130,18));jobProgress.setVisible(false);cancelButton.setEnabled(false);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,10,0));actions.setOpaque(false);actions.add(jobProgress);actions.add(cancelButton);
        activity.setForeground(INK);bar.add(activity,BorderLayout.CENTER);bar.add(actions,BorderLayout.EAST);return bar;
    }
    private JPanel overview() {
        JPanel out=vertical();out.setBorder(new EmptyBorder(18,0,0,0));
        out.add(title("Chia sẻ hồ sơ. Kiểm soát bằng thuộc tính.","Mô hình SP-MAACS theo Sensors 2023, 23, 2617 · Mục 5, công thức (1)-(14)."));
        JPanel cards=new JPanel(new GridLayout(1,3,14,0));cards.setOpaque(false);
        cards.add(infoCard("MIỀN MỞ","Hospital · Medical · Research","Cơ quan cấp khóa theo thuộc tính đã phê duyệt."));
        cards.add(infoCard("MIỀN ĐÓNG","Data owner · Bạn bè","Chủ hồ sơ đóng vai trò AA cho người quen."));
        cards.add(infoCard("CHÍNH SÁCH RIÊNG TƯ","Công khai tên · Ẩn giá trị","Mỗi nhánh DNF là một đường truy cập độc lập."));
        fixedHeight(cards,104);
        out.add(cards);out.add(Box.createVerticalStrut(14));
        JPanel flow=card();flow.setLayout(new BorderLayout());flow.add(new FlowDiagram(),BorderLayout.CENTER);fixedHeight(flow,86);out.add(flow);
        out.add(Box.createVerticalStrut(14));
        JPanel results=card();results.setLayout(new BorderLayout(0,10));
        JPanel line=new JPanel(new BorderLayout());line.setOpaque(false);line.add(label("Tình huống mẫu",18,true),BorderLayout.WEST);line.add(sessionButton("Chạy 8 tình huống mẫu",this::runDemo),BorderLayout.EAST);results.add(line,BorderLayout.NORTH);
        JTable t=table(demoModel);results.add(scroll(t),BorderLayout.CENTER);fixedHeight(results,390);
        out.add(results);out.add(Box.createVerticalStrut(14));
        JTextArea note=area("Thực thi pairing thật bằng JPBC 2.0.0 Type A1, ba số nguyên tố "+primeBits+" bit; chữ ký BLS01. Benchmark đo riêng ABE và lưu từng lượt, không gộp AES-GCM vào thời gian Encrypt/Decrypt.\n\nCách thử: chạy dữ liệu mẫu → xem khóa người dùng → soạn chính sách và mã hóa → chọn GID để giải mã → thử ghép colluder-a + colluder-b. Phiên làm việc ở trong bộ nhớ; JSON cloud được xuất để kiểm tra.",4);
        note.setEditable(false);note.setBackground(new Color(228,239,241));note.setBorder(new EmptyBorder(14,16,14,16));note.setForeground(INK);fixedHeight(note,130);out.add(note);
        JPanel wrapper=new JPanel(new BorderLayout());wrapper.setOpaque(false);JScrollPane scroller=scroll(out);scroller.setBorder(null);scroller.getViewport().setBackground(BG);scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroller.getVerticalScrollBar().setUnitIncrement(22);wrapper.add(scroller);return wrapper;
    }
    private JPanel identities() {
        JPanel out=new JPanel(new BorderLayout(14,14));out.setOpaque(false);out.setBorder(new EmptyBorder(18,0,0,0));
        JPanel left=vertical();left.setPreferredSize(new Dimension(345,600));
        JPanel user=card();user.setLayout(new BorderLayout(8,8));user.add(label("Đăng ký tại CA",18,true),BorderLayout.NORTH);
        JPanel row=new JPanel(new BorderLayout(6,0));row.setOpaque(false);gidInput.setToolTipText("GID duy nhất, ví dụ doctor-cuong");row.add(gidInput,BorderLayout.CENTER);row.add(sessionButton("Đăng ký",()->{String gid=gidInput.getText().strip();work("CAKeyGen / BLS",()->{sim.register(gid);return true;},x->{refresh();gidInput.setText("");});}),BorderLayout.EAST);user.add(row,BorderLayout.CENTER);user.setMaximumSize(new Dimension(Integer.MAX_VALUE,110));left.add(user);left.add(Box.createVerticalStrut(12));
        JPanel aa=card();aa.setLayout(new BorderLayout(8,8));aa.add(label("Thêm cơ quan thuộc tính",18,true),BorderLayout.NORTH);
        JPanel fields=vertical();fields.add(new JLabel("Mã AA (ví dụ CLINIC)"));fields.add(aaInput);fields.add(Box.createVerticalStrut(8));fields.add(new JLabel("Danh mục: mỗi dòng Name=Value"));fields.add(scroll(catalogInput));aa.add(fields,BorderLayout.CENTER);
        aa.add(sessionButton("AASetup · Tạo cơ quan",()->safe(this::createAuthority)),BorderLayout.SOUTH);aa.setMaximumSize(new Dimension(Integer.MAX_VALUE,280));left.add(aa);left.add(Box.createVerticalStrut(12));
        JPanel grant=card();grant.setLayout(new BorderLayout(8,8));grant.add(label("AA phê duyệt & cấp khóa",18,true),BorderLayout.NORTH);
        JPanel pick=new JPanel(new BorderLayout(0,8));pick.setOpaque(false);pick.add(grantUser,BorderLayout.NORTH);catalogList.setVisibleRowCount(7);pick.add(scroll(catalogList),BorderLayout.CENTER);grant.add(pick,BorderLayout.CENTER);
        grant.add(sessionButton("Cấp các thuộc tính đã chọn",()->safe(()->{String gid=selected(grantUser);List<Attribute> attrs=catalogList.getSelectedValuesList();work("AAKeyGen / xác minh BLS",()->{sim.issue(gid,attrs);return true;},x->refresh());})),BorderLayout.SOUTH);left.add(grant);
        out.add(left,BorderLayout.WEST);
        JPanel right=card();right.setLayout(new BorderLayout(0,12));right.add(title("Người dùng & khóa đã cấp","Thao tác tại đây đại diện người quản trị CA/AA trong mô phỏng."),BorderLayout.NORTH);
        JTable t=table(userModel);t.getColumnModel().getColumn(0).setPreferredWidth(140);t.getColumnModel().getColumn(2).setPreferredWidth(500);right.add(scroll(t),BorderLayout.CENTER);
        JTextArea note=area("GID và K1 được CA ký. AA kiểm tra chữ ký trước khi cấp K2, K3. Khóa cấp thêm giữ cùng định danh; người đọc hồ sơ dùng các khóa đã được cấp.\n\nCtrl/Shift để chọn nhiều thuộc tính. Giá trị có dấu cách phải đặt trong dấu nháy khi khai báo danh mục.",4);note.setEditable(false);note.setBackground(BG);right.add(note,BorderLayout.SOUTH);
        out.add(right,BorderLayout.CENTER);return out;
    }
    private void createAuthority() {
        String id=aaInput.getText().strip();List<Attribute> attrs=new ArrayList<>();
        for(String line:catalogInput.getText().lines().filter(s->!s.isBlank()).toList()) {
            Policy p=Policy.parse(id+"."+line.strip());
            if(p.clauses().size()!=1||p.clauses().getFirst().size()!=1) throw new IllegalArgumentException("Mỗi dòng chỉ khai báo một Name=Value.");
            attrs.add(p.clauses().getFirst().getFirst());
        }
        work("AASetup",()->{sim.addAuthority(id,attrs);return true;},x->refresh());
    }
    private JPanel encryptPanel() {
        JPanel out=new JPanel(new BorderLayout(18,14));out.setOpaque(false);out.setBorder(new EmptyBorder(18,0,0,0));
        JPanel editor=card();editor.setLayout(new BorderLayout(0,12));editor.add(title("Chủ hồ sơ · Soạn & mã hóa","Khóa K được bọc bằng SP-MAACS; nội dung được mã hóa bằng AES-GCM."),BorderLayout.NORTH);
        JPanel content=vertical();JPanel id=new JPanel(new BorderLayout(12,0));id.setOpaque(false);id.add(new JLabel("Mã hồ sơ"),BorderLayout.WEST);id.add(recordId,BorderLayout.CENTER);id.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));content.add(id);content.add(Box.createVerticalStrut(12));
        content.add(new JLabel("Nội dung EHR giả lập"));content.add(scroll(ehr));content.add(Box.createVerticalStrut(12));content.add(new JLabel("Chính sách (AND / OR / ngoặc / AA.Name=Value)"));content.add(scroll(policy));content.add(Box.createVerticalStrut(10));
        compiled.setForeground(TEAL);content.add(compiled);editor.add(content,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.setOpaque(false);
        actions.add(button("Xem cấu trúc DNF",()->safe(()->{Policy p=Policy.parse(policy.getText());compiled.setText(p.clauses().size()+" nhánh · "+p.rowCount()+" dòng LSSS · "+(2*p.clauses().size()+2*p.rowCount())+" phần tử ciphertext");appendLog("DNF công khai: "+p.hidden());})));
        actions.add(sessionButton("Mã hóa & đưa lên cloud",()->safe(()->{
            String newRecord=recordId.getText().strip(),text=ehr.getText(),expression=policy.getText();
            work("Encrypt / JPBC Type A1",()->sim.encrypt(newRecord,text,expression),ct->{refresh();select(cloudPicker,ct.id());select(recordPicker,ct.id());
            compiled.setText("Đã mã hóa "+ct.id()+" · "+ct.payloadSize()+" byte AES-GCM · "+ct.clauses().size()+" nhánh DNF");status.setText("Mã hóa thành công. Xem tab Cloud công khai hoặc Truy cập.");});
        })));editor.add(actions,BorderLayout.SOUTH);out.add(editor,BorderLayout.CENTER);
        JPanel help=card();help.setPreferredSize(new Dimension(330,600));help.setLayout(new BorderLayout(0,14));help.add(label("Đường truy cập độc lập",19,true),BorderLayout.NORTH);
        JTextArea explanation=area("1. Chính sách được chuyển sang DNF: OR giữa các nhánh, AND trong mỗi nhánh.\n\n2. Tạo ma trận LSSS cho từng nhánh, chia sẻ s thành các λx.\n\n3. Tính C, C0, C1,x, C2,x theo công thức (5)-(8).\n\n4. Cloud chỉ nhận tên thuộc tính, W, các thành phần đã mã hóa và EHR AES-GCM.\n\nVí dụ:\nOWNER.Relation=Friend\n\nGiá trị có khoảng trắng:\nOWNER.Relation=\"Close Friend\"\n\nTên và giá trị phân biệt hoa/thường. AND/OR không phân biệt hoa/thường.",20);explanation.setEditable(false);explanation.setBackground(Color.WHITE);help.add(explanation,BorderLayout.CENTER);out.add(help,BorderLayout.EAST);return out;
    }
    private JPanel accessPanel() {
        JPanel out=new JPanel(new BorderLayout(16,14));out.setOpaque(false);out.setBorder(new EmptyBorder(18,0,0,0));
        JPanel left=card();left.setLayout(new BorderLayout(0,12));left.add(title("Yêu cầu truy cập hồ sơ","Giải mã dùng khóa đã cấp; không đọc giá trị thuộc tính từ cloud."),BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(0,12));body.setOpaque(false);JPanel controls=new JPanel(new GridLayout(2,2,12,8));controls.setOpaque(false);controls.add(new JLabel("Người dùng / GID"));controls.add(new JLabel("Hồ sơ cloud"));controls.add(userPicker);controls.add(recordPicker);body.add(controls,BorderLayout.NORTH);
        decrypted.setBackground(new Color(247,250,251));body.add(scroll(decrypted),BorderLayout.CENTER);left.add(body,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(10,0));bottom.setOpaque(false);accessStatus.setForeground(MUTED);bottom.add(accessStatus,BorderLayout.CENTER);bottom.add(sessionButton("Giải mã hồ sơ",this::attemptDecrypt),BorderLayout.EAST);left.add(bottom,BorderLayout.SOUTH);out.add(left,BorderLayout.CENTER);
        JPanel experiment=card();experiment.setPreferredSize(new Dimension(350,500));experiment.setLayout(new BorderLayout(0,12));experiment.add(label("Thử ghép khóa",20,true),BorderLayout.NORTH);
        JPanel col=vertical();col.add(new JLabel("Người dùng A (chọn K1 của A)"));col.add(colluderOne);col.add(Box.createVerticalStrut(12));col.add(new JLabel("Người dùng B (góp khóa)"));col.add(colluderTwo);col.add(Box.createVerticalStrut(12));
        JTextArea outcome=area("Mẫu: colluder-a có khóa HOSPITAL; colluder-b có khóa MEDICAL. Mỗi người thiếu một phần của nhánh bác sĩ.",12);outcome.setEditable(false);outcome.setBackground(BG);
        col.add(sessionButton("Ghép & thử cả hai K1",()->safe(()->{String id=selected(recordPicker),one=selected(colluderOne),two=selected(colluderTwo);work("Thử ghép khóa / JPBC",()->sim.collusionExperiment(id,one,two),outcome::setText,this::showError,false);})));col.add(Box.createVerticalStrut(14));col.add(scroll(outcome));experiment.add(col,BorderLayout.CENTER);out.add(experiment,BorderLayout.EAST);return out;
    }
    private void attemptDecrypt() {
        decrypted.setText(""); // Clear a previous successful result before every access request.
        safe(()->{String id=selected(recordPicker),gid=selected(userPicker);long start=System.nanoTime();
            work("Decrypt / JPBC Type A1",()->sim.decrypt(id,gid),result->{double ms=(System.nanoTime()-start)/1_000_000.0;
            decrypted.setText(new String(result.plaintext(),StandardCharsets.UTF_8));accessStatus.setForeground(TEAL);
            accessStatus.setText(String.format(Locale.ROOT,"ĐƯỢC PHÉP · nhánh %d · %d pairing · %.3f ms",result.clauseIndex(),result.operations().pairings(),ms));status.setText("Giải mã và xác thực AES-GCM thành công.");
            },e->{if(e instanceof AccessDenied){decrypted.setText(e.getMessage());accessStatus.setForeground(new Color(164,61,56));accessStatus.setText("TỪ CHỐI TRUY CẬP");appendLog(e.getMessage());}else showError(e);},false);});
    }
    private void clearDecryption() {decrypted.setText("Chọn người dùng và hồ sơ, sau đó nhấn Giải mã.");accessStatus.setText("Chưa có yêu cầu giải mã");accessStatus.setForeground(MUTED);}
    private JPanel cloudPanel() {
        JPanel out=card();out.setLayout(new BorderLayout(0,12));out.setBorder(new CompoundBorder(new EmptyBorder(18,0,0,0),new EmptyBorder(16,18,16,18)));
        JPanel heading=new JPanel(new BorderLayout(12,0));heading.setOpaque(false);heading.add(title("Góc nhìn cloud","Tên thuộc tính và cấu trúc DNF hiện công khai; giá trị không được ghi vào JSON."),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);cloudPicker.setPreferredSize(new Dimension(180,32));actions.add(cloudPicker);actions.add(button("Xuất JSON công khai",()->safe(()->{
            Ciphertext ct=sim.cloud().get(selected(cloudPicker));if(ct==null)throw new IllegalArgumentException("Chưa có hồ sơ cloud.");
            chooseSave("cloud-ciphertext.json",ct.publicJson());
        })));heading.add(actions,BorderLayout.EAST);out.add(heading,BorderLayout.NORTH);out.add(scroll(cloudText),BorderLayout.CENTER);
        JTextArea note=area("JSON chứa phần tử đường cong/GT do JPBC mã hóa, không chứa số mũ bí mật, khóa người dùng, giá trị thuộc tính hay EHR gốc. Tham số có phân tích N nằm trong build/private và không được xuất lên cloud.",2);note.setEditable(false);note.setBackground(BG);out.add(note,BorderLayout.SOUTH);return out;
    }
    private JPanel benchmarkPanel() {
        JPanel out=vertical();out.add(title("Thực nghiệm JPBC Type A1 · 3 × "+primeBits+" bit","5 AA × 5 thuộc tính; chính sách 5/10/15/20/25, tập thỏa 3/5/7/10/12; mặc định 10 lượt/điểm."));
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,10,8));toolbar.setOpaque(false);toolbar.add(new JLabel("Lượt đo / điểm"));toolbar.add(runs);toolbar.add(benchmarkButton);
        toolbar.add(button("Nạp CSV đã đo",()->{JFileChooser picker=new JFileChooser();if(picker.showOpenDialog(this)==JFileChooser.APPROVE_OPTION)safe(()->loadBenchmarkCsv(picker.getSelectedFile().toPath()));}));
        toolbar.add(button("Xuất CSV + PNG",()->safe(()->{
            if(benchmarkActive)throw new IllegalArgumentException("Benchmark đang chạy; hãy chờ đủ 5 điểm đo trước khi xuất bộ kết quả.");
            if(benchmarkRows.size()!=5)throw new IllegalArgumentException("Cần đủ 5 điểm đo. Có thể chạy benchmark hoặc nạp CSV đã đo.");var target=chooseSave("benchmark.csv",Benchmark.csv(benchmarkRows));if(target==null)return;
            try{ExperimentCharts.writePng(benchmarkRows,java.nio.file.Path.of(target+".png"));if(benchmarkReport!=null){Files.writeString(java.nio.file.Path.of(target+".raw.csv"),Benchmark.rawCsv(benchmarkReport),StandardCharsets.UTF_8);Files.writeString(java.nio.file.Path.of(target+".metadata.json"),Benchmark.metadata(benchmarkReport),StandardCharsets.UTF_8);}}catch(IOException ex){throw new java.io.UncheckedIOException(ex);}
        })));benchmarkButton.addActionListener(e->runBenchmark());out.add(toolbar);
        benchmarkPlan.setName("benchmarkPlan");benchmarkPlan.setEditable(false);benchmarkPlan.setFocusable(false);benchmarkPlan.setBackground(new Color(228,239,241));fixedHeight(benchmarkPlan,62);
        ((javax.swing.text.DefaultCaret)benchmarkPlan.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
        benchmarkDetail.setName("benchmarkDetail");fixedHeight(benchmarkDetail,24);benchmarkProgress.setName("benchmarkProgress");benchmarkProgress.setStringPainted(true);fixedHeight(benchmarkProgress,25);
        runs.addChangeListener(e->updateBenchmarkPlan());updateBenchmarkPlan();
        out.add(benchmarkPlan);out.add(Box.createVerticalStrut(8));out.add(benchmarkDetail);out.add(Box.createVerticalStrut(6));out.add(benchmarkProgress);out.add(Box.createVerticalStrut(12));
        plot.setPreferredSize(new Dimension(1000,300));plot.setMinimumSize(new Dimension(0,200));plot.setMaximumSize(new Dimension(Integer.MAX_VALUE,340));out.add(plot);out.add(Box.createVerticalStrut(14));
        JScrollPane table=scroll(table(benchModel));fixedHeight(table,200);out.add(table);out.add(Box.createVerticalStrut(14));
        JTextArea note=area("Pairing và lũy thừa JPBC chạy thật. Encrypt/Decrypt chỉ đo ABE, loại AES/HKDF/JSON. Cấp khóa = CAKeyGen + AAKeyGen, có lưu riêng từng phần. Mỗi điểm warm-up 1 lượt, sau đó đo trung bình và SD; mọi lượt xác nhận khôi phục đúng K.\n\nĐối chứng [26] Ma–Ge–Zhang cần PDF toàn văn để triển khai; chưa có đường số liệu đối chứng. Dùng hai nhánh DNF có tổng n dòng; các kích thước tập thỏa lấy trực tiếp từ trục Figure 3.",4);note.setEditable(false);note.setBackground(new Color(228,239,241));note.setBorder(new EmptyBorder(14,16,14,16));fixedHeight(note,120);out.add(note);return out;
    }
    private void updateBenchmarkPlan(){
        int measured=(Integer)runs.getValue(),total=5*measured;
        benchmarkPlan.setText("5 điểm: 5 → 10 → 15 → 20 → 25 thuộc tính × "+measured+" lượt/điểm = "+total+" lượt đo + 5 warm-up. Bộ đếm từng điểm bắt đầu lại từ 1.\n"+
                (primeBits==CompositeGroup.PAPER_PRIME_BITS?"Tham khảo: bộ 10 lượt/điểm ở 517 bit đã đo trên máy này mất khoảng 26 phút. Thời gian tùy máy.":"Cấu hình "+primeBits+" bit phục vụ kiểm tra nhanh; số liệu được ghi đúng tham số sử dụng."));
        if(!benchmarkActive){benchmarkProgress.setMaximum(total+5);benchmarkProgress.setValue(0);benchmarkProgress.setString("0/"+total+" lượt đo · 0/5 warm-up");}
    }
    private void runBenchmark() {
        if(busy)return;
        int measured=(Integer)runs.getValue();
        updateBenchmarkPlan();benchmarkActive=true;liveBenchmarkRows.clear();benchmarkReport=null;benchmarkRows=List.of();showBenchmarkRows();
        benchmarkDetail.setText("Đang khởi tạo · 0/5 điểm · 0/"+(5*measured)+" lượt đo.");
        work("Benchmark JPBC · 5 điểm × "+measured+" lượt",()->Benchmark.runDetailed(measured,primeBits,this::appendLog,s->{},this::benchmarkProgressChanged,this::benchmarkPointCompleted),report->{
            benchmarkReport=report;benchmarkRows=report.results();showBenchmarkRows();
            benchmarkDetail.setText("Hoàn tất 5/5 điểm · "+report.samples().size()+"/"+(5*measured)+" lượt đo · 5/5 warm-up. Có thể xuất kết quả.");
            status.setText("Benchmark hoàn tất; có thể xuất CSV, từng lượt và metadata.");
        },this::showError,false,true);
    }
    private void benchmarkProgressChanged(Benchmark.Progress progress){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->benchmarkProgressChanged(progress));return;}
        if(!benchmarkActive)return;
        benchmarkProgress.setMaximum(progress.totalSteps());benchmarkProgress.setValue(progress.completedSteps());
        String summary=progress.completedMeasurements()+"/"+progress.totalMeasurements()+" lượt đo · "+progress.completedWarmups()+"/"+progress.totalWarmups()+" warm-up";
        benchmarkProgress.setString(summary);jobProgress.setIndeterminate(false);jobProgress.setMaximum(progress.totalSteps());jobProgress.setValue(progress.completedSteps());
        if(!cancelRequested){benchmarkDetail.setText(progress.description());jobLabel=progress.description();updateActivity();}
    }
    private void benchmarkPointCompleted(Benchmark.Result result){
        if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->benchmarkPointCompleted(result));return;}
        if(!benchmarkActive)return;
        liveBenchmarkRows.add(result);benchmarkRows=List.copyOf(liveBenchmarkRows);showBenchmarkRows();
    }
    public void loadBenchmarkCsv(java.nio.file.Path path){
        try{benchmarkRows=Benchmark.readCsv(path);benchmarkReport=null;showBenchmarkRows();status.setText("Đã nạp kết quả JPBC 3 × "+benchmarkRows.getFirst().primeBits()+" bit từ "+path.getFileName()+"; không chạy lại phép đo.");}
        catch(IOException e){throw new java.io.UncheckedIOException(e);}
    }
    private void showBenchmarkRows(){benchModel.setRowCount(0);for(var r:benchmarkRows)benchModel.addRow(new Object[]{r.attributes(),r.satisfyingAttributes(),fmt(r.keygenMs()),fmt(r.encryptionMs()),fmt(r.decryptionMs()),fmt(r.encryptionSd()),fmt(r.decryptionSd()),r.decryptPairings()});plot.setRows(benchmarkRows);}
    private void runDemo() {
        demoModel.setRowCount(0);
        String[] names={"doctor-an","doctor-binh","nurse-lan","researcher-minh","friend-ha","outsider","colluder-a","colluder-b"};
        String[] roles={"Bác sĩ tim mạch / AIIMS","Bác sĩ thần kinh: sai giá trị","Y tá: sai nghề nghiệp","Nhà nghiên cứu / HeartStudy","Bạn bè: miền đóng","Không có thuộc tính","Chỉ có khóa HOSPITAL","Chỉ có khóa MEDICAL"};
        if(!sim.cloud().containsKey("EHR-001") || Arrays.stream(names).anyMatch(n->!sim.users().containsKey(n))) {showError(new IllegalArgumentException("Nhấn Nạp lại dữ liệu mẫu trước khi chạy tình huống mẫu."));return;}
        work("Chạy 8 tình huống trên JPBC",()->{List<Object[]> rows=new ArrayList<>();for(int i=0;i<names.length;i++) {
            boolean expected=i==0||i==3||i==4, actual;
            try{sim.decrypt("EHR-001",names[i]);actual=true;}catch(AccessDenied e){actual=false;}
            boolean good=expected==actual;
            rows.add(new Object[]{names[i],roles[i],expected?"Được phép":"Từ chối",(good?"PASS · ":"FAIL · ")+(actual?"Được phép":"Từ chối")});
        }return rows;},rows->{rows.forEach(demoModel::addRow);long passed=rows.stream().filter(r->r[3].toString().startsWith("PASS")).count();status.setText("Tình huống mẫu: "+passed+"/8 kết quả đúng kỳ vọng.");},this::showError,false);
    }
    private void reset(boolean sample) {
        if(busy)return;
        if(sim!=null&&!GraphicsEnvironment.isHeadless()&&JOptionPane.showConfirmDialog(this,"Bắt đầu phiên mới sẽ xóa CA/AA, khóa, hồ sơ và kết quả phiên hiện tại trong bộ nhớ.","Bắt đầu phiên mới",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        startSession(sample);
    }
    private void startSession(boolean sample){
        log.setText("");work("Khởi tạo phiên JPBC",()->{Simulation next=new Simulation(primeBits,this::appendLog);if(sample)next.loadSample();return next;},next->{sim=next;demoModel.setRowCount(0);refresh();clearDecryption();compiled.setText(" ");if(sample){select(colluderOne,"colluder-a");select(colluderTwo,"colluder-b");select(userPicker,"doctor-an");}updateAvailability();status.setText(sample?"Sẵn sàng: JPBC Type A1 / BLS, dữ liệu mẫu đã nạp.":"Phiên trống đã sẵn sàng.");},this::showError,false);
    }
    private void refresh() {
        userModel.setRowCount(0);for(var entry:sim.users().entrySet())userModel.addRow(new Object[]{entry.getKey(),String.join(", ",entry.getValue().authorities().keySet()),String.join("; ",entry.getValue().attributes().stream().map(Attribute::toString).toList())});
        for(JComboBox<String> combo:List.of(userPicker,grantUser,colluderOne,colluderTwo)) refill(combo,sim.users().keySet());
        for(JComboBox<String> combo:List.of(recordPicker,cloudPicker))refill(combo,sim.cloud().keySet());
        catalogModel.clear();sim.authorities().values().forEach(aa->aa.publicKey().attributeBases().keySet().forEach(catalogModel::addElement));
        counts.setText(sim.authorities().size()+" AA   /   "+sim.users().size()+" người dùng   /   "+sim.cloud().size()+" hồ sơ");showCloud();
    }
    private void showCloud() {if(sim==null)return;Ciphertext ct=sim.cloud().get(cloudPicker.getSelectedItem());cloudText.setText(ct==null?"Chưa có ciphertext trong phiên.":ct.publicJson());cloudText.setCaretPosition(0);}
    private void appendLog(String message) {if(!SwingUtilities.isEventDispatchThread()){SwingUtilities.invokeLater(()->appendLog(message));return;}log.append(new SimpleDateFormat("HH:mm:ss").format(new Date())+"  "+message+"\n");log.setCaretPosition(log.getDocument().getLength());if(busy&&!cancelRequested&&!benchmarkActive){jobLabel=message;updateActivity();}}
    private void setBusy(boolean value){busy=value;jobProgress.setVisible(value);jobProgress.setIndeterminate(value);if(value){jobStarted=System.nanoTime();jobTimer.start();}else{jobTimer.stop();}updateAvailability();updateActivity();}
    private void updateAvailability(){
        for(JButton b:setupButtons)b.setEnabled(!busy);
        for(JButton b:sessionButtons){b.setEnabled(!busy&&sim!=null);b.setToolTipText(sim==null?"Nhấn Nạp dữ liệu mẫu hoặc Phiên trống trước.":busy?"Chờ tác vụ hiện tại hoặc nhấn Hủy tác vụ.":null);}
        for(JComponent c:List.of(userPicker,recordPicker,grantUser,colluderOne,colluderTwo,catalogList))c.setEnabled(!busy&&sim!=null);
        benchmarkButton.setEnabled(!busy);runs.setEnabled(!busy);cancelButton.setEnabled(busy&&!cancelRequested);
    }
    private void updateActivity(){if(busy){long seconds=(System.nanoTime()-jobStarted)/1_000_000_000L;activity.setText((cancelRequested?"Đang hủy; chờ phép tính hiện tại kết thúc":jobLabel)+" · "+seconds+" giây");activity.setToolTipText(activity.getText());}}
    private void cancelActiveTask(){if(!busy||cancelRequested)return;cancelRequested=true;Thread thread=jobThread;if(thread!=null)thread.interrupt();if(benchmarkActive)benchmarkDetail.setText("Đang hủy benchmark; chờ phép tính hiện tại kết thúc.");updateAvailability();updateActivity();}
    public boolean isTaskRunning(){return busy;}
    public boolean hasCryptoSession(){return sim!=null;}
    private JButton sessionButton(String text,Runnable action){JButton b=button(text,action);sessionButtons.add(b);return b;}
    private JButton setupButton(String text,Runnable action){JButton b=button(text,action);setupButtons.add(b);return b;}
    private <T> void work(String label,Callable<T> computation,Consumer<T> success){work(label,computation,success,this::showError);}
    private <T> void work(String label,Callable<T> computation,Consumer<T> success,Consumer<Exception> failure){work(label,computation,success,failure,true);}
    private <T> void work(String label,Callable<T> computation,Consumer<T> success,Consumer<Exception> failure,boolean discardOnCancel){
        work(label,computation,success,failure,discardOnCancel,false);
    }
    private <T> void work(String label,Callable<T> computation,Consumer<T> success,Consumer<Exception> failure,boolean discardOnCancel,boolean forceAsync){
        if(busy)return;status.setText(label+"…");
        if(synchronousTests&&!forceAsync){try{T result=computation.call();status.setText("Hoàn tất: "+label+".");success.accept(result);}catch(Exception e){failure.accept(e);}return;}
        cancelRequested=false;discardSessionOnCancel=discardOnCancel;jobLabel=label;boolean benchmarkTask=benchmarkActive;
        setBusy(true);new SwingWorker<T,Void>(){
            @Override protected T doInBackground()throws Exception{jobThread=Thread.currentThread();try{if(cancelRequested)throw new CancellationException();return computation.call();}finally{jobThread=null;Thread.interrupted();}}
            @Override protected void done(){
                try{
                    T result=get();if(cancelRequested)throw new CancellationException();status.setText("Hoàn tất: "+label+".");success.accept(result);activity.setText("Hoàn tất: "+label+".");
                }catch(Exception e){
                    Throwable cause=e instanceof java.util.concurrent.ExecutionException?e.getCause():e;
                    if(cancelRequested||cause instanceof CancellationException){
                        if(benchmarkTask)benchmarkDetail.setText("Đã hủy · hoàn tất "+liveBenchmarkRows.size()+"/5 điểm. Kết quả các điểm đã đo vẫn hiển thị.");
                        if(discardSessionOnCancel){sim=null;userModel.setRowCount(0);catalogModel.clear();for(JComboBox<String> c:List.of(userPicker,recordPicker,grantUser,colluderOne,colluderTwo,cloudPicker))c.removeAllItems();counts.setText("Chưa có phiên mật mã");clearDecryption();cloudText.setText("");}
                        String message="Đã hủy tác vụ."+(discardSessionOnCancel?" Phiên đang sửa đã được bỏ; hãy khởi tạo lại.":" Có thể bắt đầu tác vụ mới.");activity.setText(message);status.setText(message);
                    }else{if(benchmarkTask)benchmarkDetail.setText("Benchmark thất bại · hoàn tất "+liveBenchmarkRows.size()+"/5 điểm. Xem nhật ký để biết lỗi.");failure.accept(cause instanceof Exception ex?ex:new IllegalStateException(cause));activity.setText("Tác vụ thất bại; bạn có thể thử lại.");}
                }finally{if(benchmarkTask)benchmarkActive=false;cancelRequested=false;setBusy(false);}
            }
        }.execute();
    }
    private void safe(Runnable action) {try{action.run();}catch(RuntimeException e){showError(e);}}
    private void showError(Exception e) {status.setText("Lỗi: "+e.getMessage());appendLog("LỖI: "+e.getMessage());if(!GraphicsEnvironment.isHeadless())JOptionPane.showMessageDialog(this,e.getMessage(),"Không thể thực hiện",JOptionPane.ERROR_MESSAGE);}
    private java.nio.file.Path chooseSave(String filename,String text) {
        JFileChooser picker=new JFileChooser();picker.setSelectedFile(new java.io.File(filename));
        if(picker.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return null;
        var path=picker.getSelectedFile().toPath();
        if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Thay thế file đã có?", "Xuất file",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;
        try {Files.writeString(path,text,StandardCharsets.UTF_8);status.setText("Đã xuất: "+path.toAbsolutePath());return path;}catch(IOException e){showError(e);return null;}
    }
    private static void refill(JComboBox<String> box,Collection<String> choices) {Object old=box.getSelectedItem();box.removeAllItems();choices.forEach(box::addItem);if(old!=null&&choices.contains(old.toString()))box.setSelectedItem(old);}
    private static void select(JComboBox<String> box,String value){box.setSelectedItem(value);}
    private static String selected(JComboBox<String> box){Object s=box.getSelectedItem();if(s==null)throw new IllegalArgumentException("Chưa có mục để chọn.");return s.toString();}
    private static JPanel vertical(){JPanel p=new JPanel(){@Override protected void addImpl(Component c,Object constraints,int index){if(c instanceof JComponent j){j.setAlignmentX(LEFT_ALIGNMENT);if(j instanceof JComboBox<?>||j instanceof JTextField)j.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));}super.addImpl(c,constraints,index);}};p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setOpaque(false);return p;}
    private static JScrollPane page(JPanel content,int minimumHeight){
        class Page extends JPanel implements Scrollable{
            Page(){super(new BorderLayout());setBackground(BG);add(content);}
            @Override public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();return new Dimension(d.width,Math.max(minimumHeight,d.height));}
            @Override public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
            @Override public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
            @Override public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-48);}
            @Override public boolean getScrollableTracksViewportWidth(){return true;}
            @Override public boolean getScrollableTracksViewportHeight(){return getParent()!=null&&getParent().getHeight()>=getPreferredSize().height;}
        }
        JScrollPane scroller=new JScrollPane(new Page());scroller.setBorder(null);scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);return scroller;
    }
    private static void fixedHeight(JComponent c,int height){c.setMinimumSize(new Dimension(0,height));c.setPreferredSize(new Dimension(1000,height));c.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));}
    private static JPanel card(){JPanel p=new JPanel();p.setBackground(Color.WHITE);p.setBorder(new CompoundBorder(new LineBorder(new Color(219,229,233),1,true),new EmptyBorder(16,18,16,18)));return p;}
    private static JLabel label(String text,int size,boolean bold){JLabel l=new JLabel(text);l.setFont(l.getFont().deriveFont(bold?Font.BOLD:Font.PLAIN,(float)size));l.setForeground(INK);return l;}
    private static JPanel title(String heading,String description){JPanel p=new JPanel(new GridLayout(2,1,0,8));p.setOpaque(false);p.add(label(heading,23,true));JLabel d=label(description,13,false);d.setForeground(MUTED);p.add(d);p.setBorder(new EmptyBorder(0,0,18,0));p.setMaximumSize(new Dimension(Integer.MAX_VALUE,100));return p;}
    private static JPanel infoCard(String kicker,String heading,String description){JPanel p=card();p.setLayout(new GridLayout(3,1,0,8));p.add(label(kicker,12,true));p.add(label(heading,18,true));p.add(label(description,13,false));return p;}
    private static JTextArea area(String text,int rows){JTextArea a=new JTextArea(text,rows,20);a.setLineWrap(true);a.setWrapStyleWord(true);a.setBorder(new EmptyBorder(8,8,8,8));return a;}
    private static JButton button(String text,Runnable action){JButton b=new JButton(text);b.setFocusPainted(false);b.setMargin(new Insets(8,14,8,14));if(action!=null)b.addActionListener(e->action.run());return b;}
    private static JScrollPane scroll(Component c){JScrollPane s=new JScrollPane(c);if(c instanceof JTable t)s.setColumnHeaderView(t.getTableHeader());s.setBorder(new LineBorder(new Color(219,229,233)));return s;}
    private static DefaultTableModel tableModel(String...headers){return new DefaultTableModel(headers,0){@Override public boolean isCellEditable(int r,int c){return false;}};}
    private static JTable table(DefaultTableModel m){JTable t=new JTable(m);t.setRowHeight(34);t.setShowGrid(false);t.setIntercellSpacing(new Dimension(10,0));t.getTableHeader().setReorderingAllowed(false);t.setFillsViewportHeight(true);t.setSelectionBackground(new Color(215,237,238));return t;}
    private static String fmt(double d){return String.format(Locale.ROOT,"%.3f",d);}
    private static class FlowDiagram extends JComponent {
        FlowDiagram(){setPreferredSize(new Dimension(1000,80));}
        @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            String[] names={"01   CA / AA","02   Cấp khóa","03   Mã hóa EHR","04   Cloud","05   Giải mã"};int gap=22,w=(getWidth()-4*gap)/5,h=Math.min(60,getHeight()-10),y=(getHeight()-h)/2;
            for(int i=0;i<5;i++){int x=i*(w+gap);g.setColor(i==4?TEAL:new Color(234,243,245));g.fillRoundRect(x,y,w,h,12,12);g.setColor(i==4?Color.WHITE:INK);g.setFont(new Font("Segoe UI",Font.BOLD,14));FontMetrics fm=g.getFontMetrics();g.drawString(names[i],x+(w-fm.stringWidth(names[i]))/2,y+h/2+5);if(i<4){g.setColor(MUTED);g.drawString("→",x+w+5,y+h/2+5);}}g.dispose();}
    }
    private static final class BenchmarkPlot extends JComponent {
        private List<Benchmark.Result> rows=List.of();
        void setRows(List<Benchmark.Result> value){rows=value;repaint();}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();ExperimentCharts.draw(g,getWidth(),getHeight(),rows);g.dispose();
        }
    }
}
