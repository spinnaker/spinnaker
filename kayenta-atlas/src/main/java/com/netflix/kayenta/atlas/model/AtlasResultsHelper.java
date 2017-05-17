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

package com.netflix.kayenta.atlas.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import static java.util.stream.Collectors.toList;

public class AtlasResultsHelper {

  private static AtlasResults mergeByTime(List<AtlasResults> atlasResultsList) {
    if (atlasResultsList == null || atlasResultsList.isEmpty()) {
      return null;
    }

    // TODO: Verify that the times do not overlap.
    // TODO: Verify that the number of elements in the array is correct.
    atlasResultsList.sort(Comparator.comparingLong(AtlasResults::getStart));

    AtlasResults firstAtlasResults = atlasResultsList.get(0);
    AtlasResults lastAtlasResults = atlasResultsList.get(atlasResultsList.size() - 1);
    AtlasResults.AtlasResultsBuilder atlasResultsBuilder =
      AtlasResults
        .builder()
        .type(firstAtlasResults.getType())
        .id(firstAtlasResults.getId())
        .query(firstAtlasResults.getQuery())
        .label(firstAtlasResults.getLabel())
        .start(firstAtlasResults.getStart())
        .step(firstAtlasResults.getStep())
        .end(lastAtlasResults.getEnd())
        .tags(firstAtlasResults.getTags());
    List<Double> values = new ArrayList<>();
    Long lastTimestamp = null;

    for (AtlasResults atlasResults : atlasResultsList) {
      if (lastTimestamp != null) {
        long nextTimestamp = atlasResults.getStart();
        long offset = (nextTimestamp - lastTimestamp) / atlasResults.getStep();
        List<Double> padding =
          DoubleStream
            .generate(() -> Double.NaN)
            .limit(offset)
            .boxed()
            .collect(toList());

        values.addAll(padding);
      }

      values.addAll(atlasResults.getData().getValues());
      lastTimestamp = atlasResults.getEnd();
    }

    return atlasResultsBuilder.data(TimeseriesData.builder().values(values).type(firstAtlasResults.getData().getType()).build()).build();
  }

  public static Map<String, AtlasResults> merge(List<AtlasResults> atlasResultsList) {
    return atlasResultsList
      .stream()
      .filter(atlasResults -> !atlasResults.getType().equals("close"))
      .collect(Collectors.groupingBy(AtlasResults::getId))
      .entrySet()
      .stream()
      .collect(Collectors.toMap(e -> e.getKey(), e -> mergeByTime(e.getValue())));
  }
}
