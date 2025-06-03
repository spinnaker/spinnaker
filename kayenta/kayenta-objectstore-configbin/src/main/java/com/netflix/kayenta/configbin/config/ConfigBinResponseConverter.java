/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.kayenta.configbin.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;
import retrofit2.Converter;
import retrofit2.Retrofit;

@Component
@Slf4j
public class ConfigBinResponseConverter extends Converter.Factory {

  @Override
  public Converter<ResponseBody, ?> responseBodyConverter(
      Type type, Annotation[] annotations, Retrofit retrofit) {
    if (type == String.class) {
      return new StringResponseConverter();
    }
    return null;
  }

  @Override
  public @Nullable Converter<?, RequestBody> requestBodyConverter(
      Type type,
      Annotation[] parameterAnnotations,
      Annotation[] methodAnnotations,
      Retrofit retrofit) {
    if (type == RequestBody.class) {
      return value -> (RequestBody) value;
    }
    return null;
  }

  private static class StringResponseConverter implements Converter<ResponseBody, String> {
    @Override
    public String convert(ResponseBody value) {
      try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
          ResponseBody body = value) {
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = body.byteStream().read(data, 0, data.length)) != -1) {
          buffer.write(data, 0, nRead);
        }
        buffer.flush();
        return buffer.toString(StandardCharsets.UTF_8.name());
      } catch (IOException e) {
        log.error("Unable to read response body or convert it to a UTF-8 string", e);
        return null;
      }
    }
  }
}
