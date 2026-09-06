# WatermarkCam

App Android đơn giản: chụp ảnh nét, tự động đóng dấu giờ/ngày ở góc trái dưới, đổi camera trước/sau, chạm để lấy nét + kéo để chỉnh sáng tối thủ công, và nút flash tự nhớ trạng thái bật/tắt lần trước.

## Tính năng
- Xem trước camera toàn màn hình (CameraX), chụp ở chế độ chất lượng cao nhất.
- **Đổi camera**: nút góc trên trái — chuyển giữa camera sau và camera trước.
- **Flash**: nút góc trên phải (chỉ hoạt động với camera sau). Trạng thái bật/tắt được lưu lại bằng SharedPreferences — mở app lần sau vẫn giữ đúng như lần chụp trước.
- **Lấy nét & phơi sáng thủ công**: chạm vào bất kỳ điểm nào trên màn hình để lấy nét + đo sáng tại điểm đó (có vòng tròn vàng báo hiệu). Giữ chạm rồi kéo lên/xuống để tăng/giảm độ sáng thủ công (EV) — số EV hiện ở trên cùng khi đang kéo.
- **Đóng dấu thời gian**: sau khi chụp, ảnh được đóng dấu giờ (HH:mm) cỡ lớn + gạch cam + ngày (dd/MM/yyyy) + thứ trong tuần, ở góc trái dưới, có sẵn logo công ty phía trên — cùng bố cục ảnh mẫu.
- **Làm đẹp da (chỉ camera trước)**: sau khi chụp bằng camera trước, ảnh tự động được làm mịn da 30% (trộn 30% một bản làm mờ nhẹ lên ảnh gốc) và làm sáng/trắng da 30% (trộn 30% một bản tăng sáng lên trên). Camera sau không bị ảnh hưởng, giữ nguyên ảnh gốc.
- **Xem lại ảnh sau khi chụp**: hiện toàn màn hình ngay sau khi chụp — **vuốt trái/phải để xem các ảnh đã chụp trước đó**, **chụm/kéo hoặc chạm đúp để phóng to thu nhỏ**, nút 🗑 góc trên trái để **xoá ảnh đang xem** (có hỏi xác nhận), nút ✕ góc trên phải để đóng và quay lại camera.
- Ảnh lưu vào thư viện ảnh, thư mục `Pictures/WatermarkCam`.

### Ghi chú về logo & khung "đã xác thực"
Mình **không** sao chép khung "Timemark Verified / 100% Chân thực" trong ảnh mẫu — đó là con dấu xác thực gắn với một dịch vụ khác (kèm mã xác thực), sao chép y hệt sẽ giống như tạo ảnh gắn mác "đã xác thực" giả. Logo công ty ePass thì mình có gắn vào (`app/src/main/res/drawable/logo_watermark.png`) vì đây là logo nơi bạn làm việc, dùng cho ảnh công việc nội bộ của chính bạn. Muốn đổi logo khác, chỉ cần thay file đó bằng file PNG mới cùng tên.

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

   **Quan trọng nếu bạn đã push project này trước đó và giờ cập nhật bản mới:** đừng chỉ giải nén file zip mới đè lên thư mục cũ — hãy **xoá sạch thư mục cũ rồi mới giải nén file zip mới vào**, sau đó dùng `git add -A` (không phải `git add .`) để Git ghi nhận luôn những file đã bị xoá (ví dụ icon cũ), rồi mới commit + push:
   ```bash
   git add -A
   git commit -m "Cập nhật"
   git push
   ```
   Bỏ qua bước xoá thư mục cũ là lý do phổ biến nhất khiến bạn build xong nhưng không thấy thay đổi (file cũ bị bỏ sót vẫn còn nằm trong repo và được ưu tiên dùng).

3. Vào tab **Actions** trên GitHub — workflow "Build APK" sẽ tự chạy.
4. Khi chạy xong (khoảng 3–5 phút), mở lần chạy đó, kéo xuống mục **Artifacts**, tải file `WatermarkCam-debug-apk` về — bên trong là file `.apk` cài trực tiếp lên điện thoại Android (nhớ bật "Cài đặt ứng dụng không rõ nguồn" nếu cài thủ công).
5. Cũng có thể chạy tay bất cứ lúc nào qua **Actions > Build APK > Run workflow**.

Lưu ý: đây là bản build **debug** (không ký release) — cài đặt và test thoải mái. Nếu sau này cần bản APK **release** ký sẵn để phát hành/chia sẻ chính thức, mình có thể hướng dẫn tạo keystore và chỉnh workflow để ký tự động (cần lưu keystore + mật khẩu vào GitHub Secrets, không lưu trực tiếp trong repo).

**Nếu cài APK mới nhưng vẫn thấy icon/app cũ:** trên điện thoại, gỡ hẳn app cũ (Uninstall) rồi mới cài file APK mới, thay vì cài đè — một số máy/launcher giữ cache icon cũ nếu chỉ cài đè lên bản có cùng tên gói.

## Tùy chỉnh thêm
- Đổi vị trí/màu/font chữ đóng dấu: sửa hàm `drawTimestampWatermark()` trong `MainActivity.kt`.
- Đổi độ nhạy kéo để chỉnh sáng tối: đổi số `28f` trong hàm `adjustExposure()` (số nhỏ hơn = kéo nhạy hơn).
- Đổi mức độ làm mịn/làm trắng da: sửa số `77` (= 30% của 255) trong hàm `applyFrontCameraBeauty()` — tăng lên thì hiệu ứng mạnh hơn, giảm xuống thì nhẹ hơn. Có thể chỉnh riêng từng hiệu ứng bằng cách tách thành 2 biến `smoothAlpha` và `whitenAlpha` khác nhau nếu muốn.

### Lưu ý về làm mịn da
Vì app không dùng nhận diện khuôn mặt, hiệu ứng làm mịn là làm mờ nhẹ **toàn bộ ảnh** rồi trộn 30% (không riêng vùng da) — với mức 30% thì chi tiết quan trọng (mắt, tóc, viền) vẫn còn rõ, chỉ da mịn hơn chút. Nếu sau này muốn mịn da chính xác hơn (chừa mắt/mũi/miệng sắc nét), cần thêm nhận diện khuôn mặt (ví dụ ML Kit Face Detection) để chỉ làm mờ đúng vùng da — phức tạp hơn, có thể làm ở bản sau.
