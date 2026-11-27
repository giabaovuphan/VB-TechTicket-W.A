# HCN Support (Web)

Ứng dụng web nội bộ quản lý phiếu hỗ trợ kỹ thuật, điều phối sửa chữa, hóa đơn linh kiện, tồn kho vật tư và báo cáo thống kê cho **Hương Cao Nguyên** — đồng bộ dữ liệu thời gian thực qua **Firebase Firestore & Google Sign-In** dùng chung với phiên bản Android.

## Tính năng chính

- **Giao diện thích ứng Desktop & Mobile**: Sidebar điều hướng bên trái trên màn hình Desktop và thanh tab cố định phía dưới trên Mobile; hỗ trợ chuyển đổi giao diện **Sáng / Tối** (mặc định Sáng).
- **Xác thực & phân quyền (RBAC)**: Đăng nhập bằng Google Workspace/Gmail (`signInWithPopup`), tự động đồng bộ vai trò (`PENDING`, `USER`, `TECHNICIAN`, `TEAM_LEAD`, `ADMIN`) và trạng thái phê duyệt theo `firestore.rules`.
- **Quy trình Ticket 4 giai đoạn**: `Mở` -> `Đã tiếp nhận` -> `Đang xử lý` -> `Đã hoàn thành`, kèm ảnh chụp lỗi ban đầu và ảnh nghiệm thu sau sửa chữa (tự động nén JPEG dưới 300KB).
- **Hóa đơn & Kho linh kiện**: Lập hóa đơn từ kho tồn, bảng giá hoặc hạng mục ngoài danh mục; giao dịch nguyên tử (`runTransaction`) tự động trừ tồn kho và bảo vệ quy tắc theo vai trò.
- **Thông báo & Báo cáo**: Thông báo thời gian thực theo từng tài khoản, thống kê 7/14/28 ngày hoặc toàn bộ, bảng xếp hạng kỹ thuật viên và xuất báo cáo văn bản / CSV (UTF-8 BOM).

## Lệnh phát triển

```bash
npm install
npm run dev      # Chạy Vite dev server tại http://0.0.0.0:3000
npm run lint     # Kiểm tra TypeScript & ESLint
npm run build    # Build bản production vào dist/
npm test         # Chạy unit test logic nghiệp vụ
```
