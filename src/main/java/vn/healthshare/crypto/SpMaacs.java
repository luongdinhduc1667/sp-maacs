package vn.healthshare.crypto;

import vn.healthshare.math.CompositeGroup;
import vn.healthshare.math.CompositeGroup.Element;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;
import vn.healthshare.policy.Policy.Label;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.function.Consumer;

/** Section 5, equations (1)-(14), evaluated over the educational composite-group backend. */
public final class SpMaacs {
    public static final String BACKEND = "EDUCATIONAL_EXPONENT_MODEL_NOT_SECURE_ABE";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final CompositeGroup group;
    public SpMaacs(CompositeGroup group) { this.group=Objects.requireNonNull(group); }
    public CompositeGroup group() { return group; }
    public static final class CentralAuthority {
        private final CompositeGroup group;
        private final KeyPair signatureKey;
        private final Set<String> registered = new HashSet<>();
        public CentralAuthority(CompositeGroup group) {
            this.group=group;
            try { signatureKey=KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); }
            catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
        }
        public PublicKey verificationKey() { return signatureKey.getPublic(); }
        /** CAKeyGen: one fresh r_gid and Gp3 blind per gid; signature authenticates gid || K1. */
        public Identity register(String gid) {
            if(gid==null || !gid.matches("[\\p{L}\\p{N}_@.-]{1,80}")) throw new IllegalArgumentException("GID chỉ gồm chữ, số, _, @, dấu chấm, dấu gạch; tối đa 80 ký tự.");
            if(registered.contains(gid)) throw new IllegalArgumentException("GID đã được đăng ký: "+gid);
            Element k1=group.generator().pow(group.scalar()).multiply(group.subgroup3());
            try {
                Signature signer=Signature.getInstance("Ed25519"); signer.initSign(signatureKey.getPrivate());
                signer.update(identityMessage(gid,k1));
                Identity result=new Identity(gid,k1,signer.sign()); registered.add(gid); return result;
            } catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
        }
    }
    public record Identity(String gid, Element k1, byte[] signature) {
        public Identity { Objects.requireNonNull(gid); Objects.requireNonNull(k1); signature=signature.clone(); }
        @Override public byte[] signature() { return signature.clone(); }
    }
    public record PublicAuthority(String id, Element alphaTarget, Element betaBase, Map<Attribute,Element> attributeBases) {
        public PublicAuthority { attributeBases=Collections.unmodifiableMap(new LinkedHashMap<>(attributeBases)); }
    }
    public static final class AttributeAuthority {
        private final CompositeGroup group;
        private final String id;
        private final BigInteger alpha, beta;
        private final PublicKey trustedCaKey;
        private final Map<Attribute,BigInteger> exponents;
        private final PublicAuthority publicKey;
        /** AASetup, equations (1)-(4). The catalog is registered by this authority, not by the requester. */
        public AttributeAuthority(CompositeGroup group, String id, Collection<Attribute> catalog, PublicKey trustedCaKey) {
            this.group=group; this.id=id; this.trustedCaKey=Objects.requireNonNull(trustedCaKey); new Label(id,"Check");
            alpha=group.scalar(); beta=group.scalar(); exponents=new LinkedHashMap<>();
            Map<Attribute,Element> bases=new LinkedHashMap<>();
            for(Attribute a:catalog) {
                if(!a.label().authority().equals(id)) throw new IllegalArgumentException("Thuộc tính sai cơ quan: "+a);
                BigInteger t=group.scalar(); exponents.put(a,t); bases.put(a,group.generator().pow(t));
            }
            publicKey=new PublicAuthority(id,group.targetGenerator().pow(alpha),group.generator().pow(beta),bases);
        }
        public String id() { return id; }
        public PublicAuthority publicKey() { return publicKey; }
        /** AAKeyGen: verify the CA certificate before using CASK_gid, then issue (9)-(11). */
        public AuthorityKeys issue(Identity identity, Collection<Attribute> approvedAttributes) {
            if(!verifyIdentity(identity, trustedCaKey)) throw new SecurityException("Chứng nhận CA không hợp lệ; AA từ chối cấp khóa.");
            Map<Attribute,Element> keys=new LinkedHashMap<>();
            for(Attribute attribute:approvedAttributes) {
                BigInteger t=exponents.get(attribute);
                if(t==null) throw new IllegalArgumentException("Thuộc tính chưa được cơ quan "+id+" công bố: "+attribute);
                keys.put(attribute,identity.k1().pow(t).multiply(group.subgroup3()));
            }
            if(keys.isEmpty()) throw new IllegalArgumentException("Cần ít nhất một thuộc tính được AA phê duyệt.");
            Element k2=group.generator().pow(alpha).multiply(identity.k1().pow(beta)).multiply(group.subgroup3());
            return new AuthorityKeys(id,identity.gid(),k2,keys);
        }
    }
    public record AuthorityKeys(String authority, String issuedTo, Element k2, Map<Attribute,Element> k3) {
        public AuthorityKeys { k3=Collections.unmodifiableMap(new LinkedHashMap<>(k3)); }
    }
    public record UserKeys(Identity identity, Map<String,AuthorityKeys> authorities) {
        public UserKeys { authorities=Collections.unmodifiableMap(new LinkedHashMap<>(authorities)); }
        public static UserKeys empty(Identity identity) { return new UserKeys(identity,Map.of()); }
        public UserKeys with(AuthorityKeys keys) {
            if(!keys.issuedTo().equals(identity.gid())) throw new IllegalArgumentException("Khóa được cấp cho GID khác.");
            Map<String,AuthorityKeys> next=new LinkedHashMap<>(authorities);
            AuthorityKeys old=next.get(keys.authority());
            if(old!=null) {
                Map<Attribute,Element> merged=new LinkedHashMap<>(old.k3()); merged.putAll(keys.k3());
                keys=new AuthorityKeys(keys.authority(),keys.issuedTo(),keys.k2(),merged);
            }
            next.put(keys.authority(),keys); return new UserKeys(identity,next);
        }
        public List<Attribute> attributes() { return authorities.values().stream().flatMap(a->a.k3().keySet().stream()).toList(); }
    }
    public static boolean verifyIdentity(Identity identity, PublicKey publicKey) {
        try {
            Signature verifier=Signature.getInstance("Ed25519"); verifier.initVerify(publicKey);
            verifier.update(identityMessage(identity.gid(),identity.k1())); return verifier.verify(identity.signature());
        } catch(GeneralSecurityException | IllegalArgumentException e) { return false; }
    }
    private static byte[] identityMessage(String gid, Element k1) {
        return binary(out->{out.writeUTF("SP-MAACS/CA/v1"); writeString(out,gid); writeBytes(out,k1.bytes());});
    }

    public record Row(Label label, List<BigInteger> matrixRow, Element c1, Element c2) {
        public Row { matrixRow=List.copyOf(matrixRow); }
    }
    public record Clause(List<String> authorities, Element c, Element c0, List<Row> rows) {
        public Clause { authorities=List.copyOf(authorities); rows=List.copyOf(rows); }
    }
    /** Cloud object deliberately has NO plaintext policy values, session key, user keys, or source EHR. */
    public static final class Ciphertext {
        private final String id;
        private final BigInteger order;
        private final List<Clause> clauses;
        private final byte[] salt, iv, payload;
        public Ciphertext(String id, BigInteger order, List<Clause> clauses, byte[] salt, byte[] iv, byte[] payload) {
            this.id=Objects.requireNonNull(id); this.order=order; this.clauses=List.copyOf(clauses);
            this.salt=salt.clone(); this.iv=iv.clone(); this.payload=payload.clone();
        }
        public String id() { return id; }
        public List<Clause> clauses() { return clauses; }
        public byte[] payload() { return payload.clone(); }
        public byte[] iv() { return iv.clone(); }
        public int payloadSize() { return payload.length; }
        public Ciphertext withPayload(byte[] changed) { return new Ciphertext(id,order,clauses,salt,iv,changed); }
        public Ciphertext withClauses(List<Clause> changed) { return new Ciphertext(id,order,changed,salt,iv,payload); }
        public String hiddenPolicy() {
            return String.join(" OR ",clauses.stream().map(c->"("+String.join(" AND ",c.rows().stream().map(r->r.label()+"=*").toList())+")").toList());
        }
        public byte[] authenticatedHeader() {
            return binary(out->{
                writeString(out,"SP-MAACS/v1"); writeString(out,BACKEND); writeString(out,id); writeBytes(out,order.toByteArray());
                writeBytes(out,salt); writeBytes(out,iv); out.writeInt(clauses.size());
                for(Clause c:clauses) {
                    out.writeInt(c.authorities().size()); for(String aa:c.authorities()) writeString(out,aa);
                    writeBytes(out,c.c().bytes()); writeBytes(out,c.c0().bytes()); out.writeInt(c.rows().size());
                    for(Row r:c.rows()) {
                        writeString(out,r.label().authority()); writeString(out,r.label().name());
                        out.writeInt(r.matrixRow().size()); for(BigInteger m:r.matrixRow()) writeBytes(out,m.toByteArray());
                        writeBytes(out,r.c1().bytes()); writeBytes(out,r.c2().bytes());
                    }
                }
            });
        }
        public String publicJson() {
            StringBuilder s=new StringBuilder("{\n  \"format\": \"SP-MAACS-SIM-v1\",\n  \"backend\": ").append(json(BACKEND))
                    .append(",\n  \"warning\": \"Exponent simulation; not cryptographically secure ABE. Session-only export for inspection.\",")
                    .append("\n  \"id\": ").append(json(id)).append(",\n  \"orderN\": ").append(json(order.toString()))
                    .append(",\n  \"publicPolicy\": ").append(json(hiddenPolicy())).append(",\n  \"clauses\": [");
            for(int i=0;i<clauses.size();i++) {
                Clause c=clauses.get(i); if(i>0)s.append(',');
                s.append("\n    {\"authorities\":[").append(String.join(",",c.authorities().stream().map(SpMaacs::json).toList()))
                        .append("],\"C\":").append(json(b64(c.c().bytes()))).append(",\"C0\":").append(json(b64(c.c0().bytes()))).append(",\"rows\":[");
                for(int j=0;j<c.rows().size();j++) {
                    Row r=c.rows().get(j); if(j>0)s.append(',');
                    s.append("{\"authority\":").append(json(r.label().authority())).append(",\"attributeName\":").append(json(r.label().name()))
                            .append(",\"W\":").append(r.matrixRow()).append(",\"C1\":").append(json(b64(r.c1().bytes()))).append(",\"C2\":").append(json(b64(r.c2().bytes()))).append('}');
                }
                s.append("]}");
            }
            return s.append("\n  ],\n  \"hkdfSalt\": ").append(json(b64(salt))).append(",\n  \"iv\": ").append(json(b64(iv)))
                    .append(",\n  \"ciphertextAndGcmTag\": ").append(json(b64(payload))).append("\n}\n").toString();
        }
    }
    /** Encrypt uses ONLY public authority keys. Every DNF clause independently wraps the same K. */
    public Ciphertext encrypt(String id, byte[] ehr, Policy policy, Map<String,PublicAuthority> publicKeys, Consumer<String> trace) {
        if(id==null || id.isBlank() || id.length()>200) throw new IllegalArgumentException("Mã hồ sơ phải từ 1 đến 200 ký tự.");
        if(ehr==null || ehr.length==0 || ehr.length>2_000_000) throw new IllegalArgumentException("Hồ sơ phải có dữ liệu, tối đa 2 MB.");
        Element k=group.targetGenerator().pow(group.p1Scalar());
        List<Clause> clauses=new ArrayList<>();
        trace.accept("Encrypt: DNF có "+policy.clauses().size()+" nhánh; mỗi nhánh dùng s, v và r_x mới.");
        for(List<Attribute> attributes:policy.clauses()) {
            List<String> saa=attributes.stream().map(a->a.label().authority()).distinct().toList();
            Element alpha=group.identity(CompositeGroup.Kind.GT), beta=group.identity(CompositeGroup.Kind.G);
            for(String aa:saa) {
                PublicAuthority pk=publicKeys.get(aa);
                if(pk==null) throw new IllegalArgumentException("Chưa có cơ quan: "+aa);
                alpha=alpha.multiply(pk.alphaTarget()); beta=beta.multiply(pk.betaBase());
            }
            List<BigInteger> v=new ArrayList<>(); for(int x=0;x<attributes.size();x++) v.add(group.scalar());
            BigInteger secret=v.getFirst();
            List<List<BigInteger>> matrix=Policy.andMatrix(attributes.size());
            List<Row> rows=new ArrayList<>();
            for(int x=0;x<attributes.size();x++) {
                Attribute a=attributes.get(x);
                Element t=publicKeys.get(a.label().authority()).attributeBases().get(a);
                if(t==null) throw new IllegalArgumentException("Giá trị thuộc tính chưa được AA công bố: "+a);
                BigInteger lambda=Policy.dot(matrix.get(x),v,group.order()), rx=group.scalar();
                rows.add(new Row(a.label(),matrix.get(x),beta.pow(lambda).multiply(t.pow(rx.negate())),group.generator().pow(rx)));
            }
            clauses.add(new Clause(saa,k.multiply(alpha.pow(secret)),group.generator().pow(secret),rows));
            trace.accept("Nhánh "+clauses.size()+": W="+attributes.size()+"×"+attributes.size()+", SAA="+saa+"; tính C, C0, C1,x, C2,x theo (5)-(8).");
        }
        byte[] salt=randomBytes(32), iv=randomBytes(12);
        Ciphertext shell=new Ciphertext(id,group.order(),clauses,salt,iv,new byte[0]);
        try {
            byte[] aesKey=hkdf(k.bytes(),salt);
            try {
                byte[] payload=aes(Cipher.ENCRYPT_MODE,aesKey,iv,shell.authenticatedHeader(),ehr);
                trace.accept("AES-256-GCM: mã hóa EHR; xác thực toàn bộ header công khai bằng AAD. Cloud không lưu giá trị thuộc tính.");
                return new Ciphertext(id,group.order(),clauses,salt,iv,payload);
            } finally { Arrays.fill(aesKey,(byte)0); }
        } catch(GeneralSecurityException e) { throw new IllegalStateException("Không thể mã hóa AES-GCM.",e); }
    }
    public record Decryption(byte[] plaintext, int clauseIndex, int attempts, CompositeGroup.Counts operations) {
        public Decryption { plaintext=plaintext.clone(); }
        @Override public byte[] plaintext() { return plaintext.clone(); }
    }
    public static final class AccessDenied extends Exception {
        public AccessDenied(String message) { super(message); }
    }
    /** No plaintext-value matching and no gid equality shortcut: equation (13) + GCM decide success. */
    public Decryption decrypt(Ciphertext ciphertext, UserKeys keys, Consumer<String> trace) throws AccessDenied {
        CompositeGroup.Counts start=group.counts();
        if(!ciphertext.order.equals(group.order())) throw new AccessDenied("Ciphertext thuộc một phiên hệ thống khác.");
        List<Integer> ordered=new ArrayList<>(); for(int i=0;i<ciphertext.clauses.size();i++) ordered.add(i);
        ordered.sort(Comparator.comparingInt(i->ciphertext.clauses.get(i).rows().size()));
        int[] attempts={0};
        for(int index:ordered) {
            Clause c=ciphertext.clauses.get(index);
            Element ck=group.identity(CompositeGroup.Kind.G); boolean missing=false;
            for(String aa:c.authorities()) {
                AuthorityKeys a=keys.authorities().get(aa);
                if(a==null) { missing=true; break; } ck=ck.multiply(a.k2());
            }
            if(missing) { trace.accept("Bỏ nhánh "+(index+1)+": thiếu khóa cơ quan."); continue; }
            List<List<Element>> factors=new ArrayList<>();
            for(Row row:c.rows()) {
                AuthorityKeys aa=keys.authorities().get(row.label().authority());
                List<Element> options=new ArrayList<>();
                Element first=group.pairing(row.c1(),keys.identity().k1());
                for(var entry:aa.k3().entrySet()) if(entry.getKey().label().equals(row.label()))
                    options.add(first.multiply(group.pairing(row.c2(),entry.getValue())));
                if(options.isEmpty()) { missing=true; break; }
                factors.add(options);
            }
            if(missing) { trace.accept("Bỏ nhánh "+(index+1)+": thiếu tên thuộc tính."); continue; }
            Element numerator=group.pairing(ck,c.c0()); // (12)
            trace.accept("Nhánh "+(index+1)+": tính e(CK,C0); thử khóa có cùng tên, không đọc giá trị bị ẩn.");
            byte[] plaintext=tryCombinations(ciphertext,c,numerator,factors,0,group.identity(CompositeGroup.Kind.GT),attempts);
            if(plaintext!=null) {
                trace.accept("(13) khử phần r_gid; (14) thu hồi K; tag AES-GCM hợp lệ. Truy cập được cấp.");
                return new Decryption(plaintext,index+1,attempts[0],group.counts().minus(start));
            }
        }
        throw new AccessDenied("Từ chối: thiếu khóa, sai giá trị, khóa không cùng định danh hoặc ciphertext đã bị sửa. Đã thử "+attempts[0]+" tổ hợp.");
    }
    private byte[] tryCombinations(Ciphertext ct, Clause clause, Element numerator, List<List<Element>> factors,
                                   int row, Element product, int[] attempts) throws AccessDenied {
        if(row==factors.size()) {
            if(attempts[0]>=4096) throw new AccessDenied("Vượt 4096 tổ hợp khóa thử. Hãy giảm số giá trị/thuộc tính hoặc chia nhỏ chính sách.");
            attempts[0]++;
            Element recovered=clause.c().divide(numerator.divide(product)); // (13), mu_x=1; then (14)
            byte[] key=null;
            try {
                key=hkdf(recovered.bytes(),ct.salt);
                return aes(Cipher.DECRYPT_MODE,key,ct.iv,ct.authenticatedHeader(),ct.payload);
            } catch(AEADBadTagException e) { return null; }
            catch(GeneralSecurityException e) { throw new IllegalStateException(e); }
            finally { if(key!=null) Arrays.fill(key,(byte)0); }
        }
        for(Element option:factors.get(row)) {
            byte[] result=tryCombinations(ct,clause,numerator,factors,row+1,product.multiply(option),attempts);
            if(result!=null) return result;
        }
        return null;
    }
    private static byte[] hkdf(byte[] input, byte[] salt) throws GeneralSecurityException {
        Mac mac=Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(salt,"HmacSHA256")); byte[] prk=mac.doFinal(input);
        try {
            mac.init(new SecretKeySpec(prk,"HmacSHA256")); mac.update("SP-MAACS-SIM/AES-256-GCM/v1".getBytes(StandardCharsets.UTF_8));
            return mac.doFinal(new byte[]{1});
        } finally { Arrays.fill(prk,(byte)0); }
    }
    private static byte[] aes(int mode, byte[] key, byte[] iv, byte[] aad, byte[] data) throws GeneralSecurityException {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(mode,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));
        cipher.updateAAD(aad); return cipher.doFinal(data);
    }
    private static byte[] randomBytes(int count) { byte[] out=new byte[count]; RANDOM.nextBytes(out); return out; }
    private interface Writer { void write(DataOutputStream out) throws IOException; }
    private static byte[] binary(Writer writer) {
        try { ByteArrayOutputStream bytes=new ByteArrayOutputStream(); try(DataOutputStream out=new DataOutputStream(bytes)){writer.write(out);} return bytes.toByteArray(); }
        catch(IOException e) { throw new UncheckedIOException(e); }
    }
    private static void writeString(DataOutputStream out, String s) throws IOException { writeBytes(out,s.getBytes(StandardCharsets.UTF_8)); }
    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException { out.writeInt(bytes.length); out.write(bytes); }
    private static String b64(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    public static String json(String s) {
        StringBuilder b=new StringBuilder("\"");
        for(int i=0;i<s.length();i++) {
            char c=s.charAt(i);
            switch(c) { case '"'->b.append("\\\""); case '\\'->b.append("\\\\"); case '\n'->b.append("\\n"); case '\r'->b.append("\\r"); case '\t'->b.append("\\t");
                default->{if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);} }
        }
        return b.append('"').toString();
    }
}
