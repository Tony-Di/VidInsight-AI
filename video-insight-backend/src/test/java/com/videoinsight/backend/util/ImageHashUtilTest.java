package com.videoinsight.backend.util;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageHashUtilTest {

    private BufferedImage horizontalGradient() {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.WHITE, 200, 0, Color.BLACK));
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        return image;
    }

    private BufferedImage verticalGradient() {
        BufferedImage image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setPaint(new GradientPaint(0, 0, Color.BLACK, 0, 200, Color.WHITE));
        g.fillRect(0, 0, 200, 200);
        g.dispose();
        return image;
    }

    @Test
    void identicalImagesHaveZeroDistance() {
        long left = ImageHashUtil.differenceHash(horizontalGradient());
        long right = ImageHashUtil.differenceHash(horizontalGradient());
        assertEquals(0, ImageHashUtil.hammingDistance(left, right));
    }

    @Test
    void differentPatternsExceedDedupThreshold() {
        long left = ImageHashUtil.differenceHash(horizontalGradient());
        long right = ImageHashUtil.differenceHash(verticalGradient());
        assertTrue(ImageHashUtil.hammingDistance(left, right) > 5,
                "水平/垂直渐变的 dHash 距离应远超去重阈值 5");
    }
}
