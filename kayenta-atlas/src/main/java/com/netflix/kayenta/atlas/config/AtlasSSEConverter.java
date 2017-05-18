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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@Component
@Slf4j
// Atlas returns one json stanza per line; we rely on that here.
// We are not implementing full, proper SSE handling here.
public class AtlasSSEConverter implements Converter {

  private static final ObjectMapper objectMapper = new ObjectMapper()
    .setSerializationInclusion(NON_NULL)
    .disable(FAIL_ON_UNKNOWN_PROPERTIES);
  private static final List<String> expectedResultsTypeList = Arrays.asList(new String[]{"timeseries", "close"});

  @Override
  public List<AtlasResults> fromBody(TypedInput body, Type type) throws ConversionException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(body.in()))) {
      List<String[]> tokenizedLines =
        reader
          .lines()
          .filter(line -> !StringUtils.isEmpty(line))
          .map(line -> line.split(": ", 2))
          .collect(Collectors.toList());

      tokenizedLines
        .stream()
        .map(tokenizedLine -> tokenizedLine[0])
        .filter(openingToken -> !openingToken.equals("data"))
        .forEach(nonDataOpeningToken -> log.info("Received opening token other than 'data' from Atlas: {}", nonDataOpeningToken));

      List<AtlasResults> atlasResultsList =
        tokenizedLines
          .stream()
          .map(AtlasSSEConverter::convertTokenizedLineToAtlasResults)
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      if (atlasResultsList.isEmpty()) {
        log.error("Received no data from Atlas.");

        // TODO(duftler): Propagate exception here?
        return null;
      } else if (!atlasResultsList.get(atlasResultsList.size() - 1).getType().equals("close")) {
        log.error("Received data from Atlas that did not terminate with a 'close'.");

        // TODO(duftler): Propagate exception here?
        return null;
      }

      return atlasResultsList;
    } catch (IOException e) {
      e.printStackTrace();
    }

    return null;
  }

  private static AtlasResults convertTokenizedLineToAtlasResults(String[] tokenizedLine) {
    try {
      AtlasResults atlasResults = objectMapper.readValue(tokenizedLine[1], AtlasResults.class);
      String atlasResultsType = atlasResults.getType();

      if (StringUtils.isEmpty(atlasResultsType) || !expectedResultsTypeList.contains(atlasResultsType)) {
        log.info("Received results of type other than 'timeseries' or 'close' from Atlas: {}", atlasResults);

        // TODO: Retry on type 'error'?

        return null;
      }

      return atlasResults;
    } catch (IOException e) {
      e.printStackTrace();

      return null;
    }
  }

  @Override
  public TypedOutput toBody(Object object) {
    return null;
  }
}
