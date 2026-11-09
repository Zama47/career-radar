package com.careerradar.resume.api;

import com.careerradar.common.error.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Decodes the optional base64 PDF, accepting both raw base64 and a data URL.
 */
final class PdfDecoder {

    private PdfDecoder() {
    }

    static byte[] decode(String pdfBase64, long maxBytes) {
        String value = pdfBase64.trim();
        if (value.startsWith("data:")) {
            int comma = value.indexOf(',');
            if (comma < 0) {
                throw new BadRequestException("pdfBase64 data URL is malformed");
            }
            value = value.substring(comma + 1);
        }
        value = value.replaceAll("\\s", "");
        if (value.isEmpty()) {
            throw new BadRequestException("pdfBase64 is empty");
        }

        long maxBase64Length = ((maxBytes + 2) / 3) * 4;
        if (value.length() > maxBase64Length) {
            throw new BadRequestException("PDF file is too large");
        }

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("pdfBase64 is not valid base64");
        }
        if (bytes.length > maxBytes) {
            throw new BadRequestException("PDF file is too large");
        }
        if (bytes.length < 5 || !"%PDF-".equals(new String(bytes, 0, 5, StandardCharsets.US_ASCII))) {
            throw new BadRequestException("uploaded file is not a PDF");
        }
        return bytes;
    }
}
