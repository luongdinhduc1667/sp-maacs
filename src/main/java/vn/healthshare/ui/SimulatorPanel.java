package vn.healthshare.ui;

import vn.healthshare.crypto.SpMaacs;
import vn.healthshare.crypto.SpMaacs.*;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;
import vn.healthshare.sim.Benchmark;
import vn.healthshare.sim.Simulation;

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
    private final JButton benchmarkButton=button("Chạy benchmark",null);
    private final JSpinner runs=new JSpinner(new SpinnerNumberModel(10,1,100,1));

    public SimulatorPanel() {
        configureFonts(); setLayout(new BorderLayout());setBackground(BG);
        log.setEditable(false); cloudText.setEditable(false); decrypted.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12)); cloudText.setFont(new Font(Font.MONOSPACED,Font.PLAIN,12));
        add(header(),BorderLayout.NORTH);
        tabs.setBorder(new EmptyBorder(12,20,8,20));tabs.setBackground(BG);
        tabs.addTab("  01  Tổng quan  ",overview());
        tabs.addTab("  02  Cơ quan & người dùng  ",identities());
        tabs.addTab("  03  Mã hóa hồ sơ  ",encryptPanel());
        tabs.addTab("  04  Truy cập & thông đồng  ",accessPanel());
        tabs.addTab("  05  Cloud công khai  ",cloudPanel());
        tabs.addTab("  06  Hiệu năng  ",benchmarkPanel());
        tabs.addTab("  Nhật ký thuật toán  ",scroll(log));
        add(tabs,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.setBackground(BG);footer.setBorder(new EmptyBorder(6,24,12,24));
        status.setForeground(MUTED);counts.setForeground(MUTED);footer.add(status,BorderLayout.CENTER);footer.add(counts,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        cloudPicker.addActionListener(e->showCloud());
        userPicker.addActionListener(e->clearDecryption()); recordPicker.addActionListener(e->clearDecryption());
        sim=new Simulation(128,this::appendLog);sim.loadSample();refresh();
        select(colluderOne,"colluder-a");select(colluderTwo,"colluder-b");
        select(userPicker,"doctor-an");
    }
    private static void configureFonts() {
        Font f=new Font("Segoe UI",Font.PLAIN,14);
        for(String key:new String[]{"Label.font","Button.font","TextField.font","TextArea.font","ComboBox.font","List.font","Table.font","TableHeader.font","TabbedPane.font","Spinner.font","OptionPane.messageFont","OptionPane.buttonFont"}) UIManager.put(key,f);
    }
    private JPanel header() {
        JPanel p=new JPanel(new BorderLayout(20,0));p.setBackground(INK);p.setBorder(new EmptyBorder(22,26,20,26));
        JLabel title=new JLabel("SP-MAACS   /   Healthcare Data Sharing");title.setForeground(Color.WHITE);title.setFont(title.getFont().deriveFont(Font.BOLD,23));
        JLabel subtitle=new JLabel("Mô phỏng kiểm soát truy cập đa cơ quan · Chính sách ẩn giá trị · Java 21");subtitle.setForeground(new Color(179,208,219));
        JPanel labels=new JPanel(new GridLayout(2,1,0,8));labels.setOpaque(false);labels.add(title);labels.add(subtitle);p.add(labels,BorderLayout.CENTER);
        JPanel actions=new JPanel(new GridLayout(2,1,0,5));actions.setOpaque(false);
        actions.add(button("Nạp lại dữ liệu mẫu",()->reset(true)));actions.add(button("Phiên trống",()->reset(false)));p.add(actions,BorderLayout.EAST);return p;
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
        JPanel line=new JPanel(new BorderLayout());line.setOpaque(false);line.add(label("Tình huống mẫu",18,true),BorderLayout.WEST);line.add(button("Chạy 8 tình huống mẫu",this::runDemo),BorderLayout.EAST);results.add(line,BorderLayout.NORTH);
        JTable t=table(demoModel);results.add(scroll(t),BorderLayout.CENTER);fixedHeight(results,390);
        out.add(results);out.add(Box.createVerticalStrut(14));
        JTextArea note=area("Phạm vi mô phỏng: pairing được tính bằng số mũ trong ba nhóm con; AES-256-GCM và chữ ký Ed25519 chạy thật. Mô hình làm lộ số mũ, không bảo vệ hồ sơ thực và không tái hiện thời gian JPBC.\n\nCách thử: chạy dữ liệu mẫu → xem khóa người dùng → soạn chính sách và mã hóa → chọn GID để giải mã → thử ghép colluder-a + colluder-b. Phiên làm việc ở trong bộ nhớ; JSON cloud được xuất để kiểm tra.",4);
        note.setEditable(false);note.setBackground(new Color(228,239,241));note.setBorder(new EmptyBorder(14,16,14,16));note.setForeground(INK);fixedHeight(note,130);out.add(note);
        JPanel wrapper=new JPanel(new BorderLayout());wrapper.setOpaque(false);JScrollPane scroller=scroll(out);scroller.setBorder(null);scroller.getViewport().setBackground(BG);scroller.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);scroller.getVerticalScrollBar().setUnitIncrement(22);wrapper.add(scroller);return wrapper;
    }
    private JPanel identities() {
        JPanel out=new JPanel(new BorderLayout(14,14));out.setOpaque(false);out.setBorder(new EmptyBorder(18,0,0,0));
        JPanel left=vertical();left.setPreferredSize(new Dimension(345,600));
        JPanel user=card();user.setLayout(new BorderLayout(8,8));user.add(label("Đăng ký tại CA",18,true),BorderLayout.NORTH);
        JPanel row=new JPanel(new BorderLayout(6,0));row.setOpaque(false);gidInput.setToolTipText("GID duy nhất, ví dụ doctor-cuong");row.add(gidInput,BorderLayout.CENTER);row.add(button("Đăng ký",()->safe(()->{sim.register(gidInput.getText().strip());refresh();gidInput.setText("");})),BorderLayout.EAST);user.add(row,BorderLayout.CENTER);user.setMaximumSize(new Dimension(Integer.MAX_VALUE,110));left.add(user);left.add(Box.createVerticalStrut(12));
        JPanel aa=card();aa.setLayout(new BorderLayout(8,8));aa.add(label("Thêm cơ quan thuộc tính",18,true),BorderLayout.NORTH);
        JPanel fields=vertical();fields.add(new JLabel("Mã AA (ví dụ CLINIC)"));fields.add(aaInput);fields.add(Box.createVerticalStrut(8));fields.add(new JLabel("Danh mục: mỗi dòng Name=Value"));fields.add(scroll(catalogInput));aa.add(fields,BorderLayout.CENTER);
        aa.add(button("AASetup · Tạo cơ quan",()->safe(this::createAuthority)),BorderLayout.SOUTH);aa.setMaximumSize(new Dimension(Integer.MAX_VALUE,280));left.add(aa);left.add(Box.createVerticalStrut(12));
        JPanel grant=card();grant.setLayout(new BorderLayout(8,8));grant.add(label("AA phê duyệt & cấp khóa",18,true),BorderLayout.NORTH);
        JPanel pick=new JPanel(new BorderLayout(0,8));pick.setOpaque(false);pick.add(grantUser,BorderLayout.NORTH);catalogList.setVisibleRowCount(7);pick.add(scroll(catalogList),BorderLayout.CENTER);grant.add(pick,BorderLayout.CENTER);
        grant.add(button("Cấp các thuộc tính đã chọn",()->safe(()->{sim.issue(selected(grantUser),catalogList.getSelectedValuesList());refresh();})),BorderLayout.SOUTH);left.add(grant);
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
        sim.addAuthority(id,attrs);refresh();
    }
    private JPanel encryptPanel() {
        JPanel out=new JPanel(new BorderLayout(18,14));out.setOpaque(false);out.setBorder(new EmptyBorder(18,0,0,0));
        JPanel editor=card();editor.setLayout(new BorderLayout(0,12));editor.add(title("Chủ hồ sơ · Soạn & mã hóa","Khóa K được bọc bằng SP-MAACS; nội dung được mã hóa bằng AES-GCM."),BorderLayout.NORTH);
        JPanel content=vertical();JPanel id=new JPanel(new BorderLayout(12,0));id.setOpaque(false);id.add(new JLabel("Mã hồ sơ"),BorderLayout.WEST);id.add(recordId,BorderLayout.CENTER);id.setMaximumSize(new Dimension(Integer.MAX_VALUE,34));content.add(id);content.add(Box.createVerticalStrut(12));
        content.add(new JLabel("Nội dung EHR giả lập"));content.add(scroll(ehr));content.add(Box.createVerticalStrut(12));content.add(new JLabel("Chính sách (AND / OR / ngoặc / AA.Name=Value)"));content.add(scroll(policy));content.add(Box.createVerticalStrut(10));
        compiled.setForeground(TEAL);content.add(compiled);editor.add(content,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT,8,0));actions.setOpaque(false);
        actions.add(button("Xem cấu trúc DNF",()->safe(()->{Policy p=Policy.parse(policy.getText());compiled.setText(p.clauses().size()+" nhánh · "+p.rowCount()+" dòng LSSS · "+(2*p.clauses().size()+2*p.rowCount())+" phần tử ciphertext");appendLog("DNF công khai: "+p.hidden());})));
        actions.add(button("Mã hóa & đưa lên cloud",()->safe(()->{
            Ciphertext ct=sim.encrypt(recordId.getText().strip(),ehr.getText(),policy.getText());refresh();select(cloudPicker,ct.id());select(recordPicker,ct.id());
            compiled.setText("Đã mã hóa "+ct.id()+" · "+ct.payloadSize()+" byte AES-GCM · "+ct.clauses().size()+" nhánh DNF");status.setText("Mã hóa thành công. Xem tab Cloud công khai hoặc Truy cập.");
        })));editor.add(actions,BorderLayout.SOUTH);out.add(editor,BorderLayout.CENTER);
        JPanel help=card();help.setPreferredSize(new Dimension(330,600));help.setLayout(new BorderLayout(0,14));help.add(label("Đường truy cập độc lập",19,true),BorderLayout.NORTH);
        JTextArea explanation=area("1. Chính sách được chuyển sang DNF: OR giữa các nhánh, AND trong mỗi nhánh.\n\n2. Tạo ma trận LSSS cho từng nhánh, chia sẻ s thành các λx.\n\n3. Tính C, C0, C1,x, C2,x theo công thức (5)-(8).\n\n4. Cloud chỉ nhận tên thuộc tính, W, các thành phần đã mã hóa và EHR AES-GCM.\n\nVí dụ:\nOWNER.Relation=Friend\n\nGiá trị có khoảng trắng:\nOWNER.Relation=\"Close Friend\"\n\nTên và giá trị phân biệt hoa/thường. AND/OR không phân biệt hoa/thường.",20);explanation.setEditable(false);explanation.setBackground(Color.WHITE);help.add(explanation,BorderLayout.CENTER);out.add(help,BorderLayout.EAST);return out;
    }
    private JPanel accessPanel() {
        JPanel out=new JPanel(new BorderLayout(16,14));out.setOpaque(false);out.setBorder(new EmptyBorder(18,0,0,0));
        JPanel left=card();left.setLayout(new BorderLayout(0,12));left.add(title("Yêu cầu truy cập hồ sơ","Giải mã dùng khóa đã cấp; không đọc giá trị thuộc tính từ cloud."),BorderLayout.NORTH);
        JPanel body=new JPanel(new BorderLayout(0,12));body.setOpaque(false);JPanel controls=new JPanel(new GridLayout(2,2,12,8));controls.setOpaque(false);controls.add(new JLabel("Người dùng / GID"));controls.add(new JLabel("Hồ sơ cloud"));controls.add(userPicker);controls.add(recordPicker);body.add(controls,BorderLayout.NORTH);
        decrypted.setBackground(new Color(247,250,251));body.add(scroll(decrypted),BorderLayout.CENTER);left.add(body,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(10,0));bottom.setOpaque(false);accessStatus.setForeground(MUTED);bottom.add(accessStatus,BorderLayout.CENTER);bottom.add(button("Giải mã hồ sơ",this::attemptDecrypt),BorderLayout.EAST);left.add(bottom,BorderLayout.SOUTH);out.add(left,BorderLayout.CENTER);
        JPanel experiment=card();experiment.setPreferredSize(new Dimension(350,500));experiment.setLayout(new BorderLayout(0,12));experiment.add(label("Thử ghép khóa",20,true),BorderLayout.NORTH);
        JPanel col=vertical();col.add(new JLabel("Người dùng A (chọn K1 của A)"));col.add(colluderOne);col.add(Box.createVerticalStrut(12));col.add(new JLabel("Người dùng B (góp khóa)"));col.add(colluderTwo);col.add(Box.createVerticalStrut(12));
        JTextArea outcome=area("Mẫu: colluder-a có khóa HOSPITAL; colluder-b có khóa MEDICAL. Mỗi người thiếu một phần của nhánh bác sĩ.",12);outcome.setEditable(false);outcome.setBackground(BG);
        col.add(button("Ghép & thử cả hai K1",()->safe(()->{outcome.setText(sim.collusionExperiment(selected(recordPicker),selected(colluderOne),selected(colluderTwo)));})));col.add(Box.createVerticalStrut(14));col.add(scroll(outcome));experiment.add(col,BorderLayout.CENTER);out.add(experiment,BorderLayout.EAST);return out;
    }
    private void attemptDecrypt() {
        decrypted.setText(""); // Clear a previous successful result before every access request.
        try {
            long start=System.nanoTime();Decryption result=sim.decrypt(selected(recordPicker),selected(userPicker));double ms=(System.nanoTime()-start)/1_000_000.0;
            decrypted.setText(new String(result.plaintext(),StandardCharsets.UTF_8));accessStatus.setForeground(TEAL);
            accessStatus.setText(String.format(Locale.ROOT,"ĐƯỢC PHÉP · nhánh %d · %d pairing · %.3f ms",result.clauseIndex(),result.operations().pairings(),ms));status.setText("Giải mã và xác thực AES-GCM thành công.");
        } catch(AccessDenied e) {decrypted.setText(e.getMessage());accessStatus.setForeground(new Color(164,61,56));accessStatus.setText("TỪ CHỐI TRUY CẬP");appendLog(e.getMessage());}
        catch(RuntimeException e) {showError(e);}
    }
    private void clearDecryption() {decrypted.setText("Chọn người dùng và hồ sơ, sau đó nhấn Giải mã.");accessStatus.setText("Chưa có yêu cầu giải mã");accessStatus.setForeground(MUTED);}
    private JPanel cloudPanel() {
        JPanel out=card();out.setLayout(new BorderLayout(0,12));out.setBorder(new CompoundBorder(new EmptyBorder(18,0,0,0),new EmptyBorder(16,18,16,18)));
        JPanel heading=new JPanel(new BorderLayout(12,0));heading.setOpaque(false);heading.add(title("Góc nhìn cloud","Tên thuộc tính và cấu trúc DNF hiện công khai; giá trị không được ghi vào JSON."),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.setOpaque(false);cloudPicker.setPreferredSize(new Dimension(180,32));actions.add(cloudPicker);actions.add(button("Xuất JSON công khai",()->safe(()->{
            Ciphertext ct=sim.cloud().get(selected(cloudPicker));if(ct==null)throw new IllegalArgumentException("Chưa có hồ sơ cloud.");
            chooseSave("cloud-ciphertext.json",ct.publicJson());
        })));heading.add(actions,BorderLayout.EAST);out.add(heading,BorderLayout.NORTH);out.add(scroll(cloudText),BorderLayout.CENTER);
        JTextArea note=area("JSON dành cho kiểm tra ciphertext trong phiên hiện tại. Ứng dụng không lưu khóa riêng hay nội dung EHR gốc ra file. Mô hình số mũ không đảm bảo bí mật mật mã, dù không xuất giá trị thuộc tính ở dạng văn bản.",2);note.setEditable(false);note.setBackground(BG);out.add(note,BorderLayout.SOUTH);return out;
    }
    private JPanel benchmarkPanel() {
        JPanel out=vertical();out.add(title("Thực nghiệm quy mô 5–25 thuộc tính","5 cơ quan × 5 tên thuộc tính; 2 nhánh DNF, tập thỏa ceil(n/2). Dữ liệu được tạo mới độc lập với phiên hiện tại."));
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT,10,8));toolbar.setOpaque(false);toolbar.add(new JLabel("Lượt đo / điểm"));toolbar.add(runs);toolbar.add(benchmarkButton);toolbar.add(button("Xuất CSV",()->safe(()->{
            if(benchmarkRows.isEmpty())throw new IllegalArgumentException("Hãy chạy benchmark trước.");chooseSave("benchmark.csv",Benchmark.csv(benchmarkRows));
        })));benchmarkButton.addActionListener(e->runBenchmark());out.add(toolbar);
        plot.setPreferredSize(new Dimension(1000,300));plot.setMinimumSize(new Dimension(0,200));plot.setMaximumSize(new Dimension(Integer.MAX_VALUE,340));out.add(plot);out.add(Box.createVerticalStrut(14));
        JScrollPane table=scroll(table(benchModel));fixedHeight(table,200);out.add(table);out.add(Box.createVerticalStrut(14));
        JTextArea note=area("Đơn vị: ms, trung bình và độ lệch chuẩn tổng thể. Mỗi điểm warm-up 3 lần; cấp khóa gồm CAKeyGen + AAKeyGen; mã hóa gồm ABE mô phỏng + AES-GCM; giải mã gồm ABE mô phỏng + xác thực GCM. Không đo thời gian setup.\n\nĐây là số đo của backend số mũ trên máy hiện tại, không phải pairing đường cong Type A1/JPBC. Ba bài liên quan chỉ được đối chiếu về đặc điểm trong docs; phần mềm không tạo số liệu benchmark cho những thuật toán chưa triển khai.",4);note.setEditable(false);note.setBackground(new Color(228,239,241));note.setBorder(new EmptyBorder(14,16,14,16));fixedHeight(note,120);out.add(note);return out;
    }
    private void runBenchmark() {
        benchmarkButton.setEnabled(false);int measured=(Integer)runs.getValue();status.setText("Đang chạy benchmark "+measured+" lượt/điểm…");
        new SwingWorker<List<Benchmark.Result>,String>() {
            @Override protected List<Benchmark.Result> doInBackground() {return Benchmark.run(measured,128,this::publish);}
            @Override protected void process(List<String> messages) {messages.forEach(SimulatorPanel.this::appendLog);status.setText(messages.getLast());}
            @Override protected void done() {
                benchmarkButton.setEnabled(true);
                try {benchmarkRows=get();benchModel.setRowCount(0);for(var r:benchmarkRows)benchModel.addRow(new Object[]{r.attributes(),r.satisfyingAttributes(),fmt(r.keygenMs()),fmt(r.encryptionMs()),fmt(r.decryptionMs()),fmt(r.encryptionSd()),fmt(r.decryptionSd()),r.decryptPairings()});plot.setRows(benchmarkRows);status.setText("Benchmark hoàn tất; có thể xuất CSV.");}
                catch(Exception e){showError(e);}
            }
        }.execute();
    }
    private void runDemo() {
        demoModel.setRowCount(0);
        String[] names={"doctor-an","doctor-binh","nurse-lan","researcher-minh","friend-ha","outsider","colluder-a","colluder-b"};
        String[] roles={"Bác sĩ tim mạch / AIIMS","Bác sĩ thần kinh: sai giá trị","Y tá: sai nghề nghiệp","Nhà nghiên cứu / HeartStudy","Bạn bè: miền đóng","Không có thuộc tính","Chỉ có khóa HOSPITAL","Chỉ có khóa MEDICAL"};
        if(!sim.cloud().containsKey("EHR-001") || Arrays.stream(names).anyMatch(n->!sim.users().containsKey(n))) {showError(new IllegalArgumentException("Nhấn Nạp lại dữ liệu mẫu trước khi chạy tình huống mẫu."));return;}
        int passed=0;
        for(int i=0;i<names.length;i++) {
            boolean expected=i==0||i==3||i==4, actual;
            try{sim.decrypt("EHR-001",names[i]);actual=true;}catch(AccessDenied e){actual=false;}
            boolean good=expected==actual;if(good)passed++;
            demoModel.addRow(new Object[]{names[i],roles[i],expected?"Được phép":"Từ chối",(good?"PASS · ":"FAIL · ")+(actual?"Được phép":"Từ chối")});
        }
        status.setText("Tình huống mẫu: "+passed+"/8 kết quả đúng kỳ vọng.");
    }
    private void reset(boolean sample) {
        if(JOptionPane.showConfirmDialog(this,"Bắt đầu phiên mới sẽ xóa CA/AA, khóa, hồ sơ và kết quả phiên hiện tại trong bộ nhớ.","Bắt đầu phiên mới",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        safe(()->{log.setText("");sim=new Simulation(128,this::appendLog);if(sample)sim.loadSample();demoModel.setRowCount(0);refresh();clearDecryption();compiled.setText(" ");status.setText(sample?"Đã nạp lại dữ liệu mẫu.":"Phiên trống đã sẵn sàng. Tạo cơ quan rồi đăng ký và cấp khóa người dùng.");});
    }
    private void refresh() {
        userModel.setRowCount(0);for(var entry:sim.users().entrySet())userModel.addRow(new Object[]{entry.getKey(),String.join(", ",entry.getValue().authorities().keySet()),String.join("; ",entry.getValue().attributes().stream().map(Attribute::toString).toList())});
        for(JComboBox<String> combo:List.of(userPicker,grantUser,colluderOne,colluderTwo)) refill(combo,sim.users().keySet());
        for(JComboBox<String> combo:List.of(recordPicker,cloudPicker))refill(combo,sim.cloud().keySet());
        catalogModel.clear();sim.authorities().values().forEach(aa->aa.publicKey().attributeBases().keySet().forEach(catalogModel::addElement));
        counts.setText(sim.authorities().size()+" AA   /   "+sim.users().size()+" người dùng   /   "+sim.cloud().size()+" hồ sơ");showCloud();
    }
    private void showCloud() {if(sim==null)return;Ciphertext ct=sim.cloud().get(cloudPicker.getSelectedItem());cloudText.setText(ct==null?"Chưa có ciphertext trong phiên.":ct.publicJson());cloudText.setCaretPosition(0);}
    private void appendLog(String message) {log.append(new SimpleDateFormat("HH:mm:ss").format(new Date())+"  "+message+"\n");log.setCaretPosition(log.getDocument().getLength());}
    private void safe(Runnable action) {try{action.run();}catch(RuntimeException e){showError(e);}}
    private void showError(Exception e) {status.setText("Lỗi: "+e.getMessage());appendLog("LỖI: "+e.getMessage());if(!GraphicsEnvironment.isHeadless())JOptionPane.showMessageDialog(this,e.getMessage(),"Không thể thực hiện",JOptionPane.ERROR_MESSAGE);}
    private void chooseSave(String filename,String text) {
        JFileChooser picker=new JFileChooser();picker.setSelectedFile(new java.io.File(filename));
        if(picker.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        var path=picker.getSelectedFile().toPath();
        if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Thay thế file đã có?", "Xuất file",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try {Files.writeString(path,text,StandardCharsets.UTF_8);status.setText("Đã xuất: "+path.toAbsolutePath());}catch(IOException e){showError(e);}
    }
    private static void refill(JComboBox<String> box,Collection<String> choices) {Object old=box.getSelectedItem();box.removeAllItems();choices.forEach(box::addItem);if(old!=null&&choices.contains(old.toString()))box.setSelectedItem(old);}
    private static void select(JComboBox<String> box,String value){box.setSelectedItem(value);}
    private static String selected(JComboBox<String> box){Object s=box.getSelectedItem();if(s==null)throw new IllegalArgumentException("Chưa có mục để chọn.");return s.toString();}
    private static JPanel vertical(){JPanel p=new JPanel(){@Override protected void addImpl(Component c,Object constraints,int index){if(c instanceof JComponent j){j.setAlignmentX(LEFT_ALIGNMENT);if(j instanceof JComboBox<?>||j instanceof JTextField)j.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));}super.addImpl(c,constraints,index);}};p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setOpaque(false);return p;}
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
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Color.WHITE);g.fillRect(0,0,getWidth(),getHeight());g.setFont(new Font("Segoe UI",Font.PLAIN,13));
            int left=75,top=48,right=getWidth()-28,bottom=getHeight()-45;g.setColor(INK);g.drawString("Thời gian trung bình (ms) · backend mô phỏng số mũ",20,25);
            if(rows.isEmpty()){g.setColor(MUTED);g.drawString("Chạy benchmark để xem số liệu và biểu đồ.",left,top+55);g.dispose();return;}
            double max=rows.stream().mapToDouble(r->Math.max(r.keygenMs(),Math.max(r.encryptionMs(),r.decryptionMs()))).max().orElse(1)*1.15;max=Math.max(.001,max);
            for(int j=0;j<=4;j++){int y=bottom-(bottom-top)*j/4;g.setColor(new Color(228,235,239));g.drawLine(left,y,right,y);g.setColor(MUTED);g.drawString(String.format(Locale.ROOT,"%.2f",max*j/4),8,y+5);}
            Color[] colors={new Color(89,106,185),TEAL,new Color(215,128,52)};String[] legends={"Cấp khóa","Mã hóa","Giải mã"};
            for(int k=0;k<3;k++){g.setColor(colors[k]);g.setStroke(new BasicStroke(2.3f));int px=0,py=0;for(int i=0;i<rows.size();i++){var r=rows.get(i);double value=k==0?r.keygenMs():k==1?r.encryptionMs():r.decryptionMs();int x=left+(right-left)*i/(rows.size()-1),y=bottom-(int)((bottom-top)*value/max);if(i>0)g.drawLine(px,py,x,y);g.fillOval(x-4,y-4,8,8);px=x;py=y;}g.fillRect(left+k*180,top-15,16,3);g.drawString(legends[k],left+24+k*180,top-9);}
            g.setColor(MUTED);for(int i=0;i<rows.size();i++){int x=left+(right-left)*i/(rows.size()-1);g.drawString(rows.get(i).attributes()+"",x-8,bottom+22);}g.drawString("Số thuộc tính trong chính sách",Math.max(left,(right+left)/2-100),bottom+40);g.dispose();
        }
    }
}
