import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;

public class ArAdminTool extends JFrame {

    private static final String ASSET_DIR = "./assets";
    private static final String COMPILER_URL = "https://hiukim.github.io/mind-ar-js-doc/tools/compile";

    private File pendingImage = null;
    private File pendingVideo = null;

    private final JLabel imageLabel = createDropZone("Drop REFERENCE IMAGE here");
    private final JLabel videoLabel = createDropZone("Drop VIDEO here");
    private final JLabel statusLabel = new JLabel("Ready. Next Index: " + getNextIndex());

    public ArAdminTool() {
        setTitle("AR Content Manager");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Drop Zones Panel
        JPanel dropPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        dropPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        dropPanel.add(imageLabel);
        dropPanel.add(videoLabel);
        add(dropPanel, BorderLayout.CENTER);

        // 2. Control Panel
        JPanel controlPanel = new JPanel(new FlowLayout());
        JButton saveButton = new JButton("Save & Add Pair");
        JButton compilerButton = new JButton("1. Open .mind file Compiler");
        JButton pushButton = new JButton("2. Upload files");

        controlPanel.add(saveButton);
        controlPanel.add(new JSeparator(SwingConstants.VERTICAL));
        controlPanel.add(compilerButton);
        controlPanel.add(pushButton);

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(statusLabel, BorderLayout.NORTH);
        bottomPanel.add(controlPanel, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);

        // --- ACTIONS ---

        // Save Logic
        saveButton.addActionListener(e -> {
            if (pendingImage == null || pendingVideo == null) {
                JOptionPane.showMessageDialog(this, "Please drop both an Image and a Video!");
                return;
            }
            try {
                savePair();
                JOptionPane.showMessageDialog(this, "Saved successfully!");
                resetUI();
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error saving files: " + ex.getMessage());
            }
        });

        // Open Compiler Logic
        compilerButton.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(java.net.URI.create(COMPILER_URL));
                // Also open the local folder so you can drag files easily
                Desktop.getDesktop().open(new File(ASSET_DIR));
            } catch (IOException ex) { ex.printStackTrace(); }
        });

        // Git Push Logic
        pushButton.addActionListener(e -> {
            try {
                performGitPush();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Git Error: " + ex.getMessage());
            }
        });
    }

    private void savePair() throws IOException {
        String nextIndex = getNextIndex();
        File destDir = new File(ASSET_DIR);
        if (!destDir.exists()) destDir.mkdirs();

        // Determine extensions
        String imgExt = getExtension(pendingImage.getName());
        String vidExt = getExtension(pendingVideo.getName());

        // Base name (use the image name, stripped of extension)
        String baseName = pendingImage.getName().replace("." + imgExt, "");

        // Target Files
        File targetImage = new File(destDir, nextIndex + "_" + baseName + "." + imgExt);
        File targetVideo = new File(destDir, nextIndex + "_" + baseName + "." + vidExt);

        // Copy
        Files.copy(pendingImage.toPath(), targetImage.toPath(), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(pendingVideo.toPath(), targetVideo.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private String getNextIndex() {
        File folder = new File(ASSET_DIR);
        if (!folder.exists()) return "00";

        int max = Arrays.stream(folder.listFiles())
                .map(File::getName)
                .filter(n -> n.matches("\\d{2}_.*"))
                .map(n -> Integer.parseInt(n.substring(0, 2)))
                .max(Integer::compare)
                .orElse(-1);

        return String.format("%02d", max + 1);
    }

    private void resetUI() {
        pendingImage = null;
        pendingVideo = null;
        imageLabel.setText("Drop REFERENCE IMAGE here");
        imageLabel.setBackground(Color.LIGHT_GRAY);
        videoLabel.setText("Drop VIDEO here");
        videoLabel.setBackground(Color.LIGHT_GRAY);
        statusLabel.setText("Ready. Next Index: " + getNextIndex());
    }

    // --- HELPER METHODS FOR GUI ---

    private JLabel createDropZone(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(Color.LIGHT_GRAY);
        label.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2, true));

        // Enable Drag and Drop
        new DropTarget(label, new DropTargetAdapter() {
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        File file = droppedFiles.get(0);
                        if (text.contains("IMAGE")) {
                            pendingImage = file;
                            label.setText("Image: " + file.getName());
                            label.setBackground(new Color(200, 255, 200)); // Greenish
                        } else {
                            pendingVideo = file;
                            label.setText("Video: " + file.getName());
                            label.setBackground(new Color(200, 255, 200));
                        }
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
        });
        return label;
    }

    private String getExtension(String fileName) {
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }

    private void performGitPush() {
        try {
            // 1. Add
            runCommand("git", "add", ".");

            // 2. Commit
            // We ignore the error here in case there is nothing to commit
            try {
                runCommand("git", "commit", "-m", "Auto-added new AR content");
            } catch (Exception ignored) {
                System.out.println("Nothing to commit, proceeding to push...");
            }

            // 3. Push
            runCommand("git", "push");

            JOptionPane.showMessageDialog(this, "Success! Pushed to GitHub.");

        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Git Failed!\nCheck your IDE Console for the exact error.\n" + ex.getMessage());
        }
    }

    // Helper to run commands and print errors to console
    private void runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(".")); // Ensure we are in the project root
        pb.redirectErrorStream(true); // Merge error output with standard output

        System.out.println("Running: " + Arrays.toString(command));
        Process p = pb.start();

        // Read the output so we can see what Git is saying
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[GIT] " + line);
            }
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ArAdminTool().setVisible(true));
    }
}