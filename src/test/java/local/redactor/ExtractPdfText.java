package local.redactor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;

final class ExtractPdfText {
    private ExtractPdfText() {
    }

    public static void main(String[] args) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File(args[0]))) {
            System.out.print(new PDFTextStripper().getText(doc));
        }
    }
}
