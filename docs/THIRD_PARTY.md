# Thư viện bên thứ ba

Phiên bản 2 dùng JPBC 2.0.0 để thực nghiệm tương thích cấu hình bài báo. JAR giữ riêng trong `lib/`, không sửa nội dung.

## Nguồn và giấy phép

- Repository: [emilianobonassi/jpbc](https://github.com/emilianobonassi/jpbc).
- Commit của bản đã tải: [6c0a389ba5712534798765bc679a5d4b40df517c](https://github.com/emilianobonassi/jpbc/tree/6c0a389ba5712534798765bc679a5d4b40df517c).
- Ghi nhận nguồn/danh sách file: [../lib/provenance.json](../lib/provenance.json).
- SHA-256 sáu JAR tải từ repository: [../lib/SHA256SUMS](../lib/SHA256SUMS).
- JPBC dùng **GNU LGPL v3**, bản kèm tại [../lib/JPBC-LGPL-3.0.txt](../lib/JPBC-LGPL-3.0.txt); mã nguồn tương ứng tại commit trên. Giữ giấy phép và nguồn tương ứng khi phân phối lại thư viện.

| Thư viện | Sử dụng |
|---|---|
| `jpbc-api-2.0.0.jar` | API nhóm và pairing |
| `jpbc-plaf-2.0.0.jar` | Type A1, sinh tham số và pairing Java |
| `jpbc-crypto-2.0.0.jar` | Bộ ký BLS01 |
| `bcprov-jdk16-1.46.jar` | API mật mã/digest tương thích JPBC BLS01 |
| `jpbc-pbc-2.0.0.jar`, `jna-3.2.5.jar` | Có trong bộ JAR nguồn; backend hiện tại không dùng PBC native/JNA |

Bouncy Castle 1.46 là phiên bản cũ đi cùng bộ JPBC. Giữ nó để thực nghiệm, không khuyến nghị cho phần mềm sản xuất. AES-GCM/HKDF của lớp EHR gọi API JDK; BLS01 gọi JPBC/Bouncy Castle.

Bản quyền/giấy phép Bouncy Castle được trích trực tiếp từ `org.bouncycastle.LICENSE` trong JAR tại [../lib/BOUNCY-CASTLE-LICENSE.txt](../lib/BOUNCY-CASTLE-LICENSE.txt).

`tools/Fetch-Jpbc.ps1` tải thư viện và ghi provenance/checksum. Script lấy commit đầu nhánh tại thời điểm chạy; trước thực nghiệm mới cần kiểm tra thay đổi `provenance.json`, không mặc định mọi lần tải dùng cùng commit.

## Maven và kiểm thử

`pom.xml` lấy JPBC/BC từ kho file `lib/maven/`. `tools/Run-Maven.ps1` đăng ký chúng và đặt kho Maven làm việc trong `build/maven-repository/`. Maven tải plugin cùng **JUnit 4.13.2**/Hamcrest 1.3 khi cần; script sao chép JAR kiểm thử về `lib/` cho build trực tiếp. Chúng không thuộc sáu checksum JPBC ở trên; giấy phép JUnit/Hamcrest nằm trong JAR tương ứng.

Cache `build/private/` chứa phân tích N, không phải dữ liệu thư viện công khai: không đưa vào ZIP phân phối hoặc JSON cloud. Public BLS parameters không chứa `n0/n1/n2`. Việc có thư viện thật không tạo thành chứng nhận an toàn cho ứng dụng.
