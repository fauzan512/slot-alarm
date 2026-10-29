# Slot Alarm — APK Android

Alarm buat HP Fauzan. Bunyi walau HP **silent**, pakai `STREAM_ALARM`.

## Trigger

```
Notif "🔥 Domain ditarik" masuk dari @sellupotp_bot
   ↓
Cek data_store.json → KOSONG
   ↓
🚨 Kirim event ke HP → ALARM BUNYI (berulang sampai dimatiin)
```

## Fitur

- **Bunyi walau silent** — pakai `USAGE_ALARM`, bypass silent mode
- **Custom MP3** — pilih file sendiri lewat tombol "PILIH MP3 ALARM"
- **Berulang terus** — `isLooping = true`, bunyi sampai LO matiin
- **Getar** — pola getar ikut nyala
- **Full-screen notif** — muncul di lockscreen
- **Foreground service** — tetap jalan walau app ditutup
- **Auto-reconnect** — kalau internet mati, coba lagi tiap 5 detik
- **Tema dark** — simple, ringan

## Build APK (GitHub Actions — gratis)

### 1. Bikin repo GitHub

Buat repo baru (public atau private), misal `slot-alarm`.

### 2. Push project ini

```bash
cd ~/slot-alarm-apk
git init
git add .
git commit -m "initial"
git branch -M main
git remote add origin https://github.com/USERNAME/slot-alarm.git
git push -u origin main
```

### 3. Tunggu build selesai

Buka repo → tab **Actions** → klik run terbaru.

Build selesai (~3-5 menit), scroll ke bawah ke **Artifacts**:
- `slot-alarm-debug` → APK buat install langsung
- `slot-alarm-release` → APK unsigned

Klik → download zip → extract → dapat `app-debug.apk`.

### 4. Install di HP

Transfer APK ke HP, buka, izinkan "Install from unknown sources", install.

## Setup di HP

1. Buka app **Slot Alarm**
2. **Server** udah terisi: `wss://43.156.47.36/alarm`
3. **Token** isi: `fzn-alarm-7f3k9m2x`
4. Tekan **PILIH MP3 ALARM** kalau mau ganti bunyi (opsional)
5. Tekan **NYALAKAN**
6. Izinkan notifikasi kalau diminta

Status jadi `● TERHUBUNG — alarm siap`.

## Cara matiin alarm

Alarm bunyi berulang. Buat matiin:
- Buka app → tekan **MATIKAN**
- Atau swipe notif alarm

## Catatan

- Kalau HP **power off**, alarm nggak bunyi (nggak ada yang bisa jalan)
- Kalau **Do Not Disturb** dengan alarm di-block, alarm bisa nggak bunyi — pastiin alarm channel nggak diblokir
- Notifikasi channel "Alarm" di-set `setBypassDnd(true)` biar lolos DND

## Server side

Notifier di VPS: `~/domain-slot-bot/alarm_notifier.py` (port 8091, di-proxy Caddy di `/alarm`).

Service: `alarm-notifier.service`.
