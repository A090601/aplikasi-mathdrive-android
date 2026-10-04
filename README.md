# Math Drive Android

Konsep inti:
- Endless runner kendaraan dengan jalan bergerak dan rintangan visual.
- Level 1 sampai 10.
- Setiap level berisi 10 soal perkalian sesuai nomor level (level 1 = x1, ... level 10 = x10).
- Setiap soal memiliki batas waktu 5 detik.
- Jawaban benar lanjut ke soal berikutnya.
- Jawaban salah atau waktu habis mengurangi 1 dari 3 nyawa.
- Nyawa habis = Game Over.
- Setelah 10 soal selesai, pemain mencapai garis finish dan level berikutnya terbuka.
- Menu utama, pemilihan kendaraan, pengaturan, pause, hasil level, dan game over.
- Maskot asli jawaban benar/salah berada di res/drawable.
- Area iklan sudah disiapkan sebagai placeholder; integrasi AdMob asli membutuhkan App ID/ad unit milik developer.

## Build
1. Buka folder ini di Android Studio.
2. Tunggu Gradle sync.
3. Pilih Build > Build APK(s).
4. Integrasi AdMob dilakukan setelah memasukkan konfigurasi resmi AdMob.


Gameplay rule update: nyawa hanya berkurang ketika jawaban yang dipilih salah. Jika waktu 5 detik habis, soal dilewati tanpa mengurangi nyawa.

## UI and audio update
- Removed reference-collage overlays from functional screens to avoid the appearance of a static shadow/image panel.
- Added functional environment decoration rendered in gameplay.
- Fixed Settings > KEMBALI touch target to match its visible button.
- Added sound feedback using Android ToneGenerator for selections and answers.
- Added a lightweight generated background melody using AudioTrack; the Musik switch starts/stops it and persists the setting.
- Original monkey assets remain unchanged.


Aturan pembukaan level: level berikutnya terbuka ketika pemain mencapai minimal 6 jawaban benar (skor minimal 60 / 1 bintang). Pembukaan disimpan segera, termasuk jika pemain kemudian Game Over.


## Aturan pembukaan level terbaru
- Skor 60–100 (minimal 1 bintang) dapat membuka level berikutnya.
- Skor 0–50 (0 bintang) tidak dapat membuka level berikutnya.
- Pada halaman GAME OVER, tombol LEVEL BERIKUTNYA muncul jika skor minimal 60 dan level belum mencapai 10.

## Distribusi APK dan Google Play Protect

- Project ini tidak menonaktifkan atau melewati Google Play Protect. Android menentukan peringatan instalasi berdasarkan sumber APK, tanda tangan, reputasi, dan pemeriksaan keamanan perangkat.
- Untuk distribusi ke HP orang lain, gunakan **Build > Generate Signed App Bundle / APK** di Android Studio dengan keystore rilis milik pengembang. Jangan membagikan APK debug sebagai versi final.
- Untuk mengurangi peringatan saat distribusi luas, publikasikan melalui Google Play Console/Play App Signing atau bagikan APK rilis bertanda tangan secara resmi. Tidak ada perubahan kode yang dapat menjamin Play Protect tidak menampilkan peringatan pada semua perangkat.
- Tombol Back Android sekarang mengikuti alur halaman: Level -> Materi Pembelajaran, Materi -> Menu, Kendaraan/Pengaturan -> Menu, dan hasil permainan/Game Over -> Materi Pembelajaran.


## Branding
- Nama aplikasi: Math Drive
- Ikon launcher: maskot monyet dari aset proyek.
- Untuk membuat APK Release yang ditandatangani, gunakan **Build > Generate Signed App Bundle / APK** di Android Studio.
