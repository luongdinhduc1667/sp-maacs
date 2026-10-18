# Tài liệu chính, bài liên quan và đối chứng

Thuật toán của phần mềm là **SP-MAACS trong tài liệu số 3**. Ba PDF tham khảo ban đầu và PDF Liu và cộng sự mới bổ sung giúp đối chiếu các thiết kế đa cơ quan; chúng không tự trở thành đường đối chứng của benchmark.

| File | Bài báo | Vai trò |
|---|---|---|
| `3. Privacy preserving for healthcare data sharing.pdf` | Gupta và cộng sự, *Secured and Privacy-Preserving Multi-Authority Access Control System for Cloud-Based Healthcare Data Sharing*, Sensors 2023, 23, 2617 | Thuật toán chính: Mục 5, (1)–(14); Mục 6.3/Figure 3 cho cấu hình Type A1, ba số nguyên tố 517 bit, BLS, 5 AA × 5 thuộc tính và 10 lượt đo. |
| `maabe_rw.pdf` | Yannis Rouselakis, Brent Waters, *Efficient Statically-Secure Large-Universe Multi-Authority Attribute-Based Encryption* | Tham khảo large-universe, nhóm bậc nguyên tố, phi tập trung; chưa triển khai thành backend đối chứng. |
| `maabe_yj14.pdf` | Kan Yang, Xiaohua Jia, *Expressive, Efficient, and Revocable Data Access Control for Multi-Authority Cloud Storage* | Tham khảo chính sách biểu đạt, thu hồi bằng cập nhật khóa/ciphertext; chưa triển khai các cơ chế này. |
| `dac_macs.pdf` | Kan Yang, Xiaohua Jia, Kui Ren, Bo Zhang, *DAC-MACS: Effective Data Access Control for Multi-Authority Cloud Storage Systems*, IEEE INFOCOM 2013 | Tham khảo hỗ trợ giải mã bằng token cloud, UKeyGen/SKUpdate/CTUpdate; chưa triển khai outsourcing/cập nhật ciphertext. |
| `978-3-642-23822-2_16.pdf` | Zhen Liu, Zhenfu Cao, Qiong Huang, Duncan S. Wong, Tsz Hon Yuen, *Fully Secure Multi-authority Ciphertext-Policy Attribute-Based Encryption without Random Oracles*, ESORICS 2011 | Là tài liệu **[15]** của bài chính, DOI [10.1007/978-3-642-23822-2_16](https://doi.org/10.1007/978-3-642-23822-2_16); không phải đối chứng Ma–Ge–Zhang [26]. |

## Đối chứng [26]

Thực nghiệm bài chính dùng **Ma–Ge–Zhang, “Fully Secure Decentralized Ciphertext-Policy Attribute-Based Encryption in Standard Model” [26]**, DOI [10.1007/978-3-030-14234-6_23](https://doi.org/10.1007/978-3-030-14234-6_23). Bài này khác PDF Liu và cộng sự năm 2011 vừa bổ sung.

Người dùng cung cấp [trang ResearchGate của [26]](https://www.researchgate.net/publication/331225592_Fully_Secure_Decentralized_Ciphertext-Policy_Attribute-Based_Encryption_in_Standard_Model). Trang hiện có yêu cầu toàn văn; bộ tài liệu làm việc chưa có PDF hoặc các công thức xây dựng gốc đủ để triển khai và kiểm tra đối chứng. Tên bài, abstract hoặc bảng chi phí phép toán không đủ để suy ra cài đặt đúng.

Người dùng đã chọn **giữ Ma–Ge–Zhang [26]** và sẽ tìm đúng PDF. Vì vậy phần mềm chỉ đo **SP-MAACS chạy thật trên JPBC**, chưa có đường đối chứng [26]; không thay nó bằng Liu và cộng sự [15]. Khi có toàn văn cần ánh xạ riêng Setup/KeyGen/Encrypt/Decrypt, kiểm tra tính đúng và đo cùng máy, tham số, phạm vi đồng hồ trước khi so sánh. Không tạo số liệu đối chứng từ giả định.

## Đọc kết quả hiện tại

Giữ hai dãy trục Figure 3: chính sách **5/10/15/20/25** và cấp khóa/giải mã **3/5/7/10/12**. Chính sách gốc chưa rõ nên chọn hai nhánh DNF độc lập có tổng n dòng. Có thêm 1 warm-up trước 10 lượt đo mỗi điểm; tách ABE khỏi AES/HKDF/JSON. Vì các khác biệt đã nêu, không gọi kết quả là tái lập chính xác Figure 3.

Pairing Type A1 và BLS thật đã thay mô hình số mũ/Ed25519 của bản trước. Điều đó không thay kiểm toán hay chuyển nguyên chứng minh an toàn trong bài sang toàn bộ ứng dụng. Các vai trò vẫn trong một tiến trình, chưa có thu hồi hoặc cloud qua mạng.

SP-MAACS ẩn **giá trị** trong ciphertext; tên AA/thuộc tính, DNF và W vẫn công khai. Thu hồi của Yang/Jia hay DAC-MACS là thuật toán cập nhật riêng; xóa thuộc tính trên giao diện không thay được cơ chế này.
