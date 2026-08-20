package br.com.ecad.captacao.processingengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import br.com.ecad.captacao.shared.infrastructure.blob.BlobDownload;
import br.com.ecad.captacao.shared.infrastructure.blob.BlobStorageService;

import org.junit.jupiter.api.Test;

class DocumentContentReaderTest {
    @Test
    void extraiTextoConfiavelDePdfSemAnexarMidia() throws Exception {
        var pdfBytes = createSimplePdf("Contrato para apresentacao de show musical da Banda Solar em 12/08/2026 21:00 na praca central");
        var reader = new DocumentContentReader(new FakeBlobStorageService(pdfBytes, "application/pdf"));

        var payload = reader.read("https://blob/staging/documento.pdf");

        assertThat(payload.isPdf()).isTrue();
        assertThat(payload.hasUsableText()).isTrue();
        assertThat(payload.mediaBytes()).isNull();
        assertThat(payload.textContent()).contains("Banda Solar").contains("12/08/2026");
    }

    @Test
    void mantemPdfComoMidiaQuandoTextoNaoForConfiavel() throws Exception {
        var pdfBytes = "%PDF-1.4\nobj\nstream\ncontrato show banda\nendstream\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        var reader = new DocumentContentReader(new FakeBlobStorageService(pdfBytes, "application/pdf"));

        var payload = reader.read("https://blob/staging/documento.pdf");

        assertThat(payload.isPdf()).isTrue();
        assertThat(payload.hasUsableText()).isFalse();
        assertThat(payload.mediaBytes()).isEqualTo(pdfBytes);
        assertThat(payload.textContent()).isEmpty();
    }

    private static byte[] createSimplePdf(String text) throws Exception {
        var stream = new ByteArrayOutputStream();
        var offsets = new ArrayList<Integer>();
        offsets.add(0);
        writeAscii(stream, "%PDF-1.4\n");
        writeObject(stream, offsets, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");
        writeObject(stream, offsets, "2 0 obj\n<< /Type /Pages /Count 1 /Kids [3 0 R] >>\nendobj\n");
        writeObject(stream, offsets, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>\nendobj\n");
        var escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        var contentStream = "BT\n/F1 12 Tf\n72 720 Td\n(" + escaped + ") Tj\nET";
        writeObject(stream, offsets, "4 0 obj\n<< /Length " + contentStream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n" + contentStream + "\nendstream\nendobj\n");
        writeObject(stream, offsets, "5 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n");
        writeAscii(stream, "trailer\n<< /Size " + offsets.size() + " /Root 1 0 R >>\n%%EOF");
        return stream.toByteArray();
    }

    private static void writeObject(ByteArrayOutputStream stream, ArrayList<Integer> offsets, String value) throws Exception {
        offsets.add(stream.size());
        writeAscii(stream, value);
    }

    private static void writeAscii(ByteArrayOutputStream stream, String value) throws Exception {
        stream.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private record FakeBlobStorageService(byte[] content, String contentType) implements BlobStorageService {
        @Override
        public BlobDownload download(String blobUrl) {
            return new BlobDownload(content, contentType);
        }

        @Override
        public String moveToProduction(String stagingUrl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String uploadStaging(byte[] content, String stagingPath, String fileName) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String blobUrl) {
            throw new UnsupportedOperationException();
        }
    }
}
