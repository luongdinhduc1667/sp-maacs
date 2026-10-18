package vn.healthshare.sim;

import vn.healthshare.crypto.SpMaacs;
import vn.healthshare.crypto.SpMaacs.*;
import vn.healthshare.math.CompositeGroup;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;
import java.util.*;
import java.util.function.Consumer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Section 6.3: actual Type A1/BLS operations, 5 authorities x 5 attributes, 10 runs by default. */
public final class Benchmark {
    private Benchmark(){}
    public record Result(int attributes,int satisfyingAttributes,int clauses,int runs,double keygenMs,
                         double encryptionMs,double decryptionMs,double encryptionSd,double decryptionSd,
                         long encryptPairings,long decryptPairings,int jsonBytes,double caKeygenMs,double aaKeygenMs,int primeBits){}
    public record Sample(int attributes,int satisfyingAttributes,int iteration,int primeBits,double caKeygenMs,double aaKeygenMs,
                         double encryptionMs,double decryptionMs,long encryptPairings,long decryptPairings,boolean verified){}
    /** A finite benchmark plan: five points, one warm-up and runsPerPoint measured samples at each point. */
    public record Progress(int pointIndex,int pointCount,int attributes,int iteration,int runsPerPoint,boolean warmup,
                           int completedMeasurements,int completedWarmups,String phase){
        public int totalMeasurements(){return pointCount*runsPerPoint;}
        public int totalWarmups(){return pointCount;}
        public int completedSteps(){return completedMeasurements+completedWarmups;}
        public int totalSteps(){return totalMeasurements()+totalWarmups();}
        public String description(){
            if(pointIndex==0)return phase+" · tổng "+completedMeasurements+"/"+totalMeasurements()+" lượt đo · "+completedWarmups+"/"+totalWarmups()+" lượt khởi động";
            String current=warmup?"khởi động 1/1":"lượt "+iteration+"/"+runsPerPoint;
            return "Điểm "+pointIndex+"/"+pointCount+" · "+attributes+" thuộc tính · "+current+" · tổng "+completedMeasurements+"/"+totalMeasurements()+" · "+phase;
        }
    }
    public record Report(List<Result> results,List<Sample> samples,int primeBits,int warmups,String startedUtc){
        public Report{results=List.copyOf(results);samples=List.copyOf(samples);}
    }
    public static List<Result> run(int runs,int bits,Consumer<String> progress){return runDetailed(runs,bits,progress,s->{}).results();}
    public static Report runDetailed(int runs,int bits,Consumer<String> progress,Consumer<Sample> onSample){
        return runDetailed(runs,bits,progress,onSample,p->{},r->{});
    }
    public static Report runDetailed(int runs,int bits,Consumer<String> progress,Consumer<Sample> onSample,
                                     Consumer<Progress> onProgress,Consumer<Result> onResult){
        if(runs<1||runs>100)throw new IllegalArgumentException("Số lượt phải từ 1 đến 100.");
        String started=java.time.Instant.now().toString();
        int pointCount=5,warmups=1,completedMeasurements=0,completedWarmups=0;
        progress.accept("Benchmark gồm "+pointCount+" điểm (5, 10, 15, 20, 25 thuộc tính) × "+runs+" lượt đo = "+(pointCount*runs)+" lượt đo, cộng "+pointCount+" lượt khởi động. Số lượt bắt đầu từ 1 ở mỗi điểm.");
        progress.accept("GlobalSetup JPBC Type A1: 3 × "+bits+" bit; có thể cần thời gian tạo đường cong lần đầu.");
        emit(onProgress,new Progress(0,pointCount,0,0,runs,false,0,0,"GlobalSetup"));
        CompositeGroup group=new CompositeGroup(bits);
        emit(onProgress,new Progress(0,pointCount,0,0,runs,false,0,0,"CASetup"));
        SpMaacs scheme=new SpMaacs(group);CentralAuthority ca=new CentralAuthority(group);
        Map<String,AttributeAuthority> authorities=new LinkedHashMap<>();Map<String,PublicAuthority> publics=new LinkedHashMap<>();List<Attribute> catalog=new ArrayList<>();
        for(int aa=0;aa<5;aa++){
            List<Attribute> attrs=new ArrayList<>();for(int i=0;i<5;i++)attrs.add(Attribute.of("AA"+(aa+1),"A"+(i+1),"Value"+(i+1)));
            emit(onProgress,new Progress(0,pointCount,0,0,runs,false,0,0,"AASetup "+(aa+1)+"/5"));
            AttributeAuthority authority=new AttributeAuthority(group,"AA"+(aa+1),attrs,ca.verificationKey());authorities.put(authority.id(),authority);publics.put(authority.id(),authority.publicKey());catalog.addAll(attrs);
        }
        List<Result> results=new ArrayList<>();List<Sample> samples=new ArrayList<>();
        int pointIndex=0;
        for(int count:new int[]{5,10,15,20,25}){
            int split=new int[]{3,5,7,10,12}[pointIndex++];List<Attribute> all=List.copyOf(catalog.subList(0,count)),satisfying=all.subList(0,split);
            Policy policy=Policy.parse("("+join(satisfying)+") OR ("+join(all.subList(split,count))+")");
            progress.accept("Điểm "+pointIndex+"/"+pointCount+": "+count+" thuộc tính; cấp khóa và giải mã tập thỏa "+split+"; khởi động "+warmups+" lượt, đo "+runs+" lượt; tổng đã đo "+completedMeasurements+"/"+(pointCount*runs)+".");
            double[] caTimes=new double[runs],aaTimes=new double[runs],encTimes=new double[runs],decTimes=new double[runs];long ep=0,dp=0;int encodedBytes=0;
            for(int iteration=-warmups;iteration<runs;iteration++){
                boolean warmup=iteration<0;int displayIteration=warmup?0:iteration+1;
                emit(onProgress,new Progress(pointIndex,pointCount,count,displayIteration,runs,warmup,completedMeasurements,completedWarmups,"CAKeyGen"));
                long start=System.nanoTime();Identity identity=ca.register("bench-"+count+"-"+(iteration+warmups));double caMs=millis(start);
                emit(onProgress,new Progress(pointIndex,pointCount,count,displayIteration,runs,warmup,completedMeasurements,completedWarmups,"AAKeyGen"));
                start=System.nanoTime();UserKeys subset=issue(identity,satisfying,authorities);double aaMs=millis(start);
                var k=group.targetGenerator().pow(group.p1Scalar());var before=group.counts();
                emit(onProgress,new Progress(pointIndex,pointCount,count,displayIteration,runs,warmup,completedMeasurements,completedWarmups,"Encrypt"));
                start=System.nanoTime();List<Clause> ct=scheme.encapsulate(k,policy,publics,x->{});double encMs=millis(start);ep=group.counts().minus(before).pairings();
                emit(onProgress,new Progress(pointIndex,pointCount,count,displayIteration,runs,warmup,completedMeasurements,completedWarmups,"Decrypt"));
                before=group.counts();start=System.nanoTime();
                CompositeGroup.Element recovered;
                try{recovered=scheme.recoverCandidate(ct.getFirst(),subset);}catch(AccessDenied e){throw new IllegalStateException(e);}
                double decMs=millis(start);dp=group.counts().minus(before).pairings();
                boolean verified=k.equals(recovered); // Verify outside the measured decryption interval.
                if(!verified)throw new IllegalStateException("Khôi phục K sai tại "+count+" thuộc tính, lượt "+iteration);
                encodedBytes=ct.stream().mapToInt(c->c.c().bytes().length+c.c0().bytes().length+c.rows().stream().mapToInt(r->r.c1().bytes().length+r.c2().bytes().length).sum()).sum();
                if(iteration>=0){
                    caTimes[iteration]=caMs;aaTimes[iteration]=aaMs;encTimes[iteration]=encMs;decTimes[iteration]=decMs;
                    Sample sample=new Sample(count,split,iteration+1,bits,caMs,aaMs,encMs,decMs,ep,dp,true);samples.add(sample);onSample.accept(sample);
                    completedMeasurements++;
                    progress.accept(String.format(Locale.ROOT,"Điểm %d/%d · %d thuộc tính · lượt %d/%d · tổng %d/%d: CA %.1f + AA %.1f ms; Encrypt %.1f ms; Decrypt %.1f ms; K đúng.",pointIndex,pointCount,count,iteration+1,runs,completedMeasurements,pointCount*runs,caMs,aaMs,encMs,decMs));
                }else completedWarmups++;
                emit(onProgress,new Progress(pointIndex,pointCount,count,displayIteration,runs,warmup,completedMeasurements,completedWarmups,"K đúng"));
            }
            Result result=new Result(count,split,policy.clauses().size(),runs,mean(caTimes)+mean(aaTimes),mean(encTimes),mean(decTimes),sd(encTimes),sd(decTimes),ep,dp,encodedBytes,mean(caTimes),mean(aaTimes),bits);
            results.add(result);onResult.accept(result);CompositeGroup.checkCancelled();
        }
        emit(onProgress,new Progress(pointCount,pointCount,25,runs,runs,false,completedMeasurements,completedWarmups,"Hoàn tất"));
        progress.accept("Hoàn tất benchmark: "+pointCount+"/"+pointCount+" điểm, "+completedMeasurements+"/"+(pointCount*runs)+" lượt đo và "+completedWarmups+"/"+pointCount+" lượt khởi động; mọi khóa K đều đúng.");
        return new Report(results,samples,bits,warmups,started);
    }
    private static void emit(Consumer<Progress> callback,Progress value){
        CompositeGroup.checkCancelled();callback.accept(value);CompositeGroup.checkCancelled();
    }
    private static UserKeys issue(Identity identity,List<Attribute> all,Map<String,AttributeAuthority> authorities){
        Map<String,List<Attribute>> byAa=new LinkedHashMap<>();for(Attribute a:all)byAa.computeIfAbsent(a.label().authority(),x->new ArrayList<>()).add(a);
        UserKeys keys=UserKeys.empty(identity);for(var entry:byAa.entrySet())keys=keys.with(authorities.get(entry.getKey()).issue(identity,entry.getValue()));return keys;
    }
    private static UserKeys subset(UserKeys full,Set<Attribute> selected){
        Map<String,AuthorityKeys> filtered=new LinkedHashMap<>();
        for(AuthorityKeys keys:full.authorities().values()){
            Map<Attribute,CompositeGroup.Element> k3=new LinkedHashMap<>();keys.k3().forEach((a,k)->{if(selected.contains(a))k3.put(a,k);});
            if(!k3.isEmpty())filtered.put(keys.authority(),new AuthorityKeys(keys.authority(),keys.issuedTo(),keys.k2(),k3));
        }
        return new UserKeys(full.identity(),filtered);
    }
    private static String join(List<Attribute> attrs){return String.join(" AND ",attrs.stream().map(Attribute::toString).toList());}
    private static double millis(long start){return(System.nanoTime()-start)/1_000_000.0;}
    private static double mean(double[] x){return Arrays.stream(x).average().orElse(0);}
    private static double sd(double[] x){double m=mean(x);return Math.sqrt(Arrays.stream(x).map(v->(v-m)*(v-m)).average().orElse(0));}
    public static String csv(List<Result> rows){
        StringBuilder out=new StringBuilder("backend,prime_bits,authorities,attributes_per_authority,policy_attributes,keygen_attributes,decryption_attributes,clauses,runs,keygen_total_mean_ms,ca_keygen_mean_ms,aa_keygen_mean_ms,abe_encrypt_mean_ms,abe_decrypt_mean_ms,encrypt_population_sd_ms,decrypt_population_sd_ms,encrypt_pairings,decrypt_pairings,abe_element_bytes\n");
        for(Result r:rows)out.append(String.format(Locale.ROOT,"%s,%d,5,5,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%d,%d%n",SpMaacs.BACKEND,r.primeBits,r.attributes,r.satisfyingAttributes,r.satisfyingAttributes,r.clauses,r.runs,r.keygenMs,r.caKeygenMs,r.aaKeygenMs,r.encryptionMs,r.decryptionMs,r.encryptionSd,r.decryptionSd,r.encryptPairings,r.decryptPairings,r.jsonBytes));
        return out.toString();
    }
    public static String rawHeader(){return"backend,prime_bits,policy_attributes,keygen_attributes,decryption_attributes,iteration,ca_keygen_ms,aa_keygen_ms,abe_encrypt_ms,abe_decrypt_ms,encrypt_pairings,decrypt_pairings,key_recovered\n";}
    public static String rawLine(Sample s){return String.format(Locale.ROOT,"%s,%d,%d,%d,%d,%d,%.6f,%.6f,%.6f,%.6f,%d,%d,%s%n",SpMaacs.BACKEND,s.primeBits,s.attributes,s.satisfyingAttributes,s.satisfyingAttributes,s.iteration,s.caKeygenMs,s.aaKeygenMs,s.encryptionMs,s.decryptionMs,s.encryptPairings,s.decryptPairings,s.verified);}
    public static String rawCsv(Report report){return rawHeader()+String.join("",report.samples().stream().map(Benchmark::rawLine).toList());}
    public static List<Result> readCsv(Path path)throws IOException{
        List<String> lines=Files.readAllLines(path);if(lines.isEmpty()||!lines.getFirst().startsWith("backend,prime_bits,authorities,"))throw new IllegalArgumentException("Không phải CSV benchmark SP-MAACS phiên bản 2.");
        List<Result> out=new ArrayList<>();
        for(String line:lines.subList(1,lines.size())){
            if(line.isBlank())continue;String[] c=line.split(",",-1);if(c.length!=19||!c[0].equals(SpMaacs.BACKEND))throw new IllegalArgumentException("CSV sai cột hoặc backend.");
            if(!c[2].equals("5")||!c[3].equals("5")||!c[5].equals(c[6]))throw new IllegalArgumentException("CSV không đúng cấu hình 5 AA × 5 thuộc tính.");
            for(int i=1;i<c.length;i++){double v=Double.parseDouble(c[i]);if(!Double.isFinite(v)||v<0)throw new IllegalArgumentException("Số liệu CSV không hợp lệ.");}
            out.add(new Result(Integer.parseInt(c[4]),Integer.parseInt(c[6]),Integer.parseInt(c[7]),Integer.parseInt(c[8]),Double.parseDouble(c[9]),Double.parseDouble(c[12]),Double.parseDouble(c[13]),Double.parseDouble(c[14]),Double.parseDouble(c[15]),Long.parseLong(c[16]),Long.parseLong(c[17]),Integer.parseInt(c[18]),Double.parseDouble(c[10]),Double.parseDouble(c[11]),Integer.parseInt(c[1])));
        }
        if(out.size()!=5)throw new IllegalArgumentException("CSV cần đủ 5 điểm đo.");
        for(int i=0;i<5;i++)if(out.get(i).attributes()!=new int[]{5,10,15,20,25}[i]||out.get(i).satisfyingAttributes()!=new int[]{3,5,7,10,12}[i]||out.get(i).primeBits()!=out.getFirst().primeBits())throw new IllegalArgumentException("Các mốc trục CSV không đúng Figure 3.");
        return List.copyOf(out);
    }
    public static String metadata(Report report){
        return """
                {
                  "backend": %s,
                  "primeBits": %d,
                  "primeCount": 3,
                  "signature": "JPBC BLS01 / SHA-256",
                  "warmupsPerPoint": %d,
                  "startedUtc": %s,
                  "java": %s,
                  "os": %s,
                  "availableProcessors": %d,
                  "policyAttributes": [5,10,15,20,25],
                  "satisfyingAttributes": [3,5,7,10,12],
                  "encryptionScope": "ABE equations 5-8, excludes AES/HKDF and JSON",
                  "decryptionScope": "ABE equations 12-14 on a selected satisfying clause, equality checked after timer",
                  "keygenScope": "CAKeyGen and AAKeyGen for the satisfying set, reported separately and summed",
                  "baseline26": "Unavailable: primary full text needed; no comparison timings invented"
                }
                """.formatted(SpMaacs.json(SpMaacs.BACKEND),report.primeBits,report.warmups,SpMaacs.json(report.startedUtc),SpMaacs.json(System.getProperty("java.version")),SpMaacs.json(System.getProperty("os.name")+" "+System.getProperty("os.arch")),Runtime.getRuntime().availableProcessors());
    }
}
