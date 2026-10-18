package vn.healthshare.sim;

import vn.healthshare.crypto.SpMaacs;
import vn.healthshare.crypto.SpMaacs.*;
import vn.healthshare.math.CompositeGroup;
import vn.healthshare.policy.Policy;
import vn.healthshare.policy.Policy.Attribute;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;

/** Local roles: trusted CA/AAs, data-owner editor, cloud ciphertext store, and users. */
public final class Simulation {
    public static final String DEFAULT_POLICY = "(HOSPITAL.Profession=Doctor AND MEDICAL.Specialization=Cardiology AND HOSPITAL.Affiliation=AIIMS)\nOR (RESEARCH.Role=Researcher AND RESEARCH.Project=HeartStudy)\nOR OWNER.Relation=Friend";
    public static final String DEFAULT_EHR = "HỒ SƠ Y TẾ GIẢ LẬP\nMã bệnh nhân: BN-2026-001\nHọ tên: Nguyễn An (dữ liệu giả)\nChẩn đoán mô phỏng: theo dõi tim mạch\nGhi chú: dữ liệu mẫu phục vụ học tập, không dùng cho điều trị.\n";
    private final CompositeGroup group;
    private final CentralAuthority ca;
    private final SpMaacs scheme;
    private final Map<String,AttributeAuthority> authorities=new LinkedHashMap<>();
    private final Map<String,UserKeys> users=new LinkedHashMap<>();
    private final Map<String,Ciphertext> cloud=new LinkedHashMap<>();
    private final Consumer<String> log;
    public Simulation(int bits, Consumer<String> log) {
        this.log=log;log.accept("GlobalSetup: đang tạo/đọc đường cong Type A1, 3 × "+bits+" bit…");group=new CompositeGroup(bits);log.accept("CASetup: đang tạo khóa chữ ký BLS01…");ca=new CentralAuthority(group);scheme=new SpMaacs(group);
        log.accept("GlobalSetup: JPBC Type A1, 3 nhóm con, "+bits+" bit/số nguyên tố. CASetup: chữ ký BLS01/SHA-256.");
    }
    public SpMaacs scheme() { return scheme; }
    public CentralAuthority ca() { return ca; }
    public Map<String,AttributeAuthority> authorities() { return Collections.unmodifiableMap(authorities); }
    public Map<String,UserKeys> users() { return Collections.unmodifiableMap(users); }
    public Map<String,Ciphertext> cloud() { return Collections.unmodifiableMap(cloud); }
    public Map<String,PublicAuthority> publicKeys() {
        Map<String,PublicAuthority> p=new LinkedHashMap<>(); authorities.forEach((id,aa)->p.put(id,aa.publicKey())); return p;
    }
    public void addAuthority(String id, Collection<Attribute> catalog) {
        if(authorities.containsKey(id)) throw new IllegalArgumentException("Cơ quan đã tồn tại: "+id);
        if(catalog.isEmpty()) throw new IllegalArgumentException("Danh mục thuộc tính không được rỗng.");
        authorities.put(id,new AttributeAuthority(group,id,catalog,ca.verificationKey())); log.accept("AASetup: "+id+", "+catalog.size()+" cặp tên/giá trị được công bố.");
    }
    public void register(String gid) {
        users.put(gid,UserKeys.empty(ca.register(gid))); log.accept("CAKeyGen: đăng ký "+gid+"; ký chứng nhận CA gắn GID và K1.");
    }
    public void issue(String gid, Collection<Attribute> approved) {
        UserKeys user=users.get(gid);
        if(user==null) throw new IllegalArgumentException("Người dùng chưa đăng ký.");
        if(approved.isEmpty()) throw new IllegalArgumentException("Hãy chọn ít nhất một thuộc tính.");
        Map<String,List<Attribute>> byAuthority=new LinkedHashMap<>();
        for(Attribute a:approved) {
            AttributeAuthority aa=authorities.get(a.label().authority());
            if(aa==null || !aa.publicKey().attributeBases().containsKey(a)) throw new IllegalArgumentException("Chưa có thuộc tính: "+a);
            byAuthority.computeIfAbsent(a.label().authority(),x->new ArrayList<>()).add(a);
        }
        for(var entry:byAuthority.entrySet()) user=user.with(authorities.get(entry.getKey()).issue(user.identity(),entry.getValue()));
        users.put(gid,user); log.accept("AAKeyGen: AA đã phê duyệt "+approved.size()+" thuộc tính cho "+gid+"; tính K2, K3 theo (10)-(11).");
    }
    public Ciphertext encrypt(String id, String ehr, String policy) {
        if(cloud.containsKey(id)) throw new IllegalArgumentException("Mã hồ sơ đã tồn tại. Hãy dùng mã khác.");
        Ciphertext ciphertext=scheme.encrypt(id,ehr.getBytes(StandardCharsets.UTF_8),Policy.parse(policy),publicKeys(),log);
        cloud.put(id,ciphertext); return ciphertext;
    }
    public Decryption decrypt(String id, String gid) throws AccessDenied {
        if(!cloud.containsKey(id) || !users.containsKey(gid)) throw new IllegalArgumentException("Hãy chọn hồ sơ và người dùng.");
        log.accept("Decrypt: "+gid+" yêu cầu hồ sơ "+id+"."); return scheme.decrypt(cloud.get(id),users.get(gid),log);
    }
    public void loadSample() {
        if(!authorities.isEmpty() || !users.isEmpty() || !cloud.isEmpty()) throw new IllegalArgumentException("Dữ liệu mẫu chỉ nạp vào phiên trống.");
        addAuthority("HOSPITAL",List.of(a("HOSPITAL","Profession","Doctor"),a("HOSPITAL","Profession","Nurse"),a("HOSPITAL","Affiliation","AIIMS"),a("HOSPITAL","Affiliation","OtherHospital")));
        addAuthority("MEDICAL",List.of(a("MEDICAL","Specialization","Cardiology"),a("MEDICAL","Specialization","Neurology")));
        addAuthority("RESEARCH",List.of(a("RESEARCH","Role","Researcher"),a("RESEARCH","Role","Assistant"),a("RESEARCH","Project","HeartStudy")));
        addAuthority("INSURANCE",List.of(a("INSURANCE","Role","Agent"),a("INSURANCE","Company","HealthCare")));
        addAuthority("OWNER",List.of(a("OWNER","Relation","Friend"),a("OWNER","Relation","Family")));
        addUser("doctor-an",List.of(a("HOSPITAL","Profession","Doctor"),a("HOSPITAL","Affiliation","AIIMS"),a("MEDICAL","Specialization","Cardiology")));
        addUser("doctor-binh",List.of(a("HOSPITAL","Profession","Doctor"),a("HOSPITAL","Affiliation","AIIMS"),a("MEDICAL","Specialization","Neurology")));
        addUser("nurse-lan",List.of(a("HOSPITAL","Profession","Nurse"),a("HOSPITAL","Affiliation","AIIMS"),a("MEDICAL","Specialization","Cardiology")));
        addUser("researcher-minh",List.of(a("RESEARCH","Role","Researcher"),a("RESEARCH","Project","HeartStudy")));
        addUser("friend-ha",List.of(a("OWNER","Relation","Friend")));
        register("outsider");
        addUser("colluder-a",List.of(a("HOSPITAL","Profession","Doctor"),a("HOSPITAL","Affiliation","AIIMS")));
        addUser("colluder-b",List.of(a("MEDICAL","Specialization","Cardiology")));
        encrypt("EHR-001",DEFAULT_EHR,DEFAULT_POLICY);
        log.accept("Đã nạp mẫu: 5 AA, 8 người dùng, 1 hồ sơ. OWNER đại diện miền đóng; các AA khác thuộc miền mở.");
    }
    private void addUser(String gid,List<Attribute> attrs) { register(gid);issue(gid,attrs); }
    private static Attribute a(String aa,String name,String value) { return Attribute.of(aa,name,value); }
    /** Deliberately bypasses normal GID validation to test the algebra against mixed-origin keys. */
    public static UserKeys unsafePoolForExperiment(UserKeys first, UserKeys second) {
        Map<String,AuthorityKeys> merged=new LinkedHashMap<>(first.authorities());
        for(var e:second.authorities().entrySet()) {
            AuthorityKeys old=merged.get(e.getKey());
            if(old==null) merged.put(e.getKey(),e.getValue());
            else {
                Map<Attribute,CompositeGroup.Element> attrs=new LinkedHashMap<>(old.k3());
                e.getValue().k3().forEach(attrs::putIfAbsent);
                merged.put(e.getKey(),new AuthorityKeys(old.authority(),old.issuedTo(),old.k2(),attrs));
            }
        }
        return new UserKeys(first.identity(),merged);
    }
    public String collusionExperiment(String id, String first, String second) {
        if(!cloud.containsKey(id) || !users.containsKey(first) || !users.containsKey(second)) throw new IllegalArgumentException("Hãy chọn hồ sơ và hai người dùng.");
        if(first.equals(second)) throw new IllegalArgumentException("Hãy chọn hai GID khác nhau.");
        Ciphertext ct=cloud.get(id);
        boolean firstWorks=canDecrypt(ct,users.get(first)), secondWorks=canDecrypt(ct,users.get(second));
        StringBuilder result=new StringBuilder("Thử ghép khóa "+first+" + "+second+"\n");
        result.append("Truy cập riêng: ").append(first).append(firstWorks?" = được phép":" = bị từ chối").append(", ")
                .append(second).append(secondWorks?" = được phép":" = bị từ chối").append(".\n");
        boolean pooled=false;
        for(UserKeys k:List.of(unsafePoolForExperiment(users.get(first),users.get(second)),unsafePoolForExperiment(users.get(second),users.get(first)))) {
            try { scheme.decrypt(ct,k,log); pooled=true; } catch(AccessDenied ignored) { }
        }
        if(firstWorks || secondWorks) result.append("Ít nhất một người đã được phép truy cập riêng; trường hợp này không kiểm chứng thông đồng.");
        else if(!pooled) result.append("Ghép theo cả hai K1 đều bị từ chối: các phần r_gid khác nhau không khử được trong (12)-(14); tag GCM không hợp lệ.");
        else result.append("Ghép khóa giải mã thành công trong mô hình. Cần kiểm tra lại tình huống.");
        result.append("\nĐây là kiểm thử thực thi JPBC, không phải chứng minh an toàn mật mã.");
        log.accept(result.toString()); return result.toString();
    }
    private boolean canDecrypt(Ciphertext ct, UserKeys keys) {
        try { scheme.decrypt(ct,keys,x->{}); return true; } catch(AccessDenied e) { return false; }
    }
}
