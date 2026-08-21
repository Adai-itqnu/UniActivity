import QRCode from 'qrcode'

export function createClassJoinQrDataUrl(joinCode) {
    return QRCode.toDataURL(joinCode, {
        errorCorrectionLevel: 'M',
        margin: 2,
        width: 384,
    })
}
