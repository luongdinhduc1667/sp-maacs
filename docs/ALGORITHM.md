# Ánh xạ thuật toán SP-MAACS sang chương trình

Nguồn: `3. Privacy preserving for healthcare data sharing.pdf`, Gupta và cộng sự, *Sensors* 2023, 23, 2617, DOI 10.3390/s23052617. Dùng Mục 3–5, đặc biệt trang 11–12; quy mô thực nghiệm tham khảo Mục 6.3, trang 15–17.

## 1. Nhóm song tuyến tính bậc hợp số

Bài sử dụng N=p1·p2·p3, nhóm G và GT, g thuộc Gp1 và các phần tử ngẫu nhiên thuộc Gp3 để làm mù khóa. Backend `CompositeGroup` biểu diễn mỗi phần tử bằng bộ ba số mũ modulo p1, p2, p3:

```text
u = (u1,u2,u3), v = (v1,v2,v3)
u * v  = (u1+v1,u2+v2,u3+v3)
u / v  = (u1-v1,u2-v2,u3-v3)
u ^ z  = (z*u1,z*u2,z*u3)
e(u,v) = (u1*v1,u2*v2,u3*v3) thuộc GT
```

Mỗi tọa độ được giảm theo số nguyên tố tương ứng. Nhờ đó e(g^a,g^b)=e(g,g)^(ab); pairing giữa Gp1 và Gp3 là đơn vị. Sự khử các thành phần R thuộc Gp3 xảy ra khi tính pairing với ciphertext thuộc Gp1.

Đây là mô hình trực tiếp của đại số, **không phải pairing trên đường cong elliptic**. Tọa độ số mũ và tham số nội bộ được biết, nên giả thiết khó về discrete logarithm và ẩn nhóm con không tồn tại. Việc JSON không chứa chuỗi giá trị thuộc tính không đem lại bảo đảm riêng tư mật mã với backend này.

## 2. Setup và cấp khóa

| Thủ tục / công thức | Mã thực thi | Hành vi |
|---|---|---|
| GlobalSetup, §5.1 | `CompositeGroup(int)` | Tạo 3 số nguyên tố phân biệt, N, G, GT; phép pairing trực giao |
| CASetup, §5.1 | `SpMaacs.CentralAuthority` | Tạo cặp chữ ký Ed25519; phân phối khóa xác minh |
| AASetup, (1)–(4) | `SpMaacs.AttributeAuthority` | Chọn αk, βk, t cho từng cặp tên/giá trị; công bố e(g,g)^αk, g^βk, g^t |
| CAKeyGen, §5.3 | `CentralAuthority.register` | K1=g^r_gid·R_gid; ký gid và K1; GID duy nhất |
| AAKeyGen, (9)–(11) | `AttributeAuthority.issue` | Xác minh chứng nhận CA; tính K2,k=g^αk·K1^βk·R và K3,k,a=K1^t·R |

Danh mục thuộc tính được AA công bố khi setup. Người vận hành mô phỏng đóng vai AA để phê duyệt thuộc tính cho GID; chứng nhận CA không thay thế kiểm tra nghề nghiệp ngoài đời. Lệnh cấp khóa chỉ chấp nhận thuộc tính đã công bố, đúng namespace AA.

Ed25519 là lựa chọn cụ thể cho giao diện chữ ký `Sign/Verify` tổng quát ở Mục 5.1. Thực nghiệm bài báo dùng BLS; thời gian và cấu trúc chữ ký do đó khác.

## 3. Chính sách DNF và LSSS

`Policy.parse` phân tích công thức đơn điệu và đổi thành OR của các phép AND. Ví dụ:

```text
A.X=1 AND (B.Y=2 OR C.Z=3)
  => (A.X=1 AND B.Y=2) OR (A.X=1 AND C.Z=3)
```

Mỗi literal có AA, tên thuộc tính và giá trị. Ciphertext chỉ lưu AA và tên; đối tượng `Row` không có trường giá trị. Chương trình không gắn bản chính sách đầy đủ vào đối tượng cloud. Bộ biên dịch chặn số nhánh/dòng để tránh nổ kích thước khi phân phối AND qua OR.

Với nhánh AND l dòng, `Policy.andMatrix(l)` dựng W vuông dạng nối tiếp. Ví dụ l=3:

```text
W = [1  1  0]
    [0 -1  1]
    [0  0 -1]

v = (s,v2,v3)^T
λ = (s+v2, -v2+v3, -v3)^T
λ1+λ2+λ3=s
```

Hệ số μx=1 cho mọi dòng. Tổng các dòng W=(1,0,...,0), nên toàn bộ literal của nhánh AND là cần thiết. Ma trận được tính thực sự trong Encrypt; Decrypt dùng tính chất tái tạo cố định này. Chương trình không nhận ma trận LSSS tùy ý để giải tổng quát.

Namespace `AA.Name` phân biệt thuộc tính trùng tên do các AA khác nhau phát hành. Người dùng có thể sở hữu nhiều giá trị của cùng một tên.

## 4. Encrypt, công thức (5)–(8)

`SpMaacs.encrypt` chỉ nhận bản đồ **khóa công khai** của các AA. Chọn một K ngẫu nhiên trong GT,p1 cho toàn bộ EHR. Với mỗi nhánh, chọn s, vector v và rx độc lập; SAA là tập AA phân biệt xuất hiện **trong nhánh đó**:

```text
λx   = Wx · v mod N
C    = K * (product_k e(g,g)^αk)^s                 (5)
C0   = g^s                                       (6)
C1,x = (product_k g^βk)^λx * T_(name,value)^(-rx)  (7)
C2,x = g^rx                                      (8)
```

