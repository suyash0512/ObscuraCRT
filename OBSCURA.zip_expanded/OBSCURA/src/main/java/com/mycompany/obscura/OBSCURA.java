package com.mycompany.obscura;


import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class OBSCURA extends JFrame {

    private final JLabel originalPreview = new JLabel("Original Image", SwingConstants.CENTER);
    private final JLabel encryptedPreview = new JLabel("Encrypted Image", SwingConstants.CENTER);
    //private final JTextArea secretDataArea = new JTextArea();
    private final JTextArea secretDataArea = new JTextArea() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Color.GRAY);
                Insets ins = getInsets();
                g2.drawString("Enter Secret Message", ins.left + 2, ins.top + g2.getFontMetrics().getAscent());
                g2.dispose();
            }
        }
    };
     
    private final JTextField keyField = new JTextField() {
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(Color.GRAY);
                Insets ins = getInsets();

                FontMetrics fm = g2.getFontMetrics();
                String line1 = "Enter / Generate Key";
                String line2 = "Format -  3,5,9";

                int lineHeight = fm.getHeight();
                int totalHeight = lineHeight * 2;
                int startY = (getHeight() - totalHeight) / 2 + fm.getAscent();

                // Draw first line
                g2.drawString(line1, ins.left + 2, startY);
                // Draw second line just below the first one
                g2.drawString(line2, ins.left + 2, startY + lineHeight);

                g2.dispose();
            }
        }
    };
    private final JLabel psnrLabel = new JLabel("PSNR: -");
    private final JTextArea descriptionArea = new JTextArea();
    private final JButton encryptButton = new JButton("Encrypt (CRT)");
    private final JButton decryptButton = new JButton("Decrypt");
    private final JButton generateKeyButton = new JButton("Generate Key");
    private final JButton loadKeyButton = new JButton("Load Key");
    private final JButton loadImageButton = new JButton("Load Image");
    private final JButton saveImageButton = new JButton("Save Image");
    private final JButton clearButton = new JButton("Clear");
    private BufferedImage loadedImage = null;
    private BufferedImage encryptedImage = null;
    private final CRTLogic crt = new CRTLogic();

    public OBSCURA() {
        super("OBSCURA - CRT Steganography");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);
        initUI();
        hookActions();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));
        getContentPane().setBackground(new Color(28, 34, 43));

        JLabel title = new JLabel("OBSCURA — Secure CRT Steganography", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        add(title, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        originalPreview.setBackground(new Color(43, 50, 63));
        originalPreview.setOpaque(true);
        originalPreview.setForeground(Color.WHITE);
        encryptedPreview.setBackground(new Color(43, 50, 63));
        encryptedPreview.setOpaque(true);
        encryptedPreview.setForeground(Color.WHITE);
        centerPanel.add(originalPreview);
        centerPanel.add(encryptedPreview);
        add(centerPanel, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setBackground(new Color(36, 44, 58));
        right.setPreferredSize(new Dimension(350, 700));
        right.setBorder(new EmptyBorder(10, 10, 10, 10));

        right.add(new JLabel("Secret Data:"));
        JScrollPane secretScroll = new JScrollPane(secretDataArea);
        secretScroll.setPreferredSize(new Dimension(300, 100));
        right.add(secretScroll);
        right.add(Box.createRigidArea(new Dimension(0, 10)));
        right.add(new JLabel("Key (comma-separated):"));
        right.add(keyField);
        right.add(Box.createRigidArea(new Dimension(0, 5)));

        JPanel keyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        keyRow.add(generateKeyButton);
        keyRow.add(loadKeyButton);
        right.add(keyRow);

        right.add(Box.createRigidArea(new Dimension(0, 10)));
        JPanel btnRow = new JPanel(new GridLayout(3, 2, 5, 5));
        btnRow.add(loadImageButton);
        btnRow.add(saveImageButton);
        btnRow.add(encryptButton);
        btnRow.add(decryptButton);
        btnRow.add(clearButton);
        right.add(btnRow);

        right.add(Box.createRigidArea(new Dimension(0, 10)));
        right.add(psnrLabel);
        add(right, BorderLayout.EAST);

        descriptionArea.setEditable(false);
        descriptionArea.setBackground(new Color(20, 26, 36));
        descriptionArea.setForeground(Color.WHITE);
        JScrollPane descScroll = new JScrollPane(descriptionArea);
        descScroll.setPreferredSize(new Dimension(1000, 150));
        add(descScroll, BorderLayout.SOUTH);

        encryptButton.setEnabled(false);
        decryptButton.setEnabled(false);
        saveImageButton.setEnabled(false);
    }


    private void hookActions() {
        loadImageButton.addActionListener(e -> loadImage());
        generateKeyButton.addActionListener(e -> generateKey());
        loadKeyButton.addActionListener(e -> loadKey());
        encryptButton.addActionListener(e -> encryptImage());
        decryptButton.addActionListener(e -> decryptImage());
        saveImageButton.addActionListener(e -> saveImage());
        clearButton.addActionListener(e -> clearAll());
    }
    
    private void loadImage() {
        try {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            File f = chooser.getSelectedFile();
            loadedImage = ImageIO.read(f);
            if (loadedImage == null) throw new Exception("Invalid image file.");
            originalPreview.setIcon(new ImageIcon(loadedImage.getScaledInstance(400, 400, Image.SCALE_SMOOTH)));
            encryptedImage = loadedImage;
            encryptButton.setEnabled(true);
            decryptButton.setEnabled(true);
            append("Image loaded: " + f.getName());
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void generateKey() {
        crt.generateKey(3, 3, 20);
        keyField.setText(crt.getKey().toString().replaceAll("[\\[\\]\\s]", ""));
        append("Key generated: " + crt.getKey());
        encryptButton.setEnabled(loadedImage != null);
    }

    private void loadKey() {
        try {
            String[] parts = keyField.getText().split(",");
            List<Integer> list = new ArrayList<>();
            for (String p : parts) list.add(Integer.parseInt(p.trim()));
            crt.setKey(list);
            append("Key loaded: " + list);
            encryptButton.setEnabled(loadedImage != null);
        } catch (Exception ex) {
            showError("Invalid key format");
        }
    }

    private void encryptImage() {
        try {
            if (loadedImage == null) throw new Exception("No image loaded.");
            if (crt.getKey().isEmpty()) throw new Exception("Key not loaded.");
            String message = secretDataArea.getText();
            if (message.isEmpty()) throw new Exception("Secret data empty.");

            String bits = toBinary(message);
            String header = String.format("%32s", Integer.toBinaryString(bits.length())).replace(' ', '0');
            encryptedImage = CRTLogic.embedData(loadedImage, header + bits, crt);
            encryptedPreview.setIcon(new ImageIcon(encryptedImage.getScaledInstance(400, 400, Image.SCALE_SMOOTH)));
            saveImageButton.setEnabled(true);
            decryptButton.setEnabled(true);
            double psnr = CRTLogic.calculatePSNR(loadedImage, encryptedImage);
            psnrLabel.setText(String.format("PSNR: %.2f dB", psnr));
            append("Encryption done. PSNR = " + psnr);
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void decryptImage() {
        try {
            if (encryptedImage == null) throw new Exception("No encrypted image.");
            if (crt.getKey().isEmpty()) throw new Exception("Key not loaded.");
            String extracted = CRTLogic.extractData(encryptedImage, crt);
            if (extracted.length() < 32) throw new Exception("No valid header found.");
            int payloadLen = Integer.parseUnsignedInt(extracted.substring(0, 32), 2);
            String payloadBits = extracted.substring(32, 32 + payloadLen);
            String msg = bitsToText(payloadBits);
            append("Decryption complete: " + msg);
            JOptionPane.showMessageDialog(this, "Decrypted message:\n" + msg);
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }

    private void saveImage() {
        try {
            JFileChooser chooser = new JFileChooser();
            if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File f = chooser.getSelectedFile();
                ImageIO.write(encryptedImage, "png", f);
                append("Saved encrypted image: " + f.getName());
            }
        } catch (Exception ex) {
            showError(ex.getMessage());
        }
    }
    
    private void clearAll() {
        loadedImage = null;
        encryptedImage = null;
        keyField.setText("");
        secretDataArea.setText("");
        descriptionArea.setText("");
        originalPreview.setIcon(null);
        encryptedPreview.setIcon(null);
        psnrLabel.setText("PSNR: -");
        encryptButton.setEnabled(false);
        decryptButton.setEnabled(false);
        saveImageButton.setEnabled(false);
    }

    private String toBinary(String text) {
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray())
            sb.append(String.format("%8s", Integer.toBinaryString(c)).replace(' ', '0'));
        return sb.toString();
    }

    private String bitsToText(String bits) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 8 <= bits.length(); i += 8)
            sb.append((char) Integer.parseInt(bits.substring(i, i + 8), 2));
        return sb.toString();
    }

    private void append(String msg) {
        descriptionArea.append(msg + "\n");
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
        append("[ERROR] " + msg);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new OBSCURA().setVisible(true));
    }
}
