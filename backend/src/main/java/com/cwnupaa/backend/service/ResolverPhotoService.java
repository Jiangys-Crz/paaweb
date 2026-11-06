package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.*;
import com.cwnupaa.backend.mapper.*;
import com.cwnupaa.backend.vo.BoardVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import net.coobird.thumbnailator.Thumbnails;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import java.util.*;
import java.util.zip.ZipInputStream;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ResolverPhotoService {
    private static final Logger LOG = LoggerFactory.getLogger(ResolverPhotoService.class);
    private static final Pattern PHOTO_FILE = Pattern.compile("^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(?:-thumb)?\\.(?:jpg|png)$");
    private static final long MAX_ZIP = 512L * 1024 * 1024;
    private static final int MAX_ENTRY = 32 * 1024 * 1024;
    private static final long MAX_EXPANDED = 1024L * 1024 * 1024;
    private final ExternalBoardMapper boards;
    private final EventMapper events;
    private final SignupTeamMapper teams;
    private final SignupPersonalMapper people;
    private final SignupTeamDetailMapper details;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Path root;
    private final java.util.concurrent.Semaphore processing = new java.util.concurrent.Semaphore(2);
    private final Set<Long> uploading = ConcurrentHashMap.newKeySet();

    public ResolverPhotoService(ExternalBoardMapper boards, EventMapper events, SignupTeamMapper teams,
                               SignupPersonalMapper people, SignupTeamDetailMapper details, JdbcTemplate jdbc, PlatformTransactionManager manager,
                               @Value("${file.upload.path}") String path) {
        this.boards=boards; this.events=events; this.teams=teams; this.people=people; this.details=details; this.jdbc=jdbc;
        this.transaction = new TransactionTemplate(manager);
        this.root=Path.of(path).toAbsolutePath().resolve("resolver-photos");
    }

    private Event event(Long boardId, boolean requireOpen) {
        ExternalBoard board = boards.selectById(boardId);
        if (board == null) throw fail(HttpStatus.NOT_FOUND, "外榜不存在");
        Event event = events.selectById(board.getEventId());
        if (event == null || (requireOpen && !Boolean.TRUE.equals(board.getExternalOpen()))) throw fail(HttpStatus.FORBIDDEN,"榜单未开放");
        if (event.getType()!=null && !"XCPC".equals(event.getType())) throw fail(HttpStatus.BAD_REQUEST,"仅 XCPC 外榜支持滚榜照片");
        return event;
    }

    public Map<String,Object> upload(Long boardId, MultipartFile file, boolean coverImage) throws IOException {
        Event event=event(boardId,false);
        if (!uploading.add(boardId)) throw fail(HttpStatus.TOO_MANY_REQUESTS,"该外榜正在处理照片，请稍后重试");
        if (!processing.tryAcquire()) { uploading.remove(boardId); throw fail(HttpStatus.TOO_MANY_REQUESTS,"服务器正在处理其他照片，请稍后重试"); }
        List<String> created=new ArrayList<>();
        try {
            if(file.isEmpty() || file.getSize()>(coverImage ? MAX_ENTRY : MAX_ZIP)) throw fail(HttpStatus.PAYLOAD_TOO_LARGE,"照片包最多512MB，滚榜封面最多32MB");
            Map<String,Long> accounts=new HashMap<>();
            if (!coverImage && Integer.valueOf(1).equals(event.getIsTeam())) {
                List<SignupTeam> approvedTeams = teams.selectList(new QueryWrapper<SignupTeam>()
                        .eq("event_id", event.getEventId()).eq("status", "approved"));
                for (SignupTeam team : approvedTeams) addAccount(accounts, team.getContestAccount(), team.getSignupId());
                List<Long> signupIds = approvedTeams.stream().map(SignupTeam::getSignupId).toList();
                if (!signupIds.isEmpty()) {
                    for (SignupTeamDetail detail : details.selectList(new QueryWrapper<SignupTeamDetail>().in("signup_team_id", signupIds)))
                        addAccount(accounts, detail.getContestAccount(), detail.getSignupTeamId());
                }
            } else if (!coverImage) {
                for (SignupPersonal person : people.selectList(new QueryWrapper<SignupPersonal>()
                        .eq("event_id", event.getEventId()).eq("status", "approved")))
                    addAccount(accounts, person.getContestAccount(), person.getUserId());
            }
            Map<Long,String> replacements=new LinkedHashMap<>();
            List<String> unmatched=new ArrayList<>();
            if(coverImage) {
                validateName(file.getOriginalFilename());
                String key=UUID.randomUUID().toString(); created.add(key);
                writeImage(file.getBytes(),key); replacements.put(0L,key);
            } else {
                String name=Optional.ofNullable(file.getOriginalFilename()).orElse("");
                if(!name.toLowerCase(Locale.ROOT).endsWith(".zip")) throw fail(HttpStatus.BAD_REQUEST,"请选择 ZIP 照片包");
                long expanded=0; int count=0;
                try(ZipInputStream zip=new ZipInputStream(file.getInputStream())) {
                    java.util.zip.ZipEntry entry;
                    while((entry=zip.getNextEntry())!=null) {
                        if(++count>5000) throw fail(HttpStatus.BAD_REQUEST,"ZIP文件数量过多");
                        String entryName=entry.getName().replace('\\','/');
                        if(entryName.startsWith("/") || Arrays.asList(entryName.split("/")).contains("..")) throw fail(HttpStatus.BAD_REQUEST,"ZIP包含非法路径");
                        if(entry.isDirectory()) continue;
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); byte[] buffer=new byte[16384]; int n;
                        while((n=zip.read(buffer))!=-1) {
                            expanded+=n;
                            if(expanded>MAX_EXPANDED || bytes.size()+n>MAX_ENTRY) throw fail(HttpStatus.PAYLOAD_TOO_LARGE,"ZIP解压大小超限");
                            bytes.write(buffer,0,n);
                        }
                        String basename=entryName.substring(entryName.lastIndexOf('/')+1);
                        if(entryName.startsWith("__MACOSX/") || basename.startsWith(".")) continue;
                        validateName(basename);
                        String account=basename.substring(0,basename.lastIndexOf('.'));
                        Long participant=accounts.get(account);
                        if(participant==null) { unmatched.add(basename); continue; }
                        if(replacements.containsKey(participant)) throw fail(HttpStatus.BAD_REQUEST,"同一比赛账号出现重复照片："+account);
                        String key=UUID.randomUUID().toString(); created.add(key);
                        writeImage(bytes.toByteArray(),key); replacements.put(participant,key);
                    }
                }
            }
            if(replacements.isEmpty()) throw fail(HttpStatus.BAD_REQUEST,"没有匹配到已通过报名的比赛账号");
            transaction.executeWithoutResult(status -> {
                for(var replacement:replacements.entrySet()) {
                    jdbc.update("INSERT INTO external_board_photo(board_id,participant_id,photo_key) VALUES (?,?,?) ON CONFLICT(board_id,participant_id) DO UPDATE SET photo_key=excluded.photo_key, updated_at=CURRENT_TIMESTAMP",boardId,replacement.getKey(),replacement.getValue());
                }
            });
            return Map.of("imported",replacements.size(),"unmatched",unmatched,"cover",coverImage);
        } catch(Exception ex) {
            for(String key:created) for(String suffix:List.of(".jpg", "-thumb.jpg", ".png", "-thumb.png")) Files.deleteIfExists(root.resolve(key+suffix));
            if(ex instanceof IOException io) throw io;
            throw ex;
        } finally { uploading.remove(boardId); processing.release(); }
    }

    private void addAccount(Map<String,Long> accounts,String account,Long id) {
        if(account==null || account.isBlank()) return;
        Long previous=accounts.putIfAbsent(account.trim(),id);
        if(previous!=null && !previous.equals(id)) throw fail(HttpStatus.BAD_REQUEST,"赛事存在重复比赛账号，请先修正账号");
    }

    private void validateName(String name) {
        if(name==null || !name.toLowerCase(Locale.ROOT).matches(".+\\.(jpg|jpeg|png)")) throw fail(HttpStatus.BAD_REQUEST,"照片仅支持 JPG、JPEG、PNG");
    }

    // Read dimensions before decoding; subsampling bounds memory for camera originals.
    private void writeImage(byte[] bytes,String key) throws IOException {
        BufferedImage decoded;
        boolean png = false;
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) throw fail(HttpStatus.BAD_REQUEST,"照片内容不是有效图片");
            ImageReader reader=readers.next();
            try {
                reader.setInput(input,true,true);
                if(!Set.of("JPEG","PNG","JPG").contains(reader.getFormatName().toUpperCase(Locale.ROOT))) throw fail(HttpStatus.BAD_REQUEST,"照片内容不是JPG或PNG");
                png = "PNG".equals(reader.getFormatName().toUpperCase(Locale.ROOT));
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0 || height<=0 || (long)width*height>60_000_000L) throw fail(HttpStatus.BAD_REQUEST,"照片像素过大");
                var param=reader.getDefaultReadParam(); int sampling=Math.max(1,Math.max(width,height)/1800);
                param.setSourceSubsampling(sampling,sampling,0,0); decoded=reader.read(0,param);
            } finally {reader.dispose();}
        }
        // PNG must retain its original alpha; painting white or encoding JPEG destroys it.
        BufferedImage image=new BufferedImage(decoded.getWidth(),decoded.getHeight(),png ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics=image.createGraphics();
        if (png) graphics.setComposite(AlphaComposite.Src);
        else { graphics.setColor(Color.WHITE); graphics.fillRect(0,0,image.getWidth(),image.getHeight()); }
        graphics.drawImage(decoded,0,0,null); graphics.dispose(); decoded.flush();
        Files.createDirectories(root);
        String format = png ? "png" : "jpg";
        var full = Thumbnails.of(image).size(1600,1600).outputFormat(format);
        var thumbnail = Thumbnails.of(image).size(320,320).outputFormat(format);
        if (!png) { full.outputQuality(.86); thumbnail.outputQuality(.8); }
        full.toFile(root.resolve(key+"."+format).toFile());
        thumbnail.toFile(root.resolve(key+"-thumb."+format).toFile()); image.flush();
    }

    public Path photo(Long boardId,String key,boolean thumb) {
        event(boardId,true);
        if(!key.matches("[0-9a-f-]{36}") || jdbc.queryForObject("SELECT count(*) FROM external_board_photo WHERE board_id=? AND photo_key=?",Long.class,boardId,key)==0) throw fail(HttpStatus.NOT_FOUND,"照片不存在");
        Path file=root.resolve(key+(thumb?"-thumb":"")+".png");
        if(!Files.isRegularFile(file)) file=root.resolve(key+(thumb?"-thumb":"")+".jpg");
        if(!Files.isRegularFile(file)) throw fail(HttpStatus.NOT_FOUND,"照片不存在");
        return file;
    }

    public void attach(Long boardId,BoardVO... snapshots) {
        Map<Long,String> photos=new HashMap<>();
        jdbc.query("SELECT participant_id,photo_key FROM external_board_photo WHERE board_id=?",rs -> {photos.put(rs.getLong(1),rs.getString(2));},boardId);
        for(BoardVO snapshot:snapshots) for(var row:snapshot.getRows()) {
            row.setHasTeamPhoto(photos.containsKey(row.getItemId()));
            // Participant 0 is the opening cover, never a participant photo fallback.
            String key=photos.get(row.getItemId());
            if(key!=null) {
                String url="/api/leaderboard/"+boardId+"/resolver/photos/"+key;
                row.setPhoto(url); row.setPhotoThumbnail(url+"?thumbnail=true");
            } else {
                row.setPhoto(null); row.setPhotoThumbnail(null);
            }
        }
    }

    public Map<String,Object> status(Long boardId) {
        event(boardId,false);
        return Map.of("photoCount",jdbc.queryForObject("SELECT count(*) FROM external_board_photo WHERE board_id=? AND participant_id>0",Long.class,boardId),"hasCover",!coverUrl(boardId).isEmpty());
    }

    public String coverUrl(Long boardId) {
        List<String> keys = jdbc.queryForList("SELECT photo_key FROM external_board_photo WHERE board_id=? AND participant_id=0", String.class, boardId);
        return keys.isEmpty() ? "" : "/api/leaderboard/" + boardId + "/resolver/photos/" + keys.get(0);
    }

    public void removeCover(Long boardId) {
        event(boardId, false);
        // Replaced or removed cover files are reclaimed by the existing orphan cleanup.
        if (!uploading.add(boardId)) throw fail(HttpStatus.TOO_MANY_REQUESTS,"该外榜正在处理照片，请稍后重试");
        try { jdbc.update("DELETE FROM external_board_photo WHERE board_id=? AND participant_id=0", boardId); }
        finally { uploading.remove(boardId); }
    }
    /** Reclaim superseded and deleted-board images only after the safety window. */
    @Scheduled(cron = "0 20 4 * * *", zone = "Asia/Shanghai")
    public void cleanOrphanedPhotos() {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return;
        try {
            // A failed query (including a pre-migration database) must never trigger deletion.
            Set<String> referenced = new HashSet<>(jdbc.queryForList("SELECT photo_key FROM external_board_photo", String.class));
            Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
            try (var files = Files.list(root)) {
                files.forEach(file -> {
                    var match = PHOTO_FILE.matcher(file.getFileName().toString());
                    if (!match.matches() || referenced.contains(match.group(1))
                            || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return;
                    try {
                        if (Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))
                            Files.deleteIfExists(file);
                    } catch (IOException ex) { LOG.warn("Resolver photo cleanup skipped a file ({})", ex.getClass().getSimpleName()); }
                });
            }
        } catch (Exception ex) {
            LOG.warn("Resolver photo cleanup skipped ({})", ex.getClass().getSimpleName());
        }
    }

    private ResponseStatusException fail(HttpStatus status,String message) { return new ResponseStatusException(status,message); }
}
