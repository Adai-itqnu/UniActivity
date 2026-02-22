package com.example.uniactivity.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrCodeService {

    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 300;
    
    // Custom colors (Bootstrap success green)
    private static final int ON_COLOR = 0xFF198754;  // Green
    private static final int OFF_COLOR = 0xFFFFFFFF; // White

    /**
     * Generate QR code as PNG byte array
     * @param content The content to encode in QR
     * @return PNG image as byte array
     */
    public byte[] generateQrCode(String content) throws WriterException, IOException {
        return generateQrCode(content, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    /**
     * Generate QR code with custom size
     * @param content The content to encode
     * @param width Image width
     * @param height Image height
     * @return PNG image as byte array
     */
    public byte[] generateQrCode(String content, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);
        
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageConfig config = new MatrixToImageConfig(ON_COLOR, OFF_COLOR);
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream, config);
        
        return outputStream.toByteArray();
    }

    /**
     * Generate QR code with black color
     */
    public byte[] generateQrCodeBlack(String content) throws WriterException, IOException {
        return generateQrCodeBlack(content, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public byte[] generateQrCodeBlack(String content, int width, int height) throws WriterException, IOException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        
        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        hints.put(EncodeHintType.MARGIN, 2);
        
        BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
        
        return outputStream.toByteArray();
    }
}
