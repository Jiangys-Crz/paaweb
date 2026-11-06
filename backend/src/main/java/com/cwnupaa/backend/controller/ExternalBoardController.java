package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.dto.ExternalBoardConfigDTO;
import com.cwnupaa.backend.service.ExternalBoardService;
import com.cwnupaa.backend.vo.BoardStatsVO;
import com.cwnupaa.backend.vo.BoardVO;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import com.cwnupaa.backend.vo.ExternalBoardSummaryVO;

@RestController
@RequestMapping("/leaderboard")
public class ExternalBoardController {

    @Autowired
    private ExternalBoardService externalBoardService;
    @Autowired
    private com.cwnupaa.backend.service.ResolverPhotoService resolverPhotoService;

    @GetMapping("/list")
    public Result<List<ExternalBoardSummaryVO>> listPublic(@RequestParam(required = false) String keyword) {
        return Result.success(externalBoardService.listBoards(null, true, keyword));
    }

    @GetMapping("/info/{boardId}")
    public Result<ExternalBoardSummaryVO> info(@PathVariable Long boardId) {
        return Result.success(externalBoardService.boardInfo(boardId));
    }

    @GetMapping("/admin/event/{eventId}/boards")
    public Result<List<ExternalBoardSummaryVO>> listForEvent(@PathVariable Long eventId) {
        return Result.success(externalBoardService.listBoards(eventId, false, null));
    }

    public record CreateBoardRequest(Long eventId, String name) {}

    @PostMapping("/admin/boards")
    public Result<ExternalBoardConfigDTO> create(@RequestBody CreateBoardRequest request) {
        return Result.success(externalBoardService.createBoard(request.eventId(), request.name()));
    }

    @DeleteMapping("/admin/boards/{boardId}")
    public Result<String> delete(@PathVariable Long boardId) {
        externalBoardService.deleteBoard(boardId);
        return Result.success("外榜已删除");
    }

    @GetMapping("/{boardId}")
    public Result<BoardVO> getLeaderboard(@PathVariable Long boardId, @RequestParam(required = false) Long timePoint, @RequestParam(defaultValue = "team") String scope, // team or personal
                                          @RequestParam(required = false) String group,
                                          @RequestAttribute(value = "user", required = false) UserProfileVO currentUser) {
        return Result.success(externalBoardService.getBoardDetail(boardId, timePoint, scope, group, currentUser));
    }

    public record ResolverRequest(String password) {}

    @PostMapping("/{boardId}/resolver")
    public org.springframework.http.ResponseEntity<Result<Map<String, Object>>> resolver(
            @PathVariable Long boardId, @RequestBody(required = false) ResolverRequest request, jakarta.servlet.http.HttpServletRequest httpRequest) {
        var result = externalBoardService.resolveBoard(boardId, request == null ? null : request.password(), httpRequest.getRemoteAddr());
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(Result.success(result));
    }

    @GetMapping("/admin/{boardId}/resolver/participants")
    public org.springframework.http.ResponseEntity<Result<Map<String,Object>>> resolverParticipants(@PathVariable Long boardId,
            @RequestAttribute(value = "user", required = false) UserProfileVO currentUser) {
        return org.springframework.http.ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(Result.success(externalBoardService.resolverParticipants(boardId, currentUser)));
    }

    @GetMapping("/admin/{boardId}/resolver/photos")
    public Result<Map<String,Object>> photoStatus(@PathVariable Long boardId) { return Result.success(resolverPhotoService.status(boardId)); }

    @PostMapping("/admin/{boardId}/resolver/photos")
    public Result<Map<String,Object>> uploadPhotos(@PathVariable Long boardId, @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(defaultValue="false") boolean cover,
            @RequestParam(defaultValue="false") boolean defaultPhoto) throws java.io.IOException {
        // Accept old clients' parameter while storing its image as an opening cover.
        return Result.success(resolverPhotoService.upload(boardId,file,cover || defaultPhoto));
    }

    @DeleteMapping("/admin/{boardId}/resolver/cover")
    public Result<String> removeResolverCover(@PathVariable Long boardId) {
        resolverPhotoService.removeCover(boardId);
        return Result.success("滚榜封面已移除");
    }

    @GetMapping("/{boardId}/resolver/photos/{key}")
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> photo(@PathVariable Long boardId, @PathVariable String key,
            @RequestParam(defaultValue="false") boolean thumbnail) {
        var file=resolverPhotoService.photo(boardId,key,thumbnail);
        var mediaType = file.getFileName().toString().endsWith(".png") ? org.springframework.http.MediaType.IMAGE_PNG : org.springframework.http.MediaType.IMAGE_JPEG;
        return org.springframework.http.ResponseEntity.ok().contentType(mediaType)
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic().immutable())
                .body(new org.springframework.core.io.FileSystemResource(file));
    }

    @GetMapping("/{boardId}/stats")
    public Result<BoardStatsVO> getStats(@PathVariable Long boardId, @RequestParam(required = false) Long timePoint) {
        return Result.success(externalBoardService.getStatistics(boardId, timePoint));
    }

    @GetMapping("/submissions/{boardId}")
    public Result<Map<String, Object>> getSubmissions(@PathVariable Long boardId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(required = false) String problemId, @RequestParam(required = false) String status, @RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) Long signupTeamId,
                                                       @RequestParam(required = false) Long userId,
                                                       @RequestParam(required = false) Long timePoint) {
        return Result.success(externalBoardService.getBoardSubmissions(boardId, page, pageSize, problemId, status, keyword, signupTeamId, userId, timePoint));
    }

    @GetMapping("/submission/{submissionId}/code")
    public Result<Map<String, String>> getSubmissionCode(@PathVariable Long submissionId) {
        try {
            return Result.success(externalBoardService.getSubmissionCode(submissionId));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/admin/config/{boardId}")
    public Result<ExternalBoardConfigDTO> getConfig(@PathVariable Long boardId) {
        return Result.success(externalBoardService.getConfig(boardId));
    }

    @PostMapping("/admin/config/update")
    public Result<String> updateConfig(@RequestBody ExternalBoardConfigDTO dto) {
        externalBoardService.updateConfig(dto);
        return Result.success("Success");
    }
}
