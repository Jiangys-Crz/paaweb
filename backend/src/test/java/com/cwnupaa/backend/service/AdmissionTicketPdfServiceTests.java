package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.Major;
import com.cwnupaa.backend.entity.School;
import com.cwnupaa.backend.entity.SignupPersonal;
import com.cwnupaa.backend.entity.SignupTeam;
import com.cwnupaa.backend.entity.SignupTeamDetail;
import com.cwnupaa.backend.entity.University;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.mapper.MajorMapper;
import com.cwnupaa.backend.mapper.SchoolMapper;
import com.cwnupaa.backend.mapper.UniversityMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdmissionTicketPdfServiceTests {
    @TempDir
    Path tempDir;

    private AdmissionTicketPdfService service;
    private Users user;
    private Event event;
    private Major major;
    private School school;
    private University university;

    @BeforeEach
    void setUp() throws Exception {
        service = new AdmissionTicketPdfService();
        MajorMapper majorMapper = mock(MajorMapper.class);
        SchoolMapper schoolMapper = mock(SchoolMapper.class);
        UniversityMapper universityMapper = mock(UniversityMapper.class);
        FileSystemMapper fileSystemMapper = mock(FileSystemMapper.class);
        ReflectionTestUtils.setField(service, "majorMapper", majorMapper);
        ReflectionTestUtils.setField(service, "schoolMapper", schoolMapper);
        ReflectionTestUtils.setField(service, "universityMapper", universityMapper);
        ReflectionTestUtils.setField(service, "fileSystemMapper", fileSystemMapper);
        ReflectionTestUtils.setField(service, "uploadPath", tempDir.toString());

        major = new Major();
        major.setMajorId(1L);
        major.setSchoolId(2L);
        major.setMajorName("软件工程");
        school = new School();
        school.setSchoolId(2L);
        school.setUniversityId(3L);
        school.setSchoolName("计算机学院");
        university = new University();
        university.setUniversityId(3L);
        university.setUniversityName("示例大学");
        when(majorMapper.selectById(1L)).thenReturn(major);
        when(schoolMapper.selectById(2L)).thenReturn(school);
        when(universityMapper.selectById(3L)).thenReturn(university);

        Path photoDir = Files.createDirectories(tempDir.resolve("id-photos"));
        Path photoPath = photoDir.resolve("preview.png");
        BufferedImage photo = new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = photo.createGraphics();
        graphics.setColor(new Color(220, 235, 248));
        graphics.fillRect(0, 0, 300, 400);
        graphics.setColor(new Color(48, 94, 138));
        graphics.fillOval(95, 58, 110, 130);
        graphics.fillRoundRect(58, 190, 184, 210, 30, 30);
        graphics.dispose();
        ImageIO.write(photo, "png", photoPath.toFile());

        FileSystem photoFile = new FileSystem();
        photoFile.setFileId(9L);
        photoFile.setType("png");
        photoFile.setUrl("/id-photos/preview.png");
        when(fileSystemMapper.selectById(9L)).thenReturn(photoFile);

        user = new Users();
        user.setUserId(10L);
        user.setRealName("张三");
        user.setGender(1);
        user.setMajorId(1L);
        user.setGradeYear("2024");
        user.setNumberId("202600000001");
        user.setEduBg("undergraduate");
        user.setIdPhotoFileId(9L);

        event = new Event();
        event.setName("程序设计竞赛");
        event.setAdmissionTicketRules("一、请携带有效证件参赛。\n二、请遵守赛场纪律并服从工作人员安排。");
    }

    @Test
    void rendersPersonalAndTeamLayoutsForVisualReview() throws Exception {
        SignupPersonal personal = new SignupPersonal();
        personal.setType("official");
        personal.setIsRanking(1);
        personal.setAdvisor("示例教师");
        personal.setContestAccount("contest001");
        personal.setContestPassword("pass001");
        personal.setSeatNumber("A-001");

        SignupTeam team = new SignupTeam();
        team.setTeamName("星辰算法队");
        team.setType("official");
        team.setIsRanking(1);
        team.setAdvisor("示例教师");
        team.setContestAccount("team001");
        team.setContestPassword("team-pass");
        team.setSeatNumber("B-018");

        SignupTeamDetail detail = new SignupTeamDetail();
        byte[] personalPdf = service.renderPersonal(event, personal, user);
        byte[] teamPdf = service.renderTeam(event, team, detail, user, List.of(user, member("张三"), member("李四"), member("王五")));

        Path previewDir = Files.createDirectories(Path.of("target", "pdf-previews"));
        Files.write(previewDir.resolve("personal-admission-ticket.pdf"), personalPdf);
        Files.write(previewDir.resolve("team-admission-ticket.pdf"), teamPdf);

        assertPdf(personalPdf, "程序设计竞赛", "参赛证明", "指导老师", "A-001");
        assertPdf(teamPdf, "程序设计竞赛", "参赛证明", "队伍成员", "星辰算法队");
    }

    @Test
    void rendersMissingValueWhenSchoolHierarchyNamesAreDuplicated() throws Exception {
        university.setUniversityName("同名院校");
        school.setSchoolName("同名院校");
        major.setMajorName("同名院校");

        SignupPersonal personal = new SignupPersonal();
        personal.setType("official");
        personal.setIsRanking(1);
        personal.setAdvisor("示例教师");
        personal.setContestAccount("contest001");
        personal.setContestPassword("pass001");
        personal.setSeatNumber("A-001");

        byte[] pdf = service.renderPersonal(event, personal, user);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = java.text.Normalizer.normalize(new PDFTextStripper().getText(document), java.text.Normalizer.Form.NFKC);
            assertThat(text).contains("同名院校");
            assertThat(text).containsOnlyOnce("同名院校");
            assertThat(text.chars().filter(character -> character == '无').count()).isEqualTo(2);
        }
    }

    @Test
    void rendersEveryMissingTableValueAsNone() throws Exception {
        SignupPersonal personal = new SignupPersonal();
        personal.setAdvisor("   ");

        byte[] pdf = service.renderPersonal(event, personal, user);
        try (PDDocument document = PDDocument.load(pdf)) {
            String text = java.text.Normalizer.normalize(new PDFTextStripper().getText(document), java.text.Normalizer.Form.NFKC);
            assertThat(text.chars().filter(character -> character == '无').count()).isEqualTo(6);
        }
    }

    private Users member(String name) {
        Users member = new Users();
        member.setRealName(name);
        return member;
    }

    private void assertPdf(byte[] bytes, String... expectedText) throws Exception {
        assertThat(bytes.length).isGreaterThan(10_000);
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = java.text.Normalizer.normalize(new PDFTextStripper().getText(document), java.text.Normalizer.Form.NFKC);
            assertThat(text).contains(expectedText);
        }
    }
}
