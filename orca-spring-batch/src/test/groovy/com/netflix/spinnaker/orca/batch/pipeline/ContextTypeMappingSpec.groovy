/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.batch.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification

class ContextTypeMappingSpec extends Specification {

  def pipeline = new Pipeline()
  def context = [name: "foo", type: "bar", nested: [isNested: true, numbers: 1234]]
  def stage = new Stage<>(pipeline, "foo", context)

  static class StageData {
    String name
    String type
  }

  void "should be able to map stage context to POJO"() {
    given:
    def data = stage.mapTo(StageData)

    expect:
    data.name == context.name
    data.type == context.type
  }

  static class NestedData {
    boolean isNested
    int numbers
  }

  void "should be able to map nested data to POJO"() {
    given:
    def data = stage.mapTo("/nested", NestedData)

    expect:
    data.isNested
    data.numbers == context.nested.numbers
  }

  static class StageAndNestedData {
    String name
    String type
    Map<String, Object> nested
  }

  void "should be able to map top-level data perserving nested structure"() {
    given:
    def data = stage.mapTo(StageAndNestedData)

    expect:
    data.name == context.name
    data.type == context.type
    data.nested == context.nested
  }

  void "should be able to commit data structures back to the context"() {
    setup:
    def data = stage.mapTo(StageAndNestedData)

    when:
    data.type = newtype

    and:
    stage.commit(data)

    then:
    stage.context.type == newtype

    where:
    newtype = "newtype"
  }

  void "should be able to commit data structures back to the context at a nested depth"() {
    setup:
    def data = stage.mapTo("/nested", NestedData)

    when:
    data.numbers = newnum

    and:
    stage.commit("/nested", data)

    then:
    stage.context.nested.numbers == newnum

    where:
    newnum = 456
  }
}
