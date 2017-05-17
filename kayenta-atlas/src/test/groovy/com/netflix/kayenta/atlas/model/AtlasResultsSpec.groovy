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

package com.netflix.kayenta.atlas.model

import spock.lang.Shared
import spock.lang.Specification

class AtlasResultsSpec extends Specification {

  @Shared
  AtlasResults zeroToSixtyAbc =
    AtlasResults.builder()
      .type("timeseries")
      .id("abc")
      .query("query1")
      .label("query1")
      .start(0)
      .step(60)
      .end(60)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults zeroToSixtyDef =
    AtlasResults.builder()
      .type("timeseries")
      .id("def")
      .query("query1")
      .label("query1")
      .start(0)
      .step(60)
      .end(60)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults sixtyToOneTwenty =
    AtlasResults.builder()
      .type("timeseries")
      .id("abc")
      .query("query1")
      .label("query1")
      .start(60)
      .step(60)
      .end(120)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults oneTwentyToTwoForty =
    AtlasResults.builder()
      .type("timeseries")
      .id("abc")
      .query("query1")
      .label("query1")
      .start(120)
      .step(60)
      .end(240)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults threeHundredToFourTwentyAbc =
    AtlasResults.builder()
      .type("timeseries")
      .id("abc")
      .query("query1")
      .label("query1")
      .start(300)
      .step(60)
      .end(420)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults threeHundredToFourTwentyDef =
    AtlasResults.builder()
      .type("timeseries")
      .id("def")
      .query("query1")
      .label("query1")
      .start(300)
      .step(60)
      .end(420)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults assembledZeroToOneTwenty =
    AtlasResults.builder()
      .type("timeseries")
      .id("abc")
      .query("query1")
      .label("query1")
      .start(0)
      .step(60)
      .end(120)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3, 1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults assembledZeroToTwoForty =
    AtlasResults.builder()
      .type("timeseries")
      .id("abc")
      .query("query1")
      .label("query1")
      .start(0)
      .step(60)
      .end(240)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3, Double.NaN, 1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults assembledZeroToFourTwentyAbc =
    AtlasResults.builder()
      .type("timeseries")
      .id("abc")
      .query("query1")
      .label("query1")
      .start(0)
      .step(60)
      .end(420)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 1, 2, 3]).build())
      .build()

  @Shared
  AtlasResults assembledZeroToFourTwentyDef =
    AtlasResults.builder()
      .type("timeseries")
      .id("def")
      .query("query1")
      .label("query1")
      .start(0)
      .step(60)
      .end(420)
      .data(TimeseriesData.builder().type("array").values([1, 2, 3, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 1, 2, 3]).build())
      .build()

  void "should merge two"() {
    expect:
    AtlasResultsHelper.merge([zeroToSixtyAbc, sixtyToOneTwenty]) == [abc: assembledZeroToOneTwenty]
  }

  void "should sort by start time when merging"() {
    expect:
    AtlasResultsHelper.merge([sixtyToOneTwenty, zeroToSixtyAbc]) == [abc: assembledZeroToOneTwenty]
  }

  void "should merge sparse"() {
    expect:
    AtlasResultsHelper.merge([zeroToSixtyAbc, oneTwentyToTwoForty]) == [abc: assembledZeroToTwoForty]
  }

  void "should merge very sparse"() {
    expect:
    AtlasResultsHelper.merge([zeroToSixtyAbc, threeHundredToFourTwentyAbc]) == [abc: assembledZeroToFourTwentyAbc]
  }

  void "should group by id and merge very sparse"() {
    expect:
    AtlasResultsHelper.merge([zeroToSixtyAbc, zeroToSixtyDef, threeHundredToFourTwentyAbc, threeHundredToFourTwentyDef]) == [abc: assembledZeroToFourTwentyAbc, def: assembledZeroToFourTwentyDef]
  }
}
