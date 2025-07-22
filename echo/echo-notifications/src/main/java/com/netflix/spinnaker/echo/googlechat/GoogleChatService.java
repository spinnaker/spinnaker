/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.echo.googlechat;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import groovy.transform.Canonical;
import lombok.AllArgsConstructor;
import lombok.Getter;
import okhttp3.ResponseBody;

@Canonical
public class GoogleChatService {
  GoogleChatClient googleChatClient;

  public GoogleChatService(GoogleChatClient googleChatClient) {
    this.googleChatClient = googleChatClient;
  }

  ResponseBody sendMessage(String webhook, GoogleChatMessage message) {
    WebhookUrlParts parts = extractWebhookUrlParts(webhook);
    return Retrofit2SyncCall.execute(
        googleChatClient.sendMessage(
            parts.getSpaceId(), parts.getKey(), parts.getToken(), message));
  }

  @Getter
  @AllArgsConstructor
  private static class WebhookUrlParts {
    private final String spaceId;
    private final String key;
    private final String token;
  }

  private WebhookUrlParts extractWebhookUrlParts(String partialWebhookURL) {
    // Split SPACE_ID/messages?key=KEY&token=TOKEN into path and query parameters
    String[] parts = partialWebhookURL.split("\\?");
    if (parts.length != 2) {
      throw new IllegalArgumentException("Invalid Google Chat webhook URL format");
    }

    String[] queryParams = parts[1].split("&");

    String key = "";
    String token = "";

    for (String param : queryParams) {
      String[] keyValue = param.split("=");
      if (keyValue.length == 2) {
        if ("key".equals(keyValue[0])) {
          key = keyValue[1];
        } else if ("token".equals(keyValue[0])) {
          token = keyValue[1];
        }
      }
    }

    return new WebhookUrlParts(parts[0], key, token);
  }
}
