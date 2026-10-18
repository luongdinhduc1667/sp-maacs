package vn.healthshare;

import vn.healthshare.crypto.SpMaacs.AccessDenied;
import vn.healthshare.sim.Benchmark;
import vn.healthshare.sim.Simulation;
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
                case "--benchmark" -> {
                    if(args.length>3) throw new IllegalArgumentException("Dùng --benchmark [số_lượt] [đường_dẫn.csv].");
                    int runs=args.length>1?Integer.parseInt(args[1]):10;
                    Path output=Path.of(args.length>2?args[2]:"output/benchmark.csv");
                    var rows=Benchmark.run(runs,128,System.out::println); save(output,Benchmark.csv(rows));
                    System.out.println("CSV: "+output.toAbsolutePath());
                    System.out.println("Các thời gian thuộc mô hình số mũ; không tương đương benchmark JPBC/Type A1 trong bài báo.");
                }
                case "--help", "-h" -> System.out.println("SP-MAACS Simulator — Java 21\n  --gui\n  --demo [thư_mục_xuất]\n  --benchmark [số_lượt=10] [csv=output/benchmark.csv]\n  --help\nPairing được mô phỏng đại số; không dùng để bảo vệ hồ sơ thực.");
                default -> throw new IllegalArgumentException("Tùy chọn không hợp lệ. Dùng --help.");
            }
        } catch(Exception e) { System.err.println("Lỗi: "+e.getMessage()); System.exit(1); }
    }
    private static void demo(String[] args) throws Exception {
        if(args.length>2) throw new IllegalArgumentException("Dùng --demo [thư_mục_xuất].");
        System.out.println("SP-MAACS — MÔ PHỎNG ĐẠI SỐ, KHÔNG PHẢI ABE AN TOÀN MẬT MÃ\n");
        Simulation sim=new Simulation(128,System.out::println); sim.loadSample();
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
