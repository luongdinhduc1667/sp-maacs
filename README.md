# SP-MAACS — Java / JPBC Type A1 / BLS

Ứng dụng Java 21, giao diện tiếng Việt, triển khai SP-MAACS trong **“Secured and Privacy-Preserving Multi-Authority Access Control System for Cloud-Based Healthcare Data Sharing”**, Gupta và cộng sự, *Sensors* 2023, 23, 2617. Tài liệu chính là `3. Privacy preserving for healthcare data sharing.pdf` trong thư mục.

Phiên bản **2.0.1** dùng **pairing đường cong elliptic thật của JPBC 2.0.0, Type A1, N=p1·p2·p3 với từng số nguyên tố 517 bit**, và **BLS01/SHA-256** cho chứng nhận CA. Phần tử G/GT lưu biểu diễn của JPBC; backend số mũ và Ed25519 của bản trước đã được thay thế. Đây là triển khai phục vụ thực nghiệm, chưa được kiểm toán để bảo vệ hồ sơ y tế thật.

## Build và chạy trên Windows

Cần **JDK 21+** và Maven. Trong thư mục dự án:

```powershell
pwsh -NoProfile -File .\tools\Run-Maven.ps1 -Goal verify -PaperTests
java -jar target/sp-maacs-simulator-2.0.1.jar
```

Script tìm Maven trong PATH, đăng ký các JAR có trong `lib/` vào kho Maven của dự án rồi chạy JUnit 4.13.2. `-PaperTests` bật kiểm tra bổ sung với ba số nguyên tố 517 bit; bỏ cờ này để chỉ chạy bộ kiểm tra thông thường với tham số nhỏ hơn. Lần đầu Maven có thể cần mạng để tải plugin và thư viện kiểm thử. Kết quả kiểm tra nằm trong `target/surefire-reports/`.

Có thể build trực tiếp bằng JDK với thư viện đã kèm:

```powershell
pwsh -NoProfile -File .\build.ps1
.\run.bat
```

`run.bat` tạo JAR nếu chưa có. Sau khi sửa source, chạy lại `build.ps1` hoặc Maven để cập nhật JAR tương ứng. Giữ `lib/` cạnh `build/` hoặc `target/` khi chuyển sang máy khác.

Giao diện mở ngay ở cấu hình 517 bit, **chưa khởi tạo phiên mật mã**. Nút **Nạp dữ liệu mẫu** và **Phiên trống** dùng được ngay; chỉ khi chọn chúng chương trình mới tạo phiên trên worker nền. **Xem cấu trúc DNF**, **Nạp CSV đã đo** và chuyển tab không cần chờ setup.

Lần tạo đường cong và các phép pairing 517 bit có thể mất nhiều thời gian. Thanh trạng thái hiển thị tác vụ, số giây đã chạy và nút **Hủy tác vụ**. Khi hủy, chương trình chờ phép tính hiện tại kết thúc trước khi cho bắt đầu tác vụ mật mã khác; phiên khởi tạo đã hủy không được công bố. CSV đã nạp vẫn được giữ. Nội dung các tab có thể cuộn để tới nút thao tác trên cửa sổ nhỏ.

Cache setup riêng của máy tại `build/private/type-a1-3x517.properties` chứa phân tích N; không xuất nó lên cloud hoặc đưa vào gói phân phối.

## Thực nghiệm theo quy mô Figure 3

Đã có bộ kết quả để xem tại [output/paper-figure3-517.csv](output/paper-figure3-517.csv). Trong tab **Hiệu năng**, chọn **Nạp CSV đã đo** để xem biểu đồ mà không chạy lại. Có thể tạo PNG từ CSV bằng:

```powershell
java -jar target/sp-maacs-simulator-2.0.1.jar --plot output/paper-figure3-517.csv
```

Để đo lại trên máy hiện tại:

```powershell
java -jar target/sp-maacs-simulator-2.0.1.jar --benchmark 10 output/paper-figure3-517.csv 517
```

| Tham số | Cấu hình |
|---|---|
| Backend | JPBC 2.0.0 Type A1, 3 × 517 bit; BLS01/SHA-256 |
| Cơ quan / danh mục | 5 AA, mỗi AA có 5 thuộc tính |
| Thuộc tính trong chính sách | 5, 10, 15, 20, 25 |
| Thuộc tính cấp khóa và dùng giải mã | 3, 5, 7, 10, 12 tương ứng |
| Mỗi điểm | 1 lượt warm-up, mặc định 10 lượt đo; GID/khóa mới mỗi lượt |
| KeyGen | CAKeyGen + AAKeyGen, có báo riêng từng phần; không gồm setup |
| Encrypt / Decrypt | Chỉ ABE theo (5)–(8) / (12)–(14); loại AES, HKDF, JSON |

Mỗi lượt kiểm tra K khôi phục bằng K gốc sau khi dừng đồng hồ. Ngoài CSV tổng hợp, CLI ghi `*.raw.csv` từng lượt, `*.metadata.json` mô tả môi trường/phạm vi đo và `*.png` gồm ba biểu đồ. Số liệu thô được ghi dần trong khi chạy. CSV có trung bình, độ lệch chuẩn tổng thể Encrypt/Decrypt, số pairing và kích thước các phần tử ABE.

