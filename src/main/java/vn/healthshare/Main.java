package vn.healthshare;

import vn.healthshare.crypto.SpMaacs.AccessDenied;
import vn.healthshare.sim.Benchmark;
import vn.healthshare.sim.Simulation;
import vn.healthshare.sim.ExperimentCharts;
import vn.healthshare.math.CompositeGroup;
import vn.healthshare.ui.SimulatorFrame;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out),true,StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err),true,StandardCharsets.UTF_8));
        try {
            if(args.length==0 || args[0].equals("--gui")) {
                if(GraphicsEnvironment.isHeadless()) throw new IllegalArgumentException("Không có màn hình. Dùng --demo hoặc --benchmark.");
                SwingUtilities.invokeLater(()->new SimulatorFrame().setVisible(true)); return;
            }
            switch(args[0]) {
                case "--demo" -> demo(args);
                case "--plot" -> {
                    if(args.length!=2)throw new IllegalArgumentException("Dùng --plot đường_dẫn.csv.");
                    Path csv=Path.of(args[1]),png=Path.of(args[1]+".png");ExperimentCharts.writePng(Benchmark.readCsv(csv),png);System.out.println("Biểu đồ: "+png.toAbsolutePath());
                }
                case "--benchmark" -> {
                    if(args.length>4) throw new IllegalArgumentException("Dùng --benchmark [số_lượt] [đường_dẫn.csv] [bit_nguyên_tố].");
                    int runs=args.length>1?Integer.parseInt(args[1]):10;
                    Path output=Path.of(args.length>2?args[2]:"output/benchmark.csv");
                    int bits=args.length>3?Integer.parseInt(args[3]):CompositeGroup.PAPER_PRIME_BITS;
                    Path raw=Path.of(output.toString()+".raw.csv");save(raw,Benchmark.rawHeader());
                    var report=Benchmark.runDetailed(runs,bits,System.out::println,s->{try{Files.writeString(raw,Benchmark.rawLine(s),StandardCharsets.UTF_8,StandardOpenOption.APPEND);}catch(Exception e){throw new IllegalStateException(e);}});
                    save(output,Benchmark.csv(report.results()));save(Path.of(output+".metadata.json"),Benchmark.metadata(report));ExperimentCharts.writePng(report.results(),Path.of(output+".png"));
                    System.out.println("CSV: "+output.toAbsolutePath());
                    System.out.println("JPBC Type A1 thật, 3 × "+bits+" bit, BLS01; đo ABE riêng khỏi AES. Số liệu thô: "+raw.toAbsolutePath());
                }
                case "--help", "-h" -> System.out.println("SP-MAACS — JPBC Type A1 + BLS01 — Java 21\n  --gui\n  --demo [thư_mục_xuất] [primeBits=517]\n  --benchmark [số_lượt=10] [csv=output/benchmark.csv] [primeBits=517]\n  --plot đường_dẫn.csv\n  --help\nCấu hình bài báo: 3 × 517 bit; 5 AA × 5 thuộc tính; 10 lượt/điểm.\nTham số nhỏ chỉ dành cho smoke test, được ghi trong CSV.");
                default -> throw new IllegalArgumentException("Tùy chọn không hợp lệ. Dùng --help.");
            }
        } catch(Exception e) { System.err.println("Lỗi: "+e.getMessage()); System.exit(1); }
    }
    private static void demo(String[] args) throws Exception {
        if(args.length>3) throw new IllegalArgumentException("Dùng --demo [thư_mục_xuất] [primeBits=517].");
        int bits=args.length>2?Integer.parseInt(args[2]):CompositeGroup.PAPER_PRIME_BITS;
        System.out.println("SP-MAACS — JPBC TYPE A1, 3 × "+bits+" BIT, BLS01\n");
        Simulation sim=new Simulation(bits,System.out::println); sim.loadSample();
        int correct=0;
        for(String gid:new String[]{"doctor-an","doctor-binh","nurse-lan","researcher-minh","friend-ha","outsider","colluder-a","colluder-b"}) {
            boolean expected=gid.equals("doctor-an")||gid.equals("researcher-minh")||gid.equals("friend-ha"); boolean success;
            try {sim.decrypt("EHR-001",gid);success=true;}catch(AccessDenied e){success=false;}
            System.out.println((success==expected?"PASS":"FAIL")+" | "+gid+" | "+(success?"ĐƯỢC PHÉP":"TỪ CHỐI"));
            if(success==expected) correct++;
        }
        System.out.println(sim.collusionExperiment("EHR-001","colluder-a","colluder-b"));
        Path output=Path.of(args.length>1?args[1]:"output/demo"); save(output.resolve("cloud-EHR-001.json"),sim.cloud().get("EHR-001").publicJson());
        if(correct!=8) throw new IllegalStateException("Demo chỉ đạt "+correct+"/8 tình huống.");
        System.out.println("\nKết quả: "+correct+"/8. Ciphertext công khai: "+output.resolve("cloud-EHR-001.json").toAbsolutePath());
    }
    private static void save(Path path,String text) throws Exception {
        Path absolute=path.toAbsolutePath(); if(absolute.getParent()!=null) Files.createDirectories(absolute.getParent());
        Files.writeString(absolute,text,StandardCharsets.UTF_8);
    }
}
