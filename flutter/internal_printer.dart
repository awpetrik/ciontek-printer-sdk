import 'package:flutter/services.dart';

/// Satu baris/blok teks untuk printer internal Ciontek.
///
/// Berbeda dari jalur Bluetooth (raw ESC/POS), service Ciontek
/// menerima teks terstruktur: konten dirender sebagai daftar segmen
/// dengan align/bold/underline per segmen, lalu service yang
/// mengurus font & pemotongan baris. Lihat
/// docs/CONTRACT.md di repo SDK ini.
class ReceiptSegment {
  final String text;

  /// 0 = kiri, 1 = tengah, 2 = kanan (konvensi Lib_PrnSetAlign).
  final int align;
  final bool bold;
  final bool underline;

  const ReceiptSegment(
    this.text, {
    this.align = 0,
    this.bold = false,
    this.underline = false,
  });

  Map<String, dynamic> toMap() => {
        'text': text,
        'align': align,
        'bold': bold,
        'underline': underline,
      };
}

/// Status printer internal dari Lib_PrnCheckStatus.
enum InternalPrinterStatus {
  ready,
  noPaper,
  tooHot,
  lowVoltage,
  unknown,
  unavailable,
}

extension InternalPrinterStatusX on InternalPrinterStatus {
  /// Label Bahasa Indonesia untuk UI pengaturan & chip status.
  String get label => switch (this) {
        InternalPrinterStatus.ready => 'Siap',
        InternalPrinterStatus.noPaper => 'Kertas habis',
        InternalPrinterStatus.tooHot => 'Printer terlalu panas',
        InternalPrinterStatus.lowVoltage => 'Tegangan rendah',
        InternalPrinterStatus.unknown => 'Status tidak dikenal',
        InternalPrinterStatus.unavailable =>
          'Tidak tersedia di perangkat ini',
      };

  static InternalPrinterStatus fromCode(int? code) => switch (code) {
        0 => InternalPrinterStatus.ready,
        -1 => InternalPrinterStatus.noPaper,
        -2 => InternalPrinterStatus.tooHot,
        -3 => InternalPrinterStatus.lowVoltage,
        null => InternalPrinterStatus.unavailable,
        _ => InternalPrinterStatus.unknown,
      };
}

/// Klien platform channel `ciontek/internal_printer`.
///
/// Semua method aman dipanggil di perangkat non-Ciontek: channel
/// menjawab tidak tersedia dan hasil dipetakan ke false/null/kode
/// [unavailableCode], tidak pernah melempar.
class InternalPrinter {
  static const _channel = MethodChannel('ciontek/internal_printer');

  /// Kode hasil bila service posmanager tidak ada / binder mati.
  static const unavailableCode = -1000;

  final MethodChannel _ch;

  InternalPrinter([MethodChannel channel = _channel]) : _ch = channel;

  /// True bila service Ciontek menjawab di perangkat ini.
  Future<bool> isAvailable() async {
    try {
      return await _ch.invokeMethod<bool>('isAvailable') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Kode status mentah (0 siap, -1 kertas habis, -2 panas,
  /// -3 tegangan rendah); null bila tidak tersedia.
  Future<int?> checkStatus() async {
    try {
      return await _ch.invokeMethod<int>('checkStatus');
    } catch (_) {
      return null;
    }
  }

  Future<InternalPrinterStatus> status() async =>
      InternalPrinterStatusX.fromCode(await checkStatus());

  /// Cetak satu dokumen. Mengembalikan kode PrnStart:
  /// 0 = sukses, negatif = error printer (semakna checkStatus),
  /// [unavailableCode] bila service tidak ada.
  Future<int> printDocument({
    required List<ReceiptSegment> segments,
    Uint8List? logoPng,
    int density = 3,
    int feedLines = 4,
  }) async {
    try {
      return await _ch.invokeMethod<int>('printDocument', {
            'segments': [for (final s in segments) s.toMap()],
            'logoPng': logoPng,
            'density': density,
            'feedLines': feedLines,
          }) ??
          unavailableCode;
    } catch (_) {
      return unavailableCode;
    }
  }
}
