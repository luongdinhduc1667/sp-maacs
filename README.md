# SP-MAACS — Mô phỏng chia sẻ hồ sơ y tế bằng Java

Ứng dụng desktop Java 21, giao diện tiếng Việt, mô phỏng thuật toán trong **“Secured and Privacy-Preserving Multi-Authority Access Control System for Cloud-Based Healthcare Data Sharing”**, Gupta và cộng sự, *Sensors* 2023, 23, 2617. Tài liệu chính là file `3. Privacy preserving for healthcare data sharing.pdf` có sẵn trong thư mục.

Phần mềm tính các thành phần khóa và ciphertext theo Mục 5, công thức (1)–(14), dùng chính sách DNF và ma trận LSSS cho từng nhánh. Các bài còn lại được đối chiếu trong [docs/RELATED_WORK.md](docs/RELATED_WORK.md).

**Phạm vi:** đây là mô phỏng đại số, không phải triển khai ABE an toàn mật mã. Backend biểu diễn phần tử bằng số mũ nên làm lộ discrete logarithm; không dùng để bảo vệ hồ sơ thật. AES-256-GCM, HKDF-SHA256 và chữ ký Ed25519 được thực thi bằng thư viện chuẩn Java. Không cần tải thư viện ngoài để chạy.

## Chạy nhanh trên Windows

Máy cần **JDK 21 trở lên**. Trong thư mục dự án:

```powershell
./run.bat
```

File này build nếu chưa có JAR rồi mở giao diện. Hoặc chạy bản đã build:

```powershell
java -jar build/sp-maacs-simulator.jar
```

Build lại sau khi sửa mã, chạy kiểm thử và tạo ảnh giao diện:

```powershell
pwsh -NoProfile -File ./build.ps1 -Test -RenderUi
```

Nếu chỉ có Windows PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./build.ps1 -Test -RenderUi
```

Build không dùng Maven, không cần mạng. `pom.xml` cung cấp cấu trúc dự án để nhập vào IntelliJ IDEA / Eclipse; kiểm thử hành vi của dự án được chạy bằng `build.ps1 -Test`, không phụ thuộc JUnit hay Surefire.

## Dùng giao diện

Ứng dụng tự nạp một phiên mẫu gồm **5 cơ quan, 8 người dùng và hồ sơ EHR-001**.

1. **Tổng quan:** nhấn **Chạy 8 tình huống mẫu** để xem kết quả cấp/từ chối truy cập.
2. **Cơ quan & người dùng:** xem khóa đã cấp; đăng ký GID mới tại CA; tạo AA và danh mục thuộc tính; chọn người dùng và các thuộc tính rồi cấp khóa. Thao tác này đại diện việc AA phê duyệt trong mô phỏng, không phải người đọc tự xác nhận nghề nghiệp.
3. **Mã hóa hồ sơ:** nhập EHR giả, mã hồ sơ mới và chính sách; xem cấu trúc DNF rồi mã hóa. Mỗi mã hồ sơ duy nhất trong phiên.
4. **Truy cập & thông đồng:** chọn GID và hồ sơ rồi giải mã. Thử `colluder-a` + `colluder-b` trên EHR-001: từng người không đủ quyền và ghép khóa với cả hai lựa chọn K1 đều thất bại.
5. **Cloud công khai:** xem/xuất JSON chỉ chứa tên thuộc tính, W, các thành phần ciphertext và EHR mã hóa; không xuất giá trị thuộc tính ở dạng văn bản, khóa người dùng hay EHR gốc.
6. **Hiệu năng:** đo thời gian cấp khóa, mã hóa và giải mã với 5, 10, 15, 20, 25 thuộc tính; mặc định 10 lượt/điểm; xem biểu đồ và xuất CSV.
7. **Nhật ký thuật toán:** xem trình tự GlobalSetup, CASetup, AASetup, CAKeyGen, AAKeyGen, Encrypt và Decrypt.

**Phiên trống** tạo môi trường mới để tự khai báo. **Nạp lại dữ liệu mẫu** tạo lại ví dụ ban đầu. Khóa và hồ sơ chỉ ở trong bộ nhớ, bị xóa khi đóng ứng dụng hoặc tạo phiên mới. JSON xuất ra dành cho kiểm tra và báo cáo; phiên bản này không nhập lại JSON hay lưu/khôi phục khóa riêng.

## Chính sách mẫu

```text
(HOSPITAL.Profession=Doctor
 AND MEDICAL.Specialization=Cardiology
 AND HOSPITAL.Affiliation=AIIMS)
