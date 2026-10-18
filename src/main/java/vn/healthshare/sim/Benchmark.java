package vn.healthshare.sim;

import vn.healthshare.crypto.SpMaacs;
import vn.healthshare.crypto.SpMaacs.*;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

public final class Benchmark {
    private Benchmark() {}
    public record Result(int attributes, int satisfyingAttributes, int clauses, int runs, double keygenMs,
                         double encryptionMs, double decryptionMs, double encryptionSd, double decryptionSd,
                         long encryptPairings, long decryptPairings, int jsonBytes) {}
    /** Five authorities x five names; 10 measured fresh-key runs, following section 6.3's scale. */
    public static List<Result> run(int runs, int primeBits, Consumer<String> progress) {
        if(runs<1 || runs>100) throw new IllegalArgumentException("Số lượt phải từ 1 đến 100.");
        List<Result> results=new ArrayList<>();
        for(int count:new int[]{5,10,15,20,25}) {
            if(Thread.currentThread().isInterrupted()) throw new IllegalStateException("Đã hủy benchmark.");
            Simulation s=new Simulation(primeBits,x->{});
            List<Attribute> catalog=new ArrayList<>();
            for(int aa=0;aa<5;aa++) {
                List<Attribute> attrs=new ArrayList<>();
                for(int i=0;i<5;i++) attrs.add(Attribute.of("AA"+(aa+1),"A"+(i+1),"Value"+(i+1)));
                catalog.addAll(attrs); s.addAuthority("AA"+(aa+1),attrs);
            }
            List<Attribute> all=catalog.subList(0,count);
            int split=(count+1)/2;
            String policyText="("+join(all.subList(0,split))+") OR ("+join(all.subList(split,count))+")";
            Policy policy=Policy.parse(policyText);
            // Supply only the first clause; decryption visits the shorter nonmatching clause then succeeds.
            List<Attribute> satisfying=List.copyOf(all.subList(0,split));
            byte[] plain="Synthetic EHR benchmark payload; no patient information.".repeat(20).getBytes(StandardCharsets.UTF_8);
            for(int warm=0;warm<3;warm++) {
                s.register("warm-"+warm); s.issue("warm-"+warm,satisfying);
                Ciphertext ct=s.scheme().encrypt("warm",plain,policy,s.publicKeys(),x->{});
                try{s.scheme().decrypt(ct,s.users().get("warm-"+warm),x->{});}catch(AccessDenied e){throw new IllegalStateException(e);}
            }
            double[] keygen=new double[runs], enc=new double[runs], dec=new double[runs];
            long ep=0,dp=0; int size=0;
            for(int i=0;i<runs;i++) {
                String gid="bench-"+i;
                long start=System.nanoTime(); s.register(gid); s.issue(gid,satisfying); keygen[i]=millis(start);
                var before=s.scheme().group().counts();
                start=System.nanoTime(); Ciphertext ct=s.scheme().encrypt("bench",plain,policy,s.publicKeys(),x->{}); enc[i]=millis(start);
                ep=s.scheme().group().counts().minus(before).pairings();
                start=System.nanoTime();
                try {
                    Decryption d=s.scheme().decrypt(ct,s.users().get(gid),x->{}); dec[i]=millis(start); dp=d.operations().pairings();
                    if(!Arrays.equals(plain,d.plaintext())) throw new IllegalStateException("Benchmark round trip sai.");
                } catch(AccessDenied e) { throw new IllegalStateException(e); }
                size=ct.publicJson().getBytes(StandardCharsets.UTF_8).length;
            }
            Result r=new Result(count,split,policy.clauses().size(),runs,mean(keygen),mean(enc),mean(dec),sd(enc),sd(dec),ep,dp,size);
            results.add(r); progress.accept(String.format(Locale.ROOT,"%d thuộc tính: keygen %.3f ms, encrypt %.3f ms, decrypt %.3f ms (%d lượt).",count,r.keygenMs,r.encryptionMs,r.decryptionMs,runs));
        }
        return List.copyOf(results);
    }
    private static String join(List<Attribute> attrs) { return String.join(" AND ",attrs.stream().map(Attribute::toString).toList()); }
    private static double millis(long start) { return (System.nanoTime()-start)/1_000_000.0; }
    private static double mean(double[] x) { return Arrays.stream(x).average().orElse(0); }
    private static double sd(double[] x) { double m=mean(x);return Math.sqrt(Arrays.stream(x).map(v->(v-m)*(v-m)).average().orElse(0)); }
    public static String csv(List<Result> rows) {
        StringBuilder out=new StringBuilder("backend,attributes,satisfying_attributes,clauses,runs,keygen_mean_ms,encrypt_mean_ms,decrypt_mean_ms,encrypt_population_sd_ms,decrypt_population_sd_ms,encrypt_pairings,decrypt_pairings,public_json_bytes\n");
        for(Result r:rows) out.append(String.format(Locale.ROOT,"%s,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d%n",SpMaacs.BACKEND,r.attributes,r.satisfyingAttributes,r.clauses,r.runs,r.keygenMs,r.encryptionMs,r.decryptionMs,r.encryptionSd,r.decryptionSd,r.encryptPairings,r.decryptPairings,r.jsonBytes));
        return out.toString();
    }
}
