# Kiểm tra phiên bản 2.0.2

Ngày cập nhật: 19/10/2026. Môi trường: Windows 11 amd64, Oracle JDK 21.0.12, Maven 3.10.0. Thực thi JPBC 2.0.0 Type A1 bằng Java và chữ ký BLS01/SHA-256; không dùng backend số mũ.

Bản 2.0.2 bổ sung tiến độ benchmark: mặc định 5 điểm × 10 lượt đo, cộng 5 warm-up, tổng 55 lượt. Giao diện hiển thị điểm `/5`, thuộc tính, lượt, bước CAKeyGen/AAKeyGen/Encrypt/Decrypt và số lượt đo/warm-up đã xong; bảng và biểu đồ cập nhật sau mỗi điểm. Tác vụ kết thúc với thông báo hoàn tất 5/5. Hủy giữ những điểm đã hoàn tất để xem; chỉ xuất bộ CSV đầy đủ khi đủ 5 điểm và không còn tác vụ chạy. Các callback tiến độ nằm ngoài vùng bấm giờ; không thay thuật toán hoặc dữ liệu thực nghiệm 517 bit đã lưu.

Các kiểm tra xác nhận bản 2.0.2 đã đạt:

| Kiểm tra 2.0.2 | Kết quả |
|---|---|
| Maven `verify -Ppaper-tests` | 11 bài JUnit đạt; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS phiên bản 2.0.2 |
| `BenchmarkProgressTest` | 2 bài đạt: benchmark hữu hạn, tiến độ tăng đúng thứ tự và dừng khi hủy, dùng tham số 64 bit |
| `UiBenchmarkProgressTest` | 1 bài đạt: chạy mặc định 10 lượt × 5 điểm ở 64 bit, đủ 55 lượt; điểm đầu hiện khi worker còn chạy; điểm 2/5 được hiển thị; một lần hoàn tất với đủ 5 dòng và 50/50 lượt đo |
| Các bài JUnit hiện có | 60 kiểm tra hành vi mật mã, 3 bài LSSS, 3 bài vòng đời UI và 1 bài cấu hình 517 bit đều đạt trong lần Maven trên |
| `build.ps1 -RenderUi` | GUI smoke toàn bộ luồng thao tác và worker nền đạt |

Kết quả xác nhận nằm trong `output/benchmark-progress-verification.log` và `output/maven-verification.log`. Các kiểm tra này không chạy lại 50 lượt 517 bit. Bộ 517 bit lưu trước đây bắt đầu lúc 12:59:07 UTC và ghi xong lúc 13:24:56 UTC ngày 18/10/2026, khoảng 25 phút 48 giây; con số này giải thích thời gian chờ và không phải cam kết thời lượng cho lần chạy sau.

Ảnh `output/screenshots/10-benchmark-progress.png` ghi lại lần chạy thực tế 64 bit với 10 lượt/điểm: điểm đầu đã xuất hiện trong bảng khi worker còn chạy ở điểm 2/5. Ảnh `output/screenshots/11-benchmark-completed.png` ghi lại trạng thái hoàn tất 5/5 và 50/50 lượt đo. Hai ảnh này xác nhận giao diện tiến độ, không thay thế số liệu thực nghiệm 517 bit.

Bảng sau ghi lại các kiểm tra đã đạt ở bản 2.0.1 ngày 18/10/2026, trước thay đổi tiến độ benchmark:

