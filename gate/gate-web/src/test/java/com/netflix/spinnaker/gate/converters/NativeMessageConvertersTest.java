/*
 * Copyright 2026 McIntosh.farm
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.converters;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Gate no longer registers its own JSON converter, so Spring's default order applies: {@link
 * StringHttpMessageConverter} ahead of the Jackson converter. Raw JSON bodies read as a {@code
 * String} (e.g. the MCP transport's {@code ServerRequest.body(String.class)}) must come back
 * untouched, and JSON bodies must still bind to maps.
 */
@SuppressWarnings("removal")
class NativeMessageConvertersTest {

  private static final String JSON_BODY = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

  private final List<HttpMessageConverter<?>> converters =
      List.of(
          new StringHttpMessageConverter(),
          new MappingJackson2HttpMessageConverter(new ObjectMapper()));

  @Test
  void readsRawJsonObjectBodyAsString() throws Exception {
    assertThat(serverRequest(JSON_BODY).body(String.class)).isEqualTo(JSON_BODY);
  }

  @Test
  void readsJsonArrayBodyAsRawString() throws Exception {
    assertThat(serverRequest("[1,2]").body(String.class)).isEqualTo("[1,2]");
  }

  @Test
  void stillBindsJsonBodyToMap() throws Exception {
    Map<String, Object> body =
        serverRequest(JSON_BODY).body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(body).containsEntry("method", "ping");
  }

  @Test
  void stringConverterWritesStringsUnquoted() {
    assertThat(converters.get(0).canWrite(String.class, MediaType.APPLICATION_JSON)).isTrue();
  }

  private ServerRequest serverRequest(String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(body.getBytes());
    return ServerRequest.create(request, converters);
  }
}
