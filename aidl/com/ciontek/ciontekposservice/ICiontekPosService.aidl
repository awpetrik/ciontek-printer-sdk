// ICiontekPosService.aidl
package com.ciontek.ciontekposservice;

import android.graphics.Bitmap;

// Kontrak AIDL service "posmanager" (CiontekPosService), dipulihkan
// dari bedah APK SdkDemo v1.0.4 (lihat docs/CONTRACT.md di repo ini).
//
// PERINGATAN: urutan deklarasi method MENENTUKAN kode transaksi
// Binder (method ke-n = TRANSACTION_n). Urutan di file ini harus
// selalu sama dengan interface asli; jangan menyisipkan/menghapus
// method di tengah. Method non-printer tetap dideklarasikan agar
// kode transaksi printer (16..43) tidak bergeser.
//
// Arah parameter array memakai `in` di semua tempat: untuk method
// yang kita panggil (printer) itu memang arah yang benar, dan untuk
// method yang tidak pernah dipanggil arah tidak berpengaruh.
interface ICiontekPosService {
    int installRomPackage(String path);              // 1
    String getOSVersion();                           // 2
    String getDeviceId();                            // 3
    int Lib_LogSwitch(int sw);                       // 4
    int Lib_GetRand(in byte[] rand);                 // 5
    int Lib_Update_32550();                          // 6
    int Lib_GetVersion(in byte[] ver);               // 7
    int Lib_ReadSN(in byte[] sn);                    // 8
    int Lib_WriteSN(in byte[] sn);                   // 9
    int Lib_ReadChipID(in byte[] chip, int len);     // 10
    int Lib_IccOpen(byte slot, byte type, in byte[] atr);        // 11
    int Lib_IccClose(byte slot);                                 // 12
    int Lib_IccCommand(byte slot, in byte[] cmd, in byte[] resp); // 13
    int Lib_IccCheck(byte slot);                                 // 14
    int SC_ApduCmd(byte slot, in byte[] apdu, int len, in byte[] resp, in byte[] respLen); // 15

    // ---- Printer ----
    int Lib_PrnInit();                               // 16
    int Lib_PrnSetSpace(byte x, byte y);             // 17
    int Lib_PrnSetFont(byte a, byte b, byte c);      // 18
    int Lib_PrnGetFont(in byte[] a, in byte[] b, in byte[] c); // 19
    int Lib_PrnStep(int step);                       // 20
    int Lib_PrnSetVoltage(int v);                    // 21
    int Lib_PrnIsCharge(int v);                      // 22
    int Lib_PrnStr(String str);                      // 23
    int Lib_PrnBmp(in Bitmap bmp);                   // 24
    int Lib_PrnBarcode(String data, int width, int height, String extra); // 25
    int Lib_PrintCutQrCode(String data, int width, int height, String extra); // 26
    int Lib_PrintCutQrCodeStr(String a, String b, int x, int y, int z, String extra); // 27
    int Lib_PrnLogo(in byte[] data);                 // 28
    int Lib_SetLinPixelDis(char c);                  // 29
    int Lib_PrnStart();                              // 30
    int Lib_PrnSetLeftIndent(int v);                 // 31
    int Lib_PrnSetAlign(int v);                      // 32
    int Lib_PrnSetCharSpace(int v);                  // 33
    int Lib_PrnSetLineSpace(int v);                  // 34
    int Lib_PrnSetLeftSpace(int v);                  // 35
    int Lib_PrnSetGray(int v);                       // 36
    int Lib_PrnSetSpeed(int v);                      // 37
    int Lib_PrnCheckStatus();                        // 38
    int Lib_PrnFeedPaper(int lines);                 // 39
    int Lib_PrnSetMode(int mode);                    // 40
    int Lib_PrnSetUnderline(int v);                  // 41
    int Lib_PrnSetReverse(int v);                    // 42
    int Lib_PrnSetBold(int v);                       // 43

    // ---- Manajemen aplikasi & fiskal (tidak dipakai) ----
    boolean enableAppInstallWhiteList();             // 44
    boolean disableAppInstallWhiteList();            // 45
    boolean addAppToInstallWhiteList(String pkg);    // 46
    boolean delAppFromInstallWhiteList(String pkg);  // 47
    List getAppInstallWhiteList();                   // 48
    boolean enableAppUninstallBlackList();           // 49
    boolean disableAppUninstallBlackList();          // 50
    boolean addAppToUninstallBlackList(String pkg);  // 51
    boolean delAppFromUninstallBlackList(String pkg);// 52
    List getAppUninstallBlackList();                 // 53
    int fiscalOpen(int a, int b, int c, char d, char e); // 54
    int fiscalClose();                               // 55
    int fiscalWrite(in byte[] data);                 // 56
    int fiscalRead(in byte[] buf, int len, int timeout); // 57
}
