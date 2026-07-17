package com.videoinsight.backend.smoke;

import com.videoinsight.backend.service.impl.TesseractOcrServiceImpl;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 依赖本机 tesseract,不可用时整类跳过(CI 未装则自动 skip)。 */
class TesseractOcrSmokeTest {

    private static final String TESSERACT =
            System.getenv().getOrDefault("TESSERACT_PATH", "tesseract");

    private static boolean tesseractAvailable() {
        try {
            Process p = new ProcessBuilder(TESSERACT, "--version")
                    .redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    void recognizesRenderedEnglishText() throws Exception {
        Assumptions.assumeTrue(tesseractAvailable(), "tesseract not on PATH, skipping");

        BufferedImage image = new BufferedImage(600, 160, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 600, 160);
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 48));
        g.drawString("HELLO VIDINSIGHT", 30, 90);
        g.dispose();
        Path png = Files.createTempFile("ocr-smoke-", ".png");
        ImageIO.write(image, "png", png.toFile());

        try {
            TesseractOcrServiceImpl ocr = new TesseractOcrServiceImpl(TESSERACT, "eng");
            String text = ocr.recognize(png);
            assertTrue(text.toUpperCase().contains("HELLO"), "OCR 输出: " + text);
        } finally {
            Files.deleteIfExists(png);
        }
    }
}
