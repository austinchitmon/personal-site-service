package com.chitmon.backend.files;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResumeService {
    static final int MAX_PDF_BYTES = 10 * 1024 * 1024;
    private final Path file;

    public ResumeService(@Value("${resume.file-path:./public-files/resume.pdf}") String filePath) {
        this.file = Path.of(filePath).toAbsolutePath().normalize();
    }

    public Path configuredFile() {
        return file;
    }

    public byte[] readPdf() throws IOException {
        // Read afresh for every request. A directory bind mount also picks up atomic replacements.
        // Buffer this small, bounded document before sending headers so read failures return a 503.
        try (InputStream input = Files.newInputStream(file)) {
            byte[] pdf = input.readNBytes(MAX_PDF_BYTES + 1);
            if (pdf.length > MAX_PDF_BYTES) {
                throw new IOException("Resume PDF exceeds the 10 MiB limit");
            }
            if (pdf.length < 5 || !new String(pdf, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new IOException("Resume is empty or does not have a PDF header");
            }
            return pdf;
        }
    }
}
