# Ánh xạ SP-MAACS sang triển khai JPBC

Nguồn chính: `3. Privacy preserving for healthcare data sharing.pdf`, Gupta và cộng sự, *Sensors* 2023, 23, 2617, DOI [10.3390/s23052617](https://doi.org/10.3390/s23052617). Triển khai theo Mục 5, (1)–(14); cấu hình thực nghiệm theo Mục 6.3 và các trục Figure 3.

## 1. GlobalSetup và nhóm bậc hợp số

`CompositeGroup` dùng **JPBC 2.0.0 Type A1** trên đường cong elliptic, N=p1·p2·p3. Cấu hình bài báo dùng `TypeA1CurveGenerator(3, 517)`; constructor kiểm tra ba số nguyên tố phân biệt, mỗi số 517 bit và tích N.

`ElementUtils.getGenerator` tạo generator các nhóm con. g thuộc Gp1, thành phần làm mù khóa thuộc Gp3. Pairing thật có tính song tuyến tính và trực giao giữa các nhóm con khác nhau. Đối tượng `Element` chứa phần tử bất biến của JPBC; `bytes()` là mã hóa đường cong/trường mở rộng, không phải tọa độ số mũ của bản trước.

Cache `build/private/type-a1-3x<bits>.properties` chứa `n0/n1/n2`, tức phân tích N. Cache không được xuất lên cloud hoặc đưa vào gói phân phối. `publicPairingParameters()` chỉ sao chép `type/p/n/l` cho BLS; JSON cloud không xuất các thừa số.

## 2. Setup CA/AA và cấp khóa

| Thuật toán | Mã thực thi | Hành vi |
|---|---|---|
| GlobalSetup, §5.1 | `CompositeGroup` | Tạo G, GT, N và các nhóm con bằng Type A1 |
| CASetup, §5.1 | `CentralAuthority`, `BlsSignature` | Sinh cặp khóa BLS01; digest SHA-256 |
| AASetup, (1)–(4) | `AttributeAuthority` | Chọn αk, βk, t cho từng cặp tên/giá trị; công bố e(g,g)^αk, g^βk, g^t |
| CAKeyGen, §5.3 | `CentralAuthority.register` | K1=g^r_gid·R_gid; ký mã hóa chuẩn tắc của GID và K1 |
| AAKeyGen, (9)–(11) | `AttributeAuthority.issue` | Xác minh BLS; K2,k=g^αk·K1^βk·R, K3,k,a=K1^t·R |

`BlsSignature` gọi **BLS01 của JPBC** với Bouncy Castle `SHA256Digest`, không tự dựng ánh xạ digest thành số mũ. Dùng backend Java JPBC, không bật PBC native. Họ chữ ký phù hợp mô tả bài; digest và encoding chứng nhận là lựa chọn cụ thể của chương trình do chưa có mã nguồn cấu hình gốc.

Mỗi AA có bí mật riêng, chỉ cấp thuộc tính thuộc danh mục của mình sau khi xác minh CA. Người vận hành đóng vai AA phê duyệt; chứng nhận không thay việc kiểm tra nghề nghiệp ngoài đời.

## 3. DNF và LSSS

`Policy.parse` đổi AND/OR đơn điệu thành OR của các nhánh AND. Mỗi literal gồm AA, tên, giá trị; ciphertext `Row` chỉ giữ nhãn `AA.Name`. Tên AA, số nhánh/dòng và W vẫn công khai: cơ chế ẩn giá trị không ẩn toàn bộ chính sách.

Một nhánh AND ba dòng được dựng:

```text
W = [1  1  0]       v = (s,v2,v3)^T
    [0 -1  1]       λ = (s+v2,-v2+v3,-v3)^T
    [0  0 -1]       λ1+λ2+λ3=s
```

`Lsss.reconstruct` giải W_I^T·μ=(1,0,...,0) trên Z_N bằng khử Gauss, chọn pivot khả nghịch rồi xác minh nghiệm. Với ma trận AND được sinh, μx đều bằng 1, nhưng Decrypt tính và dùng vector μ. Solver có thể từ chối hệ cần xử lý pivot không khả nghịch; chưa phải bộ giải đầy đủ cho mọi ma trận trên Z_N. Giao diện vẫn nhận DNF, chưa có ngưỡng k-of-n hay nhập W tùy ý.

Giới hạn biên dịch: 32 nhánh, 64 dòng/nhánh, 256 dòng tổng, 64 mức ngoặc, 16000 ký tự; loại nhánh trùng hoặc bị hấp thụ.

## 4. Encrypt, công thức (5)–(8)

`encapsulate` nhận K thuộc GT,p1, chính sách và khóa **công khai** các AA. Mỗi nhánh chọn s, v, rx mới; SAA chỉ gồm các AA phân biệt trong nhánh:

```text
λx   = Wx · v mod N
C    = K · (product_k e(g,g)^αk)^s                 (5)
C0   = g^s                                       (6)
C1,x = (product_k g^βk)^λx · T_(name,value)^(-rx)  (7)
C2,x = g^rx                                      (8)
```

Mỗi AA chỉ xuất hiện một lần trong các tích αk/βk dù quản lý nhiều dòng. T=g^t lấy từ danh mục công khai của đúng giá trị đã chọn; không sinh t mới lúc mã hóa. Mọi nhánh bọc cùng K.

`encrypt` bọc ABE bằng lớp mã hóa EHR: chọn K, dẫn xuất khóa AES-256 qua HKDF-SHA256 với salt 32 byte, rồi AES-GCM với IV 12 byte/tag 128 bit. AAD xác thực toàn bộ header, gồm backend, EHR ID, N, salt/IV và mọi nhánh, tên, W, C. Đây là lựa chọn bổ sung cho bước mã hóa đối xứng trong bài, không tính vào benchmark ABE.

## 5. Decrypt, công thức (12)–(14)

Với một nhánh và bộ khóa ứng viên:

```text
CK          = product_k K2,k
numerator   = e(CK,C0)                                      (12)
denominator = product_x [e(C1,x,K1) · e(C2,x,K3,x)]^μx        (13)
K'          = C / (numerator / denominator)                  (14)
```

`recoverCandidate` dùng một khóa K3 cho mỗi tên trong nhánh đã chọn, giải μ rồi khôi phục K'; không so sánh giá trị thuộc tính plaintext. Benchmark so sánh K'=K sau khi dừng đồng hồ.

Luồng EHR `decrypt` thử nhánh ít dòng trước, bỏ nhánh thiếu AA/tên, thử các khóa cùng tên khi có nhiều giá trị, dùng HKDF/GCM xác nhận K'. Tối đa 4096 tổ hợp; tag sai không trả plaintext một phần. Thao tác GUI vì vậy có phạm vi khác ABE Decrypt của benchmark.

Với khóa đúng giá trị và cùng r_gid, denominator=e(g,g)^(r_gid·s·Σβk); khử trong numerator để lại e(g,g)^(s·Σαk). Khóa sai hoặc khác GID thường không khử, K' sai và GCM từ chối.

## 6. Ghép khóa và giới hạn bảo mật

`Simulation.unsafePoolForExperiment` cố ý ghép khóa hai GID, bỏ kiểm tra GID ở tầng quản lý. Decrypt không dùng so sánh GID làm lối tắt từ chối; kết quả đến từ khôi phục K/GCM.

Mẫu `colluder-a` có Doctor/AIIMS, `colluder-b` có Cardiology; mỗi người thiếu một phần nhánh bác sĩ. Thí nghiệm thử cả hai K1 khi ghép. Các phép thử hành vi không thay thế chứng minh an toàn thích nghi hoặc bao quát mọi đối thủ/kênh rò rỉ. JPBC/Bouncy Castle cũ được giữ cho nghiên cứu, xem [THIRD_PARTY.md](THIRD_PARTY.md).

## 7. Phạm vi thực nghiệm

Có 5 AA × 5 thuộc tính. Các điểm **5/10/15/20/25 thuộc tính chính sách** tương ứng **3/5/7/10/12 thuộc tính cấp khóa/giải mã**, lấy từ trục Figure 3. Danh mục sắp cố định theo AA; chọn hai nhánh AND độc lập, tổng n dòng, nhánh đầu là tập thỏa. Cấu trúc này là lựa chọn công khai của triển khai vì chưa có chính sách/mã nguồn thực nghiệm gốc.

Mỗi điểm có 1 warm-up, mặc định 10 lượt đo, GID/khóa mới mỗi lượt:

- **CAKeyGen:** tính K1 và ký BLS.
- **AAKeyGen:** xác minh BLS tại các AA cần thiết, cấp đúng tập thỏa. Tổng KeyGen=CAKeyGen+AAKeyGen; báo riêng hai phần.
- **ABE Encrypt:** `encapsulate`, gồm LSSS và (5)–(8); chọn K trước đồng hồ.
- **ABE Decrypt:** `recoverCandidate` trên nhánh thỏa đã chọn; gồm tìm khóa theo tên, tính μ và (12)–(14). So sánh K sau đồng hồ.

Setup nhóm/CA/AA, parse policy, AES, HKDF, JSON và xuất biểu đồ không nằm trong các thời gian ABE. Cột `abe_element_bytes` là tổng byte phần tử ABE, không phải kích thước JSON/toàn hồ sơ.

Dùng `System.nanoTime`, lưu từng lượt và metadata máy; báo trung bình và độ lệch chuẩn tổng thể Encrypt/Decrypt. JIT, GC, lịch CPU và cache có thể ảnh hưởng số đo; đây chưa phải JMH. CSV đã đo có thể nạp vào GUI hoặc dựng lại PNG bằng `--plot`; nạp CSV tổng hợp không tự tạo dữ liệu thô.

**Chưa tái lập chính xác Figure 3** do chưa có chính sách/mã gốc và toàn văn đối chứng Ma–Ge–Zhang [26]. Không tạo số liệu thay thế cho đối chứng; không so sánh trực tiếp thời gian trên máy khác như cùng điều kiện.
