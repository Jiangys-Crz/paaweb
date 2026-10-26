package com.cwnupaa.backend.controller;

import com.cwnupaa.backend.entity.Event;
import com.cwnupaa.backend.entity.Users;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EventImportTypeTests {
    private final EventController controller = new EventController();
    private Users member(Integer gender) {
        Users user = new Users();
        user.setGender(gender);
        return user;
    }
    private Event event(String type) {
        Event event = new Event();
        event.setType(type);
        return event;
    }
    @Test
    void officialGirlsAreConvertedAndOtherTeamsKeepTheirType() {
        Event xcpc = event("XCPC");
        List<Users> girls = List.of(member(2), member(2));
        assertThat(controller.resolveImportedSignupType(xcpc, "official", girls)).isEqualTo("girl");
        assertThat(controller.resolveImportedSignupType(xcpc, null, List.of(member(2)))).isEqualTo("girl");
        assertThat(controller.resolveImportedSignupType(xcpc, "official", List.of(member(2), member(1)))).isEqualTo("official");
        assertThat(controller.resolveImportedSignupType(xcpc, "official", List.of(member(null)))).isEqualTo("official");
        assertThat(controller.resolveImportedSignupType(xcpc, "star", girls)).isEqualTo("star");
        assertThatThrownBy(() -> controller.resolveImportedSignupType(xcpc, "girl", List.of(member(1))))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void contestGroupsAreRequiredAndPreservedForGirls() {
        for (String group : List.of("星芒初绽", "长风破浪", "凌霄问鼎")) {
            assertThat(controller.resolveImportedSignupType(event("GPLT"), group, List.of(member(2)))).isEqualTo(group);
        }
        for (String group : List.of("C/C++程序设计", "Python程序设计", "Java软件开发")) {
            assertThat(controller.resolveImportedSignupType(event("LANQIAO"), group, List.of(member(2)))).isEqualTo(group);
        }
        for (String type : List.of("GPLT", "LANQIAO")) {
            assertThatThrownBy(() -> controller.resolveImportedSignupType(event(type), null, List.of(member(2))))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> controller.resolveImportedSignupType(event(type), "official", List.of(member(2))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
