package com.cwnupaa.backend.service;

import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.BoardVO;
import com.cwnupaa.backend.vo.RankItemVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.sql.ResultSet;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResolverPhotoServiceTests {
    @TempDir Path temp;
    ExternalBoardMapper boards = mock(ExternalBoardMapper.class);
    EventMapper events = mock(EventMapper.class);
    SignupTeamMapper teams = mock(SignupTeamMapper.class);
    SignupPersonalMapper people = mock(SignupPersonalMapper.class);
    SignupTeamDetailMapper details = mock(SignupTeamDetailMapper.class);
    JdbcTemplate jdbc = mock(JdbcTemplate.class);
    PlatformTransactionManager transaction = mock(PlatformTransactionManager.class);
    ResolverPhotoService service;

    @BeforeEach void setup() {
        var board = new ExternalBoard(); board.setBoardId(1L); board.setEventId(2L); board.setExternalOpen(true);
        var event = new Event(); event.setEventId(2L); event.setType("XCPC"); event.setIsTeam(1);
        when(boards.selectById(1L)).thenReturn(board);
        when(events.selectById(2L)).thenReturn(event);
        SignupTeam team = new SignupTeam(); team.setSignupId(11L); team.setContestAccount("Team01");
        when(teams.selectList(any())).thenReturn(List.of(team));
        when(transaction.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new ResolverPhotoService(boards, events, teams, people, details, jdbc, transaction, temp.toString());
    }

    static byte[] image() throws IOException {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(5, 5, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
    static MockMultipartFile zip(Map<String, byte[]> entries) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            for (var e : entries.entrySet()) { zip.putNextEntry(new ZipEntry(e.getKey())); zip.write(e.getValue()); zip.closeEntry(); }
        }
        return new MockMultipartFile("file", "photos.ZIP", "application/zip", out.toByteArray());
    }
    static void assertBadRequest(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test void uploadsSubdirectoryAndUppercaseExtensionsIgnoringMacMetadata() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("photos/Team01.PNG", image());
        entries.put("__MACOSX/photos/._Team01.PNG", new byte[]{1, 2});
        entries.put("photos/unknown.JPG", image());
        Map<String, Object> result = service.upload(1L, zip(entries), false);
        assertThat(result.get("imported")).isEqualTo(1);
        assertThat(result.get("unmatched")).isEqualTo(List.of("unknown.JPG"));
        verify(jdbc).update(anyString(), eq(1L), eq(11L), anyString());
        try (var files = Files.list(temp.resolve("resolver-photos"))) { assertThat(files.count()).isEqualTo(2); }
    }

    @Test void pathTraversalIsRejectedAndPreviouslyWrittenFilesAreRemoved() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Team01.JPG", image()); entries.put("photos/../escape.JPG", image());
        assertBadRequest(() -> service.upload(1L, zip(entries), false));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        try (var files = Files.list(temp.resolve("resolver-photos"))) { assertThat(files.count()).isZero(); }
    }

    @Test void entryLargerThan32MiBIsRejectedBeforeSaving() throws Exception {
        byte[] tooBig = new byte[32 * 1024 * 1024 + 1];
        assertThatThrownBy(() -> service.upload(1L, zip(Map.of("Team01.jpg", tooBig)), false))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test void corruptImageAndUnsupportedFileAreRejected() throws Exception {
        assertBadRequest(() -> service.upload(1L, zip(Map.of("Team01.png", new byte[]{1, 2, 3})), false));
        assertBadRequest(() -> service.upload(1L, zip(Map.of("Team01.gif", image())), false));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test void duplicateFilesForOneAccountFailAtomically() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("photos/Team01.jpg", image()); entries.put("Team01.PNG", image());
        assertBadRequest(() -> service.upload(1L, zip(entries), false));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
        try (var files = Files.list(temp.resolve("resolver-photos"))) { assertThat(files.count()).isZero(); }
    }

    @Test void duplicateRegisteredAccountsFailBeforeWriting() throws Exception {
        SignupTeam other = new SignupTeam(); other.setSignupId(12L); other.setContestAccount("Team01");
        SignupTeam first = new SignupTeam(); first.setSignupId(11L); first.setContestAccount("Team01");
        when(teams.selectList(any())).thenReturn(List.of(first, other));
        assertBadRequest(() -> service.upload(1L, zip(Map.of("Team01.JPG", image())), false));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test void memberAccountMapsToTeamAndDuplicateTeamPhotosFail() throws Exception {
        SignupTeamDetail detail = new SignupTeamDetail(); detail.setSignupTeamId(11L); detail.setContestAccount("member01");
        when(details.selectList(any())).thenReturn(List.of(detail));
        assertThat(service.upload(1L, zip(Map.of("photos/member01.JPG", image())), false).get("imported")).isEqualTo(1);
        verify(jdbc).update(anyString(), eq(1L), eq(11L), anyString());
        clearInvocations(jdbc);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("Team01.png", image()); entries.put("member01.png", image());
        assertBadRequest(() -> service.upload(1L, zip(entries), false));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test void sharedMemberAccountAcrossTeamsIsRejected() throws Exception {
        SignupTeam other = new SignupTeam(); other.setSignupId(12L); other.setContestAccount("other");
        SignupTeam first = new SignupTeam(); first.setSignupId(11L); first.setContestAccount("Team01");
        SignupTeamDetail detail = new SignupTeamDetail(); detail.setSignupTeamId(12L); detail.setContestAccount("Team01");
        when(teams.selectList(any())).thenReturn(List.of(first, other));
        when(details.selectList(any())).thenReturn(List.of(detail));
        assertBadRequest(() -> service.upload(1L, zip(Map.of("Team01.JPG", image())), false));
        verify(jdbc, never()).update(anyString(), any(), any(), any());
    }

    @Test void cleanupOnlyRemovesOldUnreferencedGeneratedFiles() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("resolver-photos"));
        String orphan = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa";
        String used = "bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb";
        String recent = "cccccccc-cccc-4ccc-cccc-cccccccccccc";
        Path old = Files.writeString(dir.resolve(orphan + ".jpg"), "old");
        Path thumb = Files.writeString(dir.resolve(orphan + "-thumb.jpg"), "old");
        Path reference = Files.writeString(dir.resolve(used + ".jpg"), "keep");
        Path unknown = Files.writeString(dir.resolve("ordinary.jpg"), "keep");
        Path fresh = Files.writeString(dir.resolve(recent + ".jpg"), "keep");
        FileTime time = FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS));
        for (Path path : List.of(old, thumb, reference, unknown)) Files.setLastModifiedTime(path, time);
        when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(List.of(used));
        service.cleanOrphanedPhotos();
        assertThat(old).doesNotExist(); assertThat(thumb).doesNotExist();
        assertThat(reference).exists(); assertThat(unknown).exists(); assertThat(fresh).exists();
    }

    @Test void cleanupKeepsFilesWhenReferenceLookupFails() throws Exception {
        Path dir = Files.createDirectories(temp.resolve("resolver-photos"));
        Path old = Files.writeString(dir.resolve("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa.jpg"), "old");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(25, ChronoUnit.HOURS)));
        when(jdbc.queryForList(anyString(), eq(String.class))).thenThrow(new IllegalStateException());
        service.cleanOrphanedPhotos();
        assertThat(old).exists();
    }

    @Test void failedDatabaseUpdateRemovesNewFiles() throws Exception {
        when(jdbc.update(anyString(), any(), any(), any())).thenThrow(new IllegalStateException());
        assertThatThrownBy(() -> service.upload(1L, zip(Map.of("Team01.JPG", image())), false))
                .isInstanceOf(IllegalStateException.class);
        try (var files = Files.list(temp.resolve("resolver-photos"))) { assertThat(files.count()).isZero(); }
        verify(transaction).rollback(any());
    }

    @Test void publicPhotoReadsRequireOpenBoardAndAStoredMatchingKey() throws Exception {
        String key = "aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa";
        Files.createDirectories(temp.resolve("resolver-photos"));
        Files.writeString(temp.resolve("resolver-photos").resolve(key + ".jpg"), "image");
        when(jdbc.queryForObject(anyString(), eq(Long.class), eq(1L), eq(key))).thenReturn(1L, 0L);
        assertThat(service.photo(1L, key, false)).exists();
        assertThatThrownBy(() -> service.photo(1L, key, false)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.photo(1L, "../" + key, false)).isInstanceOf(ResponseStatusException.class);
        ExternalBoard board = boards.selectById(1L); board.setExternalOpen(false);
        assertThatThrownBy(() -> service.photo(1L, key, false)).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test void openingCoverNeverReplacesMissingParticipantPhotos() throws Exception {
        var input = new MockMultipartFile("file", "cover.JPEG", "image/jpeg", image());
        assertThat(service.upload(1L, input, true).get("imported")).isEqualTo(1);
        verify(jdbc).update(anyString(), eq(1L), eq(0L), anyString());
        doAnswer(call -> {
            RowCallbackHandler callback = call.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong(1)).thenReturn(0L, 11L);
            when(rs.getString(2)).thenReturn("cover", "team");
            callback.processRow(rs); callback.processRow(rs); return null;
        }).when(jdbc).query(anyString(), any(RowCallbackHandler.class), eq(1L));
        var matched = new RankItemVO(); matched.setItemId(11L);
        var unmatched = new RankItemVO(); unmatched.setItemId(12L);
        var frozen = new BoardVO(); frozen.setRows(List.of(matched, unmatched));
        var finalRow = new RankItemVO(); finalRow.setItemId(12L);
        var finalBoard = new BoardVO(); finalBoard.setRows(List.of(finalRow));
        service.attach(1L, frozen, finalBoard);
        assertThat(matched.getPhoto()).endsWith("/team");
        assertThat(matched.getHasTeamPhoto()).isTrue();
        assertThat(unmatched.getPhoto()).isNull();
        assertThat(unmatched.getHasTeamPhoto()).isFalse();
        assertThat(finalRow.getPhotoThumbnail()).isNull();
        assertThat(finalRow.getHasTeamPhoto()).isFalse();
    }
}
