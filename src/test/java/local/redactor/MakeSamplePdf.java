package local.redactor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.File;

final class MakeSamplePdf {
    private MakeSamplePdf() {
    }

    public static void main(String[] args) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 14);
                content.newLineAtOffset(72, 720);
                content.showText("Patient: Jane Example");
                content.newLineAtOffset(0, -24);
                content.showText("Email: jane.example@example.com SSN: 123-45-6789 Phone: (212) 555-0199");
                content.newLineAtOffset(0, -24);
                content.showText("Card: 4111 1111 1111 1111 DOB: 01/02/1980");
                content.endText();
            }
            doc.save(new File(args[0]));
        }
    }
}
