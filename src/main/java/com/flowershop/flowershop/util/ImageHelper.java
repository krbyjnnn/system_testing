package com.flowershop.flowershop.util;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class ImageHelper {

    private static final Path IMAGE_DIR = Paths.get("product-images");
    private static final int OVERSAMPLE = 2;

    public static String saveImage(File source, String productCode) throws IOException {
        Files.createDirectories(IMAGE_DIR);

        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot).toLowerCase() : ".jpg";
        String safeCode = productCode.replaceAll("[^a-zA-Z0-9_-]", "_");

        File[] old = IMAGE_DIR.toFile().listFiles((dir, n) -> n.startsWith(safeCode + "."));
        if (old != null) {
            for (File f : old) {
                f.delete();
            }
        }

        String fileName = safeCode + ext;
        Files.copy(source.toPath(), IMAGE_DIR.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        return fileName;
    }

    public static Icon loadIcon(String fileName, int width, int height) {
        if (fileName == null || fileName.isBlank()) {
            return placeholder(width, height);
        }
        File file = IMAGE_DIR.resolve(fileName).toFile();
        if (!file.exists()) {
            return placeholder(width, height);
        }
        return loadIconFromFile(file, width, height);
    }

    public static Icon loadIconFromFile(File file, int width, int height) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) {
                return placeholder(width, height);
            }
            BufferedImage big = fit(img, width * OVERSAMPLE, height * OVERSAMPLE);
            return new ScaledIcon(big, width, height);
        } catch (IOException e) {
            return placeholder(width, height);
        }
    }

    public static ImageIcon placeholder(int width, int height) {
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(235, 235, 235));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(150, 150, 150));
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, Math.min(width, height) / 8)));
        FontMetrics fm = g.getFontMetrics();
        String text = "No photo";
        int x = (width - fm.stringWidth(text)) / 2;
        int y = (height - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, x, y);
        g.dispose();
        return new ImageIcon(canvas);
    }

    private static BufferedImage fit(BufferedImage src, int width, int height) {
        double scale = Math.min((double) width / src.getWidth(), (double) height / src.getHeight());
        int w = Math.max(1, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(src.getHeight() * scale));

        BufferedImage current = src;
        int curW = src.getWidth();
        int curH = src.getHeight();
        while (curW / 2 >= w && curH / 2 >= h) {
            curW = curW / 2;
            curH = curH / 2;
            current = resize(current, curW, curH);
        }

        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(235, 235, 235));
        g.fillRect(0, 0, width, height);
        g.drawImage(current, (width - w) / 2, (height - h) / 2, w, h, null);
        g.dispose();
        return canvas;
    }

    private static BufferedImage resize(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private static class ScaledIcon implements Icon {
        private final BufferedImage image;
        private final int width;
        private final int height;

        ScaledIcon(BufferedImage image, int width, int height) {
            this.image = image;
            this.width = width;
            this.height = height;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2.drawImage(image, x, y, width, height, null);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return width;
        }

        @Override
        public int getIconHeight() {
            return height;
        }
    }
}