package com.fourcut.photo.video;

import com.fourcut.photo.common.ApiException;
import com.fourcut.photo.common.ErrorCode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class QrCodeService {

	private static final int QR_SIZE = 512;

	public byte[] generatePng(String content) {
		try {
			Map<EncodeHintType, Object> hints = Map.of(
				EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
				EncodeHintType.MARGIN, 1
			);
			BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			MatrixToImageWriter.writeToStream(matrix, "PNG", out);
			return out.toByteArray();
		} catch (WriterException | IOException e) {
			throw new ApiException(ErrorCode.STORAGE_ERROR, e.getMessage());
		}
	}
}
