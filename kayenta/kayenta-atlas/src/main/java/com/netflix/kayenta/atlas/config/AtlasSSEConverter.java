/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta.atlas.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.metrics.FatalQueryException;
import com.netflix.kayenta.metrics.RetryableQueryException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import retrofit2.Converter;
import retrofit2.Retrofit;

@Component
@Slf4j
// Atlas returns one json stanza per line; we rely on that here.
// We are not implementing full, proper SSE handling here.
public class AtlasSSEConverter extends Converter.Factory {

  private static final List<String> EXPECTED_RESULTS_TYPE_LIST =
      Arrays.asList("timeseries", "close");

  private final ObjectMapper kayentaObjectMapper;
  private String queryName;
  private String queryString;
  private String configName;

  @Autowired
  public AtlasSSEConverter(ObjectMapper kayentaObjectMapper) {
    this(kayentaObjectMapper, null, null, null);
  }

  public AtlasSSEConverter(
      ObjectMapper kayentaObjectMapper, String configName, String queryName, String queryString) {
    this.kayentaObjectMapper = kayentaObjectMapper;
    this.queryName = queryName;
    this.queryString = queryString;
    this.configName = configName;
  }

  @Override
  public Converter<ResponseBody, List<AtlasResults>> responseBodyConverter(
      Type type, Annotation[] annotations, Retrofit retrofit) {
    if (isListOfAtlasResults(type)) {
      return new AtlasSSEResponseConverter(kayentaObjectMapper, configName, queryName, queryString);
    }
    return null;
  }

  private boolean isListOfAtlasResults(Type type) {
    if (!(type instanceof java.lang.reflect.ParameterizedType)) {
      return false;
    }
    java.lang.reflect.ParameterizedType parameterizedType =
        (java.lang.reflect.ParameterizedType) type;
    if (parameterizedType.getRawType() != List.class) {
      return false;
    }
    Type[] typeArguments = parameterizedType.getActualTypeArguments();
    return typeArguments.length > 0 && typeArguments[0] == AtlasResults.class;
  }

  private static class AtlasSSEResponseConverter
      implements Converter<ResponseBody, List<AtlasResults>> {
    private final ObjectMapper objectMapper;
    private final String configName;
    private final String queryName;
    private final String queryString;

    public AtlasSSEResponseConverter(
        ObjectMapper objectMapper, String configName, String queryName, String queryString) {
      this.objectMapper = objectMapper;
      this.configName = configName;
      this.queryName = queryName;
      this.queryString = queryString;
    }

    @Override
    public List<AtlasResults> convert(ResponseBody value) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(value.byteStream()))) {
        return processInput(reader);
      } catch (IOException e) {
        log.error("Cannot process Atlas results", e);
      }
      return null;
    }

    protected List<AtlasResults> processInput(BufferedReader reader) {
      List<String[]> tokenizedLines =
          reader
              .lines()
              .filter(line -> !StringUtils.isEmpty(line))
              .map(line -> line.split(": ", 2))
              .collect(Collectors.toList());

      tokenizedLines.stream()
          .map(tokenizedLine -> tokenizedLine[0])
          .filter(openingToken -> !openingToken.equals("data"))
          .forEach(
              nonDataOpeningToken ->
                  log.info(
                      "Received opening token other than 'data' from Atlas: {}",
                      nonDataOpeningToken));

      List<AtlasResults> atlasResultsList =
          tokenizedLines.stream()
              .map(this::convertTokenizedLineToAtlasResults)
              .filter(Objects::nonNull)
              .collect(Collectors.toList());

      if (!atlasResultsList.get(atlasResultsList.size() - 1).getType().equals("close")) {
        log.error("Received data from Atlas that did not terminate with a 'close'.");
        throw new RetryableQueryException(
            "Atlas response did not end in a 'close', we cannot guarantee all data was received.");
      }

      return atlasResultsList;
    }

    protected AtlasResults convertTokenizedLineToAtlasResults(String[] tokenizedLine) {
      try {
        AtlasResults atlasResults = objectMapper.readValue(tokenizedLine[1], AtlasResults.class);
        String atlasResultsType = atlasResults.getType();

        if (StringUtils.isEmpty(atlasResultsType)
            || !EXPECTED_RESULTS_TYPE_LIST.contains(atlasResultsType)) {
          if (atlasResultsType.equals("error")) {
            if (atlasResults.getMessage().contains("IllegalStateException")) {
              throw new FatalQueryException(
                  "Atlas query"
                      + ((configName != null) ? " in canary config [" + configName + "]" : "")
                      + ((queryName != null) ? " for query [" + queryName + "]" : "")
                      + ((queryString != null) ? " with query string [" + queryString + "]" : "")
                      + " failed: "
                      + atlasResults.getMessage());
            } else {
              throw new RetryableQueryException("Atlas query failed: " + atlasResults.getMessage());
            }
          }
          log.info(
              "Received results of type other than 'timeseries' or 'close' from Atlas: {}",
              atlasResults);

          return null;
        }

        return atlasResults;
      } catch (IOException e) {
        log.error("Cannot process Atlas results", e);
        return null;
      }
    }
  }
}
