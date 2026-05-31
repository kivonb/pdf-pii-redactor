package local.redactor;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class PdfPiiRedactorUi {
    private static final Path DEFAULT_OUTPUT_DIR = Path.of("E:\\Redacted");

    private final JFrame frame = new JFrame("PDF PII Redactor");
    private final List<Path> inputFiles = new ArrayList<>();
    private final DefaultTableModel fileTableModel = new DefaultTableModel(new Object[]{"File", "Folder"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable fileTable = new JTable(fileTableModel);
    private final JTextField outputField = new JTextField(DEFAULT_OUTPUT_DIR.toString());
    private final JTextField termsField = new JTextField();
    private final JSpinner dpiSpinner = new JSpinner(new SpinnerNumberModel(200, 96, 600, 25));
    private final JTextArea logArea = new JTextArea(7, 60);
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JButton redactButton = new JButton("Redact PDFs");
    private final JButton openOutputButton = new JButton("Open Output");

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
        frame.setMinimumSize(new Dimension(880, 620));
        frame.setLayout(new BorderLayout(14, 14));
        frame.add(buildHeaderPanel(), BorderLayout.NORTH);
        frame.add(buildCenterPanel(), BorderLayout.CENTER);
        frame.add(buildBottomPanel(), BorderLayout.SOUTH);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 0, 18));

        JLabel title = new JLabel("PDF PII Redactor");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel subtitle = new JLabel("Local PDF redaction. Output defaults to " + DEFAULT_OUTPUT_DIR);
        subtitle.setForeground(new Color(80, 80, 80));

        JPanel text = new JPanel(new BorderLayout(0, 4));
        text.add(title, BorderLayout.NORTH);
        text.add(subtitle, BorderLayout.SOUTH);
        panel.add(text, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 18, 0, 18));
        panel.add(buildFilePanel(), BorderLayout.CENTER);
        panel.add(buildSettingsPanel(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFilePanel() {
        fileTable.setFillsViewportHeight(true);
        fileTable.setRowHeight(24);
        fileTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setAutoCreateRowSorter(true);
        TableColumnModel columns = fileTable.getColumnModel();
        columns.getColumn(0).setPreferredWidth(260);
        columns.getColumn(1).setPreferredWidth(520);

        JButton addButton = new JButton("Add PDFs");
        addButton.addActionListener(event -> addFiles());
        JButton removeButton = new JButton("Remove Selected");
        removeButton.addActionListener(event -> removeSelectedFiles());
        JButton clearButton = new JButton("Clear");
        clearButton.addActionListener(event -> clearFiles());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(addButton);
        buttons.add(removeButton);
        buttons.add(clearButton);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createTitledBorder("PDF files"));
        panel.add(new JScrollPane(fileTable), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSettingsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Redaction settings"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JButton browseOutputButton = new JButton("Browse");
        browseOutputButton.addActionListener(event -> chooseOutputFolder());
        JPanel outputRow = fieldWithButton(outputField, browseOutputButton);

        JButton browseTermsButton = new JButton("Browse");
        browseTermsButton.addActionListener(event -> chooseTermsFile());
        JPanel termsRow = fieldWithButton(termsField, browseTermsButton);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        panel.add(new JLabel("Save to"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(outputRow, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        panel.add(new JLabel("Terms file"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(termsRow, gbc);

        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actionRow.add(new JLabel("DPI"));
        actionRow.add(dpiSpinner);
        actionRow.add(redactButton);
        actionRow.add(openOutputButton);
        redactButton.addActionListener(event -> startRedaction());
        openOutputButton.addActionListener(event -> openOutputFolder());

        gbc.gridx = 1;
        gbc.gridy = 2;
        gbc.weightx = 1;
        panel.add(actionRow, gbc);
        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(0, 18, 16, 18));

        progressBar.setStringPainted(true);
        progressBar.setString("Ready");

        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        panel.add(progressBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private static JPanel fieldWithButton(JTextField field, JButton button) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.add(field, BorderLayout.CENTER);
        panel.add(button, BorderLayout.EAST);
        return panel;
    }

    private void addFiles() {
        FileDialog dialog = new FileDialog(frame, "Select PDF files", FileDialog.LOAD);
        dialog.setMultipleMode(true);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".pdf"));
        dialog.setVisible(true);
        File[] files = dialog.getFiles();
        dialog.dispose();

        for (File file : files) {
            Path path = file.toPath();
            if (!inputFiles.contains(path)) {
                inputFiles.add(path);
            }
        }
        inputFiles.sort(Comparator.comparing(Path::toString));
        refreshFileTable();
        refreshFrame();
    }

    private void removeSelectedFiles() {
        int[] selectedRows = fileTable.getSelectedRows();
        List<Path> selectedPaths = new ArrayList<>();
        for (int row : selectedRows) {
            int modelRow = fileTable.convertRowIndexToModel(row);
            selectedPaths.add(inputFiles.get(modelRow));
        }
        inputFiles.removeAll(selectedPaths);
        refreshFileTable();
    }

    private void clearFiles() {
        inputFiles.clear();
        refreshFileTable();
    }

    private void refreshFileTable() {
        fileTableModel.setRowCount(0);
        for (Path path : inputFiles) {
            Path parent = path.getParent();
            fileTableModel.addRow(new Object[]{
                    path.getFileName(),
                    parent == null ? "" : parent.toString()
            });
        }
    }

    private void chooseOutputFolder() {
        JFileChooser chooser = new JFileChooser(outputField.getText().isBlank() ? DEFAULT_OUTPUT_DIR.toString() : outputField.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            outputField.setText(chooser.getSelectedFile().toPath().toString());
        }
        refreshFrame();
    }

    private void chooseTermsFile() {
        FileDialog dialog = new FileDialog(frame, "Select terms text file", FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".txt"));
        dialog.setVisible(true);
        File[] files = dialog.getFiles();
        dialog.dispose();
        if (files.length > 0) {
            termsField.setText(files[0].toPath().toString());
        }
        refreshFrame();
    }

    private void startRedaction() {
        if (inputFiles.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Add at least one PDF first.", "No PDFs selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path outputDir = Path.of(outputField.getText().trim().isEmpty() ? DEFAULT_OUTPUT_DIR.toString() : outputField.getText().trim());
        Path termsFile = termsField.getText().isBlank() ? null : Path.of(termsField.getText().trim());
        int dpi = (Integer) dpiSpinner.getValue();
        List<Path> filesToRedact = List.copyOf(inputFiles);

        setControlsEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Redacting...");
        logArea.setText("");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                Files.createDirectories(outputDir);
                for (int i = 0; i < filesToRedact.size(); i++) {
                    Path input = filesToRedact.get(i);
                    Path output = nextOutputPath(outputDir, input);
                    publish("Redacting " + input);
                    publish("  -> " + output);
                    PdfPiiRedactor.RedactionReport report = PdfPiiRedactor.redact(input, output, termsFile, dpi);
                    publish("  redacted " + report.matchCount() + " match(es) across " + report.pagesWithMatches() + " page(s)");
                    setProgress(Math.round(((i + 1) * 100f) / filesToRedact.size()));
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
                setControlsEnabled(true);
                try {
                    get();
                    progressBar.setValue(100);
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

    private void setControlsEnabled(boolean enabled) {
        redactButton.setEnabled(enabled);
        fileTable.setEnabled(enabled);
        outputField.setEnabled(enabled);
        termsField.setEnabled(enabled);
        dpiSpinner.setEnabled(enabled);
    }

    private void openOutputFolder() {
        try {
            Path outputDir = Path.of(outputField.getText().trim().isEmpty() ? DEFAULT_OUTPUT_DIR.toString() : outputField.getText().trim());
            Files.createDirectories(outputDir);
            Desktop.getDesktop().open(outputDir.toFile());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(frame, ex.getMessage(), "Cannot open output folder", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshFrame() {
        SwingUtilities.invokeLater(() -> {
            frame.invalidate();
            frame.validate();
            frame.repaint();
            frame.toFront();
        });
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