OR (RESEARCH.Role=Researcher AND RESEARCH.Project=HeartStudy)
OR OWNER.Relation=Friend
```

Cloud chỉ hiển thị:

```text
(HOSPITAL.Profession=* AND MEDICAL.Specialization=* AND HOSPITAL.Affiliation=*)
OR (RESEARCH.Role=* AND RESEARCH.Project=*)
OR (OWNER.Relation=*)
```

`HOSPITAL`, `MEDICAL`, `RESEARCH`, `INSURANCE` là các AA thuộc miền mở. `OWNER` là chủ hồ sơ đóng vai AA trong miền đóng. `INSURANCE` có trong danh mục nhưng không được cấp quyền theo chính sách mẫu này.

Tên AA, tên thuộc tính và giá trị phân biệt hoa/thường. Hỗ trợ `AND`, `OR`, `&&`, `||`, `&`, `|`, dấu ngoặc; AND ưu tiên hơn OR. Giá trị chứa khoảng trắng dùng dấu nháy, ví dụ `OWNER.Relation="Bạn thân"`. Cần khai báo giá trị đó trong danh mục AA trước khi dùng. Không hỗ trợ NOT hay ngưỡng k-of-n.

Giới hạn biên dịch: 32 nhánh DNF, 64 dòng/nhánh, 256 dòng tổng; 64 mức ngoặc, 16000 ký tự. Các nhánh trùng và nhánh bị hấp thụ được loại bỏ. Người dùng có thể có nhiều giá trị cùng tên; giải mã thử khóa tương ứng theo tên và xác nhận bằng tag GCM, tối đa 4096 tổ hợp.

| Người dùng | Kết quả trên EHR-001 | Lý do |
|---|---|---|
| doctor-an | Được phép | Doctor + Cardiology + AIIMS |
| doctor-binh | Từ chối | Specialization=Neurology |
| nurse-lan | Từ chối | Profession=Nurse |
| researcher-minh | Được phép | Researcher + HeartStudy |
| friend-ha | Được phép | Miền đóng: Relation=Friend |
| outsider | Từ chối | Chưa được cấp thuộc tính |
| colluder-a | Từ chối | Thiếu khóa MEDICAL |
| colluder-b | Từ chối | Thiếu khóa HOSPITAL |

## Chạy bằng dòng lệnh

```powershell
java -jar build/sp-maacs-simulator.jar --demo
java -jar build/sp-maacs-simulator.jar --benchmark 10 output/benchmark.csv
java -jar build/sp-maacs-simulator.jar --help
```

`--demo [thư_mục_xuất]` kiểm tra 8 trường hợp, thử ghép khóa và xuất ciphertext công khai. Nếu kết quả cấp/từ chối không đúng kỳ vọng, chương trình trả mã lỗi.

`--benchmark [số_lượt] [đường_dẫn.csv]` chạy cùng phép đo như giao diện. Mỗi điểm có 3 lượt warm-up, sau đó các lượt đo với định danh và khóa mới. Setup CA/AA không tính vào thời gian đo. Chính sách gồm hai nhánh độc lập, tổng n dòng; người dùng chỉ có tập thỏa gồm ceil(n/2) thuộc tính. CSV chứa số lượt, trung bình, độ lệch chuẩn tổng thể, số pairing và kích thước JSON UTF-8.

**Thời gian benchmark thuộc backend số mũ, gồm chi phí AES/Ed25519 khi tương ứng; không thể dùng thay số đo JPBC Type A1 hoặc so sánh tốc độ với các bài báo.** Ba thuật toán liên quan không được triển khai thành backend benchmark, vì vậy không có số liệu so sánh giả lập cho chúng.

## Cấu trúc mã nguồn

```text
src/main/java/vn/healthshare/
  Main.java                      Giao diện hoặc CLI
  math/CompositeGroup.java       G, GT, 3 nhóm con, pairing theo số mũ
  policy/Policy.java             Parser, DNF, LSSS cho AND
  crypto/SpMaacs.java             Công thức (1)-(14), CA/AA, AES-GCM, HKDF
  sim/Simulation.java            Vai trò, dữ liệu mẫu, cloud, ghép khóa
  sim/Benchmark.java             Đo thời gian, thống kê, CSV
  ui/SimulatorFrame.java         Cửa sổ desktop
  ui/SimulatorPanel.java         Các màn hình tương tác, biểu đồ
src/test/java/vn/healthshare/
  SelfTest.java                  Kiểm thử hành vi và đại số
  UiSmokeTest.java               Thử luồng GUI và render ảnh
docs/ALGORITHM.md                Ánh xạ công thức sang mã và sai khác có chủ đích
docs/RELATED_WORK.md             Đối chiếu 4 tài liệu trong thư mục
```

Kiểm thử bao gồm tính song tuyến tính, trực giao nhóm con, tái tạo LSSS, phép đổi DNF, cấp/từ chối truy cập, giá trị sai, đa giá trị, giả chứng nhận, sửa EHR/header, ghép khóa cùng/khác AA và benchmark round trip. Kiểm thử GUI kiểm tra xóa plaintext cũ khi chuyển người dùng để tránh hiển thị lại kết quả của người được phép.

## Khác biệt với thực nghiệm trong bài

- Bài báo sử dụng JPBC Type A1, ba số nguyên tố 517 bit và BLS. Ứng dụng này dùng mô hình số mũ, mặc định ba số nguyên tố 128 bit và Ed25519 để có thể chạy hoàn toàn bằng JDK. Số bit này là kích thước mô phỏng, không phải mức an toàn mật mã.
- Ma trận LSSS được dựng riêng cho từng phép AND trong DNF, không phải bộ giải LSSS tổng quát. Mọi hệ số tái tạo bằng 1 với cấu trúc ma trận đã chọn.
- Bổ sung HKDF, AES-GCM và AAD để kiểm tra kết quả khôi phục khóa và phát hiện chỉnh sửa. Bài mô tả bước mã hóa đối xứng nhưng không ấn định các lựa chọn này.
- Không triển khai thu hồi thuộc tính, outsourcing giải mã, cloud qua mạng, cơ sở dữ liệu, đăng nhập người dùng hay hệ thống bệnh án thật. Các vai trò nằm trong một tiến trình mô phỏng.
- Kiểm thử thông đồng chỉ xác minh các tình huống trên mô hình; không chuyển chứng minh an toàn thích nghi của bài báo sang backend số mũ.

Xem [docs/ALGORITHM.md](docs/ALGORITHM.md) để theo dõi từng công thức và [docs/RELATED_WORK.md](docs/RELATED_WORK.md) để phân biệt các thuật toán liên quan.
