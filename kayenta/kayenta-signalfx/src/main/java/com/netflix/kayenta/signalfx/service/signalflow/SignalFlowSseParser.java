/*
 * Copyright 2026 Harness, Inc.
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
package com.netflix.kayenta.signalfx.service.signalflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Parses a Splunk Observability SignalFlow response stream (text/event-stream) into typed {@link
 * SignalFlowMessage}s. Replaces {@code
 * com.signalfx.signalflow.ServerSentEventsTransport.TransportEventStreamParser} from the archived
 * signalfx-java library.
 *
 * <p>The wire format is standard SSE: records separated by blank lines, each record composed of
 * {@code event: <name>} and one or more {@code data: <line>} fields. The payload of a record is the
 * concatenation of its data lines joined with {@code \n}, and — for SignalFlow — is JSON. Unknown
 * event types are surfaced as {@link SignalFlowMessage.RawMessage} with type {@code UNKNOWN} rather
 * than dropped, so callers can log or ignore them.
 */
public class SignalFlowSseParser implements Iterator<SignalFlowMessage>, AutoCloseable {

  private final BufferedReader reader;
  private final ObjectMapper objectMapper;
  private SignalFlowMessage next;
  private boolean eof;

  public SignalFlowSseParser(InputStream inputStream) {
    this(inputStream, new ObjectMapper());
  }

  public SignalFlowSseParser(InputStream inputStream, ObjectMapper objectMapper) {
    this.reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    this.objectMapper = objectMapper;
  }

  @Override
  public boolean hasNext() {
    if (next != null) {
      return true;
    }
    if (eof) {
      return false;
    }
    try {
      next = readNext();
    } catch (IOException e) {
      throw new SignalFlowParseException("Failed reading SignalFlow SSE stream", e);
    }
    if (next == null) {
      eof = true;
      return false;
    }
    return true;
  }

  @Override
  public SignalFlowMessage next() {
    if (!hasNext()) {
      throw new NoSuchElementException();
    }
    SignalFlowMessage result = next;
    next = null;
    return result;
  }

  private SignalFlowMessage readNext() throws IOException {
    String eventName = null;
    StringBuilder data = null;
    String line;
    while ((line = reader.readLine()) != null) {
      if (line.isEmpty()) {
        if (eventName != null && data != null) {
          return decode(eventName, data.toString());
        }
        // stray blank line between records with no fields; keep scanning
        eventName = null;
        data = null;
        continue;
      }
      if (line.startsWith(":")) {
        // SSE comment
        continue;
      }
      int colon = line.indexOf(':');
      String field;
      String value;
      if (colon < 0) {
        field = line;
        value = "";
      } else {
        field = line.substring(0, colon);
        value = line.substring(colon + 1);
        if (!value.isEmpty() && value.charAt(0) == ' ') {
          value = value.substring(1);
        }
      }
      switch (field) {
        case "event":
          eventName = value;
          break;
        case "data":
          if (data == null) {
            data = new StringBuilder(value);
          } else {
            data.append('\n').append(value);
          }
          break;
        default:
          // ignore id:, retry:, and any unknown fields
      }
    }
    if (eventName != null && data != null) {
      return decode(eventName, data.toString());
    }
    return null;
  }

  private SignalFlowMessage decode(String eventName, String data) {
    SignalFlowMessage.Type type = SignalFlowMessage.parseEventType(eventName);
    JsonNode payload;
    try {
      payload = objectMapper.readTree(data);
    } catch (IOException e) {
      throw new SignalFlowParseException(
          "Failed parsing SignalFlow JSON payload for event '" + eventName + "'", e);
    }
    return SignalFlowMessage.fromJson(type, payload);
  }

  @Override
  public void close() throws IOException {
    reader.close();
  }

  public static class SignalFlowParseException extends RuntimeException {
    public SignalFlowParseException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
