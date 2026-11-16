# BeatSync Cut v2.2 (with Project History & Native Android Export)

Mobile-first beat-synced video editor with Instagram Edits-style center-locked playhead timeline, multi-aspect ratio support (**9:16**, **16:9**, **4:5**, **1:1**), **Auto-Save Project History**, **Native Android Filesystem Export (`@capacitor/filesystem`)**, direct video span sliding, title-based audio search, Film LUTs, Beat FX, and AI Auto Captions (including **ADVERCASE**).

## Fitur Penyimpanan & Proyek
- **Auto-Save & Project History:** Setiap perubahan otomatis tersimpan ke memori aplikasi. Ketuk ikon menu kiri atas untuk membuka riwayat proyek, membuat draf baru, mengganti nama, menduplikasi versi (`9:16` vs `16:9`), atau memuat draf lama.
- **Native Save APK (`@capacitor/filesystem`):** Saat tombol **Export** ditekan di dalam APK Android, video langsung disimpan ke folder **`Download/BeatSync-Cut/`** (atau **`Documents/BeatSync-Cut/`**) di penyimpanan internal HP.

## Cara Build APK Otomatis di GitHub
1. Extract seluruh isi ZIP ini lalu upload ke repository GitHub Anda (branch `main`).
2. Buka tab **Actions** pada repository GitHub.
3. Workflow **Build BeatSync Cut v2.2 APK** akan berjalan otomatis (~2-3 menit).
4. Setelah centang hijau, unduh **`BeatSync-Cut-v2.2-APK`** di bagian **Artifacts** paling bawah.
