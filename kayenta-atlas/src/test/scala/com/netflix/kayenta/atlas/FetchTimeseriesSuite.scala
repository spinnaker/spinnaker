/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.kayenta.atlas

import org.scalatest.FunSuite

class FetchTimeseriesSuite extends FunSuite {
  test("merges two") {
    val a = FetchTimeseries("timeseries", "abc", "query1", "query1", 0, 60, 60, Map(), TimeseriesData("array", List(1, 2, 3)))
    val b = FetchTimeseries("timeseries", "abc", "query1", "query1", 60, 60, 120, Map(), TimeseriesData("array", List(1, 2, 3)))

    val result = FetchTimeseries("timeseries", "abc", "query1", "query1", 0, 60, 120, Map(), TimeseriesData("array", List(1, 2, 3, 1, 2, 3)))
    assert(FetchTimeseries.merge(List(a, b)) === Map("abc" -> result))
  }

  test("merges sparse") {
    val tags = Map.empty[String, String]
    val a = FetchTimeseries("timeseries", "abc", "query1", "query1", 0, 60, 60, tags, TimeseriesData("array", List(1, 2, 3)))
    val b = FetchTimeseries("timeseries", "abc", "query1", "query1", 120, 60, 240, tags, TimeseriesData("array", List(1, 2, 3)))

    val result = List(1, 2, 3, Double.NaN, 1, 2, 3)
    val actual = FetchTimeseries.merge(List(a, b))("abc")
    assert(actual.query === a.query)
    assert(actual.id === a.id)
    assert(actual.label === a.label)
    assert(actual.start === a.start)
    assert(actual.end === b.end)
    assert(actual.step === b.step)
    val valueMatch = actual.data.values.zip(result).map { a => a._1.equals(a._2) }
    valueMatch.foreach { v => assert(v, "data array equality")}
  }

  test("merges very sparse") {
    val tags = Map.empty[String, String]
    val a = FetchTimeseries("timeseries", "abc", "query1", "query1", 0, 60, 60, tags, TimeseriesData("array", List(1, 2, 3)))
    val b = FetchTimeseries("timeseries", "abc", "query1", "query1", 300, 60, 420, tags, TimeseriesData("array", List(1, 2, 3)))

    val result = List(1, 2, 3, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 1, 2, 3)
    val actual = FetchTimeseries.merge(List(a, b))("abc")
    assert(actual.query === a.query)
    assert(actual.id === a.id)
    assert(actual.label === a.label)
    assert(actual.start === a.start)
    assert(actual.end === b.end)
    assert(actual.step === b.step)
    val valueMatch = actual.data.values.zip(result).map { a => a._1.equals(a._2) }
    valueMatch.foreach { v => assert(v, "data array equality")}
  }
}
