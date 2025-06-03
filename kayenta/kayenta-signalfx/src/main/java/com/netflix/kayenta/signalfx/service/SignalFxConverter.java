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
import com.netflix.kayenta.metrics.ConversionException;
import com.signalfx.signalflow.ChannelMessage;
import com.signalfx.signalflow.ServerSentEventsTransport;
import com.signalfx.signalflow.StreamMessage;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;

/**
 * The SignalFx SignalFlow api returns Mime-Type: "text/plain" with a custom body with messages in
 * it. This Converter knows how to parse those responses and return typed Objects
 */
@Slf4j
public class SignalFxConverter extends Converter.Factory {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final List<Class<?>> CONVERTIBLE_TYPES =
      ImmutableList.of(SignalFlowExecutionResult.class, ErrorResponse.class);

  @Override
  public @Nullable Converter<ResponseBody, ?> responseBodyConverter(
      Type type, Annotation[] annotations, Retrofit retrofit) {

    if (!CONVERTIBLE_TYPES.contains(type)) {
      throw new ConversionException(
          String.format(
              "The SignalFxConverter Retrofit converter can only handle Types: [ %s ], received: %s",
              CONVERTIBLE_TYPES.stream().map(Type::getTypeName).collect(Collectors.joining(", ")),
              type.getTypeName()));
    }

    if (type == SignalFlowExecutionResult.class) {
      return new SignalFlowExecutionResultConverter(objectMapper);
    } else if (type == ErrorResponse.class) {
      return new ErrorResponseConverter(objectMapper);
    }
    return null;
  }

  @Override
  public @Nullable Converter<?, RequestBody> requestBodyConverter(
      Type type,
      Annotation[] parameterAnnotations,
      Annotation[] methodAnnotations,
      Retrofit retrofit) {
    if (type == String.class) {
      return value -> RequestBody.create((String) value, okhttp3.MediaType.get("text/plain"));
    }
    return null;
  }

  private static class SignalFlowExecutionResultConverter
      implements Converter<ResponseBody, SignalFlowExecutionResult> {
    private final ObjectMapper objectMapper;

    SignalFlowExecutionResultConverter(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    @Override
    public SignalFlowExecutionResult convert(ResponseBody value) {
      List<ChannelMessage> messages = new LinkedList<>();
      try (ServerSentEventsTransport.TransportEventStreamParser parser =
          new ServerSentEventsTransport.TransportEventStreamParser(value.byteStream())) {

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
  }

  private static class ErrorResponseConverter implements Converter<ResponseBody, ErrorResponse> {
    private final ObjectMapper objectMapper;

    ErrorResponseConverter(ObjectMapper objectMapper) {
      this.objectMapper = objectMapper;
    }

    @Override
    public ErrorResponse convert(ResponseBody value) {
      try {
        return objectMapper.readValue(value.charStream(), ErrorResponse.class);
      } catch (Exception e) {
        throw new ConversionException("Failed to parse error response", e);
      }
    }
  }
}
