# Commuter Reminder

## Deskripsi
Aplikasi Android untuk pengguna Commuter Line (KRL) yang sering ketiduran di perjalanan.
Aplikasi melacak posisi pengguna dan memberi peringatan (alarm + notifikasi) saat kereta
mendesati stasiun tujuan, khususnya saat tinggal 1 stasiun lagi sebelum tujuan.

## Fitur Inti (MVP)
1. Pilih stasiun keberangkatan dan stasiun tujuan dari daftar stasiun KRL.
2. Mulai perjalanan: aplikasi melacak lokasi via GPS di background (foreground service).
3. Saat pengguna mendekati stasiun tepat sebelum stasiun tujuan, bunyikan alarm + notifikasi.
4. Tombol stop / selesai perjalanan.

## Teknologi
- Bahasa: Kotlin (JVM target 17)
- UI: Jetpack Compose + Material 3, single-activity
- Min SDK 26, Target/Compile SDK 36 (lokal sudah terpasang; rencana awal 35 disesuaikan)
- Arsitektur: MVVM + Repository, ViewModeel per layar
- DI: manual (constructor injection) dulu, Hilt menyusul jika proyek membesar
- Data stasiun: database lokal (Room 2.8) berisi nama stasiun + koordinat GPS (offline-first)
- Lokasi: FusedLocationProviderClient (Google Play Services)
- Background tracking: Foreground Service dengan tipe `location`, notifikasi permanen
- Notifikasi & alarm: NotificationChannel terpisah untuk "tracking" (senyap) dan "alarm" (suara keras, full-screen intent)

## Logika Deteksi (Rencana)
- Data stasiun KRL (Jabodetabek) disimpan lokal sebagai JSON seed lalu dimuat ke Room.
- Deteksi berbasis geofence/jarak: hitung jarak pengguna ke tiap stasiun di sepanjang rute.
- Saat pengguna berada dalam radius ~1 km dari stasiun sebelum tujuan -> trigger alarm "1 stasiun lagi".
- Saat pengguna melewati stasiun tujuan tanpa berhenti -> trigger alarm "sudah lewat tujuan".
- Tidak bergantung API online agar tetap bekerja saat sinyal hilang di perjalanan.

## Aturan Kode
- Komponen functional + Compose, tanpa XML layout kecuali kebutuhan sistem (splash, dsb).
- Nama package: `com.personaldevelopment.commuterreminder`
- Semua string UI dalam Bahasa Indonesia (file `strings.xml`).
- Logika perhitungan jarak dan deteksi stasiun wajib berada di kelas terpisah (bukan di Composable/ViewModel) agar mudah di-unit-test.
- Error handling wajib untuk permission GPS (ditolak / permanen ditolak) dan lokasi mati.
- Ikuti konvensi Kotlin resmi (kotlin.code.style=official).

## Keamanan & Privasi
- Tidak ada API key / secret di kode — gunakan `local.properties` atau `.env` jika suatu saat perlu.
- Data lokasi hanya diproses di perangkat, tidak dikirim ke server manapun.
- Jelaskan alasan permission lokasi ke pengguna (in-app rationale) sebelum request.

## Yang Dilarang
- Jangan tambah dependency baru tanpa konfirmasi.
- Jangan request permission di luar yang benar-benar dipakai.
- Jangan refactor file di luar scope pekerjaan yang diminta.
- Jangan matikan atau tipis-kan mekanisme alarm; fitur inti adalah membangunkan user yang tertidur.

## Status Proyek
- [x] Berkas context (AGENTS.md)
- [x] Setup project Android (Gradle, struktur folder)
- [x] Data stasiun + model
- [x] UI pilih rute
- [x] Foreground service + tracking lokasi
- [x] Logika deteksi stasiun + alarm
- [ ] Testing di perangkat nyata
