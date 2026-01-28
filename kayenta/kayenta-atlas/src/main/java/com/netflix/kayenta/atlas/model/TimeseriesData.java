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

package com.netflix.kayenta.atlas.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import lombok.*;

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TimeseriesData {

  @NotNull @Getter private String type;

  @NotNull @Getter private List<Double> values;

  public static TimeseriesData dummy(String type, long count) {
    List<Double> values =
        DoubleStream.iterate(1.0, d -> d + 1.0).limit(count).boxed().collect(Collectors.toList());
    return TimeseriesData.builder().type(type).values(values).build();
  }
}
