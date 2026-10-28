# Commuter Reminder

Aplikasi Android untuk pengguna **Commuter Line (KRL) Jabodetabek** yang sering ketiduran di perjalanan. Aplikasi memantau posisi pengguna lewat GPS dan **membunyikan alarm** saat pengguna sudah dekat stasiun tujuan (radius 3 km), sehingga tidak pernah turun terlambat lagi.

---

## Konsep Utama

Berbeda dari aplikasi navigasi umum, Commuter Reminder tidak memandu perjalanan — ia hanya **membangunkan kamu di momen yang tepat**:

1. Buka aplikasi → pilih stasiun tujuan (bisa dari mana saja, tidak harus dari stasiun).
2. Peta menampilkan **pin posisi kamu** dan **pin stasiun tujuan** + lingkaran radius 3 km.
3. Tekan **Mulai Reminder** → aplikasi memantau posisi di background (foreground service).
4. Saat masuk radius 3 km dari tujuan → **alarm berbunyi** (notifikasi full-screen, suara alarm, getar).
5. Tekan tombol **"Sampai Tujuan"** di notifikasi → alarm mati dan **seluruh proses background berhenti total** (lokasi berhenti dilacak, service dimatikan, notifikasi hilang).

### Fitur

| Fitur | Keterangan |
|---|---|
| Pilih stasiun tujuan | Berdasarkan line, dengan search bar dan bagian *Rekomendasi* |
| Peta real-time | OpenStreetMap (osmdroid) — pin posisi + pin tujuan + garis penghubung + lingkaran radius 3 km |
| Reminder radius 3 km | Alarm aktif otomatis saat jarak ke tujuan ≤ 3 km |
| Alarm full-screen | Notifikasi high-priority dengan full-screen intent, suara alarm, vibrasi, bypass DND |
| Tombol "Sampai Tujuan" | Mematikan alarm + menghentikan seluruh tracking dalam sekali klik |
| Notifikasi tracking | Senyap, menampilkan sisa jarak ke tujuan yang terus diperbarui |
| Edit koordinat stasiun | Form edit dengan peta OSM — tekan lama titik di peta untuk mengisi latitude/longitude |
| Offline-first | Semua data stasiun tersimpan lokal (Room), tidak butuh internet untuk deteksi (internet hanya untuk tile peta) |
| Privasi | Posisi GPS hanya diproses di perangkat, tidak dikirim ke server mana pun |

---

## Teknologi

| Komponen | Pilihan |
|---|---|
| Bahasa | Kotlin 2.2.20 (JVM target 17) |
| UI | Jetpack Compose + Material 3, single-activity |
| SDK | minSdk 26, target/compile SDK 36 |
| Arsitektur | MVVM + Repository, ViewModel per layar |
| DI | Manual (constructor injection) |
| Database | Room 2.8 (SQLite lokal) |
| Lokasi | FusedLocationProviderClient (Google Play Services) |
| Background | Foreground Service tipe `location` dengan notifikasi permanen |
| Peta | osmdroid 6.1.20 + tile OpenStreetMap |
| Testing | JUnit 4 (unit test logika domain) |

### Struktur Proyek

```
app/src/main/java/com/personaldevelopment/commuterreminder/
├── MainActivity.kt          # Entry point, permission handling (lokasi + notifikasi)
├── alarm/
│   ├── Notifier.kt          # Notification channels, alarm & tracking notification
│   └── AlarmActionReceiver  # Receiver tombol "Sampai Tujuan" di notifikasi
├── data/
│   ├── StationJsonParser.kt # Parser seed stations.json
│   ├── StationRepository.kt # Akses data stasiun (Room) + update koordinat
│   └── local/               # Room: CommuterDatabase, StationDao, StationEntity
├── domain/
│   ├── GeoCalculator.kt     # Haversine distance (unit-testable)
│   ├── ReminderDetector.kt  # Logika deteksi radius 3 km & kedatangan (unit-testable)
│   └── model/Station.kt     # Model stasiun
├── tracking/
│   ├── LocationClient.kt    # Flow update lokasi untuk UI
│   └── TrackingService.kt   # Foreground service: pantau posisi, picu alarm
└── ui/
    ├── RouteScreen.kt       # Layar utama + dialog pilih stasiun (search & rekomendasi)
    ├── RouteViewModel.kt    # State UI
    └── map/
        ├── StationMap.kt           # Peta utama (posisi + tujuan + radius)
        └── CoordinatePickerMap.kt  # Peta long-press untuk edit koordinat
```

**Prinsip:** semua logika hitung jarak dan deteksi stasiun berada di `domain/` sebagai kelas murni Kotlin — bukan di Composable atau ViewModel — agar mudah di-unit-test.

---

## Build & Install

**Prasyarat:** Android Studio (dengan JBR/JDK 17), Android SDK 36, device/emulator dengan Google Play Services.

```bash
# Build debug APK
./gradlew assembleDebug            # Linux/macOS
.\gradlew.bat assembleDebug        # Windows

# Jalankan unit test
./gradlew testDebugUnitTest

# Install ke device yang tersambung
./gradlew installDebug
# atau manual:
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> **Windows:** jika `JAVA_HOME` tidak terdeteksi, set dulu:
> `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"`

### Permission yang Diminta

