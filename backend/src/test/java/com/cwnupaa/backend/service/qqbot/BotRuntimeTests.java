package com.cwnupaa.backend.service.qqbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotRuntimeTests {
    final BotConfigService config=mock(BotConfigService.class);
    final OneBotConnection connection=mock(OneBotConnection.class);
    final BotQueryService queries=mock(BotQueryService.class);
    final BotTaskService tasks=mock(BotTaskService.class);
    final BotDeliveryStore deliveries=mock(BotDeliveryStore.class);
    final BotScheduledImageService images=mock(BotScheduledImageService.class);
    final ObjectMapper json=new ObjectMapper();
    final BotConfig c=new BotConfig();
    BotRuntime runtime;
    @BeforeEach void setup(){
        c.setEnabled(true);c.setMinReplyDelayMillis(0);c.setMaxReplyDelayMillis(0);c.setTypingMillisPerChar(0);
        when(config.get()).thenReturn(c);when(connection.selfId()).thenReturn("99999");when(connection.connected()).thenReturn(true);
        when(queries.recognized(anyString())).thenAnswer(a->Set.of("notice","help").contains(a.getArgument(0))||((String)a.getArgument(0)).startsWith("/"));
        when(queries.reply(anyString())).thenAnswer(a->new BotQueryService.BotReply("reply:"+a.getArgument(0),null));
        when(images.prepare(anyList())).thenReturn(List.of());
        runtime=new BotRuntime(config,connection,queries,tasks,deliveries,images);
    }
    @AfterEach void cleanup(){runtime.close();}
    JsonNode event(String group,String user,String id,Object message){return json.valueToTree(Map.of("post_type","message","message_type","group","self_id","99999","group_id",group,"user_id",user,"message_id",id,"message",message));}
    @Test void concurrentUsersAreQueuedAndRepliesKeepTheOriginalMentionAndMessage() throws Exception {
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),sent=new CountDownLatch(2);
        when(queries.reply("notice")).thenAnswer(a->{entered.countDown();assertThat(release.await(3,TimeUnit.SECONDS)).isTrue();return new BotQueryService.BotReply("notice result",null);});
        List<List<Map<String,Object>>> outgoing=new CopyOnWriteArrayList<>();
        doAnswer(a->{outgoing.add(a.getArgument(2));sent.countDown();return null;}).when(connection).send(anyString(),anyString(),anyList());
        runtime.onMessage(event("12345","101","1","notice"));assertThat(entered.await(2,TimeUnit.SECONDS)).isTrue();
        runtime.onMessage(event("12345","202","2","help"));release.countDown();assertThat(sent.await(3,TimeUnit.SECONDS)).isTrue();
        assertThat(outgoing.get(0)).contains(BotRuntime.segment("at",Map.of("qq","101")),BotRuntime.segment("reply",Map.of("id","1")));
        assertThat(outgoing.get(1)).contains(BotRuntime.segment("at",Map.of("qq","202")),BotRuntime.segment("reply",Map.of("id","2")));
        assertThat(outgoing.get(0).toString()).doesNotContain("202");
    }
    @Test void unallowedGroupsSelfMessagesAndOrdinaryConversationAreIgnored(){
        c.setQueryGroups(List.of("12345"));
        runtime.onMessage(event("54321","101","1","help"));
        runtime.onMessage(event("12345","99999","2","help"));
        runtime.onMessage(event("12345","101","3","随便聊天"));
        verify(queries,never()).reply(anyString());verifyNoInteractions(deliveries);
    }
    @Test void addressedUnknownCommandGetsHelpAndCqTextIsNeverAnExecutableSegment() throws Exception {
        CountDownLatch sent=new CountDownLatch(1);
        doAnswer(a->{sent.countDown();return null;}).when(connection).send(anyString(),anyString(),anyList());
        when(queries.reply("bad command")).thenReturn(new BotQueryService.BotReply("help [CQ:at,qq=all]",null));
        JsonNode e=event("12345","101","1",List.of(Map.of("type","at","data",Map.of("qq","99999")),Map.of("type","text","data",Map.of("text","bad command"))));
        runtime.onMessage(e);runtime.onMessage(e);assertThat(sent.await(3,TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<List<Map<String,Object>>> parts=ArgumentCaptor.forClass(List.class);
        verify(connection,times(1)).send(eq("12345"),eq("101"),parts.capture());
        assertThat(parts.getValue()).contains(BotRuntime.segment("text",Map.of("text","\nhelp [CQ:at,qq=all]")));
        assertThat(parts.getValue()).doesNotContain(BotRuntime.segment("at",Map.of("qq","all")));
    }
    @Test void discoveryQueuesOnlyConfiguredCategoriesAndGroupsInTheDueWindow(){
        var now=LocalDateTime.now(BotTaskService.ZONE);
        c.setPushGroups(List.of("12345"));c.setPushOnline(true);
        when(queries.pushes(any())).thenReturn(List.of(new BotQueryService.BotPush("oj1","online",now.minusMinutes(1),"OJ"),
                new BotQueryService.BotPush("of1","offline",now.minusMinutes(1),"offline"),
                new BotQueryService.BotPush("oj2","online",now.plusMinutes(1),"future"),
                new BotQueryService.BotPush("oj3","online",now.minusHours(1),"stale")));
        runtime.discover();
        verify(deliveries,times(1)).enqueue(startsWith("push:oj1:"),eq("12345"),isNull(),eq("online"),any());
        verify(deliveries,never()).enqueue(contains("of1"),any(),any(),any(),any());
        verify(deliveries,never()).enqueue(contains("oj2"),any(),any(),any(),any());
        verify(deliveries,never()).enqueue(contains("oj3"),any(),any(),any(),any());
    }
    @Test void pushMentionsEveryoneAndDisabledTaskIsCancelledBeforeSending() throws Exception {
        var now=LocalDateTime.now(BotTaskService.ZONE);
        var disabled=new BotTask();disabled.setId("task");disabled.setEnabled(false);
        when(tasks.find("task")).thenReturn(disabled);
        var d=new BotDeliveryStore.Delivery("key","12345","task",null,new BotDeliveryStore.Payload("old",List.of(),now,"rev"),1);
        when(deliveries.claim(any())).thenReturn(d);runtime.deliver();
        verify(deliveries,timeout(2000)).cancel(d);verify(connection,never()).send(any(),any(),anyList());
    }
    @Test void publicPushOnlySendsToConfiguredGroupAndMentionsAll() throws Exception {
        c.setPushGroups(List.of("12345"));c.setPushBoard(true);
        var now=LocalDateTime.now(BotTaskService.ZONE);
        var d=new BotDeliveryStore.Delivery("key","12345",null,"board",new BotDeliveryStore.Payload("board",List.of(),now,null),1);
        when(deliveries.claim(any())).thenReturn(d);runtime.deliver();
        verify(deliveries,timeout(2000)).complete(eq(d),any());
        ArgumentCaptor<List<Map<String,Object>>> parts=ArgumentCaptor.forClass(List.class);
        verify(connection).send(eq("12345"),isNull(),parts.capture());
        assertThat(parts.getValue()).contains(BotRuntime.segment("at",Map.of("qq","all")));
    }
    @Test void chunksDoNotBreakUnicodeSurrogatePairs(){
        assertThat(BotRuntime.chunks("abc😀def",4)).containsExactly("abc","😀de","f");
    }
    @Test void privateReplyUsesTheSenderAndKeepsGroupOnlyAtSegmentsOutOfPrivateMessages() throws Exception {
        CountDownLatch sent=new CountDownLatch(1);
        doAnswer(a->{sent.countDown();return null;}).when(connection).send(isNull(),eq("101"),anyList());
        runtime.onMessage(json.valueToTree(Map.of("post_type","message","message_type","private","self_id","99999","user_id","101","message_id","3","message","help")));
        assertThat(sent.await(3,TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<List<Map<String,Object>>> parts=ArgumentCaptor.forClass(List.class);
        verify(connection).send(isNull(),eq("101"),parts.capture());
        assertThat(parts.getValue()).contains(BotRuntime.segment("text",Map.of("text","@101 ")));
        assertThat(parts.getValue()).noneMatch(p->"at".equals(p.get("type")));
    }
}