Mỗi AA chỉ được nhân một lần trong các tích αk/βk dù có nhiều dòng của AA đó. Không tạo t mới theo từng lần mã hóa: t và T thuộc setup AA; Encrypt dùng T công khai của đúng giá trị do chủ hồ sơ chọn.

K là phần tử nhóm GT, không phải chuỗi 32 byte được ép trực tiếp vào nhóm. HKDF-SHA256 với salt ngẫu nhiên 32 byte dẫn xuất khóa AES 256 bit từ biểu diễn K. AES-GCM dùng IV ngẫu nhiên 12 byte và tag 128 bit. Mọi lần mã hóa có K, salt và IV mới.

AAD là mã hóa nhị phân chuẩn tắc của phiên bản, backend, mã EHR, N, salt, IV, mọi nhánh, AA, tên thuộc tính, W, C, C0, C1 và C2. Vì vậy thay đổi một nhánh không dùng đến cũng làm xác thực thất bại. Đó là phần bổ sung của chương trình cho bước mã hóa đối xứng trong bài.

## 5. Decrypt, công thức (12)–(14)

`SpMaacs.decrypt` xét nhánh ít dòng trước. Không đọc chính sách bí mật hay so sánh giá trị plaintext:

1. Nếu thiếu khóa AA hoặc tên thuộc tính, bỏ qua nhánh.
2. Tính CK=∏k K2,k và numerator=e(CK,C0), công thức (12).
3. Với mỗi dòng, thu thập các khóa K3 có cùng **AA và tên**, tính e(C1,x,K1)·e(C2,x,K3,x). Nếu người dùng có nhiều giá trị, thử tổ hợp các khóa đó.
4. Nhân các kết quả với μx=1 để có denominator, công thức (13).
5. Tính mask=numerator/denominator rồi K'=C/mask, công thức (14).
6. Dẫn xuất khóa từ K', xác thực và giải mã AES-GCM. Tag đúng mới trả EHR; tag sai thì thử tổ hợp hoặc nhánh tiếp theo.

Giới hạn 4096 tổ hợp trên toàn bộ yêu cầu giải mã; nếu vượt, yêu cầu báo rõ cần giảm số giá trị/thuộc tính. Lỗi tag không trả plaintext một phần.

Khi mọi khóa đúng giá trị và cùng r_gid, denominator=e(g,g)^(r_gid·s·Σβk). Phần này khử trong numerator, để lại e(g,g)^(s·Σαk). Khi khóa sai giá trị hoặc khác r_gid, các hạng thường không khử; K' sai và GCM từ chối. Cơ chế tag là cách nhận biết tổ hợp hợp lệ được bổ sung cho việc khớp giá trị ẩn.

## 6. Thử thông đồng

Luồng cấp khóa bình thường ràng buộc mỗi bộ khóa với GID. Riêng `Simulation.unsafePoolForExperiment` cố ý ghép dữ liệu khóa từ hai GID, bỏ qua kiểm tra GID ở tầng ứng dụng. Hàm Decrypt cũng không dùng phép so sánh GID để bác bỏ bộ ghép; việc từ chối đến từ phương trình không khử và xác thực GCM.

Tình huống mẫu:

- colluder-a: HOSPITAL.Profession=Doctor, HOSPITAL.Affiliation=AIIMS.
- colluder-b: MEDICAL.Specialization=Cardiology.
- Từng người không đủ nhánh bác sĩ. Ghép các khóa và thử K1 của từng người đều bị từ chối trong thử nghiệm.

Kiểm thử còn bao gồm ghép hai K3 khác GID trong cùng một AA. Nếu một người đã đủ quyền độc lập, giao diện ghi rõ tình huống đó không kiểm chứng thông đồng.

Các phép thử này kiểm tra công thức được cài đặt, không chứng minh an toàn trước đối thủ biết số mũ; đối thủ đó có thể phá backend mô phỏng.

## 7. Hiệu năng và giới hạn

Quy mô 5 AA × 5 tên và 10 lượt đo lấy theo §6.3. Phiên benchmark dùng 2 nhánh DNF rời nhau, tổng n=5,10,15,20,25 dòng. Người dùng được cấp ceil(n/2) thuộc tính của nhánh đầu. Cả 5 AA được tạo cho mỗi điểm; chỉ các AA của nhánh tham gia vào tích khóa của nhánh đó.

Thời gian đo gồm:

- Cấp khóa: CAKeyGen và AAKeyGen cho tập thỏa, gồm ký và xác minh Ed25519; không gồm setup CA/AA.
- Mã hóa: chia sẻ LSSS, tạo ciphertext số mũ, HKDF, mã hóa AES-GCM và tạo AAD; không gồm parse policy hay xuất JSON.
- Giải mã: tìm nhánh, pairing mô phỏng, khôi phục K, HKDF và xác thực AES-GCM.

Có 3 lượt warm-up trước mỗi điểm, các lần đo tạo GID mới. Số liệu dùng `System.nanoTime`, báo trung bình và độ lệch chuẩn tổng thể. Cần đọc SD vì JIT, GC, lập lịch và thứ tự chạy gây dao động; đây không phải harness JMH. Cột pairing phản ánh số lời gọi backend, không phải thời gian pairing elliptic curve.

Ứng dụng không tái lập Figure 3 hay triển khai thuật toán [26] mà bài chính dùng làm đối chứng. Cũng không thay thế JPBC, BLS, chứng minh dual-system, phân quyền vận hành hoặc hệ thống cloud thật. Các phần mở rộng này cần một triển khai và đánh giá riêng.
