/*
 * Copyright 2020 Armory, Inc.
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
 *
 */

package com.netflix.spinnaker.echo.scm;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.echo.api.events.Event;
import com.netflix.spinnaker.echo.api.events.Metadata;
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class BitbucketWebhookEventHandlerTest {

  @Test
  void canHandlePayload() throws IOException {
    File file = new File(getClass().getResource("/bitbucket_payload.json").getFile());
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("bitbucket");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "repo:push");

    BitbucketWebhookEventHandler handler = new BitbucketWebhookEventHandler(null);
    assertThatCode(() -> handler.handle(event, payload, HttpHeaders.EMPTY))
        .doesNotThrowAnyException();

    assertThat(event.content)
        .contains(
            entry("repoProject", "TES"),
            entry("slug", "bitbucketfan/test_webhooks"),
            entry("hash", "9384d1bf6db69ea35cda200648ade30f8ea7b1a4"),
            entry("branch", "master"),
            entry("action", "repo:push"));
  }
}
