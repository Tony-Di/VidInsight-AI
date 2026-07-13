package com.videoinsight.backend.service.impl;

import com.videoinsight.backend.service.OcrService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Service
public class TesseractOcrServiceImpl implements OcrService {

    private final String tesseractPath;

    private final String languages;

    public TesseractOcrServiceImpl(@Value("${app.ocr.tesseract-path}") String tesseractPath,
                                   @Value("${app.ocr.languages}") String languages) {
        this.tesseractPath = tesseractPath;
        this.languages = languages;
    }

    @Override
    public String recognize(Path image) {
        if (image == null || !Files.isRegularFile(image)) {
            throw new IllegalArgumentException("OCR image does not exist: " + image);
        }
        Path output = null;
        Process process = null;
        try {
            output = Files.createTempFile("vid-ocr-", ".txt");
            process = new ProcessBuilder(
                    tesseractPath, image.toAbsolutePath().toString(), "stdout", "-l", languages)
                    .redirectErrorStream(true)
                    .redirectOutput(output.toFile())
                    .start();
            if (!process.waitFor(2, TimeUnit.MINUTES)) {
                process.destroyForcibly();
                throw new IllegalStateException("tesseract timed out for " + image.getFileName());
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("tesseract failed with exit code " + process.exitValue());
            }
            return Files.readString(output, StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to run tesseract", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("tesseract was interrupted", exception);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            if (output != null) {
                try { Files.deleteIfExists(output); } catch (IOException ignored) {}
            }
        }
    }
}
