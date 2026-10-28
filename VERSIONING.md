# Versioning & Release

## Skema Versi

Mengikuti [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`

- **MAJOR** — perubahan besar/breaking (mis. ubah skema data stasiun, ganti arsitektur)
- **MINOR** — fitur baru (mis. tambah search stasiun, tambah peta long-press)
- **PATCH** — perbaikan bug (mis. perbaiki koordinat stasiun meleset)

Dua tempat yang harus disamakan saat rilis:

| File | Properti |
|---|---|
| `app/build.gradle.kts` | `versionCode` (integer naik terus) + `versionName` ("1.0.0") |
| Git tag | `v1.0.0` (dengan prefix `v`) |

## Cara Rilis

1. Naikkan `versionCode` (+1) dan `versionName` di `app/build.gradle.kts`.
2. Commit perubahan versi.
3. Buat tag dan push:

```bash
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin v1.0.0
```

4. GitHub Actions (`.github/workflows/release.yml`) otomatis:
   - Menjalankan unit test
   - Build APK debug
   - Membuat **GitHub Release** berjudul `Commuter Reminder v<versi>` dengan APK bernama `CommuterReminder-v<versi>.apk`

5. APK dapat diunduh di tab **Releases** repository.

## Nama Aplikasi per Build

Supaya debug dan release tidak bentrok saat dipasang di device yang sama:

| Build | Nama di launcher | APK di release |
|---|---|---|
| debug | Commuter Reminder (Dev) | — |
| release (via tag) | Commuter Reminder | `CommuterReminder-v<versi>.apk` |

Keduanya masih satu `applicationId`, jadi tetap harus uninstall dulu sebelum bergantian — bedanya sekarang kelihatan jelas mana debug mana release dari ikon/launcher.

## Rilis yang Ada

| Tag | versionName | Catatan |
|---|---|---|
| v1.0.0 | 1.0.0 | Rilis pertama: flow destination-only, alarm radius 3 km, tombol "Sampai Tujuan", peta + edit koordinat long-press, search & rekomendasi stasiun |
| v1.1.0 | 1.1.0 | Radius reminder dinamis dari satu variabel (ReminderSettings), nama app berbeda untuk debug "(Dev)" dan release |
