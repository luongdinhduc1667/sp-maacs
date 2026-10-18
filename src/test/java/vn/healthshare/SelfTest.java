package vn.healthshare;

import vn.healthshare.crypto.SpMaacs;
import vn.healthshare.crypto.SpMaacs.*;
import vn.healthshare.math.CompositeGroup;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;
import vn.healthshare.sim.Benchmark;
import vn.healthshare.sim.Simulation;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Dependency-free behavioral checks; every failure terminates the build with a nonzero status. */
public final class SelfTest {
    private static int passed;
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed++;System.out.println("PASS "+label);}
    @FunctionalInterface private interface Action {void run() throws Exception;}
    private static void rejects(Class<? extends Exception> type,Action action,String label)throws Exception{
        try{action.run();}catch(Exception e){if(!type.isInstance(e))throw e;check(true,label);return;}throw new AssertionError("Expected "+type.getSimpleName()+": "+label);
    }
    private static byte[] bytes(String value){return value.getBytes(StandardCharsets.UTF_8);}
    public static void main(String[] args)throws Exception{
        CompositeGroup g=new CompositeGroup(64);
        var base=g.generator();var a=g.scalar();var b=g.scalar();
        check(g.pairing(base.pow(a),base.pow(b)).equals(g.pairing(base,base).pow(a.multiply(b))),"bilinearity");
        check(g.pairing(base,g.subgroup3()).equals(g.identity(CompositeGroup.Kind.GT)),"Gp1 orthogonal Gp3");
        check(g.pairing(base,g.subgroup2()).equals(g.identity(CompositeGroup.Kind.GT)),"Gp1 orthogonal Gp2");
        check(!g.pairing(base,base).equals(g.identity(CompositeGroup.Kind.GT)),"non-degenerate Gp1 pairing");
        check(base.multiply(base).divide(base).equals(base),"group product inverse");
        rejects(IllegalArgumentException.class,()->base.multiply(g.targetGenerator()),"reject cross-group operations");
        rejects(IllegalArgumentException.class,()->g.pairing(new CompositeGroup(64).generator(),base),"reject cross-session elements");
        Policy p=Policy.parse("A.X=1 AND (B.Y=2 OR C.Z=3)");
        check(p.clauses().size()==2&&p.rowCount()==4,"AND/OR distributed to DNF");
        check(!p.hidden().contains("=1")&&p.hidden().contains("A.X=*"),"public names with hidden values");
        check(Policy.parse("A.X=1 OR (A.X=1 AND B.Y=2)").rowCount()==1,"DNF absorption");
        check(Policy.parse("A.X=1 AND A.X=1").rowCount()==1,"duplicate literal collapsed");
        check(Policy.parse("A.X=1 OR A.X=1").clauses().size()==1,"duplicate clause collapsed");
        check(Policy.parse("OWNER.Relation=\"Bạn thân\"").clauses().getFirst().getFirst().value().equals("Bạn thân"),"quoted Unicode values");
        check(Policy.parse("AA.Name=\"a\\\"b\\\\c\"").canonical().contains("a\\\"b\\\\c"),"escaped quote and slash round trip");
        check(Policy.parse("A.X=1 && (B.Y=2 | C.Z=3)").clauses().size()==2,"symbolic operators");
        rejects(IllegalArgumentException.class,()->Policy.parse("A.X=1 AND"),"reject dangling AND");
        rejects(IllegalArgumentException.class,()->Policy.parse("(A.X=1"),"reject unmatched parenthesis");
        rejects(IllegalArgumentException.class,()->Policy.parse("Name=Value"),"authority namespace required");
        rejects(IllegalArgumentException.class,()->Policy.parse("A.X=\"unterminated"),"reject missing quote");
        rejects(IllegalArgumentException.class,()->Policy.parse("A.X=1 NOT B.Y=2"),"reject unsupported NOT");
        String expansion=String.join(" AND ",java.util.stream.IntStream.range(0,7).mapToObj(i->"(A.X"+i+"=1 OR A.X"+i+"=2)").toList());
        rejects(IllegalArgumentException.class,()->Policy.parse(expansion),"bound exponential DNF expansion");
        for(int size:new int[]{1,2,3,8,25,64}){
            var matrix=Policy.andMatrix(size);var vector=new ArrayList<BigInteger>();for(int i=0;i<size;i++)vector.add(g.scalar());
            BigInteger reconstructed=matrix.stream().map(row->Policy.dot(row,vector,g.order())).reduce(BigInteger.ZERO,BigInteger::add).mod(g.order());
            check(reconstructed.equals(vector.getFirst()),"LSSS reconstructs s with "+size+" rows");
        }
        Simulation s=new Simulation(64,x->{});s.loadSample();
        check(s.authorities().size()==5 && s.users().size()==8 && s.cloud().size()==1,"sample system entities");
        for(String id:new String[]{"doctor-an","researcher-minh","friend-ha"})check(Arrays.equals(s.decrypt("EHR-001",id).plaintext(),bytes(Simulation.DEFAULT_EHR)),"authorized "+id);
        for(String id:new String[]{"doctor-binh","nurse-lan","outsider","colluder-a","colluder-b"})rejects(AccessDenied.class,()->s.decrypt("EHR-001",id),"denied "+id);
        check(s.decrypt("EHR-001","friend-ha").clauseIndex()==3,"closed-domain smallest matching clause");
        check(s.decrypt("EHR-001","researcher-minh").operations().pairings()==5,"pairing count 2*l+1 for two-row satisfied branch");
        for(String first:new String[]{"colluder-a","colluder-b"}){
            String second=first.equals("colluder-a")?"colluder-b":"colluder-a";
            var pooled=Simulation.unsafePoolForExperiment(s.users().get(first),s.users().get(second));
            rejects(AccessDenied.class,()->s.scheme().decrypt(s.cloud().get("EHR-001"),pooled,x->{}),"raw algebra rejects pooled keys using K1 of "+first);
        }
        var original=s.users().get("doctor-an").identity();byte[] badSignature=original.signature();badSignature[0]^=1;
        var forged=new Identity(original.gid(),original.k1(),badSignature);
        check(!SpMaacs.verifyIdentity(forged,s.ca().verificationKey()),"modified CA signature invalid");
        check(!SpMaacs.verifyIdentity(new Identity("other-gid",original.k1(),original.signature()),s.ca().verificationKey()),"certificate binds GID");
        check(!SpMaacs.verifyIdentity(new Identity(original.gid(),original.k1().multiply(generatedForeignBlind(s)),original.signature()),s.ca().verificationKey()),"certificate binds K1");
        rejects(SecurityException.class,()->s.authorities().get("HOSPITAL").issue(forged,List.of(Attribute.of("HOSPITAL","Profession","Doctor"))),"AA rejects forged certificate");
        var rogueCa=new CentralAuthority(s.scheme().group());var rogueIdentity=rogueCa.register("rogue-ca-user");
        rejects(SecurityException.class,()->s.authorities().get("HOSPITAL").issue(rogueIdentity,List.of(Attribute.of("HOSPITAL","Profession","Doctor"))),"AA trusts configured CA only, not requester-provided CA");
        rejects(IllegalArgumentException.class,()->s.register("doctor-an"),"unique GID");
        rejects(IllegalArgumentException.class,()->s.issue("outsider",List.of(Attribute.of("HOSPITAL","Profession","Administrator"))),"AA rejects unpublished attribute");
        var ct=s.cloud().get("EHR-001");byte[] corrupted=ct.payload();corrupted[0]^=1;
        rejects(AccessDenied.class,()->s.scheme().decrypt(ct.withPayload(corrupted),s.users().get("doctor-an"),x->{}),"modified EHR fails GCM authentication");
        var clauses=new ArrayList<>(ct.clauses());var last=clauses.getLast();
        clauses.set(clauses.size()-1,new Clause(last.authorities(),last.c().multiply(s.scheme().group().targetGenerator()),last.c0(),last.rows()));
        rejects(AccessDenied.class,()->s.scheme().decrypt(ct.withClauses(clauses),s.users().get("doctor-an"),x->{}),"AAD binds even an unused DNF branch");
        String export=ct.publicJson();
        check(!export.contains("\"Cardiology\"")&&!export.contains("\"HeartStudy\"")&&!export.contains("\"AIIMS\"")&&!export.contains("\"Friend\"")&&!export.contains("=Cardiology"),"cloud JSON has no policy values in plaintext");
        check(!export.contains("Nguyễn An")&&!export.contains("BN-2026-001")&&!export.contains("\"CASK\":")&&!export.contains("\"K3\":"),"cloud JSON excludes EHR and private keys");
        check(export.contains(SpMaacs.BACKEND)&&export.contains("ciphertextAndGcmTag"),"cloud export labels real JPBC backend");
        check(!s.ca().verificationKey().parameters().getParameters().getParameters().containsKey("n0"),"BLS public parameters omit factorization of N");
        var another=s.scheme().encrypt("fresh",bytes("same plaintext"),Policy.parse("OWNER.Relation=Friend"),s.publicKeys(),x->{});
        var another2=s.scheme().encrypt("fresh",bytes("same plaintext"),Policy.parse("OWNER.Relation=Friend"),s.publicKeys(),x->{});
        check(!Arrays.equals(another.iv(),another2.iv())&&!Arrays.equals(another.payload(),another2.payload()),"fresh randomness per encryption");
        s.register("multi-value");s.issue("multi-value",List.of(Attribute.of("MEDICAL","Specialization","Neurology"),Attribute.of("MEDICAL","Specialization","Cardiology"),Attribute.of("HOSPITAL","Profession","Doctor"),Attribute.of("HOSPITAL","Affiliation","AIIMS")));
        check(s.decrypt("EHR-001","multi-value").attempts()==2,"hidden value resolved by trying candidate keys and GCM");
        s.issue("outsider",List.of(Attribute.of("OWNER","Relation","Family")));s.issue("outsider",List.of(Attribute.of("OWNER","Relation","Friend")));
        check(s.users().get("outsider").attributes().size()==2&&s.decrypt("EHR-001","outsider").clauseIndex()==3,"incremental AA issuance preserves old attributes");
        sameAuthorityCollusion(s);
        var rows=Benchmark.run(1,64,x->{});
        check(rows.size()==5&&rows.getLast().attributes()==25,"benchmark 5 to 25 attributes round trips");
        check(rows.stream().allMatch(r->r.runs()==1&&r.encryptionMs()>0&&r.decryptionMs()>0&&r.jsonBytes()>0),"benchmark returns real measured data");
        check(Benchmark.csv(rows).lines().count()==6&&Benchmark.csv(rows).contains(SpMaacs.BACKEND),"CSV includes backend and measurements");
        System.out.println("\nALL "+passed+" CHECKS PASSED");
    }
    private static CompositeGroup.Element generatedForeignBlind(Simulation s){return s.scheme().group().subgroup3();}
    private static void sameAuthorityCollusion(Simulation s)throws Exception{
        s.register("same-aa-a");s.register("same-aa-b");
        s.issue("same-aa-a",List.of(Attribute.of("HOSPITAL","Profession","Doctor")));
        s.issue("same-aa-b",List.of(Attribute.of("HOSPITAL","Affiliation","AIIMS")));
        var ct=s.scheme().encrypt("same-aa",bytes("secret"),Policy.parse("HOSPITAL.Profession=Doctor AND HOSPITAL.Affiliation=AIIMS"),s.publicKeys(),x->{});
        var pooled=Simulation.unsafePoolForExperiment(s.users().get("same-aa-a"),s.users().get("same-aa-b"));
        rejects(AccessDenied.class,()->s.scheme().decrypt(ct,pooled,x->{}),"algebra rejects K3 pooled within the same authority");
    }
}
