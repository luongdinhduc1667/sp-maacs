# Vai trò của bốn tài liệu trong thư mục

Ứng dụng triển khai **mô phỏng SP-MAACS của tài liệu số 3**. Ba file còn lại là tài liệu tham khảo để phân biệt các thiết kế đa cơ quan, khả năng biểu đạt chính sách, giải mã và thu hồi thuộc tính. Không lấy công thức mã hóa của bài này trộn vào thuật toán của bài khác rồi gọi là SP-MAACS.

| File | Bài báo | Điểm tham khảo | Vai trò trong phần mềm |
|---|---|---|---|
| `3. Privacy preserving for healthcare data sharing.pdf` | Gupta và cộng sự, *Secured and Privacy-Preserving Multi-Authority Access Control System for Cloud-Based Healthcare Data Sharing*, Sensors 2023, 23, 2617 | SP-MAACS; miền mở và đóng; CA ký định danh; nhiều AA; chính sách ẩn giá trị; DNF/LSSS; nhóm bậc hợp số | **Thuật toán chính**, Mục 5, công thức (1)–(14); quy mô benchmark tham khảo Mục 6.3 |
| `maabe_rw.pdf` | Yannis Rouselakis, Brent Waters, *Efficient Statically-Secure Large-Universe Multi-Authority Attribute-Based Encryption* | Thiết kế large-universe, phi tập trung không có CA cấp khóa; nhóm bậc nguyên tố; chính sách đơn điệu; bảo mật tĩnh trong random oracle model | Đối chiếu việc quản lý thuộc tính quy mô lớn và khác biệt với CA/chữ ký trong SP-MAACS; không triển khai thuật toán này |
| `maabe_yj14.pdf` | Kan Yang, Xiaohua Jia, *Expressive, Efficient, and Revocable Data Access Control for Multi-Authority Cloud Storage* | Quản lý nhiều AA; chính sách biểu đạt; thu hồi thuộc tính với forward/backward security; cơ chế cập nhật khóa/ciphertext | Giải thích thu hồi thuộc tính là một cơ chế riêng, không đồng nhất với xóa thuộc tính khỏi danh sách trên giao diện |
| `dac_macs.pdf` | Kan Yang, Xiaohua Jia, Kui Ren, Bo Zhang, *DAC-MACS: Effective Data Access Control for Multi-Authority Cloud Storage Systems*, IEEE INFOCOM 2013 | Giải mã bằng token với hỗ trợ cloud; UKeyGen, SKUpdate, CTUpdate; thu hồi thuộc tính | Đối chiếu outsourcing và cập nhật ciphertext; chương trình không giả lập những cơ chế này dưới tên SP-MAACS |

## Những điểm cần giữ đúng khi mô phỏng

1. **Đa cơ quan:** khóa của HOSPITAL, MEDICAL và RESEARCH phải có bí mật riêng. Không dùng một bảng boolean để thay phép cấp khóa của AA.
2. **Ràng buộc người dùng:** khóa định danh CA và khóa AA dùng chung r_gid của một người. Ghép khóa từ hai GID không tương đương hợp các thuộc tính hợp lệ của một GID.
3. **Ẩn một phần chính sách:** SP-MAACS công khai tên, cấu trúc và ma trận; giá trị được dùng trong thành phần ciphertext. Không tuyên bố ẩn cả số nhánh, số dòng, tên AA hoặc tên thuộc tính.
4. **Thu hồi:** bài Yang/Jia và DAC-MACS có thuật toán cập nhật riêng. SP-MAACS chính không định nghĩa cơ chế đó. Phiên bản này không có nút “thu hồi” chỉ xóa bản ghi nhưng để khóa cũ tiếp tục giải mã.
5. **Hiệu năng:** chỉ tạo số đo cho mã đang chạy. Không suy ra tốc độ của Rouselakis–Waters, Yang–Jia hay DAC-MACS từ backend số mũ của SP-MAACS.

## Giới hạn diễn giải

Bài chính dùng DNF ở phần xây dựng, dù bảng so sánh đặc điểm có dùng thuật ngữ LSSS cho họ chính sách. Phần mềm theo **phần xây dựng cụ thể**: mỗi nhánh DNF là một ma trận AND; các nhánh là các cách khôi phục cùng K. Không triển khai LSSS tùy ý hay ngưỡng tổng quát.

Mức an toàn của bài báo phụ thuộc nhóm, giả thiết và mô hình đối thủ. Backend số mũ phục vụ giảng giải và kiểm tra đồng nhất thức nên không kế thừa chứng minh an toàn đó.
