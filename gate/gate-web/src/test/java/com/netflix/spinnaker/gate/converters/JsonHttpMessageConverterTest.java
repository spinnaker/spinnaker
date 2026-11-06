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

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Gate registers {@link JsonHttpMessageConverter} ahead of {@link StringHttpMessageConverter}. It
 * must not choke on JSON objects/arrays when asked for a {@code String}, otherwise anything reading
 * a raw JSON body as a {@code String} (e.g. the MCP transport's {@code
 * ServerRequest.body(String.class)}) fails with "Cannot deserialize value of type
 * `java.lang.String` from Object value".
 */
class JsonHttpMessageConverterTest {

  private static final String JSON_BODY = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"ping\"}";

  private final JsonHttpMessageConverter converter =
      new JsonHttpMessageConverter(JsonMapper.builder().build());

  @Test
  void stillReadsMapsAndPojos() {
    assertThat(converter.canRead(Map.class, MediaType.APPLICATION_JSON)).isTrue();
    assertThat(converter.canRead(List.class, MediaType.APPLICATION_JSON)).isTrue();
    assertThat(converter.canRead(Foo.class, MediaType.APPLICATION_JSON)).isTrue();
  }

  @Test
  void stillUnwrapsJsonStringLiteralsIntoString() throws Exception {
    List<HttpMessageConverter<?>> converters = List.of(converter, new StringHttpMessageConverter());

    assertThat(serverRequest(converters, "\"new message\"").body(String.class))
        .isEqualTo("new message");
  }

  @Test
  void unwrapsJsonStringLiteralWithLeadingWhitespace() throws Exception {
    List<HttpMessageConverter<?>> converters = List.of(converter, new StringHttpMessageConverter());

    assertThat(serverRequest(converters, "  \n\"new message\"").body(String.class))
        .isEqualTo("new message");
  }

  @Test
  void readsJsonArrayBodyAsRawString() throws Exception {
    List<HttpMessageConverter<?>> converters = List.of(converter, new StringHttpMessageConverter());

    assertThat(serverRequest(converters, "[1,2]").body(String.class)).isEqualTo("[1,2]");
  }

  @Test
  void stillWritesString() {
    assertThat(converter.canWrite(String.class, MediaType.APPLICATION_JSON)).isTrue();
  }

  @Test
  void routerFunctionCanReadRawJsonBodyAsStringWhenJsonConverterIsFirst() throws Exception {
    List<HttpMessageConverter<?>> converters = List.of(converter, new StringHttpMessageConverter());

    assertThat(serverRequest(converters, JSON_BODY).body(String.class)).isEqualTo(JSON_BODY);
  }

  @Test
  void routerFunctionStillBindsJsonBodyToMapWhenJsonConverterIsFirst() throws Exception {
    List<HttpMessageConverter<?>> converters = List.of(converter, new StringHttpMessageConverter());

    Map<String, Object> body =
        serverRequest(converters, JSON_BODY)
            .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    assertThat(body).containsEntry("method", "ping");
  }

  private static ServerRequest serverRequest(
      List<HttpMessageConverter<?>> converters, String body) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/mcp");
    request.setContentType(MediaType.APPLICATION_JSON_VALUE);
    request.setContent(body.getBytes());
    return ServerRequest.create(request, converters);
  }

  static class Foo {
    public String name;
  }
}
