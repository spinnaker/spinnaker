package com.netflix.spinnaker.echo.scm;

import static org.assertj.core.api.Assertions.assertThatCode;

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

class StashWebhookEventHandlerTest {

  @Test
  void canHandleLegacyPayload() throws IOException {
    File file = new File(getClass().getResource("/legacy_stash_payload.json").getFile());
    String rawPayload = new String(Files.readAllBytes(file.toPath()));

    ObjectMapper mapper = EchoObjectMapper.getInstance();
    Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<>() {});

    Event event = new Event();
    Metadata metadata = new Metadata();
    metadata.setType("git");
    metadata.setSource("stash");
    event.details = metadata;
    event.rawContent = rawPayload;
    event.payload = payload;
    event.content = payload;
    event.content.put("event_type", "repo:push");

    StashWebhookEventHandler handler = new StashWebhookEventHandler();
    assertThatCode(() -> handler.handle(event, payload)).doesNotThrowAnyException();
  }
}
