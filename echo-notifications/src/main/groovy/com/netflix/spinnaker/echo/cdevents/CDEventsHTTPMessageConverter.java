/*
    Copyright (C) 2023 Nordix Foundation.
    For a full list of individual contributors, please see the commit history.
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
          http://www.apache.org/licenses/LICENSE-2.0
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
*/

package com.netflix.spinnaker.echo.cdevents;

import static com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import io.cloudevents.CloudEvent;
import io.cloudevents.jackson.JsonFormat;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import org.springframework.http.MediaType;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedByteArray;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

public class CDEventsHTTPMessageConverter implements Converter {

  private final ObjectMapper objectMapper;

  public CDEventsHTTPMessageConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public static CDEventsHTTPMessageConverter create() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(JsonFormat.getCloudEventJacksonModule());
    objectMapper.disable(FAIL_ON_EMPTY_BEANS);
    return new CDEventsHTTPMessageConverter(objectMapper);
  }

  public String convertCDEventToJson(CloudEvent cdEvent) {
    try {
      return objectMapper.writeValueAsString(cdEvent);
    } catch (JsonProcessingException e) {
      throw new InvalidRequestException("Unable to convert CDEvent to Json format.", e);
    }
  }

  @Override
  public Object fromBody(TypedInput body, Type type) throws ConversionException {
    try {
      JavaType javaType = objectMapper.getTypeFactory().constructType(type);
      return objectMapper.readValue(body.in(), javaType);
    } catch (JsonParseException | JsonMappingException e) {
      throw new ConversionException(e);
    } catch (IOException e) {
      throw new ConversionException(e);
    }
  }

  @Override
  public TypedOutput toBody(Object object) {
    try {
      String json = objectMapper.writeValueAsString(object);
      return new TypedByteArray(MediaType.APPLICATION_JSON_VALUE, json.getBytes("UTF-8"));
    } catch (JsonProcessingException e) {
      throw new AssertionError(e);
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }
}
