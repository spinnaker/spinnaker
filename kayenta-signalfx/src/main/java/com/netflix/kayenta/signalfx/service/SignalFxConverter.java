/*
 * Copyright (c) 2018 Nike, inc.
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
 *
 */

package com.netflix.kayenta.signalfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.signalfx.signalflow.ChannelMessage;
import com.signalfx.signalflow.ServerSentEventsTransport;
import com.signalfx.signalflow.StreamMessage;
import lombok.extern.slf4j.Slf4j;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;
import retrofit.mime.TypedString;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The SignalFx SignalFlow api returns Mime-Type: "text/plain" with a custom body with messages in it.
 * This Converter knows how to parse those responses and return typed Objects
 */
@Slf4j
public class SignalFxConverter implements Converter {

  private ObjectMapper objectMapper = new ObjectMapper();

  private static final List<Type> CONVERTIBLE_TYPES = ImmutableList.of(
      SignalFlowExecutionResult.class,
      ErrorResponse.class
  );

  @Override
  public Object fromBody(TypedInput body, Type type) throws ConversionException {

    if (!CONVERTIBLE_TYPES.contains(type)) {
      throw new ConversionException(
          String.format("The SignalFxConverter Retrofit converter can only handle Types: [ %s ], received: %s",
              CONVERTIBLE_TYPES.stream().map(Type::getTypeName).collect(Collectors.joining(", ")),
              type.getTypeName()));
    }

    if (type.getTypeName().equals(SignalFlowExecutionResult.class.getTypeName())) {
      return getSignalFlowExecutionResultFromBody(body);
    } else {
      return getErrorResponseFromBody(body);
    }
  }

  private ErrorResponse getErrorResponseFromBody(TypedInput body) throws ConversionException {
    try {
      return objectMapper.readValue(body.in(), ErrorResponse.class);
    } catch (Exception e) {
      throw new ConversionException("Failed to parse error response", e);
    }

  }

  private SignalFlowExecutionResult getSignalFlowExecutionResultFromBody(TypedInput body) throws ConversionException {
    List<ChannelMessage> messages = new LinkedList<>();
    try (ServerSentEventsTransport.TransportEventStreamParser parser =
             new ServerSentEventsTransport.TransportEventStreamParser(body.in())) {

      while (parser.hasNext()) {
        StreamMessage streamMessage = parser.next();
        ChannelMessage channelMessage = ChannelMessage.decodeStreamMessage(streamMessage);
        messages.add(channelMessage);
      }
    } catch (Exception e) {
      throw new ConversionException("There was an issue parsing the SignalFlow response", e);
    }
    return new SignalFlowExecutionResult(messages);
  }

  @Override
  public TypedOutput toBody(Object object) {
    String string = (String) object;
    return new TypedString(string);
  }
}
