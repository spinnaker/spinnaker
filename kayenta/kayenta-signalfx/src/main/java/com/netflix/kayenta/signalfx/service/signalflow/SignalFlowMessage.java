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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;

/**
 * Internal replacement for the {@code ChannelMessage} hierarchy from the archived
 * com.signalfx.public:signalfx-java library. Only the fields kayenta actually consumes are modeled.
 */
@Getter
public abstract class SignalFlowMessage {

  public enum Type {
    CONTROL_MESSAGE,
    METADATA_MESSAGE,
    DATA_MESSAGE,
    EVENT_MESSAGE,
    INFO_MESSAGE,
    ERROR_MESSAGE,
    EXPIRED_TSID_MESSAGE,
    UNKNOWN
  }

  private final Type type;

  protected SignalFlowMessage(Type type) {
    this.type = type;
  }

  /** Maps the SSE {@code event:} name to a message type. */
  static Type parseEventType(String eventName) {
    if (eventName == null) {
      return Type.UNKNOWN;
    }
    switch (eventName) {
      case "control-message":
        return Type.CONTROL_MESSAGE;
      case "metadata":
        return Type.METADATA_MESSAGE;
      case "data":
        return Type.DATA_MESSAGE;
      case "event":
        return Type.EVENT_MESSAGE;
      case "message":
        return Type.INFO_MESSAGE;
      case "error":
        return Type.ERROR_MESSAGE;
      case "expired-tsid":
        return Type.EXPIRED_TSID_MESSAGE;
      default:
        return Type.UNKNOWN;
    }
  }

  /** Data point message; kayenta reads only {@link #getLogicalTimestampMs} and {@link #getData}. */
  @Getter
  public static class DataMessage extends SignalFlowMessage {
    private final long logicalTimestampMs;
    private final Map<String, Number> data;

    DataMessage(long logicalTimestampMs, Map<String, Number> data) {
      super(Type.DATA_MESSAGE);
      this.logicalTimestampMs = logicalTimestampMs;
      this.data = data;
    }
  }

  /** Any non-data message we don't need to introspect (control/metadata/info/event/error/etc). */
  @Getter
  public static class RawMessage extends SignalFlowMessage {
    private final JsonNode payload;

    RawMessage(Type type, JsonNode payload) {
      super(type);
      this.payload = payload;
    }
  }

  static SignalFlowMessage fromJson(Type type, JsonNode payload) {
    if (type == Type.DATA_MESSAGE) {
      long logicalTimestampMs =
          payload.hasNonNull("logicalTimestampMs")
              ? payload.get("logicalTimestampMs").asLong()
              : 0L;
      JsonNode dataArray = payload.get("data");
      Map<String, Number> data = new LinkedHashMap<>();
      if (dataArray != null && dataArray.isArray()) {
        for (JsonNode point : dataArray) {
          JsonNode tsId = point.get("tsId");
          JsonNode value = point.get("value");
          if (tsId != null && value != null) {
            data.put(tsId.asText(), value.isIntegralNumber() ? value.asLong() : value.asDouble());
          }
        }
      }
      return new DataMessage(logicalTimestampMs, Collections.unmodifiableMap(data));
    }
    return new RawMessage(type, payload);
  }
}
