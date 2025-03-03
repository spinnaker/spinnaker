/*
 * Copyright 2025 OpsMx, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.echo.bearychat;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.netflix.spinnaker.echo.api.Notification;
import com.netflix.spinnaker.echo.notification.NotificationTemplateEngine;
import java.util.List;
import java.util.Map;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.mock.Calls;

@SpringBootTest(
    properties = {"bearychat.enabled=true", "bearychat.token=abc"},
    classes = {
      BearychatNotificationService.class,
      NotificationTemplateEngine.class,
      freemarker.template.Configuration.class,
      BearychatNotificationServiceTest.TestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class BearychatNotificationServiceTest {

  @Autowired BearychatNotificationService bearychatNotificationService;

  @Autowired BearychatService bearychatService;

  @Test
  void testBearychatNotificationService() {
    BearychatUserInfo userInfo = new BearychatUserInfo();
    userInfo.setId("uid1");
    userInfo.setEmail("foo@bar.com");
    Mockito.when(bearychatService.getUserList("abc")).thenReturn(Calls.response(List.of(userInfo)));

    CreateP2PChannelPara para = new CreateP2PChannelPara();
    para.setUser_id("uid1");
    CreateP2PChannelResponse channelResponse = new CreateP2PChannelResponse();
    channelResponse.setId("cid1");
    channelResponse.setVchannel_id("cid1");
    Mockito.when(
            bearychatService.createp2pchannel(
                Mockito.anyString(), Mockito.any(CreateP2PChannelPara.class)))
        .thenReturn(Calls.response(channelResponse));

    Mockito.when(
            bearychatService.sendMessage(Mockito.anyString(), Mockito.any(SendMessagePara.class)))
        .thenReturn(Calls.response(ResponseBody.create("", MediaType.parse("application/json"))));

    Notification notification = new Notification();
    notification.setNotificationType("bearychat");
    notification.setTo(List.of("foo@bar.com"));
    notification.setAdditionalContext(Map.of("body", "notification Body"));

    assertDoesNotThrow(() -> bearychatNotificationService.handle(notification));
  }

  @Configuration
  public static class TestConfig {
    @MockBean BearychatService bearychatService;

    @Bean
    public BearychatService bearychatService() {
      return bearychatService;
    }
  }
}
