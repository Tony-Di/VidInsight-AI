package com.videoinsight.backend.util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * dHash(差值哈希):把图缩成 9x8 灰度,逐像素与右邻比亮度得 64 位指纹。
 * 两帧指纹的汉明距离 ≤5 视为"画面基本没变",用于关键帧去重,避免重复 OCR。
 */
public final class ImageHashUtil {

    private ImageHashUtil() {}

    public static long differenceHash(BufferedImage source) {
        BufferedImage scaled = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, 9, 8, null);
        } finally {
            graphics.dispose();
        }
        long hash = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                hash <<= 1;
                if (scaled.getRGB(x, y) > scaled.getRGB(x + 1, y)) {
                    hash |= 1;
                }
            }
        }
        return hash;
    }

    public static long differenceHash(File imageFile) throws IOException {
        BufferedImage source = ImageIO.read(imageFile);
        return source == null ? 0 : differenceHash(source);
    }

    public static int hammingDistance(long left, long right) {
        return Long.bitCount(left ^ right);
    }
}
