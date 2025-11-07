//BACKEND

package com.mycompany.obscura;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class CRTLogic {

    private List<Integer> key = new ArrayList<>();

    public void generateKey(int count, int min, int max) {
        key.clear();
        Random rand = new Random();
        while (true) {
            key.clear();
            while (key.size() < count) {
                int n = rand.nextInt(Math.max(1, max - min)) + min;
                if (n < 2) continue;
                if (isCoprimeWithAll(n, key)) key.add(n);
            }
            long prod = 1;
            for (int m : key) prod *= m;
            if (prod <= 255) break; // safe for 8-bit pixels
        }
    }

    public List<Integer> getKey() {
        return key;
    }

    public void setKey(List<Integer> key) {
        this.key = new ArrayList<>(key);
    }

    private boolean isCoprimeWithAll(int n, List<Integer> list) {
        for (int m : list) if (gcd(n, m) != 1) return false;
        return true;
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    public static BufferedImage embedData(BufferedImage image, String bits, CRTLogic crt) throws Exception {
        List<Integer> moduli = crt.getKey();
        if (moduli.isEmpty()) throw new Exception("Key not set.");

        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        int idx = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                if (idx < bits.length()) r = (r & 0xFE) | (bits.charAt(idx++) - '0');
                if (idx < bits.length()) g = (g & 0xFE) | (bits.charAt(idx++) - '0');
                if (idx < bits.length()) b = (b & 0xFE) | (bits.charAt(idx++) - '0');

                int newPixel = (r << 16) | (g << 8) | b;
                out.setRGB(x, y, newPixel);
            }
        }
        return out;
    }

    public static String extractData(BufferedImage image, CRTLogic crt) {
        StringBuilder sb = new StringBuilder();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getRGB(x, y);
                sb.append((pixel >> 16) & 1);
                sb.append((pixel >> 8) & 1);
                sb.append(pixel & 1);
            }
        }
        return sb.toString();
    }

    public static double calculatePSNR(BufferedImage img1, BufferedImage img2) {
        int w = img1.getWidth(), h = img1.getHeight();
        double mse = 0.0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int rgb1 = img1.getRGB(x, y);
                int rgb2 = img2.getRGB(x, y);
                int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
                int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
                mse += (Math.pow(r1 - r2, 2) + Math.pow(g1 - g2, 2) + Math.pow(b1 - b2, 2)) / 3.0;
            }
        mse /= (w * h);
        if (mse == 0) return 99.99;
        return 10 * Math.log10((255 * 255) / mse);
    }
}