| Kiểm tra | Kết quả |
|---|---|
| Maven `verify -Ppaper-tests` | 8 bài kiểm thử JUnit đạt; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS phiên bản 2.0.1 |
| `build.ps1 -Test -RenderUi` | 7 bài JUnit đạt, GUI smoke và kiểm tra worker nền đạt |
| Bộ kiểm tra hành vi `SelfTest`, gọi trong JUnit | 60/60 kiểm tra đạt, dùng pairing thật với tham số nhỏ để chạy nhanh |
| Kiểm tra cấu hình bài báo | Xác nhận ba số nguyên tố đều 517 bit; BLS; Encrypt/Recover K đạt |
| Demo CLI ở 3 × 517 bit | 8/8 kết quả cấp/từ chối đúng kỳ vọng; ghép hai GID thất bại |
| Bộ benchmark Figure-3 profile đã đo | 5 điểm × 10 lượt = 50 lượt; tất cả khôi phục đúng K; giữ nguyên dữ liệu, không đo lại khi sửa giao diện |
| Quy mô chính sách / tập thỏa | 5/10/15/20/25 và 3/5/7/10/12 |
| Kiểm tra CSV độc lập bằng Python chuẩn | Đủ 50 lượt, đúng mốc trục, số pairing; trung bình và SD khớp dữ liệu thô |
| GUI Swing component | Demo, mã hóa, cấp/từ chối truy cập, ghép khóa, benchmark và biểu đồ đạt |
| Worker nền của GUI | Khởi tạo JPBC và đăng ký BLS chạy ngoài EDT; trạng thái người dùng cập nhật đúng |
| Khởi động mặc định 517 bit | Chưa tạo phiên mật mã; Nạp dữ liệu mẫu/Phiên trống, xem DNF và nạp CSV dùng được ngay |
| `UiLifecycleTest` | 3 bài đạt: khởi động tức thì; hủy setup 517 bit giữ CSV, chặn worker thứ hai; lỗi worker trả lại quyền thao tác |
| `VisibleUiClickTest` trên desktop thật | 3 tình huống đạt bằng AWT Robot: bấm tab/DNF; mở hộp chọn CSV modal và nạp 5 dòng đã đo; bấm khởi tạo/hủy ở 517 bit khi EDT vẫn đáp ứng |
| Hủy khởi tạo | Chờ thread hiện tại thoát, không công bố phiên đã hủy; CSV/nút nạp CSV còn dùng được |
| Chuyển người dùng/yêu cầu mới | Plaintext của kết quả trước được xóa |
| Khóa CA giả/chữ ký sửa/GID hoặc K1 sửa | AA từ chối cấp khóa |
| EHR hoặc header ciphertext bị sửa | AES-GCM từ chối xác thực |
| Public BLS parameters | Không có n0/n1/n2, không xuất phân tích N |

Bằng chứng của các kiểm tra trước:

- `output/maven-verification.log`: log Maven/JUnit và kiểm tra 517 bit; được cập nhật khi kiểm tra bản mới.
- `output/ui-lock-fix-verification.log`: 7 bài JUnit, GUI smoke/render và worker nền sau sửa vòng đời tác vụ.
- `output/visible-ui-click-verification.log`: 3 tình huống bấm chuột trên cửa sổ thật bằng AWT Robot.
- `output/screenshots/08-visible-startup.png`: cửa sổ mặc định 517 bit trước khi tạo phiên.
- `output/screenshots/09-visible-cancelled.png`: cửa sổ sau khi hủy khởi tạo; CSV đã nạp được giữ.
- `output/paper-demo-run.txt`: demo chạy với 517 bit.
- `output/paper-figure3-517.csv`: số đo tổng hợp.
- `output/paper-figure3-517.csv.raw.csv`: 50 lượt đo riêng.
- `output/paper-figure3-517.csv.metadata.json`: cấu hình và môi trường.
- `output/paper-figure3-517.csv.png`: ba biểu đồ theo số đo trên máy này.

Chạy kiểm tra lại:

```powershell
pwsh -NoProfile -File tools/Run-Maven.ps1 -Goal verify -PaperTests
pwsh -NoProfile -File build.ps1 -Test -RenderUi
python tools/verify_experiment.py
```

Kiểm tra chuột trên cửa sổ thật cần desktop tương tác; sau khi build test classes có thể chạy riêng:

```powershell
java '-Djava.awt.headless=false' -cp 'build/classes;build/test-classes;lib/*' vn.healthshare.VisibleUiClickTest
```

Test này bấm các nút bằng AWT Robot và xác nhận hộp chọn CSV thật là modal; tên file được chọn qua API Swing. Nó dùng `output/paper-figure3-517.csv` đã đo, không chạy lại benchmark. `UiLifecycleTest` dùng CSV fixture tổng hợp riêng cho hồi quy parser/giao diện; fixture đó không phải số liệu thực nghiệm và không nằm trong gói phân phối.

Ảnh `01`–`07` trong `output/screenshots/` dùng profile kiểm tra 64 bit, có nhãn trên giao diện; không dùng số liệu đó thay bộ 517 bit. Ảnh `08`–`09` chụp cửa sổ mặc định 517 bit, kiểm tra mở nhanh và hủy setup; không biểu thị một phiên 517 bit đã khởi tạo xong. Biểu đồ thực nghiệm 517 bit nằm ở đường dẫn riêng nêu trên. Kiểm tra GUI gồm cả render headless lẫn bấm chuột trên desktop thật.

Các kiểm tra xác minh cài đặt và phép đo, không thay thế chứng minh an toàn hay kiểm toán phần mềm. Chính sách cụ thể của tác giả chưa được công bố và chưa có toàn văn đối chứng [26], nên chưa tái lập đầy đủ Figure 3 với hai thuật toán. Xem `docs/ALGORITHM.md` để biết giả định tải đo.
