# BeatSync Cut — Auto Beat-Sync Video Editor (Android)

Aplikasi Android untuk mengedit kumpulan video secara otomatis mengikuti **hentakan/beat lagu** (maksimal durasi 60 detik), lengkap dengan **Audio Span Range Slider**, dukungan **link YouTube / Spotify / MP3 Lokal**, preset **Color Grading Filter**, dan efek **Beat Punch**.

## Fitur Utama (v1.0 Lengkap)
- **On-Device Beat Detection Engine:** Menggunakan `MediaCodec` + Low-Pass Bass Filter (~140 Hz) + Adaptive Peak Detection untuk mendeteksi ketukan bass/kick drum dan memotong video tepat pada titik hentakan.
- **3 Mode Kerapatan Beat:** *Chill* (tiap 4 beat), *Dynamic* (tiap 2 beat), dan *Hype* (tiap hentakan kuat).
- **Audio Source & Span Selector:**
  - Tempel link **YouTube** atau **Spotify** (via oEmbed metadata bridge) atau pilih file **MP3/M4A lokal**.
  - Geser *Range Slider* untuk memilih bagian lagu mana pun (maksimal 60 detik) dengan tombol preview audio langsung.
- **Color Grading & Beat Punch:** 6 preset filter warna (Original, Teal & Orange, Warm Vintage, Moody Dark, Vivid Pop, B&W Contrast) dengan slider intensitas + opsi zoom mikro 106% saat pergantian beat.
- **1080p Hardware Export:** Dirender via AndroidX Media3 Transformer langsung ke folder `Movies/BeatSync/` di galeri HP.

## Cara Build Jadi `.apk` Otomatis di GitHub
1. Buat repository baru di GitHub (misal: `beatsync-editor`).
2. Ekstrak seluruh isi ZIP ini lalu upload ke repository tersebut (**pastikan folder tersembunyi `.github/workflows/build-apk.yml` ikut ter-upload**).
   - *Tips:* Jika upload lewat browser dan folder `.github` tidak ikut karena tersembunyi di OS, klik **Add file -> Create new file** di GitHub, ketik nama file `.github/workflows/build-apk.yml`, lalu paste isi file tersebut.
3. Buka tab **Actions** di repository GitHub kamu, tunggu proses **Build BeatSync APK** selesai (ikon centang hijau).
4. Klik workflow yang selesai tersebut, gulir ke bagian **Artifacts**, lalu unduh **`BeatSync-v1.0-debug-apk`** dan instal file `.apk`-nya di HP Android.
