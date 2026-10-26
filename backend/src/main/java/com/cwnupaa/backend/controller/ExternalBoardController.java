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