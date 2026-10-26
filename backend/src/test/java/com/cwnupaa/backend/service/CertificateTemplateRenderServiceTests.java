package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.CertificateGrant;
import com.cwnupaa.backend.entity.CertificateType;
import com.cwnupaa.backend.entity.CertificateIssue;
import com.cwnupaa.backend.entity.CertificateIssueMember;
import com.cwnupaa.backend.mapper.CertificateGrantMapper;
import com.cwnupaa.backend.mapper.CertificateTypeMapper;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.mapper.CertificateIssueMapper;
import com.cwnupaa.backend.mapper.CertificateIssueMemberMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.time.LocalDate;
import java.awt.image.BufferedImage;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CertificateTemplateRenderServiceTests {
    @TempDir
    Path tempDir;

    private CertificateTemplateRenderService service;
    private FileSystemMapper fileMapper;

    @BeforeEach
    void setUp() throws Exception {
        service = new CertificateTemplateRenderService();
        fileMapper = mock(FileSystemMapper.class);
        ReflectionTestUtils.setField(service, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

        Path pdf = tempDir.resolve("blank.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(pdf.toFile());
        }
        FileSystem file = new FileSystem();
        file.setFileId(1L);
        file.setName("blank.pdf");
        file.setUrl("/uploads/blank.pdf");
        when(fileMapper.selectById(1L)).thenReturn(file);

        Path multiPagePdf = tempDir.resolve("multi-page.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            document.addPage(new PDPage(PDRectangle.A4));
            document.save(multiPagePdf.toFile());
        }
        FileSystem multiPageFile = new FileSystem();
        multiPageFile.setFileId(2L);
        multiPageFile.setName("multi-page.pdf");
        multiPageFile.setUrl("/uploads/multi-page.pdf");
        when(fileMapper.selectById(2L)).thenReturn(multiPageFile);
    }

    @Test
    void rendersPlaceholderFlowAndBackgroundImage() throws Exception {
        String schema = """
                {"version":1,"blocks":[
                  {"id":"main","content":"恭喜<name>同学获得<award>","x":10,"y":35,"width":80,
                   "fontSize":28,"minFontSize":12,"fontFamily":"SIMKAI","alignment":"CENTER",
                   "color":"#000000","autoFit":true},
                  {"id":"meta","content":"<number>\\n<date>","x":60,"y":80,"width":35,
                   "fontSize":14,"minFontSize":10,"fontFamily":"SIMKAI","alignment":"RIGHT",
                   "color":"#333333","autoFit":true}
                ]}
                """;

        byte[] pdf = service.renderPreview(1L, schema, false, LocalDate.of(2026, 7, 16));
        byte[] png = service.renderBackground(1L);

        assertThat(pdf.length).isGreaterThan(5_000);
        try (PDDocument rendered = PDDocument.load(pdf)) {
            assertThat(rendered.getNumberOfPages()).isEqualTo(1);
            assertImageOnly(rendered);
        }
        assertThat(png).startsWith((byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47);
    }

    @Test
    void rejectsEmptyTemplateSchema() {
        assertThatThrownBy(() -> service.normalizeAndValidateSchema("{\"version\":1,\"blocks\":[]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("文字占位符");
    }

    @Test
    void rejectsMultiPagePdfTemplate() {
        assertThatThrownBy(() -> service.validateSinglePagePdf(2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只能包含 1 页");
    }

    @Test
    void oldSchemasDefaultToPlainTextAndExplicitStyleFlagsArePreserved() throws Exception {
        ObjectMapper json = new ObjectMapper();
        var legacy = json.readTree(service.normalizeAndValidateSchema("{\"blocks\":[{\"content\":\"旧模板\"}]}"));
        assertThat(legacy.at("/blocks/0/bold").asBoolean()).isFalse();
        assertThat(legacy.at("/blocks/0/underline").asBoolean()).isFalse();
        for (boolean bold : List.of(false, true)) {
            for (boolean underline : List.of(false, true)) {
                var normalized = json.readTree(service.normalizeAndValidateSchema(schema(List.of(block("SIMKAI", bold, underline, 30)))));
                assertThat(normalized.at("/blocks/0/bold").asBoolean()).isEqualTo(bold);
                assertThat(normalized.at("/blocks/0/underline").asBoolean()).isEqualTo(underline);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SIMSUN", "SIMKAI", "STXINGKA"})
    void boldAndUnderlineRemainVisibleInTheImageOnlyPdf(String family) throws Exception {
        long plainInk = ink(render(schema(List.of(block(family, false, false, 30)))), false);
        long boldInk = ink(render(schema(List.of(block(family, true, false, 30)))), false);
        long underlineInk = ink(render(schema(List.of(block(family, false, true, 30)))), false);
        long combinedInk = ink(render(schema(List.of(block(family, true, true, 30)))), false);
        assertThat(boldInk).isGreaterThan(plainInk);
        assertThat(underlineInk).isGreaterThan(plainInk);
        assertThat(combinedInk).isGreaterThan(boldInk).isGreaterThan(underlineInk);

        // Optional visual QA artifacts, kept outside the repository and production data.
        String previewDir = System.getProperty("certificate.styles.previewDir");
        if (previewDir != null) {
            List<Map<String, Object>> blocks = new ArrayList<>();
            String[] labels = {"常规", "加粗", "下划线", "加粗与下划线"};
            String[] alignments = {"LEFT", "CENTER", "RIGHT", "CENTER"};
            for (int index = 0; index < 4; index++) {
                Map<String, Object> block = block(family, index == 1 || index == 3, index >= 2, 15 + index * 15);
                block.put("alignment", alignments[index]);
                block.put("content", labels[index] + "：获奖人 张三 Agyp" + (index == 3 ? "\n换行后的文字同样保留样式" : ""));
                blocks.add(block);
            }
            Map<String, Object> fitted = block(family, true, true, 85);
            fitted.put("width", 35);
            fitted.put("fontSize", 48);
            fitted.put("content", "自适应：获奖人 张三");
            blocks.add(fitted);
            Files.createDirectories(Path.of(previewDir));
            Files.write(Path.of(previewDir, "styles-" + family + ".pdf"), render(schema(blocks)));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "CENTER", "RIGHT"})
    void styledTextAndUnderlineStayInsideTheBlockAfterAutoFit(String alignment) throws Exception {
        Map<String, Object> block = block("SIMKAI", true, true, 30);
        block.put("x", 20);
        block.put("width", 12);
        block.put("fontSize", 60);
        block.put("minFontSize", 6);
        block.put("alignment", alignment);
        try (PDDocument pdf = PDDocument.load(render(schema(List.of(block))))) {
            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 144);
            int minX = image.getWidth(), maxX = -1;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if (dark(image.getRGB(x, y))) { minX = Math.min(minX, x); maxX = Math.max(maxX, x); }
                }
            }
            assertThat(maxX).isGreaterThan(minX);
            assertThat(minX).isGreaterThanOrEqualTo((int) (image.getWidth() * .20) - 2);
            assertThat(maxX).isLessThanOrEqualTo((int) (image.getWidth() * .32) + 2);
        }
    }

    @Test
    void stylesDoNotLeakIntoAFollowingPlainTextBlock() throws Exception {
        Map<String, Object> styled = block("SIMKAI", true, true, 20);
        Map<String, Object> plain = block("SIMKAI", false, false, 65);
        assertThat(ink(render(schema(List.of(styled, plain))), true))
                .isEqualTo(ink(render(schema(List.of(plain))), true));
    }

    @Test
    void anEmptyPlaceholderDoesNotLeaveAnUnderline() throws Exception {
        Map<String, Object> block = block("SIMKAI", true, true, 30);
        block.put("content", "<member10name>");
        try (PDDocument pdf = PDDocument.load(render(schema(List.of(block))))) {
            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 72);
            long count = 0;
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) if (dark(image.getRGB(x, y))) count++;
            }
            assertThat(count).isZero();
        }
    }

    @Test
    void rebuildingThePdfDropsSourceTextFormsAnnotationsAndMetadata() throws Exception {
        try (PDDocument source = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            source.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(source, page)) {
                content.setNonStrokingColor(Color.BLUE);
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 32);
                content.newLineAtOffset(100, 500);
                content.showText("CERTIFICATE");
                content.endText();
            }
            PDAcroForm form = new PDAcroForm(source);
            PDTextField field = new PDTextField(form);
            field.setPartialName("hidden-source-field");
            form.getFields().add(field);
            source.getDocumentCatalog().setAcroForm(form);
            PDAnnotationLink link = new PDAnnotationLink();
            link.setRectangle(new PDRectangle(100, 100, 50, 20));
            page.getAnnotations().add(link);
            source.getDocumentInformation().setCustomMetadataValue("source-private-data", "discard me");
            try (PDDocument scanned = PDDocument.load(service.renderImageOnlyPdf(source))) {
                assertImageOnly(scanned);
                assertThat(scanned.getDocumentInformation().getCustomMetadataValue("source-private-data")).isNull();
                PDImageXObject image = pageImage(scanned.getPage(0));
                assertThat(image.getWidth()).isGreaterThan(2400);
                assertThat(image.getHeight()).isGreaterThan(3400);
            }
            assertThat(new PDFTextStripper().getText(source)).contains("CERTIFICATE");
            assertThat(source.getDocumentCatalog().getAcroForm().getFields()).hasSize(1);
        }
    }

    @Test
    void preservesTheVisibleCropRotationAndPhysicalPageSize() throws Exception {
        try (PDDocument source = new PDDocument()) {
            source.setVersion(1.7f);
            PDPage page = new PDPage(new PDRectangle(400, 300));
            page.setCropBox(new PDRectangle(40, 30, 180, 110));
            page.setRotation(90);
            page.setUserUnit(2);
            source.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(source, page)) {
                content.setNonStrokingColor(Color.RED);
                content.addRect(50, 40, 40, 20);
                content.fill();
            }
            try (PDDocument scanned = PDDocument.load(service.renderImageOnlyPdf(source))) {
                assertImageOnly(scanned);
                PDPage output = scanned.getPage(0);
                assertThat(output.getMediaBox().getWidth()).isEqualTo(110);
                assertThat(output.getMediaBox().getHeight()).isEqualTo(180);
                assertThat(output.getRotation()).isZero();
                assertThat(output.getUserUnit()).isEqualTo(2);
                assertThat(pageImage(output).getWidth()).isBetween(914, 918);
                assertThat(pageImage(output).getHeight()).isBetween(1498, 1502);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void boundsRasterMemoryForLargeOrVeryWidePages(boolean wide) throws Exception {
        try (PDDocument source = new PDDocument()) {
            source.addPage(new PDPage(new PDRectangle(wide ? 20000 : 10000, wide ? 100 : 10000)));
            try (PDDocument scanned = PDDocument.load(service.renderImageOnlyPdf(source))) {
                PDImageXObject image = pageImage(scanned.getPage(0));
                assertThat((long) image.getWidth() * image.getHeight()).isLessThanOrEqualTo(12_000_000);
                assertThat(Math.max(image.getWidth(), image.getHeight())).isLessThanOrEqualTo(8192);
            }
        }
    }

    @Test
    void issuedImageCertificatesKeepTheirVerificationQrCodeReadable() throws Exception {
        CertificateIssue issue = new CertificateIssue();
        issue.setCertificateNo("CERT-IMAGE-001");
        issue.setIssueDate(LocalDate.of(2026, 10, 22));
        CertificateIssueMember member = new CertificateIssueMember();
        member.setMemberName("张三");
        member.setStudentId("001");
        writeTexturedBackground(tempDir.resolve("blank.pdf"));
        Map<String, Object> text = block("SIMKAI", true, true, 30);
        text.put("content", "获奖人：<name>");
        String schema = new ObjectMapper().writeValueAsString(Map.of("blocks", List.of(text),
                "qrCode", Map.of("enabled", true, "x", 72, "y", 72, "size", 18)));
        byte[] bytes = service.renderIssuedCertificate(tempDir.resolve("blank.pdf").toFile(), schema, issue, List.of(member));
        try (PDDocument scanned = PDDocument.load(bytes)) {
            assertImageOnly(scanned);
            assertThat(pageImage(scanned.getPage(0)).getCOSObject().getDictionaryObject(COSName.FILTER))
                    .isEqualTo(COSName.DCT_DECODE);
            BufferedImage image = pageImage(scanned.getPage(0)).getImage();
            int width = image.getWidth(), height = image.getHeight();
            RGBLuminanceSource pixels = new RGBLuminanceSource(width, height, image.getRGB(0, 0, width, height, null, 0, width));
            var result = new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(pixels)));
            assertThat(result.getText()).isEqualTo("https://example.com/certificate/verify?number=CERT-IMAGE-001");
        }
    }

    @Test
    void photographicBackgroundUsesCompactJpegAndKeepsPrintResolution() throws Exception {
        writeTexturedBackground(tempDir.resolve("blank.pdf"));
        byte[] bytes = render(schema(List.of(block("SIMKAI", true, true, 30))));
        // The same textured 300 DPI page occupies several MB with lossless compression.
        assertThat(bytes.length).isLessThan(2_000_000);
        try (PDDocument scanned = PDDocument.load(bytes)) {
            assertImageOnly(scanned);
            PDImageXObject image = pageImage(scanned.getPage(0));
            assertThat(image.getWidth()).isGreaterThan(2400);
            assertThat(image.getHeight()).isGreaterThan(3400);
            assertThat(image.getCOSObject().getDictionaryObject(COSName.FILTER)).isEqualTo(COSName.DCT_DECODE);
        }
    }

    private void writeTexturedBackground(Path file) throws Exception {
        var image = new BufferedImage(800, 1100, BufferedImage.TYPE_INT_RGB);
        var random = new Random(41);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, new Color(225 + random.nextInt(26), 225 + random.nextInt(26),
                        225 + random.nextInt(26)).getRGB());
            }
        }
        try (var pdf = new PDDocument()) {
            var page = new PDPage(PDRectangle.A4);
            pdf.addPage(page);
            try (var stream = new PDPageContentStream(pdf, page)) {
                stream.drawImage(org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory.createFromImage(pdf, image),
                        0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight());
            }
            pdf.save(file.toFile());
        } finally { image.flush(); }
    }

    @Test
    void cachedPreviewsNoticeTemplateContentDatesAndFileChanges() throws Exception {
        var observed = spy(service);
        doReturn(new byte[]{1}).when(observed).renderImageOnlyPdf(any());
        Map<String, Object> block = block("SIMKAI", false, false, 30);
        block.put("content", "获奖人：<name>");
        String first = schema(List.of(block));
        String second = first.replace("<name>", "<name> changed");
        var date = LocalDate.of(2026, 10, 22);
        observed.renderPreview(1L, first, false, date);
        observed.renderPreview(1L, first, false, date);
        verify(observed, times(1)).renderImageOnlyPdf(any());
        observed.renderPreview(1L, second, false, date);
        verify(observed, times(2)).renderImageOnlyPdf(any());
        String dated = first.replace("<name>", "<name> <date>");
        observed.renderPreview(1L, dated, false, date);
        observed.renderPreview(1L, dated, false, date.plusDays(1));
        verify(observed, times(4)).renderImageOnlyPdf(any());
        var file = tempDir.resolve("blank.pdf");
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(
                Files.getLastModifiedTime(file).toMillis() + 2000));
        observed.renderPreview(1L, first, false, date);
        verify(observed, times(5)).renderImageOnlyPdf(any());
    }

    @Test
    void cachedDownloadsStillRejectUnauthorizedDeletedAndRevokedCertificates() throws Exception {
        var grant = new CertificateGrant();
        grant.setCertificateTypeId(9L);
        grant.setIssueId(7L);
        var type = new CertificateType();
        type.setUrlId(1L);
        type.setTemplateSchema(schema(List.of(block("SIMKAI", true, true, 30))));
        var issue = new CertificateIssue();
        issue.setStatus("active");
        var grants = mock(CertificateGrantMapper.class);
        var types = mock(CertificateTypeMapper.class);
        var issues = mock(CertificateIssueMapper.class);
        var members = mock(CertificateIssueMemberMapper.class);
        when(grants.selectById(8L)).thenReturn(grant);
        when(types.selectById(9L)).thenReturn(type);
        when(issues.selectById(7L)).thenReturn(issue);
        when(members.selectList(any())).thenReturn(List.of());
        var render = spy(service);
        doReturn(new byte[]{1}).when(render).renderImageOnlyPdf(any());
        var grantsService = new CertificateGrantService();
        ReflectionTestUtils.setField(grantsService, "grantMapper", grants);
        ReflectionTestUtils.setField(grantsService, "typeMapper", types);
        ReflectionTestUtils.setField(grantsService, "issueMapper", issues);
        ReflectionTestUtils.setField(grantsService, "issueMemberMapper", members);
        ReflectionTestUtils.setField(grantsService, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(grantsService, "templateRenderService", render);
        grantsService.generatePdf(8L, record -> true);
        grantsService.generatePdf(8L, record -> true);
        verify(render, times(1)).renderImageOnlyPdf(any());
        assertThatThrownBy(() -> grantsService.generatePdf(8L, record -> false))
                .isInstanceOf(CertificateGrantService.CertificateAccessDeniedException.class);
        issue.setStatus("revoked");
        assertThatThrownBy(() -> grantsService.generatePdf(8L, record -> true)).hasMessageContaining("已撤销");
        when(grants.selectById(8L)).thenReturn(null);
        assertThatThrownBy(() -> grantsService.generatePdf(8L, record -> true)).hasMessageContaining("记录不存在");
        verify(render, times(2)).renderIssuedCertificate(any(), any(), any(), any());
        verify(render, times(1)).renderImageOnlyPdf(any());
    }

    @Test
    void legacyFormCertificatesAlsoExportAsWholePageImages() throws Exception {
        Path path = tempDir.resolve("legacy.pdf");
        try (PDDocument source = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            source.addPage(page);
            PDAcroForm form = new PDAcroForm(source);
            PDResources resources = new PDResources();
            resources.put(COSName.getPDFName("Helv"), PDType1Font.HELVETICA);
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 24 Tf 0 g");
            source.getDocumentCatalog().setAcroForm(form);
            PDTextField name = new PDTextField(form);
            name.setPartialName("Name");
            form.getFields().add(name);
            var widget = name.getWidgets().get(0);
            widget.setRectangle(new PDRectangle(100, 300, 300, 40));
            widget.setPage(page);
            page.getAnnotations().add(widget);
            source.save(path.toFile());
        }
        FileSystem file = new FileSystem();
        file.setFileId(3L);
        file.setName("legacy.pdf");
        file.setUrl("/uploads/legacy.pdf");
        when(fileMapper.selectById(3L)).thenReturn(file);
        CertificateGrant grant = new CertificateGrant();
        grant.setCertificateTypeId(9L);
        grant.setRealName("张三");
        CertificateType type = new CertificateType();
        type.setUrlId(3L);
        CertificateGrantMapper grants = mock(CertificateGrantMapper.class);
        CertificateTypeMapper types = mock(CertificateTypeMapper.class);
        when(grants.selectById(8L)).thenReturn(grant);
        when(types.selectById(9L)).thenReturn(type);
        CertificateGrantService legacy = new CertificateGrantService();
        ReflectionTestUtils.setField(legacy, "grantMapper", grants);
        ReflectionTestUtils.setField(legacy, "typeMapper", types);
        ReflectionTestUtils.setField(legacy, "fileMapper", fileMapper);
        ReflectionTestUtils.setField(legacy, "templateRenderService", service);
        byte[] bytes = legacy.generatePdf(8L, record -> true);
        assertThat(legacy.generatePdf(8L, record -> true)).isSameAs(bytes);
        assertThatThrownBy(() -> legacy.generatePdf(8L, record -> false))
                .isInstanceOf(CertificateGrantService.CertificateAccessDeniedException.class);
        try (PDDocument scanned = PDDocument.load(bytes)) { assertImageOnly(scanned); }
        assertThat(ink(bytes, false)).isGreaterThan(100);
    }

    @Test
    void eachPageOfALegacyMultiPageDocumentBecomesOneImage() throws Exception {
        try (PDDocument source = new PDDocument()) {
            source.addPage(new PDPage(PDRectangle.A4));
            source.addPage(new PDPage(new PDRectangle(400, 300)));
            try (PDDocument scanned = PDDocument.load(service.renderImageOnlyPdf(source))) {
                assertThat(scanned.getNumberOfPages()).isEqualTo(2);
                assertImageOnly(scanned);
                assertThat(scanned.getPage(1).getMediaBox().getWidth()).isEqualTo(400);
            }
        }
    }

    @Test
    void pendingChineseFontSubsetsRenderExactlyLikeTheCompletedVectorPdf() throws Exception {
        BufferedImage expected;
        try (PDDocument source = chineseTextDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            source.save(output);
            try (PDDocument completed = PDDocument.load(output.toByteArray())) {
                expected = new PDFRenderer(completed).renderImageWithDPI(0, 300);
            }
        }
        try (PDDocument source = chineseTextDocument();
             PDDocument scanned = PDDocument.load(service.renderImageOnlyPdf(source))) {
            BufferedImage actual = pageImage(scanned.getPage(0)).getImage();
            assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
            assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
            long differences = 0;
            for (int y = 0; y < actual.getHeight(); y++) {
                for (int x = 0; x < actual.getWidth(); x++) {
                    if (actual.getRGB(x, y) != expected.getRGB(x, y)) differences++;
                }
            }
            assertThat(differences).as("Chinese glyph pixels must survive rasterization without fallback fonts").isZero();
        } finally {
            expected.flush();
        }
    }

    private PDDocument chineseTextDocument() throws Exception {
        PDDocument document = new PDDocument();
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (var fontData = getClass().getResourceAsStream("/fonts/NotoSansSC.ttf");
             PDPageContentStream stream = new PDPageContentStream(document, page)) {
            PDType0Font font = PDType0Font.load(document, fontData, true);
            stream.beginText();
            stream.setFont(font, 32);
            stream.newLineAtOffset(100, 500);
            stream.showText("张三的获奖证书");
            stream.endText();
        }
        return document;
    }

    private void assertImageOnly(PDDocument pdf) throws Exception {
        assertThat(pdf.getVersion()).isGreaterThanOrEqualTo(1.6f);
        assertThat(pdf.isEncrypted()).isTrue();
        var permissions = pdf.getCurrentAccessPermission();
        assertThat(permissions.canPrint()).isTrue();
        assertThat(permissions.canModify()).isFalse();
        assertThat(permissions.canExtractContent()).isFalse();
        assertThat(permissions.canModifyAnnotations()).isFalse();
        assertThat(permissions.canFillInForm()).isFalse();
        assertThat(permissions.canAssembleDocument()).isFalse();
        assertThat(new PDFTextStripper().getText(pdf)).isBlank();
        assertThat(pdf.getDocumentCatalog().getAcroForm()).isNull();
        assertThat(pdf.getDocumentCatalog().getNames()).isNull();
        for (PDPage page : pdf.getPages()) {
            assertThat(page.getAnnotations()).isEmpty();
            assertThat(page.getResources().getFontNames()).isEmpty();
            pageImage(page);
            PDFStreamParser parser = new PDFStreamParser(page);
            parser.parse();
            List<String> operators = parser.getTokens().stream().filter(token -> token instanceof Operator)
                    .map(token -> ((Operator) token).getName()).toList();
            assertThat(operators).containsExactly("q", "cm", "Do", "Q");
        }
    }

    private PDImageXObject pageImage(PDPage page) throws Exception {
        List<COSName> names = new ArrayList<>();
        page.getResources().getXObjectNames().forEach(names::add);
        assertThat(names).hasSize(1);
        var image = page.getResources().getXObject(names.get(0));
        assertThat(image).isInstanceOf(PDImageXObject.class);
        return (PDImageXObject) image;
    }

    private Map<String, Object> block(String family, boolean bold, boolean underline, int y) {
        Map<String, Object> block = new java.util.LinkedHashMap<>();
        block.put("id", family + "-" + y);
        block.put("content", "获奖人：张三 Agyp");
        block.put("x", 10);
        block.put("y", y);
        block.put("width", 80);
        block.put("fontSize", 28);
        block.put("fontFamily", family);
        block.put("alignment", "CENTER");
        block.put("color", "#194c99");
        block.put("bold", bold);
        block.put("underline", underline);
        return block;
    }

    private String schema(List<Map<String, Object>> blocks) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("version", 2, "blocks", blocks));
    }

    private byte[] render(String schema) throws Exception {
        return service.renderPreview(1L, schema, false, LocalDate.of(2026, 10, 22));
    }

    private long ink(byte[] bytes, boolean bottomHalfOnly) throws Exception {
        try (PDDocument pdf = PDDocument.load(bytes)) {
            assertImageOnly(pdf);
            BufferedImage image = new PDFRenderer(pdf).renderImageWithDPI(0, 144);
            long count = 0;
            for (int y = bottomHalfOnly ? image.getHeight() / 2 : 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) if (dark(image.getRGB(x, y))) count++;
            }
            return count;
        }
    }

    private boolean dark(int rgb) {
        return ((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255) < 400;
    }
}
