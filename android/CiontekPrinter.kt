package com.ciontek.sdk

import android.graphics.Bitmap
import android.os.IBinder
import com.ciontek.ciontekposservice.ICiontekPosService

/**
 * Klien mandiri printer internal Ciontek (system service
 * "posmanager", mis. pada terminal Kassen XA-02 Pro).
 *
 * Kontrak dipulihkan dari bedah APK SdkDemo vendor, lihat
 * docs/CONTRACT.md. Tidak butuh binary/SDK resmi vendor:
 * cukup sertakan file AIDL dari folder `aidl/` repo ini di
 * modul Android-mu (src/main/aidl/...), aktifkan
 * `buildFeatures.aidl`, lalu pakai object ini.
 *
 * PERINGATAN hidden API: akses lewat refleksi ServiceManager.
 * Pada perangkat/targetSdk yang menolaknya, semua fungsi gagal
 * aman: isAvailable() false, status() null, print() = -1000.
 * Selalu cek isAvailable() lebih dulu dan sediakan fallback
 * (mis. printer Bluetooth) untuk perangkat non-Ciontek.
 */
object CiontekPrinter {

    /** Kode bila service tidak tersedia / binder mati. */
    const val CODE_UNAVAILABLE = -1000

    /** Status printer siap (kode 0 dari Lib_PrnCheckStatus). */
    const val STATUS_READY = 0
    const val STATUS_NO_PAPER = -1
    const val STATUS_TOO_HOT = -2
    const val STATUS_LOW_VOLTAGE = -3

    /** Satu baris teks; align: 0 kiri, 1 tengah, 2 kanan. */
    data class PrintLine(
        val text: String,
        val align: Int = 0,
        val bold: Boolean = false,
        val underline: Boolean = false,
    )

    @Volatile
    private var cached: ICiontekPosService? = null

    /**
     * Ambil proxy service "posmanager" via refleksi
     * ServiceManager, persis cara SDK vendor melakukannya.
     * Null bila perangkat bukan Ciontek / refleksi diblokir.
     */
    fun service(): ICiontekPosService? {
        cached?.let { return it }
        val svc = try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService =
                smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, "posmanager")
                as? IBinder ?: return null
            ICiontekPosService.Stub.asInterface(binder)
        } catch (t: Throwable) {
            null
        }
        cached = svc
        return svc
    }

    /** True bila service printer menjawab di perangkat ini. */
    fun isAvailable(): Boolean = service() != null

    /**
     * Kode status mentah: 0 siap, -1 kertas habis, -2 terlalu
     * panas, -3 tegangan rendah. Null bila tidak tersedia.
     */
    fun status(): Int? {
        val svc = service() ?: return null
        return try {
            svc.Lib_PrnCheckStatus()
        } catch (t: Throwable) {
            cached = null
            null
        }
    }

    /**
     * Cetak dokumen mengikuti resep demo vendor:
     * PrnInit -> SetMode(0 Receipt) -> SetGray -> SetFont(24,24,0)
     * -> logo (opsional) -> baris-baris -> PrnStart -> feed.
     *
     * @return kode PrnStart (0 = sukses; negatif bermakna sama
     * seperti status()), kode PrnInit bila init gagal, atau
     * [CODE_UNAVAILABLE].
     *
     * Panggil dari thread latar: panggilan Binder bisa memblok.
     */
    fun print(
        lines: List<PrintLine>,
        logo: Bitmap? = null,
        density: Int = 3,
        feedLines: Int = 4,
    ): Int {
        val svc = service() ?: return CODE_UNAVAILABLE
        return try {
            val init = svc.Lib_PrnInit()
            if (init != 0) return init
            svc.Lib_PrnSetMode(0) // 0 = Receipt, 1 = Label
            svc.Lib_PrnSetGray(density.coerceIn(1, 5))
            // Font bawaan yang dipakai semua contoh cetak demo.
            svc.Lib_PrnSetFont(24, 24, 0)

            if (logo != null) {
                svc.Lib_PrnSetAlign(1)
                svc.Lib_PrnBmp(scaleToPaper(logo))
            }

            var lastAlign = -1
            var lastBold = -1
            var lastUnderline = -1
            for (line in lines) {
                if (line.align != lastAlign) {
                    svc.Lib_PrnSetAlign(line.align)
                    lastAlign = line.align
                }
                val bold = if (line.bold) 1 else 0
                if (bold != lastBold) {
                    svc.Lib_PrnSetBold(bold)
                    lastBold = bold
                }
                val under = if (line.underline) 1 else 0
                if (under != lastUnderline) {
                    svc.Lib_PrnSetUnderline(under)
                    lastUnderline = under
                }
                svc.Lib_PrnStr(line.text)
            }
            if (lastBold == 1) svc.Lib_PrnSetBold(0)
            if (lastUnderline == 1) svc.Lib_PrnSetUnderline(0)
            if (lastAlign != 0) svc.Lib_PrnSetAlign(0)

            val start = svc.Lib_PrnStart()
            if (start == 0 && feedLines > 0) {
                try {
                    svc.Lib_PrnFeedPaper(feedLines)
                } catch (_: Throwable) {
                    // Feed tambahan hanya kosmetik.
                }
            }
            start
        } catch (t: Throwable) {
            cached = null
            CODE_UNAVAILABLE
        }
    }

    /** Kecilkan bitmap ke lebar kertas 58mm (384 dot) bila perlu. */
    private fun scaleToPaper(bmp: Bitmap): Bitmap {
        if (bmp.width <= 384) return bmp
        val h = (bmp.height * (384f / bmp.width)).toInt()
            .coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, 384, h, true)
    }
}
