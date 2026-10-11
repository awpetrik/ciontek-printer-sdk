# Ciontek POS Printer SDK (tidak resmi)

SDK hasil rekayasa balik untuk printer thermal internal terminal POS
Ciontek, terverifikasi pada Kassen XA-02 Pro (Safedroid, Android 10). Ciontek adalah OEM di balik banyak terminal POS
white-label; bila perangkatmu menjalankan system service bernama
`posmanager`, SDK ini kemungkinan besar bekerja.

> Status: kontrak API dipulihkan 100% dari APK demo vendor dan sudah
> dipakai di aplikasi POS produksi. Cetak di perangkat
> nyata terverifikasi lewat app demo vendor; integrasi app pihak
> ketiga bergantung kebijakan hidden-API perangkat (lihat bawah).

## Kenapa repo ini ada

Vendor tidak menerbitkan SDK secara publik. Tidak ada AAR, tidak
ada dokumentasi, distribusi hanya lewat distributor perangkat.
Printer internalnya juga tidak muncul sebagai device Bluetooth, jadi
jalur printer Bluetooth biasa tidak bisa memakainya. Satu-satunya
jejak adalah APK `SdkDemo` yang terinstal di perangkat. Repo ini
memulihkan kontraknya dari APK tersebut agar pemilik perangkat bisa
memakai printer yang sudah mereka bayar.

## Cara kerja

Printer digerakkan **system service** Android bernama `posmanager`
(bukan aplikasi yang di-bind):

```kotlin
val binder = Class.forName("android.os.ServiceManager")
    .getMethod("getService", String::class.java)
    .invoke(null, "posmanager") as IBinder
val svc = ICiontekPosService.Stub.asInterface(binder)
```

Model cetaknya buffered & berbasis teks terstruktur (bukan raw
ESC/POS): `PrnInit` → atur mode/density/font → `PrnStr` per baris,
`PrnBmp` untuk gambar, QR/barcode native → `PrnStart`.

Kode status: `0` siap, `-1` kertas habis, `-2` terlalu panas,
`-3` tegangan rendah. Kontrak lengkap (57 method AIDL + kode
transaksi + resep cetak dari app demo): [docs/CONTRACT.md](docs/CONTRACT.md).

## Pakai

1. Salin `aidl/com/ciontek/ciontekposservice/ICiontekPosService.aidl`
   ke `src/main/aidl/` modul Android-mu. Jangan mengubah urutan
   method: urutan deklarasi menentukan kode transaksi Binder.
2. Aktifkan AIDL: `buildFeatures { aidl = true }`.
3. Salin `android/CiontekPrinter.kt`, sesuaikan package.
4. Cetak dari thread latar:

```kotlin
if (CiontekPrinter.isAvailable() && CiontekPrinter.status() == 0) {
    val code = CiontekPrinter.print(listOf(
        CiontekPrinter.PrintLine("TOKO SAYA", align = 1, bold = true),
        CiontekPrinter.PrintLine("Terima kasih", align = 1),
    ))
    check(code == 0) { "Print gagal: $code" }
}
```

Untuk Flutter, folder `flutter/` berisi contoh platform channel
yang siap pakai (`InternalPrinterChannel.kt` di sisi Android +
klien Dart `internal_printer.dart` dengan model segmen struk).

## Risiko hidden API

`android.os.ServiceManager` adalah API non-SDK. Demo vendor
bertargetSdk 28; app modern bertargetSdk lebih tinggi bisa ditolak
refleksinya tergantung kebijakan perangkat/Android. SDK ini gagal
secara aman (mengembalikan "tidak tersedia"), tidak crash. Uji
selalu di perangkat target: bila `isAvailable()` false di build-mu,
jalur ini tertutup di perangkat itu.

## Legal

Repo tidak resmi, tidak berafiliasi dengan Ciontek/Kassen. Hanya
berisi definisi antarmuka (AIDL) dan kode klien yang ditulis dari
hasil pengamatan perilaku APK demo pada perangkat milik sendiri.
Tidak ada binary atau aset vendor di repo ini.
