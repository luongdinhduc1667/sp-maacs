# Kết quả kiểm tra

Kiểm tra ngày 18/10/2026, trên Windows, Oracle JDK 21.0.12. Chương trình dùng backend mô phỏng số mũ, không cần thư viện ngoài hay truy cập mạng.

| Hạng mục | Kết quả |
|---|---|
| Biên dịch Java 21 và đóng gói executable JAR | Thành công |
| Kiểm thử lõi `SelfTest` | 59/59 kiểm tra đạt |
| Demo 8 người dùng trên EHR-001 | 8/8 kết quả đúng kỳ vọng |
| Ghép khóa từ hai GID khác AA, cả hai K1 | Bị từ chối bởi công thức khôi phục khóa và tag GCM |
| Ghép khóa từ hai GID trong cùng AA | Bị từ chối |
| Sửa chữ ký CA, GID, K1; chứng nhận từ CA giả | AA từ chối cấp khóa |
| Sửa ciphertext EHR hoặc một nhánh DNF không dùng | GCM từ chối xác thực |
| Người dùng có nhiều giá trị cùng tên | Thử các khóa ứng viên, giải mã bằng tổ hợp hợp lệ |
| Kiểm thử GUI không mở cửa sổ | Demo, mã hóa, cấp/từ chối truy cập, ghép khóa, benchmark và biểu đồ đạt |
| Hiển thị plaintext cũ khi đổi GID | Plaintext được xóa khi đổi lựa chọn hoặc bắt đầu yêu cầu mới |
| Benchmark CLI | Đo 5 điểm, 10 lượt/điểm, xuất CSV thành công |
| JSON cloud | Parse bằng JSON parser độc lập; 3 nhánh, 6 dòng; cấu trúc dòng không có trường giá trị thuộc tính |
| Build qua Windows PowerShell 5 | Thành công |

Các ảnh GUI được render từ component Swing thật vào `output/screenshots/`. Benchmark GUI chạy trong `SwingWorker`, không dùng luồng sự kiện để đo. Đây là kiểm tra component và luồng ứng dụng; chưa thực hiện kiểm thử tương tác chuột/bàn phím trên cửa sổ desktop đang mở.

Kiểm tra lại bằng:

```powershell
pwsh -NoProfile -File ./build.ps1 -Test -RenderUi
java -jar build/sp-maacs-simulator.jar --demo
java -jar build/sp-maacs-simulator.jar --benchmark 10 output/benchmark.csv
```

Giới hạn: không xác minh mức an toàn mật mã của ABE bằng mô hình số mũ; không coi thời gian của mô phỏng là số liệu JPBC Type A1; không kiểm thử backend hay thuật toán của ba bài tham khảo vì chúng không được triển khai. Maven và môi trường Linux/macOS không thuộc các môi trường đã kiểm tra.
