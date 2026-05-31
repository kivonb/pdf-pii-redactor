# Local PDF PII Redactor

This is a local-only command line tool for scrubbing common PII from PDFs and writing a new PDF.

It detects:

- Email addresses
- US SSNs
- US phone numbers
- Credit card-like numbers that pass a Luhn check
- DOB, passport, driver license, bank routing, and bank account values when they appear with labels
- Exact custom phrases from a terms file

The output PDF is rebuilt as page images with black redaction boxes painted into the pixels. That means redacted text is not left behind as selectable or copyable PDF text.

## Build

From this folder:

```powershell
..\.tools\maven\apache-maven-3.9.9\bin\mvn.cmd clean package
```

## Run

```powershell
..\.tools\java\jdk-21.0.11+10\bin\java.exe -jar target\pdf-pii-redactor-1.0.0.jar --input C:\path\sensitive.pdf --output C:\path\scrubbed.pdf
```

Add exact names, addresses, account IDs, or other known sensitive phrases with a UTF-8 text file:

```powershell
..\.tools\java\jdk-21.0.11+10\bin\java.exe -jar target\pdf-pii-redactor-1.0.0.jar --input C:\path\sensitive.pdf --output C:\path\scrubbed.pdf --terms terms.txt
```

Each `terms.txt` line is redacted exactly, case-insensitively. Blank lines and lines beginning with `#` are ignored.

## Important Limits

- Scanned PDFs need OCR first. This tool detects text that already exists in the PDF text layer.
- Names and street addresses are context-dependent, so use `--terms` for those.
- Always inspect the output PDF before sharing it.
- Keep the original PDF in a secure location until you have verified the scrubbed copy.
