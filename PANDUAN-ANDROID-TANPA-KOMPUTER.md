# Panduan menjalankan Kling Motion Control hanya dengan HP Android

## Ringkasan
- `android/` adalah aplikasi Android Kotlin.
- `backend/` adalah server Node.js yang menyimpan API Key Kling dan menerima upload.
- `.github/workflows/build-android.yml` membangun APK debug di GitHub Actions.
- ZIP ini adalah source code, bukan APK siap instal.

## Bagian 1 — Siapkan akun dan source code
1. Buat akun GitHub di https://github.com/ jika belum punya.
2. Buka GitHub melalui Chrome Android, aktifkan **Situs desktop** bila tampilan mobile menyulitkan.
3. Buat repository baru bernama `kling-motion-control`. Untuk penggunaan pribadi, pilih **Private**.
4. Ekstrak ZIP di HP menggunakan Files/ZArchiver.
5. Upload seluruh isi folder `kling-motion-control` ke repository, termasuk folder tersembunyi `.github` dan file workflow. Jangan upload file `.env` yang berisi rahasia.
6. Pastikan halaman repository menampilkan folder `android`, `backend`, dan `.github/workflows/build-android.yml`.

## Bagian 2 — Deploy backend ke Render
1. Buat akun di https://render.com/.
2. Di Render pilih **New → Web Service**, lalu hubungkan akun GitHub dan repository `kling-motion-control`.
3. Isi pengaturan:
   - Root Directory: `backend`
   - Runtime: `Node`
   - Build Command: `npm install`
   - Start Command: `npm start`
   - Health Check Path: `/health`
4. Buat service. Setelah selesai, catat alamat HTTPS, misalnya `https://nama-service.onrender.com`.
5. Buka **Environment** pada service, tambahkan:
   - `KLING_API_KEY` = API Key dari Kling API Console
   - `PUBLIC_BASE_URL` = alamat HTTPS service Render, tanpa garis miring di akhir
   - `ACCESS_TOKEN` = buat kata rahasia panjang dan acak (contoh: minimal 32 karakter); simpan untuk dimasukkan juga ke konfigurasi Android.
6. Simpan perubahan dan tunggu service selesai deploy.
7. Buka `https://nama-service.onrender.com/health`. Respons yang diharapkan: `{"ok":true,"service":"kling-motion-backend"}`.

Catatan: Render Free dapat mengalami cold start dan penyimpanan lokalnya bersifat sementara. File upload di backend starter ini disimpan di disk lokal, jadi jangan menganggapnya sebagai arsip permanen. Untuk pemakaian lebih andal, gunakan penyimpanan objek persisten dan layanan hosting yang sesuai. Backend memakai token akses sederhana untuk membatasi pemanggilan API. Token yang ditanam di APK masih bisa diekstrak oleh orang yang membongkar APK; ini hanya penghalang dasar untuk pemakaian pribadi, bukan keamanan setara login server. Jangan bagikan APK atau URL backend. Tambahkan rate limit dan autentikasi lebih kuat sebelum penggunaan publik.

## Bagian 3 — Atur alamat backend pada aplikasi
1. Di GitHub buka `android/app/build.gradle.kts`.
2. Tekan Edit.
3. Cari `API_BASE_URL`, lalu ganti alamat contoh dengan alamat Render Anda dan tetap akhiri dengan `/`.
4. Cari `API_ACCESS_TOKEN`, lalu ganti nilai contoh dengan token yang sama persis seperti `ACCESS_TOKEN` di Render.
5. Simpan perubahan (Commit changes).

Contoh: `https://nama-service.onrender.com/`

## Bagian 4 — Bangun APK melalui GitHub Actions
1. Buka repository GitHub.
2. Pilih tab **Actions**.
3. Pilih workflow **Build Kling Motion Android APK**.
4. Tekan **Run workflow → Run workflow**.
5. Tunggu hingga job berstatus hijau (Success).
6. Buka hasil workflow tersebut, gulir ke **Artifacts**, lalu unduh `kling-motion-debug-apk`.
7. Ekstrak ZIP artifact. Di dalamnya ada `app-debug.apk`.
8. Ketuk APK untuk memasang. Jika Android meminta izin, izinkan Chrome/File Manager memasang aplikasi dari sumber tersebut.

## Bagian 5 — Uji aplikasi
1. Pastikan backend aktif dengan membuka `/health`.
2. Jalankan aplikasi Kling Motion di HP.
3. Pilih foto JPG/JPEG/PNG dan video referensi MP4/MOV.
4. Tekan Generate Motion.
5. Tunggu status task sampai selesai, lalu preview dan download video.

## Keamanan dan biaya
- Jangan pernah memasukkan API Key Kling ke source Android, GitHub, chat, atau file yang diunggah ke repository.
- API Key hanya disimpan pada Environment Variables backend Render.
- `ACCESS_TOKEN` harus sama di Render dan `API_ACCESS_TOKEN` pada konfigurasi Android. Token ini berbeda dari API Key Kling.
- GitHub repository sebaiknya Private.
- Motion Control menggunakan saldo/kuota API Kling sesuai ketentuan akun. Periksa biaya sebelum Generate.
- Starter backend ini dibuat untuk uji pribadi, bukan deployment publik tanpa pengamanan tambahan.

## Troubleshooting singkat
- Workflow gagal: buka job yang merah dan periksa log; pastikan folder `android` terunggah lengkap.
- Aplikasi tidak tersambung: periksa `API_BASE_URL`, wajib HTTPS dan diakhiri `/`.
- Upload gagal: periksa backend `/health`, `PUBLIC_BASE_URL`, dan batas ukuran/format file.
- Kling menolak permintaan: periksa API Key, saldo/akses API, serta persyaratan foto dan video pada dokumentasi resmi.

Dokumentasi Motion Control: https://kling.ai/document-api/api/video/motion-control
Dokumentasi autentikasi Kling: https://kling.ai/document-api/apiReference/commonInfo
