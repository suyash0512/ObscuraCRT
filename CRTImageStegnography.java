package obscureCRT;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.math.BigInteger;
import java.util.Arrays;
import javax.imageio.ImageIO;


public class CRTImageStegnography extends JFrame {

    private final JLabel originalPreview = new JLabel("Original Image Preview", SwingConstants.CENTER);
    private final JLabel encryptedPreview = new JLabel("Encrypted Image Preview", SwingConstants.CENTER);
    private final JLabel psnrLabel = new JLabel("PSNR: -");
    private final JLabel lengthLabel = new JLabel("Length: 0 bits");
    private final JLabel logLabel = new JLabel("Ready");

    private final JTextField secretDataField = new JTextField();
    private final JComboBox<String> dataTypeDropdown = new JComboBox<>(
            new String[]{"Text (any printable)", "Numeric Only", "Binary Only"});
    private final JTextField keyField = new JTextField("17,19,23");
    private final JTextArea decryptedMessageArea = new JTextArea(6, 24);

    private BufferedImage loadedImage;
    private BufferedImage encryptedImage;

    private final CRTLogic crt = new CRTLogic();

    // Store the most recent encryption key; used to lock-in key for decryption
    private String lastUsedKey = null;

    public CRTImageStegnography() {
        super("CRT Steganography");
        setSize(1200, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("Secure Image Cryptography using CRT", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 20));
        add(title, BorderLayout.NORTH);

        // center previews
        JPanel center = new JPanel(new GridLayout(1, 2, 10, 10));
        originalPreview.setBorder(BorderFactory.createTitledBorder("Original Image"));
        encryptedPreview.setBorder(BorderFactory.createTitledBorder("Encrypted Image"));
        center.add(originalPreview);
        center.add(encryptedPreview);
        add(center, BorderLayout.CENTER);

        // right controls
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBorder(BorderFactory.createTitledBorder("Controls"));

        right.add(new JLabel("Enter Secret Data:"));
        ((AbstractDocument) secretDataField.getDocument()).setDocumentFilter(new InputFilter(".*", 32));
        right.add(secretDataField);
        right.add(lengthLabel);

        right.add(new JLabel("Data Type:"));
        right.add(dataTypeDropdown);
        dataTypeDropdown.addActionListener(e -> updateInputFilter());

        right.add(new JLabel("Key (comma-separated moduli):"));
        right.add(keyField);

        JButton genKeyBtn = new JButton("Generate Key (3)");
        genKeyBtn.addActionListener(e -> {
            crt.generateKey(3, 10, 64);
            String k = String.join(",", crt.getKey().stream().map(Object::toString).toArray(String[]::new));
            keyField.setText(k);
            log("Generated key: " + k);
        });
        right.add(genKeyBtn);

        right.add(new JLabel("Decrypted Message:"));
        decryptedMessageArea.setEditable(false);
        decryptedMessageArea.setLineWrap(true);
        decryptedMessageArea.setWrapStyleWord(true);
        right.add(new JScrollPane(decryptedMessageArea));

        right.add(Box.createVerticalGlue());
        right.add(logLabel);
        add(right, BorderLayout.EAST);

        // bottom buttons
        JPanel bottom = new JPanel(new FlowLayout());
        JButton loadBtn = new JButton("Load Image");
        JButton resizeBtn = new JButton("Resize to 512x512");
        JButton encryptBtn = new JButton("Encrypt");
        JButton decryptBtn = new JButton("Decrypt");
        JButton saveBtn = new JButton("Save Encrypted");
        JButton clearBtn = new JButton("Clear");

        bottom.add(loadBtn);
        bottom.add(resizeBtn);
        bottom.add(encryptBtn);
        bottom.add(decryptBtn);
        bottom.add(saveBtn);
        bottom.add(clearBtn);
        bottom.add(psnrLabel);
        add(bottom, BorderLayout.SOUTH);

        // listeners
        loadBtn.addActionListener(a -> loadImage());
        resizeBtn.addActionListener(a -> resizeImage());
        encryptBtn.addActionListener(a -> encryptImage());
        decryptBtn.addActionListener(a -> decryptImage());
        saveBtn.addActionListener(a -> saveEncryptedImage());
        clearBtn.addActionListener(a -> clearAll());

        secretDataField.getDocument().addDocumentListener(new SimpleDocListener(this::updateLengthLabel));
    }

