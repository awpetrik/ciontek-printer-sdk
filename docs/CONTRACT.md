# Kontrak Printer Internal Ciontek (Kassen XA-02 Pro)

Dipulihkan dari bedah APK `SdkDemo v1.0.4` (paket `test.apidemo.activity`),
aplikasi demo vendor yang terinstal di perangkat Kassen XA-02 Pro,
Oktober 2026. Vendor perangkat adalah Ciontek (kelas Safedroid).

## Cara app mendapatkan service (dari bytecode PosApiHelper.<init>)

Tidak ada binding intent/paket. Service adalah *system service* Android:

```java
IBinder b = (IBinder) Class.forName("android.os.ServiceManager")
        .getMethod("getService", String.class)
        .invoke(instance, "posmanager");   // nama service: "posmanager"
ICiontekPosService svc = ICiontekPosService.Stub.asInterface(b);
```

- Descriptor AIDL: `com.ciontek.ciontekposservice.ICiontekPosService`
- Helper SDK: `com.ctk.sdk.PosApiHelper` (singleton, `POS_SERVICE = "posmanager"`)
- Demo berjalan sebagai app biasa (targetSdk 28) tanpa permission khusus
  untuk printer. Permission MediaTek di manifest demo hanya untuk fitur
  Upgrade OS (sysoper), tidak terkait printer.
- RISIKO: `ServiceManager` adalah hidden API. Enforcement bergantung
  targetSdk app pemanggil. Probe lebih dulu di perangkat nyata
  (getService + PrnCheckStatus) sebelum bergantung pada jalur ini.
  Bila refleksi diblokir, kontrak ini tetap valid tetapi jalur akses
  harus diganti.

## Kode transaksi AIDL (dari Stub, urutan = deklarasi interface)

Relevan printer: 16 Lib_PrnInit, 17 Lib_PrnSetSpace(B,B), 18 Lib_PrnSetFont(B,B,B),
19 Lib_PrnGetFont, 20 Lib_PrnStep(I), 21 Lib_PrnSetVoltage(I),
22 Lib_PrnIsCharge(I), 23 Lib_PrnStr(String), 24 Lib_PrnBmp(Bitmap),
25 Lib_PrnBarcode(String,I,I,String), 26 Lib_PrintCutQrCode(String,I,I,String),
27 Lib_PrintCutQrCodeStr(String,String,I,I,I,String), 28 Lib_PrnLogo([B),
29 Lib_SetLinPixelDis(C), 30 Lib_PrnStart, 31 Lib_PrnSetLeftIndent(I),
32 Lib_PrnSetAlign(I), 33 Lib_PrnSetCharSpace(I), 34 Lib_PrnSetLineSpace(I),
35 Lib_PrnSetLeftSpace(I), 36 Lib_PrnSetGray(I), 37 Lib_PrnSetSpeed(I),
38 Lib_PrnCheckStatus, 39 Lib_PrnFeedPaper(I), 40 Lib_PrnSetMode(I),
41 Lib_PrnSetUnderline(I), 42 Lib_PrnSetReverse(I), 43 Lib_PrnSetBold(I).
Semua mengembalikan `int` (kode hasil). Interface juga memuat ICC/SAM,
fiscal, SN/chip-id, whitelist instalasi, dan update ROM (tidak dipakai).

## Resep print (dari PrintActivity$Print_Thread demo)

1. `PrintInit()` -> kode init (0 = sukses).
2. `PrintSetMode(0)` Receipt / `(1)` Label (radio UI demo).
3. `PrintSetGray(density)`: UI demo 1..5, default 3.
4. `PrintCheckStatus()` -> 0 OK; -1 "Error, No Paper";
   -2 "Error, Printer Too Hot"; -3 tegangan/baterai rendah.
5. Isi buffer: `PrintSetFont(b,b,b)` lalu `PrintStr("...")` per baris;
   `PrintBmp(bitmap)` untuk gambar; `PrintQrCode_Cut(...)` /
   `PrintBarcode(...)` untuk kode.
6. `PrintStart()` -> eksekusi cetak; hasil dipetakan demo ke
   "Print Finish" / "No Print Paper" / "too hot" / "label fail" /
   "Print fail". Mode label tambahan `PrintLabLocate(int)`.

Modelnya buffered dan berbasis teks terstruktur, bukan raw ESC/POS
bytes. Aplikasi yang sebelumnya mencetak lewat ESC/POS perlu merender
kontennya sebagai segmen teks (align/bold/font per segmen), plus bitmap
untuk logo. QR bisa dicetak native via PrintQrCode_Cut.

## Catatan integrasi

- Jadikan printer internal pilihan transport yang eksplisit di
  aplikasi, sejajar dengan transport lain (mis. Bluetooth). Jangan
  probing otomatis di perangkat non-Ciontek.
- Semua panggilan Binder dari thread latar. Panggilan bisa memblok,
  dan binder yang mati harus membuang cache proxy lalu mengambil
  ulang pada percobaan berikutnya.
- Petakan kegagalan secara jujur ke UI: service tidak menjawab
  berarti "tidak tersedia di perangkat ini", kode status negatif
  berarti masalah fisik printer (kertas, panas, tegangan). Jangan
  menampilkan keduanya sebagai "terhubung".
- Lebar kolom teks bergantung font bawaan service (pada XA-02 Pro,
  font 24 setara 32 kolom di kertas 58mm). Kalibrasi di perangkat
  nyata sebelum mengunci tata letak struk.
- Langkah pertama integrasi selalu probe: panggil getService dan
  PrnCheckStatus dari build aplikasimu di perangkat target. Bila
  probe gagal, hentikan dan nilai ulang, jangan paksa bypass
  hidden-API.
