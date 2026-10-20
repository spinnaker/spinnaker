/*
 *
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.gate.converters;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

public class JsonHttpMessageConverter extends AbstractJacksonHttpMessageConverter<ObjectMapper> {
  public JsonHttpMessageConverter(ObjectMapper objectMapper) {
    super(
        objectMapper,
        MediaType.parseMediaType("application/json"),
        MediaType.parseMediaType("application/json;charset=UTF-8"));
  }

  /**
   * This converter is registered ahead of {@code StringHttpMessageConverter}, so it is asked to
   * read {@code String} bodies first. Jackson can only bind a JSON string literal (e.g. {@code
   * "abc"}) to a {@code String}; a JSON object or array fails with "Cannot deserialize value of
   * type `java.lang.String` from Object value". That breaks anything reading the raw body, e.g. the
   * MCP transport's {@code ServerRequest.body(String.class)}.
   *
   * <p>So for {@code String} targets, hand back the raw text unless the body is a JSON string
   * literal, which keeps being unwrapped as before.
   */
  @Override
  public Object read(ResolvableType type, HttpInputMessage inputMessage, Map<String, Object> hints)
      throws IOException {
    if (!String.class.equals(type.resolve())) {
      return super.read(type, inputMessage, hints);
    }

    // Only peeks at the first non-whitespace byte; the body is otherwise streamed, not buffered.
    PushbackInputStream body = new PushbackInputStream(inputMessage.getBody(), 1);
    int first = body.read();
    while (first == ' ' || first == '\t' || first == '\r' || first == '\n') {
      first = body.read();
    }
    if (first == -1) {
      return super.read(type, replayable(inputMessage, body), hints);
    }
    body.unread(first);

    if (first == '"') {
      return super.read(type, replayable(inputMessage, body), hints);
    }
    return StreamUtils.copyToString(body, charsetOf(inputMessage.getHeaders()));
  }

  private static Charset charsetOf(HttpHeaders headers) {
    var contentType = headers.getContentType();
    return contentType != null && contentType.getCharset() != null
        ? contentType.getCharset()
        : StandardCharsets.UTF_8;
  }

  private static HttpInputMessage replayable(HttpInputMessage original, InputStream body) {
    return new HttpInputMessage() {
      @Override
      public InputStream getBody() {
        return body;
      }

      @Override
      public HttpHeaders getHeaders() {
        return original.getHeaders();
      }
    };
  }
}