    // update DocumentFilter based on selected data type
    private void updateInputFilter() {
        String sel = (String) dataTypeDropdown.getSelectedItem();
        DocumentFilter f;
        if ("Numeric Only".equals(sel)) {
            f = new InputFilter("[0-9]*", 32);
        } else if ("Binary Only".equals(sel)) {
            f = new InputFilter("[01]*", 256);
        } else {
            f = new InputFilter(".*", 32);
        }
        ((AbstractDocument) secretDataField.getDocument()).setDocumentFilter(f);
        updateLengthLabel();
    }

    private void updateLengthLabel() {
        String sel = (String) dataTypeDropdown.getSelectedItem();
        int chars = secretDataField.getText().length();
        if ("Binary Only".equals(sel)) lengthLabel.setText("Length: " + chars + " bits");
        else lengthLabel.setText("Length: " + (chars * 8) + " bits (" + chars + " chars)");
    }

    private void loadImage() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                File f = fc.getSelectedFile();
                BufferedImage img = ImageIO.read(f);
                if (img == null) throw new Exception("Invalid image");
                loadedImage = img;
                originalPreview.setIcon(new ImageIcon(img));
                originalPreview.setText("");
                log("Loaded " + f.getName() + " (" + img.getWidth() + "x" + img.getHeight() + ")");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Load error: " + ex.getMessage());
            }
        }
    }

    private void resizeImage() {
        if (loadedImage == null) { JOptionPane.showMessageDialog(this, "Load an image first."); return; }
        loadedImage = resize(loadedImage, 512, 512);
        originalPreview.setIcon(new ImageIcon(loadedImage));
        originalPreview.setText("");
        log("Resized to 512x512");
    }

    private void encryptImage() {
        if (loadedImage == null) { JOptionPane.showMessageDialog(this, "Load an image first."); return; }

        String type = (String) dataTypeDropdown.getSelectedItem();
        String secretText = secretDataField.getText();
        if (secretText == null || secretText.isEmpty()) { JOptionPane.showMessageDialog(this, "Enter secret data."); return; }
        if (!isValidSecret(secretText, type)) { JOptionPane.showMessageDialog(this, "Secret does not match type."); return; }

        // parse moduli from keyField
        int[] moduli;
        try {
            moduli = parseModuli(keyField.getText());
            if (moduli.length < 1) throw new Exception("Provide at least one modulus.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid key/moduli. Use comma-separated integers.");
            return;
        }

        // ensure product >= 256
        BigInteger prod = crt.productOfModuli(moduli);
        if (prod.compareTo(BigInteger.valueOf(256)) < 0) {
            JOptionPane.showMessageDialog(this, "Product of moduli must be >= 256 so bytes are uniquely reconstructible.");
            return;
        }

        // Store the key used for encryption and lock the keyField
        lastUsedKey = keyField.getText();
        keyField.setEditable(false);

        byte[] messageBytes;
        if ("Binary Only".equals(type)) {
            if (secretText.length() % 8 != 0) { JOptionPane.showMessageDialog(this, "Binary input length must be multiple of 8."); return; }
            int n = secretText.length() / 8;
            messageBytes = new byte[n];
            for (int i = 0; i < n; i++) {
                String byteStr = secretText.substring(i * 8, (i + 1) * 8);
                messageBytes[i] = (byte) Integer.parseInt(byteStr, 2);
            }
        } else {
            messageBytes = secretText.getBytes();
            if (messageBytes.length > 32) { JOptionPane.showMessageDialog(this, "Max 32 characters (256 bits)."); return; }
        }

        int width = loadedImage.getWidth(), height = loadedImage.getHeight();
        int totalPixels = width * height;
        int capacityBits = totalPixels * 3;
        int headerBits = 32;

        int[] bitsPerMod = crt.bitsPerMod(moduli);
        int bitsPerByte = Arrays.stream(bitsPerMod).sum();
        int requiredBits = headerBits + (messageBytes.length * bitsPerByte);

        if (requiredBits > capacityBits) {
            JOptionPane.showMessageDialog(this, "Image capacity insufficient. Required bits: " + requiredBits + ", capacity: " + capacityBits);
            return;
        }

        encryptedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = encryptedImage.createGraphics();
        g.drawImage(loadedImage, 0, 0, null);
        g.dispose();

        int globalBitIndex = 0;
        writeIntLSB(encryptedImage, globalBitIndex, messageBytes.length, headerBits);
        globalBitIndex += headerBits;

        for (byte b : messageBytes) {
            int val = b & 0xFF;
            int[] residues = crt.crtEncode(val, moduli);
            for (int i = 0; i < residues.length; i++) {
                int bits = bitsPerMod[i];
                for (int k = 0; k < bits; k++) {
                    int bit = (residues[i] >> k) & 1;
                    setBitInImage(encryptedImage, globalBitIndex++, bit);
                }
            }
        }

        encryptedPreview.setIcon(new ImageIcon(encryptedImage));
        encryptedPreview.setText("");
        double psnr = calculatePSNR(loadedImage, encryptedImage);
        psnrLabel.setText(String.format("PSNR: %.2f dB", psnr));
        log("Encryption done. Used bits: " + requiredBits);
        JOptionPane.showMessageDialog(this, "Encryption completed.");
    }

    private void decryptImage() {
        if (encryptedImage == null) { JOptionPane.showMessageDialog(this, "No encrypted image to decrypt."); return; }

        // Use the locked key from encryption, not whatever's in the text box
        String keyToUse = lastUsedKey != null ? lastUsedKey : keyField.getText();
        int[] moduli;
        try {
            moduli = parseModuli(keyToUse);
            if (moduli.length < 1) throw new Exception("Provide at least one modulus.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid key/moduli. Use comma-separated integers.");
            return;
        }

        int width = encryptedImage.getWidth(), height = encryptedImage.getHeight();
        int capacity = width * height * 3;
        if (capacity < 32) { JOptionPane.showMessageDialog(this, "Image too small or corrupted."); return; }

        int globalBitIndex = 0;
        int messageLength = readIntLSB(encryptedImage, globalBitIndex, 32);
        globalBitIndex += 32;

        int[] bitsPerMod = crt.bitsPerMod(moduli);
        int bitsPerByte = Arrays.stream(bitsPerMod).sum();
        int requiredBits = 32 + (messageLength * bitsPerByte);
        if (requiredBits > capacity) { JOptionPane.showMessageDialog(this, "Image does not contain enough data for provided key."); return; }

        byte[] out = new byte[messageLength];
        for (int byteIdx = 0; byteIdx < messageLength; byteIdx++) {
            int[] residues = new int[moduli.length];
            for (int i = 0; i < moduli.length; i++) {
                int bits = bitsPerMod[i];
                int r = 0;
                for (int k = 0; k < bits; k++) {
                    int bit = getBitFromImage(encryptedImage, globalBitIndex++);
                    r |= (bit << k);
                }
                residues[i] = r % moduli[i];
            }
            int decoded = crt.crtDecode(residues, moduli);
            out[byteIdx] = (byte) (decoded & 0xFF);
        }

        String sel = (String) dataTypeDropdown.getSelectedItem();
        if ("Binary Only".equals(sel)) {
            StringBuilder sb = new StringBuilder();
            for (byte b : out) sb.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0'));
            decryptedMessageArea.setText(sb.toString());
        } else {
            decryptedMessageArea.setText(new String(out));
        }

        // Unlock the key field after decryption
        keyField.setEditable(true);

        log("Decryption done. Recovered bytes: " + messageLength);
        JOptionPane.showMessageDialog(this, "Decryption completed.");
    }

    // ---------- Helpers: bit-level image LSB access ----------
    private void setBitInImage(BufferedImage img, int globalBitIndex, int bitValue) {
        int width = img.getWidth();
        int pixelIndex = globalBitIndex / 3;
        int channel = globalBitIndex % 3;
        int x = pixelIndex % width;
        int y = pixelIndex / width;
        if (y >= img.getHeight()) return;

        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        switch (channel) {
            case 0: r = (r & 0xFE) | (bitValue & 1); break;
            case 1: g = (g & 0xFE) | (bitValue & 1); break;
            default: b = (b & 0xFE) | (bitValue & 1); break;
        }
        int newRgb = (r << 16) | (g << 8) | b;
        img.setRGB(x, y, newRgb);
    }

    private int getBitFromImage(BufferedImage img, int globalBitIndex) {
        int width = img.getWidth();
        int pixelIndex = globalBitIndex / 3;
        int channel = globalBitIndex % 3;
        int x = pixelIndex % width;
        int y = pixelIndex / width;
        if (y >= img.getHeight()) return 0;
        int rgb = img.getRGB(x, y);
        switch (channel) {
            case 0: return (rgb >> 16) & 1;
            case 1: return (rgb >> 8) & 1;
            default: return rgb & 1;
        }
    }

    private void writeIntLSB(BufferedImage img, int startBitIndex, int value, int bits) {
        for (int i = 0; i < bits; i++) setBitInImage(img, startBitIndex + i, (value >> i) & 1);
    }

    private int readIntLSB(BufferedImage img, int startBitIndex, int bits) {
        int v = 0;
        for (int i = 0; i < bits; i++) v |= (getBitFromImage(img, startBitIndex + i) << i);
        return v;
    }

    private int[] parseModuli(String text) {
        String[] parts = text.split(",");
        int[] m = new int[parts.length];
        for (int i = 0; i < parts.length; i++) m[i] = Integer.parseInt(parts[i].trim());
        return m;
    }

    private static BufferedImage resize(BufferedImage img, int w, int h) {
        Image tmp = img.getScaledInstance(w, h, Image.SCALE_SMOOTH);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(tmp, 0, 0, null);
        g.dispose();
        return out;
    }

    private static boolean isValidSecret(String data, String type) {
        if (data == null || data.isEmpty()) return false;
        switch (type) {
            case "Numeric Only": return data.matches("[0-9]+");
            case "Binary Only": return data.matches("[01]+");
            default: return true;
        }
    }

    private double calculatePSNR(BufferedImage orig, BufferedImage enc) {
        int w = orig.getWidth(), h = orig.getHeight();
        double mse = 0;
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) {
            int p1 = orig.getRGB(x,y), p2 = enc.getRGB(x,y);
            int r1=(p1>>16)&255,g1=(p1>>8)&255,b1=p1&255;
            int r2=(p2>>16)&255,g2=(p2>>8)&255,b2=p2&255;
            mse += Math.pow(r1-r2,2)+Math.pow(g1-g2,2)+Math.pow(b1-b2,2);
        }
        mse /= (w*h*3.0);
        return mse==0?Double.POSITIVE_INFINITY:10*Math.log10(255*255/mse);
    }

    private void saveEncryptedImage() {
        if (encryptedImage == null) { JOptionPane.showMessageDialog(this, "No encrypted image to save."); return; }
        JFileChooser fc = new JFileChooser();
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try { ImageIO.write(encryptedImage, "png", fc.getSelectedFile()); JOptionPane.showMessageDialog(this, "Saved."); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Save error: " + ex.getMessage()); }
        }
    }

    private void clearAll() {
        loadedImage = null; encryptedImage = null;
        originalPreview.setIcon(null); originalPreview.setText("Original Image Preview");
        encryptedPreview.setIcon(null); encryptedPreview.setText("Encrypted Image Preview");
        secretDataField.setText(""); keyField.setText("17,19,23"); decryptedMessageArea.setText(""); psnrLabel.setText("PSNR: -");
        lengthLabel.setText("Length: 0 bits"); log("Cleared");
        keyField.setEditable(true);
        lastUsedKey = null;
    }

    private void log(String s) { logLabel.setText(s); }

    private static class InputFilter extends DocumentFilter {
        private final String allowedRegex;
        private final int maxLen;
        InputFilter(String allowedRegex, int maxLen) { this.allowedRegex = allowedRegex; this.maxLen = maxLen; }

        private String filterString(String s) {
            if (s == null || s.isEmpty()) return s;
            StringBuilder sb = new StringBuilder();
            for (char c : s.toCharArray()) {
                String t = String.valueOf(c);
                if (allowedRegex.equals(".*") || t.matches(allowedRegex)) sb.append(c);
            }
            return sb.toString();
        }

        @Override public void insertString(FilterBypass fb, int offs, String str, AttributeSet a) throws BadLocationException {
            String filtered = filterString(str);
            int newLen = fb.getDocument().getLength() + filtered.length();
            if (newLen <= maxLen) fb.insertString(offs, filtered, a);
            else if (maxLen - fb.getDocument().getLength() > 0) fb.insertString(offs, filtered.substring(0, maxLen - fb.getDocument().getLength()), a);
        }
        @Override public void replace(FilterBypass fb, int offs, int length, String str, AttributeSet a) throws BadLocationException {
            String filtered = filterString(str);
            int newLen = fb.getDocument().getLength() - length + filtered.length();
            if (newLen <= maxLen) fb.replace(offs, length, filtered, a);
            else {
                int allowed = maxLen - (fb.getDocument().getLength() - length);
                if (allowed > 0) fb.replace(offs, length, filtered.substring(0, allowed), a);
            }
        }
    }

    private static class SimpleDocListener implements javax.swing.event.DocumentListener {
        private final Runnable r;
        SimpleDocListener(Runnable r) { this.r = r; }
        public void insertUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void removeUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
        public void changedUpdate(javax.swing.event.DocumentEvent e) { r.run(); }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CRTImageStegnography().setVisible(true));
    }
}