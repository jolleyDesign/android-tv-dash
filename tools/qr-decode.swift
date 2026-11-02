// Decodes QR codes in a screenshot using macOS CoreImage (no dependencies).
// Usage: swift tools/qr-decode.swift screenshot.png
import CoreImage
import Foundation

let url = URL(fileURLWithPath: CommandLine.arguments[1])
guard let img = CIImage(contentsOf: url),
      let det = CIDetector(ofType: CIDetectorTypeQRCode, context: nil, options: [CIDetectorAccuracy: CIDetectorAccuracyHigh])
else { exit(1) }
for f in det.features(in: img) {
    if let q = f as? CIQRCodeFeature { print(q.messageString ?? "") }
}
