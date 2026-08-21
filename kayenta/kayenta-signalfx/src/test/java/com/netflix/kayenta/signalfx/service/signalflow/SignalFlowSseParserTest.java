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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SignalFlowSseParserTest {

  @Test
  void parses_the_fixture_signalflow_response() throws Exception {
    try (InputStream response =
            getClass().getClassLoader().getResourceAsStream("signalfx-signalflow-response.text");
        SignalFlowSseParser parser = new SignalFlowSseParser(response)) {
      List<SignalFlowMessage> messages = new ArrayList<>();
      while (parser.hasNext()) {
        messages.add(parser.next());
      }
      assertThat(messages).hasSizeGreaterThan(1);

      long dataMessageCount =
          messages.stream().filter(m -> m.getType() == SignalFlowMessage.Type.DATA_MESSAGE).count();
      assertThat(dataMessageCount).isGreaterThan(0);

      SignalFlowMessage.DataMessage anyDataPoint =
          messages.stream()
              .filter(m -> m instanceof SignalFlowMessage.DataMessage)
              .map(m -> (SignalFlowMessage.DataMessage) m)
              .filter(dm -> !dm.getData().isEmpty())
              .findFirst()
              .orElseThrow();
      assertThat(anyDataPoint.getLogicalTimestampMs()).isGreaterThan(0);
      assertThat(anyDataPoint.getData()).containsKey("AAAAAFOJhJg");
    }
  }

  @Test
  void extracts_ts_id_and_value_from_data_frame() {
    String body =
        "event: data\n"
            + "id: data-1\n"
            + "data: {\n"
            + "data:   \"data\" : [ { \"tsId\" : \"ABC\", \"value\" : 42.5 } ],\n"
            + "data:   \"logicalTimestampMs\" : 1000\n"
            + "data: }\n\n";
    SignalFlowMessage.DataMessage msg = (SignalFlowMessage.DataMessage) parseOne(body);
    assertThat(msg.getLogicalTimestampMs()).isEqualTo(1000L);
    assertThat(msg.getData()).containsEntry("ABC", 42.5);
  }

  @Test
  void empty_data_array_yields_empty_map() {
    String body = "event: data\ndata: {\"data\":[],\"logicalTimestampMs\":10}\n\n";
    SignalFlowMessage.DataMessage msg = (SignalFlowMessage.DataMessage) parseOne(body);
    assertThat(msg.getData()).isEmpty();
    assertThat(msg.getLogicalTimestampMs()).isEqualTo(10L);
  }

  @Test
  void unknown_event_types_surface_as_unknown_raw_messages() {
    String body = "event: nonsense\ndata: {\"foo\":1}\n\n";
    SignalFlowMessage msg = parseOne(body);
    assertThat(msg.getType()).isEqualTo(SignalFlowMessage.Type.UNKNOWN);
    assertThat(msg).isInstanceOf(SignalFlowMessage.RawMessage.class);
  }

  @Test
  void terminates_cleanly_on_end_of_channel() {
    String body =
        "event: data\ndata: {\"data\":[],\"logicalTimestampMs\":1}\n\n"
            + "event: control-message\ndata: {\"event\":\"END_OF_CHANNEL\"}\n\n";
    try (SignalFlowSseParser parser = newParser(body)) {
      assertThat(parser.hasNext()).isTrue();
      parser.next();
      assertThat(parser.hasNext()).isTrue();
      SignalFlowMessage control = parser.next();
      assertThat(control.getType()).isEqualTo(SignalFlowMessage.Type.CONTROL_MESSAGE);
      assertThat(parser.hasNext()).isFalse();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void malformed_json_raises_parse_exception() {
    String body = "event: data\ndata: {not-json}\n\n";
    assertThatThrownBy(() -> parseOne(body))
        .isInstanceOf(SignalFlowSseParser.SignalFlowParseException.class);
  }

  private SignalFlowMessage parseOne(String body) {
    try (SignalFlowSseParser parser = newParser(body)) {
      assertThat(parser.hasNext()).isTrue();
      return parser.next();
    } catch (Exception e) {
      if (e instanceof RuntimeException) throw (RuntimeException) e;
      throw new RuntimeException(e);
    }
  }

  private SignalFlowSseParser newParser(String body) {
    return new SignalFlowSseParser(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
  }
}
