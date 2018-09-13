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

package com.netflix.kayenta.judge

import java.util.Collections

import com.netflix.kayenta.judge.utils.{MapUtils, RandomUtils}
import org.apache.commons.math3.stat.StatUtils
import org.scalatest.FunSuite
import org.scalatest.Matchers._

class UtilsSuite extends FunSuite {

  test("RandomUtils List of Random Samples (Zero Mean)"){
    val seed = 123456789
    RandomUtils.init(seed)
    val randomSample: Array[Double] = RandomUtils.normal(mean = 0.0, stdev = 1.0, numSamples = 500)

    val mean = StatUtils.mean(randomSample)
    val variance = StatUtils.variance(randomSample)

    assert(mean === (0.0 +- 0.2))
    assert(variance === (1.0 +- 0.2))
    assert(randomSample.length === 500)
  }

  test("RandomUtils List of Random Samples (Non-zero Mean)"){
    val seed = 123456789
    RandomUtils.init(seed)
    val randomSample: Array[Double] = RandomUtils.normal(mean = 10.0, stdev = 3.0, numSamples = 1000)

    val mean = StatUtils.mean(randomSample)
    val stdev =  math.sqrt(StatUtils.variance(randomSample))

    assert(mean === (10.0 +- 0.2))
    assert(stdev === (3.0 +- 0.2))
    assert(randomSample.length === 1000)
  }

  test("RandomUtils Random Sample (Zero Variance)"){
    val randomSample = RandomUtils.normal(mean = 0.0, stdev = 0.0, numSamples = 1)
    assert(randomSample.head === 0.0)
  }

  test("MapUtils Get Path") {
    val map = Map(
      "foo" -> Map(
        "bar" -> 42,
        "baz" -> "abc"
      ),
      "list" -> List(
        Map("a" -> "1"),
        Map("b" -> "2"),
        Map("a" -> "3")
      )
    )
    assert(MapUtils.get(map) === Some(map))
    assert(MapUtils.get(42) === Some(42))
    assert(MapUtils.get(map, "not_found") === None)
    assert(MapUtils.get(map, "foo", "not_found") === None)
    assert(MapUtils.get(map, "foo", "bar") === Some(42))
    assert(MapUtils.get(map, "foo", "baz") === Some("abc"))
    assert(MapUtils.get(map, "foo", "bar", "baz") === None)
    assert(MapUtils.get(map, "list", "a") === Some(List("1", "3")))
    assert(MapUtils.get(map, "list", "b") === Some(List("2")))
    assert(MapUtils.get(map, "list", "c") === None)
  }

  test("MapUtils Get of Null") {
    assert(MapUtils.get(null, "this", "and", "that") === None)
  }

  test("MapUtils Get of Java Map") {
    val foo: java.util.Map[String, Object] = new java.util.HashMap()
    foo.put("bar", new Integer(42))
    foo.put("baz", "abc")

    val list: java.util.List[Object] = new java.util.ArrayList()
    list.add(Collections.singletonMap("a", "1"))
    list.add(Collections.singletonMap("b", "2"))
    list.add(Collections.singletonMap("a", "3"))

    val map: java.util.Map[String, Object] = new java.util.HashMap()
    map.put("foo", foo)
    map.put("list", list)

    assert(MapUtils.get(map) === Some(map))
    assert(MapUtils.get(42) === Some(42))
    assert(MapUtils.get(map, "not_found") === None)
    assert(MapUtils.get(map, "foo", "not_found") === None)
    assert(MapUtils.get(map, "foo", "bar") === Some(42))
    assert(MapUtils.get(map, "foo", "baz") === Some("abc"))
    assert(MapUtils.get(map, "foo", "bar", "baz") === None)
    assert(MapUtils.get(map, "list", "a") === Some(List("1", "3")))
    assert(MapUtils.get(map, "list", "b") === Some(List("2")))
    assert(MapUtils.get(map, "list", "c") === None)
  }

  test("MapUtils get of a double works when it's an Integer") {
    val foo: java.util.Map[String, Object] = new java.util.HashMap()
    foo.put("bar", new Integer(42))
    assert(MapUtils.getAsDoubleWithDefault(1.0, foo, "bar") === 42.0);
  }
}
