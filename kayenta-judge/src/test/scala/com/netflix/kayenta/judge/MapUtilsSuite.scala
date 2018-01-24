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

import com.netflix.kayenta.judge.utils.MapUtils
import org.scalatest.FunSuite

class MapUtilsSuite extends FunSuite {

  test("get path") {
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

  test("get of null") {
    assert(MapUtils.get(null, "this", "and", "that") === None)
  }

  test("get of java map") {
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
}
