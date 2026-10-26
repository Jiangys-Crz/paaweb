package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.common.Result;
import com.cwnupaa.backend.service.qqbot.*;
import com.cwnupaa.backend.utils.SecurityUtils;
import com.cwnupaa.backend.vo.UserProfileVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/admin/qq-bot")
public class AdminQqBotController {
    private final BotConfigService config;
    private final BotRuntime runtime;
    private final BotTaskService tasks;
    public AdminQqBotController(BotConfigService config,BotRuntime runtime,BotTaskService tasks){this.config=config;this.runtime=runtime;this.tasks=tasks;}
    @GetMapping("/config") public Result<BotConfig> config(HttpServletRequest r){requireSuper(r);return Result.success(config.publicConfig());}
    @PutMapping("/config") public Result<String> save(@RequestBody BotConfig c,HttpServletRequest r){var user=requireSuper(r);config.save(c,user.getUserId());runtime.reconnect();return Result.success("QQ机器人配置已保存");}
    @GetMapping("/status") public Result<Map<String,Object>> status(HttpServletRequest r){requireSuper(r);return Result.success(runtime.status());}
    @PostMapping("/reconnect") public Result<String> reconnect(HttpServletRequest r){requireSuper(r);runtime.reconnect();return Result.success("已重新连接");}
    @GetMapping("/tasks") public Result<List<BotTask>> tasks(HttpServletRequest r){requireSuper(r);return Result.success(tasks.list());}
    @PostMapping("/tasks") public Result<BotTask> create(@RequestBody BotTask task,HttpServletRequest r){requireSuper(r);return Result.success(tasks.save(null,task));}
    @PutMapping("/tasks/{id}") public Result<BotTask> update(@PathVariable String id,@RequestBody BotTask task,HttpServletRequest r){requireSuper(r);return Result.success(tasks.save(id,task));}
    @DeleteMapping("/tasks/{id}") public Result<String> delete(@PathVariable String id,HttpServletRequest r){requireSuper(r);tasks.delete(id);return Result.success("任务已删除");}
    static UserProfileVO requireSuper(HttpServletRequest request){
        var user=SecurityUtils.currentUser(request);
        if(user==null||!"normal".equals(user.getAccountStatus())||!("super_admin".equals(user.getRole())||"supper_admin".equals(user.getRole())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅超级管理员可管理QQ机器人");
        return user;
    }
}
