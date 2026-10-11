package com.example.pos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.ciontek.ciontekposservice.ICiontekPosService
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

/**
 * Transport printer internal Ciontek (mis. Kassen XA-02 Pro) lewat
 * platform channel `ciontek/internal_printer`.
 *
 * Service BUKAN aplikasi lain yang di-bind: ia system service bernama
 * "posmanager" yang diambil via refleksi ServiceManager, persis cara
 * SDK vendor (com.ctk.sdk.PosApiHelper dari SdkDemo) melakukannya.
 * Kontrak lengkap: docs/CONTRACT.md di repo SDK ini.
 *
 * PERINGATAN hidden API: ServiceManager adalah API non-SDK. Bila
 * Android menolak refleksinya (bergantung targetSdk & kebijakan
 * perangkat), semua method di sini gagal secara aman: isAvailable
 * false dan printDocument mengembalikan kode [CODE_UNAVAILABLE],
 * tidak pernah crash.
 */
class InternalPrinterChannel(messenger: BinaryMessenger) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var cachedService: ICiontekPosService? = null

    init {
        MethodChannel(messenger, CHANNEL_NAME).setMethodCallHandler { call, result ->
            when (call.method) {
                "isAvailable" -> runAsync(result) { service() != null }
                "checkStatus" -> runAsync(result) { checkStatus() }
                "printDocument" -> {
                    @Suppress("UNCHECKED_CAST")
                    val segments = call.argument<List<Map<String, Any>>>("segments")
                        ?: emptyList()
                    val logoPng = call.argument<ByteArray>("logoPng")
                    val density = call.argument<Int>("density") ?: 3
                    val feedLines = call.argument<Int>("feedLines") ?: 4
                    runAsync(result) {
                        printDocument(segments, logoPng, density, feedLines)
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    /** Jalankan kerja Binder di thread latar; jawab di main thread. */
    private fun <T> runAsync(result: MethodChannel.Result, block: () -> T) {
        worker.execute {
            val value = try {
                block()
            } catch (t: Throwable) {
                mainHandler.post {
                    result.error("internal_printer_error", t.message, null)
                }
                return@execute
            }
            mainHandler.post { result.success(value) }
        }
    }

    /**
     * Ambil proxy service "posmanager". Null bila perangkat bukan
     * Ciontek / refleksi diblokir / service tidak terdaftar.
     * Hasil sukses di-cache; binder mati -> cache dibuang sekali
     * dan percobaan berikutnya mengambil ulang.
     */
    private fun service(): ICiontekPosService? {
        cachedService?.let { return it }
        val svc = try {
            val smClass = Class.forName("android.os.ServiceManager")
            val getService = smClass.getMethod("getService", String::class.java)
            val binder = getService.invoke(null, SERVICE_NAME) as? IBinder
                ?: return null
            ICiontekPosService.Stub.asInterface(binder)
        } catch (t: Throwable) {
            null
        }
        cachedService = svc
        return svc
    }

    private fun invalidate() {
        cachedService = null
    }

    /**
     * Kode status printer mentah: 0 siap, -1 kertas habis,
     * -2 terlalu panas, -3 tegangan rendah. Null bila service
     * tidak tersedia.
     */
    private fun checkStatus(): Int? {
        val svc = service() ?: return null
        return try {
            svc.Lib_PrnCheckStatus()
        } catch (t: Throwable) {
            invalidate()
            null
        }
    }

    /**
     * Cetak satu dokumen mengikuti resep demo vendor:
     * PrnInit -> SetMode(Receipt) -> SetGray -> (logo) -> segmen
     * (SetAlign/SetBold/SetUnderline + PrnStr) -> PrnStart -> feed.
     *
     * Mengembalikan kode PrnStart (0 = sukses; negatif = error
     * printer bermakna sama seperti checkStatus), atau
     * [CODE_UNAVAILABLE] bila service tidak ada, atau kode PrnInit
     * bila inisialisasi gagal.
     */
    private fun printDocument(
        segments: List<Map<String, Any>>,
        logoPng: ByteArray?,
        density: Int,
        feedLines: Int,
    ): Int {
        val svc = service() ?: return CODE_UNAVAILABLE
        return try {
            val init = svc.Lib_PrnInit()
            if (init != 0) return init
            svc.Lib_PrnSetMode(0) // 0 = Receipt, 1 = Label
            svc.Lib_PrnSetGray(density.coerceIn(1, 5))
            // Font bawaan demo: (24, 24, 0), dipakai di semua
            // contoh cetak teks SdkDemo.
            svc.Lib_PrnSetFont(24, 24, 0)

            if (logoPng != null && logoPng.isNotEmpty()) {
                decodeLogo(logoPng)?.let { bmp ->
                    svc.Lib_PrnSetAlign(1)
                    svc.Lib_PrnBmp(bmp)
                }
            }

            var lastAlign = -1
            var lastBold = -1
            var lastUnderline = -1
            for (seg in segments) {
                val text = seg["text"] as? String ?: continue
                val align = (seg["align"] as? Number)?.toInt() ?: 0
                val bold = if (seg["bold"] as? Boolean == true) 1 else 0
                val under =
                    if (seg["underline"] as? Boolean == true) 1 else 0
                if (align != lastAlign) {
                    svc.Lib_PrnSetAlign(align)
                    lastAlign = align
                }
                if (bold != lastBold) {
                    svc.Lib_PrnSetBold(bold)
                    lastBold = bold
                }
                if (under != lastUnderline) {
                    svc.Lib_PrnSetUnderline(under)
                    lastUnderline = under
                }
                svc.Lib_PrnStr(text)
            }
            // Kembalikan state ke netral agar job berikutnya bersih.
            if (lastBold == 1) svc.Lib_PrnSetBold(0)
            if (lastUnderline == 1) svc.Lib_PrnSetUnderline(0)
            if (lastAlign != 0) svc.Lib_PrnSetAlign(0)

            val start = svc.Lib_PrnStart()
            if (start == 0 && feedLines > 0) {
                try {
                    svc.Lib_PrnFeedPaper(feedLines)
                } catch (_: Throwable) {
                    // Feed tambahan hanya kosmetik; jangan gagalkan job.
                }
            }
            start
        } catch (t: Throwable) {
            invalidate()
            CODE_UNAVAILABLE
        }
    }

    /**
     * Decode logo (PNG/JPG dari Dart) ke Bitmap selebar maks 384 dot
     * (58mm), proporsional. Null bila bytes tidak bisa dibaca.
     */
    private fun decodeLogo(bytes: ByteArray): Bitmap? {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return null
        if (bmp.width <= 384) return bmp
        val h = (bmp.height * (384f / bmp.width)).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bmp, 384, h, true)
    }

    companion object {
        const val CHANNEL_NAME = "ciontek/internal_printer"
        const val SERVICE_NAME = "posmanager"

        /** Service tidak tersedia / binder mati. */
        const val CODE_UNAVAILABLE = -1000
    }
}
