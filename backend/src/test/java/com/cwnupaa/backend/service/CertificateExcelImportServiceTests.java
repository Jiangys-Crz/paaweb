package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.CertificateIssue;
import com.cwnupaa.backend.entity.CertificateType;
import com.cwnupaa.backend.mapper.CertificateIssueMapper;
import com.cwnupaa.backend.mapper.CertificateTypeMapper;
import com.cwnupaa.backend.vo.CertificateImportPreviewVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CertificateExcelImportServiceTests {
    private CertificateExcelImportService service;
    private CertificateTypeMapper typeMapper;

    @BeforeEach
    void setUp() {
        service = new CertificateExcelImportService();
        typeMapper = mock(CertificateTypeMapper.class);
        CertificateIssueMapper issueMapper = mock(CertificateIssueMapper.class);
        ReflectionTestUtils.setField(service, "typeMapper", typeMapper);
        ReflectionTestUtils.setField(service, "issueMapper", issueMapper);
        when(issueMapper.selectList(any())).thenReturn(List.<CertificateIssue>of());
    }

    @Test
    void parsesPersonalWorkbookAndPreservesTextStudentId() throws Exception {
        CertificateType type = new CertificateType();
        type.setCertificateTypeId(1L);
        type.setIsTeam(0);
        when(typeMapper.selectById(1L)).thenReturn(type);

        CertificateImportPreviewVO preview = service.preview(1L, fixture("personal-valid.xlsx"));

        assertThat(preview.isValid()).isTrue();
        assertThat(preview.getCertificateCount()).isEqualTo(1);
        assertThat(preview.getRecipientCount()).isEqualTo(1);
        assertThat(preview.getRows().get(0).getMembers().get(0).getStudentId()).isEqualTo("00001234");
        assertThat(preview.getRows().get(0).getMembers().get(0).getSchool()).isEqualTo("示例大学");
        assertThat(preview.getRows().get(0).getCertificateNo()).isEqualTo("CERT-TEST-P-001");
    }

    @Test
    void parsesVariableSizeTeamWorkbook() throws Exception {
        CertificateType type = new CertificateType();
        type.setCertificateTypeId(2L);
        type.setIsTeam(1);
        when(typeMapper.selectById(2L)).thenReturn(type);

        CertificateImportPreviewVO preview = service.preview(2L, fixture("team-valid.xlsx"));

        assertThat(preview.isValid()).isTrue();
        assertThat(preview.getCertificateCount()).isEqualTo(1);
        assertThat(preview.getRecipientCount()).isEqualTo(2);
        assertThat(preview.getRows().get(0).getTeamName()).isEqualTo("星辰算法队");
        assertThat(preview.getRows().get(0).getMembers()).extracting("name").containsExactly("张三", "李四");
        assertThat(preview.getRows().get(0).getMembers()).extracting("studentId").containsExactly("20260001", "20260001");
        assertThat(preview.getRows().get(0).getMembers()).extracting("school").containsExactly("示例大学", "示例大学乙");
    }

    @Test
    void rejectsPersonalWorkbookWhenSchoolIsBlank() throws Exception {
        CertificateType type = new CertificateType();
        type.setCertificateTypeId(4L);
        type.setIsTeam(0);
        when(typeMapper.selectById(4L)).thenReturn(type);

        MockMultipartFile valid = fixture("personal-valid.xlsx");
        byte[] withoutSchool;
        try (Workbook workbook = WorkbookFactory.create(valid.getInputStream());
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getSheetAt(0).getRow(1).getCell(2).setBlank();
            workbook.write(output);
            withoutSchool = output.toByteArray();
        }

        CertificateImportPreviewVO preview = service.preview(4L, new MockMultipartFile(
                "file", "personal-missing-school.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", withoutSchool));

        assertThat(preview.isValid()).isFalse();
        assertThat(preview.getErrors()).anyMatch(error -> "学校".equals(error.getColumnName()));
    }

    @Test
    void rejectsWorkbookWhoseChineseHeadersDoNotMatchCertificateKind() throws Exception {
        CertificateType type = new CertificateType();
        type.setCertificateTypeId(3L);
        type.setIsTeam(1);
        when(typeMapper.selectById(3L)).thenReturn(type);

        CertificateImportPreviewVO preview = service.preview(3L, fixture("personal-valid.xlsx"));

        assertThat(preview.isValid()).isFalse();
        assertThat(preview.getErrors()).isNotEmpty();
        assertThat(preview.getErrors().get(0).getColumnName()).isEqualTo("表头");
    }

    @Test
    void allowsMultipleDifferentAwardsForTheSamePersonInOneWorkbook() throws Exception {
        personalType();
        CertificateImportPreviewVO preview = service.preview(1L, workbook(false,
                new String[]{"张三", "001", "示例大学", "一等奖", "P-1"},
                new String[]{"张三", "001", "示例大学", "最佳创意奖", "P-2"}));
        assertThat(preview.isValid()).isTrue();
        assertThat(preview.getCertificateCount()).isEqualTo(2);
    }

    @Test
    void rejectsTheSamePersonalAwardWithADifferentNumberOrName() throws Exception {
        personalType();
        CertificateImportPreviewVO preview = service.preview(1L, workbook(false,
                new String[]{"张三", "001", "示例大学", "一等奖", "P-1"},
                new String[]{"张三改名", "001", "示例大学", " 一等奖 ", "P-2"}));
        assertThat(preview.isValid()).isFalse();
        assertThat(preview.getErrors()).anyMatch(error -> error.getRowNumber() == 3
                && "奖项".equals(error.getColumnName()) && error.getMessage().contains("第 2 行重复"));
    }

    @Test
    void distinguishesStudentsAtDifferentSchoolsEvenWhenTheirNamesAndIdsMatch() throws Exception {
        personalType();
        CertificateImportPreviewVO preview = service.preview(1L, workbook(false,
                new String[]{"张三", "001", "示例大学", "一等奖", "P-1"},
                new String[]{"张三", "001", "示例大学丙", "一等奖", "P-2"}));
        assertThat(preview.isValid()).isTrue();
    }

    @Test
    void allowsMultipleDifferentAwardsForTheSameTeam() throws Exception {
        teamType();
        CertificateImportPreviewVO preview = service.preview(2L, workbook(true,
                new String[]{"算法队", "张三", "001", "示例大学", "李四", "002", "示例大学丙", "一等奖", "T-1"},
                new String[]{"算法队", "张三", "001", "示例大学", "李四", "002", "示例大学丙", "最佳创意奖", "T-2"}));
        assertThat(preview.isValid()).isTrue();
        assertThat(preview.getCertificateCount()).isEqualTo(2);
    }

    @Test
    void rejectsTheSameTeamAwardWhenMembersAreReorderedOrTheTeamIsRenamed() throws Exception {
        teamType();
        CertificateImportPreviewVO preview = service.preview(2L, workbook(true,
                new String[]{"算法队", "张三", "001", "示例大学", "李四", "002", "示例大学丙", "一等奖", "T-1"},
                new String[]{"新队名", "李四", "002", "示例大学丙", "张三", "001", "示例大学", "一等奖", "T-2"}));
        assertThat(preview.isValid()).isFalse();
        assertThat(preview.getErrors()).anyMatch(error -> "奖项".equals(error.getColumnName())
                && error.getMessage().contains("同一队伍"));
    }

    @Test
    void allowsDifferentTeamsWithTheSameNameAndSomeSharedMembers() throws Exception {
        teamType();
        CertificateImportPreviewVO preview = service.preview(2L, workbook(true,
                new String[]{"算法队", "张三", "001", "示例大学", "李四", "002", "示例大学丙", "一等奖", "T-1"},
                new String[]{"算法队", "张三", "001", "示例大学", "王五", "003", "示例大学丙", "一等奖", "T-2"}));
        assertThat(preview.isValid()).isTrue();
    }

    private void personalType() {
        CertificateType type = new CertificateType();
        type.setCertificateTypeId(1L);
        type.setIsTeam(0);
        when(typeMapper.selectById(1L)).thenReturn(type);
    }

    private void teamType() {
        CertificateType type = new CertificateType();
        type.setCertificateTypeId(2L);
        type.setIsTeam(1);
        when(typeMapper.selectById(2L)).thenReturn(type);
    }

    static MockMultipartFile workbook(boolean team, String[]... data) throws Exception {
        String[] headers = team
                ? new String[]{"队伍名称", "队员1姓名", "队员1学号", "队员1学校", "队员2姓名", "队员2学号", "队员2学校", "奖项", "编号"}
                : new String[]{"姓名", "学号", "学校", "奖项", "编号"};
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("发放名单");
            var header = sheet.createRow(0);
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            for (int index = 0; index < data.length; index++) {
                var row = sheet.createRow(index + 1);
                for (int column = 0; column < data[index].length; column++) row.createCell(column).setCellValue(data[index][column]);
            }
            workbook.write(output);
            return new MockMultipartFile("file", "awards.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private MockMultipartFile fixture(String filename) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/certificate-import/" + filename)) {
            if (input == null) throw new IllegalStateException("Missing test fixture: " + filename);
            return new MockMultipartFile("file", filename,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", input.readAllBytes());
        }
    }
}
