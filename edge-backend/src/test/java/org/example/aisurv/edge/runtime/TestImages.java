package org.example.aisurv.edge.runtime;

import java.awt.Color;
import java.awt.image.BufferedImage;

final class TestImages {
    private TestImages() {
    }

    static BufferedImage jpegSource() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, Color.RED.getRGB());
        return image;
    }
}