| Permission | Alasan |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Melacak posisi untuk hitung jarak ke tujuan |
| `POST_NOTIFICATIONS` (API 33+) | Menampilkan notifikasi tracking & alarm |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` | Memantau posisi saat app di background |
| `USE_FULL_SCREEN_INTENT` | Alarm muncul layar penuh saat device terkunci |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Memuat tile peta OpenStreetMap |
| `VIBRATE`, `WAKE_LOCK` | Getar alarm dan menjaga deteksi tetap jalan |

Aplikasi meminta izin dengan *in-app rationale* (dialog penjelasan) sebelum request sistem, dan menangani kasus **permission ditolak permanen** (mengarahkan ke settings).

---

## Data Stasiun

- Seed data: [`app/src/main/assets/stations.json`](app/src/main/assets/stations.json) — 95 stasiun KRL Jabodetabek (Bogor, Nambo, Cikarang Loop via Sudirman/Pasar Senen, Rangkasbitung, Tangerang, Tanjung Priok), koordinat 7 digit desimal.
- Saat pertama dijalankan, seed dimuat ke database Room (`ensureSeeded()`) — **hanya saat tabel kosong**.
- Koordinat yang dikoreksi lewat fitur *Edit Koordinat* tersimpan permanen di database device dan **tidak tertimpa** seed ulang.

### Mengedit Koordinat Stasiun

1. Buka dialog *Pilih Stasiun Tujuan* → pilih line.
2. Klik ikon **pensil** di baris stasiun yang koordinatnya meleset.
3. Di dialog edit: **tekan lama titik** di peta OSM untuk mengisi koordinat otomatis, atau ketik manual.
4. **Simpan** — koreksi langsung aktif, pin tujuan di peta ikut pindah.

### Sinkronisasi Koreksi Device → Repo

Koreksi hanya hidup di database device. Untuk membuatnya permanen di repo:

```bash
# 1. Tarik database dari device (butuh build debug / run-as)
adb exec-out run-as com.personaldevelopment.commuterreminder cat databases/commuter_reminder.db > db_export/commuter_reminder.db
adb exec-out run-as com.personaldevelopment.commuterreminder cat databases/commuter_reminder.db-wal > db_export/commuter_reminder.db-wal

# 2. Checkpoint WAL supaya data terbaru masuk db utama
sqlite3 db_export/commuter_reminder.db "PRAGMA wal_checkpoint(TRUNCATE);"

# 3. Export ke JSON (sinkronkan dengan format seed)
sqlite3 -json db_export/commuter_reminder.db \
  "SELECT id, name, line, orderIndex, ROUND(latitude,7) AS latitude, ROUND(longitude,7) AS longitude FROM stations ORDER BY line, orderIndex;"
```

Folder `db_export/` menyimpan hasil ekspor terakhir.

> ⚠️ **Peringatan:** `CommuterDatabase` memakai `fallbackToDestructiveMigration()`. Jika versi database dinaikkan tanpa `Migration` eksplisit, **semua koreksi koordinat di device hilang** (tabel di-drop lalu seed ulang). Ganti dengan `Migration(2, 3)` dst. jika skema berubah.

> ⚠️ **Windows:** redirect `>` di PowerShell merusak file binary (UTF-16). Gunakan `cmd /c "adb exec-out ... > file"` atau `adb pull` via `/sdcard/`.

---

## Logika Deteksi

Implementasi ada di [`domain/ReminderDetector.kt`](app/src/main/java/com/personaldevelopment/commuterreminder/domain/ReminderDetector.kt):

- **Jarak** dihitung dengan formula **Haversine** (`GeoCalculator.distanceKm`) — jarak garis lurus (great-circle) antara posisi GPS dan koordinat stasiun, tanpa bergantung API/jaringan.
- **Reminder masuk radius** → `ENTERED_RADIUS`: jarak ≤ 3 km. Alarm bunyi sekali per masuk radius (kalau keluar lalu masuk lagi, alarm bunyi lagi).
- **Kedatangan** → `ARRIVED_AT_DESTINATION`: jarak ≤ 300 m. Bunyi sekali, tidak diulang (bisa ditekan via `acknowledgeReminder`).
- **Foreground service** meminta lokasi tiap 5 detik / minimal 50 m pergerakan dengan `PRIORITY_HIGH_ACCURACY`.
- Alarm dikelola lewat dua notification channel: `tracking` (senyap, IMPORTANCE_LOW) dan `alarm` (IMPORTANCE_HIGH, suara alarm default, vibrasi, bypass DND, full-screen intent).

## Testing

Unit test ada di `app/src/test/` dan mencakup:

- `GeoCalculatorTest` — akurasi formula Haversine (jarak antar koordinat nyata di Jakarta/Bogor)
- `ReminderDetectorTest` — event masuk radius, keluar-masuk ulang, kedatangan sekali, `acknowledgeReminder`, batas radius 3 km (2,9 km vs 3,1 km)

```bash
./gradlew testDebugUnitTest
```

---

## Roadmap

- [x] Pilih stasiun tujuan (tanpa stasiun keberangkatan)
- [x] Peta posisi + tujuan dengan radius
- [x] Foreground service + tracking lokasi
- [x] Logika deteksi radius + alarm
- [x] Tombol "Sampai Tujuan" menghentikan semua proses
- [x] Search + rekomendasi saat memilih line/stasiun
- [x] Edit koordinat stasiun dengan peta long-press
- [ ] Testing di perangkat nyata (berkelanjutan)
- [ ] Export koreksi koordinat langsung dari dalam app

## Privasi & Keamanan

- Tidak ada API key / secret dalam kode.
- Data lokasi **hanya diproses di perangkat** — tidak dikirim ke server mana pun.
- Satu-satunya trafik jaringan adalah tile peta OpenStreetMap (publik).
- Alasan permission lokasi dijelaskan ke pengguna sebelum request (in-app rationale).
