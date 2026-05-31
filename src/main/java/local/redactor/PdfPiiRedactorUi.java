package local.redactor;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PdfPiiRedactorUi {
    private static final Path DEFAULT_OUTPUT_DIR = Path.of("E:\\Redacted");

    private final JFrame frame = new JFrame("PDF PII Redactor");
    private final DefaultListModel<Path> fileModel = new DefaultListModel<>();
    private final JList<Path> fileList = new JList<>(fileModel);
    private final JTextField outputField = new JTextField(DEFAULT_OUTPUT_DIR.toString(), 34);
    private final JTextField termsField = new JTextField(34);
    private final JSpinner dpiSpinner = new JSpinner(new SpinnerNumberModel(200, 96, 600, 25));
    private final JTextArea logArea = new JTextArea(9, 60);
    private final JProgressBar progressBar = new JProgressBar();
    private final JButton redactButton = new JButton("Redact PDFs");

    private PdfPiiRedactorUi() {
    }

    static void show() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // The default Swing look and feel is fine if the system one is unavailable.
        }
        PdfPiiRedactorUi ui = new PdfPiiRedactorUi();
        ui.createAndShow();
    }

    private void createAndShow() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(760, 560));
        frame.setLayout(new BorderLayout(12, 12));
        frame.add(buildMainPanel(), BorderLayout.CENTER);
        frame.add(buildLogPanel(), BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildMainPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 14));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        fileList.setVisibleRowCount(8);
        fileList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setPreferredSize(new Dimension(640, 150));

        JButton addButton = new JButton("Add PDFs");
        addButton.addActionListener(event -> addFiles());
        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(event -> removeSelectedFiles());
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> fileModel.clear());

        JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fileButtons.add(addButton);
        fileButtons.add(removeButton);
        fileButtons.add(clearButton);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        panel.add(new JLabel("PDF files to redact"), gbc);
        gbc.gridy = 1;
        panel.add(fileScroll, gbc);
        gbc.gridy = 2;
        panel.add(fileButtons, gbc);

        JButton browseOutputButton = new JButton("Browse");
        browseOutputButton.addActionListener(event -> chooseOutputFolder());
        JPanel outputPanel = new JPanel(new BorderLayout(8, 0));
        outputPanel.add(outputField, BorderLayout.CENTER);
        outputPanel.add(browseOutputButton, BorderLayout.EAST);

        gbc.gridy = 3;
        panel.add(new JLabel("Save redacted PDFs to"), gbc);
        gbc.gridy = 4;
        panel.add(outputPanel, gbc);

        JButton browseTermsButton = new JButton("Browse");
        browseTermsButton.addActionListener(event -> chooseTermsFile());
        JPanel termsPanel = new JPanel(new BorderLayout(8, 0));
        termsPanel.add(termsField, BorderLayout.CENTER);
        termsPanel.add(browseTermsButton, BorderLayout.EAST);

        gbc.gridy = 5;
        panel.add(new JLabel("Optional custom terms file"), gbc);
        gbc.gridy = 6;
        panel.add(termsPanel, gbc);

        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        optionsPanel.add(new JLabel("DPI"));
        optionsPanel.add(dpiSpinner);
        optionsPanel.add(redactButton);
        redactButton.addActionListener(event -> startRedaction());

        gbc.gridy = 7;
        panel.add(optionsPanel, gbc);

        progressBar.setStringPainted(true);
        progressBar.setString("Ready");
        gbc.gridy = 8;
        panel.add(progressBar, gbc);
        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 14, 14, 14));
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private void addFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("PDF documents", "pdf"));
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        for (java.io.File file : chooser.getSelectedFiles()) {
            Path path = file.toPath();
            if (!containsFile(path)) {
                fileModel.addElement(path);
            }
        }
    }

    private boolean containsFile(Path path) {
        for (int i = 0; i < fileModel.size(); i++) {
            if (fileModel.get(i).equals(path)) {
                return true;
            }
        }
        return false;
    }

    private void removeSelectedFiles() {
        List<Path> selected = fileList.getSelectedValuesList();
        for (Path path : selected) {
            fileModel.removeElement(path);
        }
    }

    private void chooseOutputFolder() {
        JFileChooser chooser = new JFileChooser(outputField.getText().isBlank() ? DEFAULT_OUTPUT_DIR.toString() : outputField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            outputField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void chooseTermsFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            termsField.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void startRedaction() {
        List<Path> inputs = new ArrayList<>();
        for (int i = 0; i < fileModel.size(); i++) {
            inputs.add(fileModel.get(i));
        }
        if (inputs.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Add at least one PDF first.", "No PDFs selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path outputDir = Path.of(outputField.getText().trim());
        Path termsFile = termsField.getText().isBlank() ? null : Path.of(termsField.getText().trim());
        int dpi = (Integer) dpiSpinner.getValue();
        redactButton.setEnabled(false);
        progressBar.setMaximum(inputs.size());
        progressBar.setValue(0);
        progressBar.setString("Redacting...");
        logArea.setText("");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Files.createDirectories(outputDir);
                for (int i = 0; i < inputs.size(); i++) {
                    Path input = inputs.get(i);
                    Path output = nextOutputPath(outputDir, input);
                    publish("Redacting " + input + " -> " + output);
                    PdfPiiRedactor.RedactionReport report = PdfPiiRedactor.redact(input, output, termsFile, dpi);
                    publish("  redacted " + report.matchCount() + " match(es) across " + report.pagesWithMatches() + " page(s)");
                    setProgress(i + 1);
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String line : chunks) {
                    logArea.append(line + System.lineSeparator());
                }
            }

            @Override
            protected void done() {
                redactButton.setEnabled(true);
                try {
                    get();
                    progressBar.setValue(inputs.size());
                    progressBar.setString("Complete");
                    JOptionPane.showMessageDialog(frame, "Redacted PDFs were saved to " + outputDir, "Redaction complete", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    progressBar.setString("Failed");
                    String message = ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage();
                    logArea.append("Failed: " + message + System.lineSeparator());
                    JOptionPane.showMessageDialog(frame, message, "Redaction failed", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.addPropertyChangeListener(event -> {
            if ("progress".equals(event.getPropertyName())) {
                progressBar.setValue((Integer) event.getNewValue());
            }
        });
        worker.execute();
    }

    private static Path nextOutputPath(Path outputDir, Path input) {
        String fileName = input.getFileName().toString();
        String baseName = fileName.toLowerCase().endsWith(".pdf") ? fileName.substring(0, fileName.length() - 4) : fileName;
        Path candidate = outputDir.resolve(baseName + "-redacted.pdf");
        int counter = 2;
        while (Files.exists(candidate)) {
            candidate = outputDir.resolve(baseName + "-redacted-" + counter + ".pdf");
            counter++;
        }
        return candidate;
    }
}
