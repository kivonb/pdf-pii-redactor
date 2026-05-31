package local.redactor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PdfPiiRedactor {
    private static final int DEFAULT_DPI = 200;
    private static final float PADDING_POINTS = 1.8f;
    private static final List<Detector> DEFAULT_DETECTORS = List.of(
            new Detector("email", Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE)),
            new Detector("ssn", Pattern.compile("\\b\\d{3}[- ]?\\d{2}[- ]?\\d{4}\\b")),
            new Detector("phone", Pattern.compile("(?<!\\d)(?:\\+?1[ .-]?)?(?:\\(?\\d{3}\\)?[ .-]?)\\d{3}[ .-]?\\d{4}(?!\\d)")),
            new Detector("credit-card", Pattern.compile("(?<!\\d)(?:\\d[ -]?){13,19}(?!\\d)")),
            new Detector("date-of-birth-label", Pattern.compile("\\b(?:DOB|D\\.O\\.B\\.|Date of Birth|Birth Date)\\s*[:#-]?\\s*\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}\\b", Pattern.CASE_INSENSITIVE)),
            new Detector("us-passport-label", Pattern.compile("\\b(?:Passport|Passport No\\.?|Passport Number)\\s*[:#-]?\\s*[A-Z0-9]{6,9}\\b", Pattern.CASE_INSENSITIVE)),
            new Detector("driver-license-label", Pattern.compile("\\b(?:Driver'?s License|DL No\\.?|License No\\.?)\\s*[:#-]?\\s*[A-Z0-9 -]{5,20}\\b", Pattern.CASE_INSENSITIVE)),
            new Detector("bank-routing-label", Pattern.compile("\\b(?:Routing|ABA)\\s*(?:No\\.?|Number)?\\s*[:#-]?\\s*\\d{9}\\b", Pattern.CASE_INSENSITIVE)),
            new Detector("bank-account-label", Pattern.compile("\\b(?:Account|Acct)\\s*(?:No\\.?|Number|#)?\\s*[:#-]?\\s*[A-Z0-9-]{6,24}\\b", Pattern.CASE_INSENSITIVE))
    );

    private PdfPiiRedactor() {
    }

    public static void main(String[] args) {
        try {
            CliOptions options = CliOptions.parse(args);
            if (options.help) {
                printUsage();
                return;
            }
            RedactionReport report = redact(options);
            System.out.printf(Locale.ROOT,
                    "Wrote %s%nRedacted %d match(es) across %d page(s).%n",
                    options.output, report.matchCount, report.pagesWithMatches);
            if (report.matchCount == 0) {
                System.out.println("No built-in or custom PII patterns were found. For names and unusual IDs, rerun with --terms terms.txt.");
            }
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.err.println();
            printUsage();
            System.exit(2);
        } catch (Exception ex) {
            System.err.println("Redaction failed: " + ex.getMessage());
            System.exit(1);
        }
    }

    private static RedactionReport redact(CliOptions options) throws IOException {
        List<String> terms = readTerms(options.termsFile);
        try (PDDocument source = Loader.loadPDF(options.input.toFile());
             PDDocument output = new PDDocument()) {
            PDFRenderer renderer = new PDFRenderer(source);
            RedactionExtractor extractor = new RedactionExtractor(terms);
            extractor.setSortByPosition(true);
            int totalMatches = 0;
            int pagesWithMatches = 0;

            for (int pageIndex = 0; pageIndex < source.getNumberOfPages(); pageIndex++) {
                extractor.setStartPage(pageIndex + 1);
                extractor.setEndPage(pageIndex + 1);
                extractor.clear();
                extractor.getText(source);

                List<RedactionBox> boxes = extractor.boxes();
                if (!boxes.isEmpty()) {
                    pagesWithMatches++;
                    totalMatches += boxes.size();
                }

                BufferedImage image = renderer.renderImageWithDPI(pageIndex, options.dpi, ImageType.RGB);
                paintRedactions(image, boxes, options.dpi);
                addImagePage(source.getPage(pageIndex), output, image);
            }

            Files.createDirectories(options.output.toAbsolutePath().getParent());
            output.save(options.output.toFile());
            return new RedactionReport(totalMatches, pagesWithMatches);
        }
    }

    private static void paintRedactions(BufferedImage image, List<RedactionBox> boxes, int dpi) {
        double scale = dpi / 72.0;
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLACK);
            for (RedactionBox box : boxes) {
                int x = (int) Math.floor(box.left * scale);
                int y = (int) Math.floor(box.top * scale);
                int width = (int) Math.ceil((box.right - box.left) * scale);
                int height = (int) Math.ceil((box.bottom - box.top) * scale);
                graphics.fillRect(Math.max(0, x), Math.max(0, y), width, height);
            }
        } finally {
            graphics.dispose();
        }
    }

    private static void addImagePage(PDPage sourcePage, PDDocument output, BufferedImage image) throws IOException {
        PDRectangle mediaBox = sourcePage.getMediaBox();
        PDPage page = new PDPage(mediaBox);
        output.addPage(page);
        PDImageXObject pageImage = JPEGFactory.createFromImage(output, image, 0.92f);
        try (PDPageContentStream content = new PDPageContentStream(output, page)) {
            content.drawImage(pageImage, 0, 0, mediaBox.getWidth(), mediaBox.getHeight());
        }
    }

    private static List<String> readTerms(Path termsFile) throws IOException {
        if (termsFile == null) {
            return List.of();
        }
        List<String> terms = new ArrayList<>();
        for (String line : Files.readAllLines(termsFile, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                terms.add(trimmed);
            }
        }
        return terms;
    }

    private static void printUsage() {
        System.out.println("""
                Local PDF PII Redactor

                Usage:
                  java -jar target/pdf-pii-redactor-1.0.0.jar --input sensitive.pdf --output scrubbed.pdf [options]

                Options:
                  --input <pdf>      Source PDF to redact.
                  --output <pdf>     New PDF to write.
                  --terms <txt>      Optional UTF-8 file with one exact phrase per line to redact.
                  --dpi <number>     Render quality for the rewritten PDF. Default: 200.
                  --help             Show this help.

                Built-in detectors:
                  emails, SSNs, US phone numbers, credit card-like numbers that pass Luhn,
                  DOB labels, passport labels, driver license labels, bank routing labels,
                  and bank account labels.

                Notes:
                  This rewrites pages as images with black boxes, so covered text is not
                  left behind as selectable PDF text. Scanned PDFs need OCR before text can
                  be detected. Names and organization-specific IDs should go in --terms.
                """);
    }

    private record Detector(String name, Pattern pattern) {
    }

    private record RedactionReport(int matchCount, int pagesWithMatches) {
    }

    private static final class RedactionBox {
        private final float left;
        private final float top;
        private final float right;
        private final float bottom;

        private RedactionBox(float left, float top, float right, float bottom) {
            this.left = Math.max(0, left - PADDING_POINTS);
            this.top = Math.max(0, top - PADDING_POINTS);
            this.right = right + PADDING_POINTS;
            this.bottom = bottom + PADDING_POINTS;
        }
    }

    private static final class RedactionExtractor extends PDFTextStripper {
        private final List<String> terms;
        private final Set<String> seen = new LinkedHashSet<>();
        private final List<RedactionBox> boxes = new ArrayList<>();

        private RedactionExtractor(List<String> terms) throws IOException {
            this.terms = terms.stream().map(term -> term.toLowerCase(Locale.ROOT)).toList();
        }

        private void clear() {
            seen.clear();
            boxes.clear();
        }

        private List<RedactionBox> boxes() {
            return List.copyOf(boxes);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            detectBuiltIns(text, textPositions);
            detectTerms(text, textPositions);
        }

        private void detectBuiltIns(String text, List<TextPosition> positions) {
            for (Detector detector : DEFAULT_DETECTORS) {
                Matcher matcher = detector.pattern.matcher(text);
                while (matcher.find()) {
                    if ("credit-card".equals(detector.name) && !passesLuhn(matcher.group())) {
                        continue;
                    }
                    addBoxes(detector.name, matcher.start(), matcher.end(), positions);
                }
            }
        }

        private void detectTerms(String text, List<TextPosition> positions) {
            String lower = text.toLowerCase(Locale.ROOT);
            for (String term : terms) {
                int from = 0;
                while (from < lower.length()) {
                    int start = lower.indexOf(term, from);
                    if (start < 0) {
                        break;
                    }
                    addBoxes("term", start, start + term.length(), positions);
                    from = start + term.length();
                }
            }
        }

        private void addBoxes(String type, int start, int end, List<TextPosition> positions) {
            int safeStart = Math.max(0, Math.min(start, positions.size()));
            int safeEnd = Math.max(safeStart, Math.min(end, positions.size()));
            List<TextPosition> matched = positions.subList(safeStart, safeEnd).stream()
                    .filter(position -> !position.getUnicode().isBlank())
                    .toList();
            if (matched.isEmpty()) {
                return;
            }

            List<List<TextPosition>> lines = splitByLine(matched);
            for (List<TextPosition> line : lines) {
                float left = Float.MAX_VALUE;
                float top = Float.MAX_VALUE;
                float right = 0;
                float bottom = 0;
                for (TextPosition position : line) {
                    left = Math.min(left, position.getXDirAdj());
                    top = Math.min(top, position.getYDirAdj() - position.getHeightDir());
                    right = Math.max(right, position.getXDirAdj() + position.getWidthDirAdj());
                    bottom = Math.max(bottom, position.getYDirAdj());
                }
                String key = type + ":" + Math.round(left) + ":" + Math.round(top) + ":" + Math.round(right) + ":" + Math.round(bottom);
                if (seen.add(key)) {
                    boxes.add(new RedactionBox(left, top, right, bottom));
                }
            }
        }

        private static List<List<TextPosition>> splitByLine(List<TextPosition> positions) {
            List<TextPosition> sorted = positions.stream()
                    .sorted(Comparator.comparing(TextPosition::getYDirAdj).thenComparing(TextPosition::getXDirAdj))
                    .toList();
            List<List<TextPosition>> lines = new ArrayList<>();
            for (TextPosition position : sorted) {
                if (lines.isEmpty()) {
                    lines.add(new ArrayList<>(List.of(position)));
                    continue;
                }
                List<TextPosition> line = lines.get(lines.size() - 1);
                float currentY = line.get(0).getYDirAdj();
                if (Math.abs(position.getYDirAdj() - currentY) <= Math.max(2.5f, position.getHeightDir() * 0.35f)) {
                    line.add(position);
                } else {
                    lines.add(new ArrayList<>(List.of(position)));
                }
            }
            return lines;
        }
    }

    private static boolean passesLuhn(String value) {
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < 13 || digits.length() > 19) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            if (doubleDigit) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }
            sum += digit;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private static final class CliOptions {
        private Path input;
        private Path output;
        private Path termsFile;
        private int dpi = DEFAULT_DPI;
        private boolean help;

        private static CliOptions parse(String[] args) {
            CliOptions options = new CliOptions();
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--help", "-h" -> options.help = true;
                    case "--input", "-i" -> options.input = nextPath(args, ++i, "--input");
                    case "--output", "-o" -> options.output = nextPath(args, ++i, "--output");
                    case "--terms", "-t" -> options.termsFile = nextPath(args, ++i, "--terms");
                    case "--dpi" -> options.dpi = nextInt(args, ++i, "--dpi");
                    default -> throw new IllegalArgumentException("Unknown argument: " + args[i]);
                }
            }
            if (options.help) {
                return options;
            }
            if (options.input == null) {
                throw new IllegalArgumentException("--input is required.");
            }
            if (options.output == null) {
                throw new IllegalArgumentException("--output is required.");
            }
            if (!Files.isRegularFile(options.input)) {
                throw new IllegalArgumentException("Input PDF does not exist: " + options.input);
            }
            if (options.output.toAbsolutePath().getParent() == null) {
                throw new IllegalArgumentException("Output must include a writable parent path.");
            }
            if (options.termsFile != null && !Files.isRegularFile(options.termsFile)) {
                throw new IllegalArgumentException("Terms file does not exist: " + options.termsFile);
            }
            if (options.dpi < 96 || options.dpi > 600) {
                throw new IllegalArgumentException("--dpi must be between 96 and 600.");
            }
            return options;
        }

        private static Path nextPath(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            return Path.of(args[index]);
        }

        private static int nextInt(String[] args, int index, String option) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " requires a value.");
            }
            try {
                return Integer.parseInt(args[index]);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(option + " must be a number.");
            }
        }
    }
}