Nút **Xuất CSV + PNG** trong giao diện xuất kết quả đang hiển thị. Khi vừa chạy benchmark, nó xuất thêm raw/metadata; khi chỉ nạp CSV tổng hợp, nó không tự tạo các lượt đo thô chưa có.

Hai dãy trục được lấy từ Figure 3. Chương trình chọn **hai nhánh DNF độc lập, tổng n dòng**, vì chính sách cụ thể và mã nguồn thực nghiệm gốc chưa có trong tài liệu. Đây là thực nghiệm theo cấu hình đã mô tả, **chưa phải tái lập chính xác Figure 3**.

Giữ đối chứng **Ma–Ge–Zhang [26]**, DOI [10.1007/978-3-030-14234-6_23](https://doi.org/10.1007/978-3-030-14234-6_23), theo lựa chọn của người dùng. Chưa có toàn văn đúng bài nên chưa triển khai đường đối chứng. PDF mới `978-3-642-23822-2_16.pdf` là Liu và cộng sự (2011), tài liệu [15], không thay cho [26]. Xem [docs/RELATED_WORK.md](docs/RELATED_WORK.md).

Tham số nhỏ chỉ phục vụ kiểm tra nhanh, không dùng thay kết quả 517 bit:

```powershell
java -jar target/sp-maacs-simulator-2.0.1.jar --demo output/demo-small 64
java -jar target/sp-maacs-simulator-2.0.1.jar --help
```

## Dùng giao diện

Nhấn **Nạp dữ liệu mẫu** để tạo phiên có 5 cơ quan, 8 người dùng và EHR-001; hoặc **Phiên trống** để tự khai báo. Các nút cần khóa/phiên được bật sau khi khởi tạo thành công. Các màn hình cho phép đăng ký GID tại CA, tạo AA, cấp thuộc tính, mã hóa EHR giả, giải mã theo người dùng, thử ghép khóa và xuất ciphertext công khai. Tab **Hiệu năng** dùng cùng phép đo ABE với CLI; có thể xem CSV đã đo ngay từ lúc mở ứng dụng.

```text
(HOSPITAL.Profession=Doctor
 AND MEDICAL.Specialization=Cardiology
 AND HOSPITAL.Affiliation=AIIMS)
OR (RESEARCH.Role=Researcher AND RESEARCH.Project=HeartStudy)
OR OWNER.Relation=Friend
```

Cloud giữ tên `AA.Name`, cấu trúc DNF, W và các phần tử ciphertext; không lưu giá trị thuộc tính ở dạng văn bản, khóa người dùng hoặc EHR gốc. Chính sách hỗ trợ AND/OR và ngoặc, phân biệt hoa/thường; giá trị có khoảng trắng đặt trong dấu nháy. Chưa có NOT hoặc ngưỡng k-of-n. Giới hạn: 32 nhánh, 64 dòng/nhánh, 256 dòng tổng; tối đa 4096 tổ hợp khóa ứng viên khi giải mã đa giá trị.

Các vai trò nằm trong một tiến trình; khóa/hồ sơ chỉ tồn tại trong phiên. JSON dành cho kiểm tra, chưa có nhập lại và khôi phục khóa. Chưa triển khai thu hồi, outsourcing, cloud qua mạng, cơ sở dữ liệu hay đăng nhập bệnh viện. HKDF-SHA256, AES-256-GCM và AAD bổ sung cho bước mã hóa đối xứng trong bài, được tách khỏi phép đo ABE.

## Mã nguồn và tài liệu

| Đường dẫn | Vai trò |
|---|---|
| `src/main/java/vn/healthshare/math/CompositeGroup.java` | Type A1, nhóm con và pairing thật |
| `src/main/java/vn/healthshare/crypto/BlsSignature.java` | BLS01/SHA-256 qua JPBC, Bouncy Castle 1.46 |
| `src/main/java/vn/healthshare/crypto/SpMaacs.java` | CA/AA, công thức (1)–(14), lớp mã hóa EHR |
| `src/main/java/vn/healthshare/policy/` | Parser DNF, ma trận AND và tái tạo LSSS |
| `src/main/java/vn/healthshare/sim/Benchmark.java` | Đo ABE, CSV tổng hợp/thô và metadata |
| [docs/ALGORITHM.md](docs/ALGORITHM.md) | Ánh xạ thuật toán và phạm vi đo |
| [docs/RELATED_WORK.md](docs/RELATED_WORK.md) | Các PDF và phân biệt tài liệu [15] với đối chứng [26] |
| [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md) | Nguồn thư viện, checksum, giấy phép |
| [docs/VALIDATION.md](docs/VALIDATION.md) | Kiểm thử mật mã, vòng đời tác vụ và thao tác chuột trên cửa sổ thật |

Pairing thật loại bỏ hạn chế làm lộ số mũ của bản cũ, nhưng không thay thế chứng minh an toàn cho toàn bộ phần mềm. JPBC/Bouncy Castle cũ được giữ để thực nghiệm tương thích; cấu hình nghiên cứu này không phải khuyến nghị triển khai sản xuất.
