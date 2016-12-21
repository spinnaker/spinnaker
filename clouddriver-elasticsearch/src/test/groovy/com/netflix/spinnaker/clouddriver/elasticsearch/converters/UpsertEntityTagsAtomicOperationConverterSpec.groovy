/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.clouddriver.elasticsearch.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.model.EntityTags
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertEntityTagsAtomicOperationConverterSpec extends Specification {
  def objectMapper = new ObjectMapper()

  @Subject
  def converter = new UpsertEntityTagsAtomicOperationConverter(objectMapper, null, null, null)

  @Unroll
  def "should set valueType when converting from Map -> Description"() {
    given:
    def entityTags = [
      tags: [
        [name: "myTag", value: tagValue]
      ]
    ]

    when:
    def description = converter.convertDescription(entityTags)

    then:
    description.tags[0].valueType == expectedValueType

    where:
    tagValue             || expectedValueType
    ["this": "is a map"] || EntityTags.EntityTagValueType.object
    ["this is a list"]   || EntityTags.EntityTagValueType.object
    "this is a string"   || EntityTags.EntityTagValueType.literal
    42                   || EntityTags.EntityTagValueType.literal
  }
}
