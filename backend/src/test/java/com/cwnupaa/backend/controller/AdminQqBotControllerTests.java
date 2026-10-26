package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.service.qqbot.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminQqBotControllerTests {
    final BotConfigService config=mock(BotConfigService.class);
    final BotRuntime runtime=mock(BotRuntime.class);
    final BotTaskService tasks=mock(BotTaskService.class);
    final AdminQqBotController controller=new AdminQqBotController(config,runtime,tasks);
    MockHttpServletRequest request(String role,String status){
        var r=new MockHttpServletRequest();var u=new UserProfileVO();u.setUserId(2L);u.setUserAccount("notbuiltin");u.setRole(role);u.setAccountStatus(status);u.setPermissions(List.of("manage_sys","manage_api","manage_permission"));r.setAttribute("user",u);return r;
    }
    @Test void allEndpointsRejectOrdinaryAdminsEvenWithEveryRelevantPermission(){
        for(String role:List.of("student","stu_admin","department","school_admin")){
            var r=request(role,"normal");
            assertThatThrownBy(()->controller.config(r)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->controller.save(new BotConfig(),r)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->controller.status(r)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->controller.reconnect(r)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->controller.tasks(r)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->controller.create(new BotTask(),r)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->controller.update("1",new BotTask(),r)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(()->controller.delete("1",r)).isInstanceOf(ResponseStatusException.class);
        }
        verifyNoInteractions(config,runtime,tasks);
    }
    @Test void bothSuperAdminRoleSpellingsCanReadButAbnormalAccountsCannot(){
        for(String role:List.of("super_admin","supper_admin")){
            controller.config(request(role,"normal"));
            assertThatThrownBy(()->controller.config(request(role,"banned"))).isInstanceOf(ResponseStatusException.class);
        }
        verify(config,times(2)).publicConfig();
        assertThatThrownBy(()->controller.config(new MockHttpServletRequest())).isInstanceOf(ResponseStatusException.class);
    }
}
