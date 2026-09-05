# WatermarkCam

App Android đơn giản: chụp ảnh nét, tự động đóng dấu giờ/ngày ở góc trái dưới, đổi camera trước/sau, chạm để lấy nét + kéo để chỉnh sáng tối thủ công, và nút flash tự nhớ trạng thái bật/tắt lần trước.

## Tính năng
- Xem trước camera toàn màn hình (CameraX), chụp ở chế độ chất lượng cao nhất.
- **Đổi camera**: nút góc trên trái — chuyển giữa camera sau và camera trước.
- **Flash**: nút góc trên phải (chỉ hoạt động với camera sau). Trạng thái bật/tắt được lưu lại bằng SharedPreferences — mở app lần sau vẫn giữ đúng như lần chụp trước.
- **Lấy nét & phơi sáng thủ công**: chạm vào bất kỳ điểm nào trên màn hình để lấy nét + đo sáng tại điểm đó (có vòng tròn vàng báo hiệu). Giữ chạm rồi kéo lên/xuống để tăng/giảm độ sáng thủ công (EV) — số EV hiện ở trên cùng khi đang kéo.
- **Đóng dấu thời gian**: sau khi chụp, ảnh được đóng dấu giờ (HH:mm) cỡ lớn + gạch cam + ngày (dd/MM/yyyy) + thứ trong tuần, ở góc trái dưới — cùng bố cục với kiểu ảnh chụp có xác thực thời gian.
- **Làm đẹp da (chỉ camera trước)**: sau khi chụp bằng camera trước, ảnh tự động được làm mịn da 30% (trộn 30% một bản làm mờ nhẹ lên ảnh gốc) và làm sáng/trắng da 30% (trộn 30% một bản tăng sáng lên trên). Camera sau không bị ảnh hưởng, giữ nguyên ảnh gốc.
- Ảnh lưu vào thư viện ảnh, thư mục `Pictures/WatermarkCam`.

### Ghi chú về logo & khung "đã xác thực"
Mình **không** sao chép logo "ePass" hay khung "Timemark Verified / 100% Chân thực" trong ảnh mẫu — đó là con dấu xác thực gắn với dịch vụ của bên thứ ba (kèm mã xác thực), sao chép y hệt sẽ giống như tạo ảnh gắn mác "đã xác thực" giả. Phần bố cục còn lại (giờ lớn, gạch cam, ngày/thứ góc trái dưới) đã làm giống ảnh mẫu.

Nếu bạn có **logo công ty của riêng bạn** (file PNG) và muốn nó xuất hiện phía trên khối giờ/ngày, chỉ cần đặt file đó vào `app/src/main/res/drawable/logo_watermark.png` — app sẽ tự động vẽ logo lên ảnh, không cần sửa code.

## Cách mở và chạy bằng Android Studio
1. Cài **Android Studio** (bản mới nhất).
2. **File > Open**, chọn thư mục `WatermarkCam` (chứa file `settings.gradle.kts`).
3. Chờ Android Studio tự tạo Gradle Wrapper và đồng bộ (Sync) — tải thư viện CameraX.
4. Cắm điện thoại (đã bật USB debugging) hoặc dùng máy ảo có camera, bấm **Run ▶**.
5. Cho phép quyền Camera khi được hỏi.

## Build APK tự động bằng GitHub Actions
Project đã có sẵn workflow `.github/workflows/build-apk.yml`, tự build file APK mỗi khi bạn đẩy code lên GitHub — không cần cài Android Studio.

Các bước:
1. Tạo một repository mới trên GitHub (có thể để private).
2. Đẩy toàn bộ nội dung thư mục `WatermarkCam` này lên repo đó, ví dụ:
   ```bash
   cd WatermarkCam
   git init
   git add .
   git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<ten-tai-khoan>/<ten-repo>.git
   git push -u origin main
   ```
3. Vào tab **Actions** trên GitHub — workflow "Build APK" sẽ tự chạy.
4. Khi chạy xong (khoảng 3–5 phút), mở lần chạy đó, kéo xuống mục **Artifacts**, tải file `WatermarkCam-debug-apk` về — bên trong là file `.apk` cài trực tiếp lên điện thoại Android (nhớ bật "Cài đặt ứng dụng không rõ nguồn" nếu cài thủ công).
5. Cũng có thể chạy tay bất cứ lúc nào qua **Actions > Build APK > Run workflow**.

Lưu ý: đây là bản build **debug** (không ký release) — cài đặt và test thoải mái. Nếu sau này cần bản APK **release** ký sẵn để phát hành/chia sẻ chính thức, mình có thể hướng dẫn tạo keystore và chỉnh workflow để ký tự động (cần lưu keystore + mật khẩu vào GitHub Secrets, không lưu trực tiếp trong repo).

## Tùy chỉnh thêm
- Đổi vị trí/màu/font chữ đóng dấu: sửa hàm `drawTimestampWatermark()` trong `MainActivity.kt`.
- Đổi độ nhạy kéo để chỉnh sáng tối: đổi số `28f` trong hàm `adjustExposure()` (số nhỏ hơn = kéo nhạy hơn).
- Đổi mức độ làm mịn/làm trắng da: sửa số `77` (= 30% của 255) trong hàm `applyFrontCameraBeauty()` — tăng lên thì hiệu ứng mạnh hơn, giảm xuống thì nhẹ hơn. Có thể chỉnh riêng từng hiệu ứng bằng cách tách thành 2 biến `smoothAlpha` và `whitenAlpha` khác nhau nếu muốn.

### Lưu ý về làm mịn da
Vì app không dùng nhận diện khuôn mặt, hiệu ứng làm mịn là làm mờ nhẹ **toàn bộ ảnh** rồi trộn 30% (không riêng vùng da) — với mức 30% thì chi tiết quan trọng (mắt, tóc, viền) vẫn còn rõ, chỉ da mịn hơn chút. Nếu sau này muốn mịn da chính xác hơn (chừa mắt/mũi/miệng sắc nét), cần thêm nhận diện khuôn mặt (ví dụ ML Kit Face Detection) để chỉ làm mờ đúng vùng da — phức tạp hơn, có thể làm ở bản sau.
