/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb.model

import spock.lang.Specification
import spock.lang.Unroll

class GoogleCloudStorageObjectSpec extends Specification {
  @Unroll
  def "properly reads a path"() {
    when:
    GoogleCloudStorageObject gcsObject = GoogleCloudStorageObject.fromReference(path)

    then:
    gcsObject.getBucket() == bucket
    gcsObject.getObject() == object
    gcsObject.getVersion() == version

    where:
    path                                       | bucket      | object                     | version
    "gs://bucket/file"                         | "bucket"    | "file"                     | null
    "gs://a_b-3.d/file"                        | "a_b-3.d"   | "file"                     | null
    "gs://a_b-3.d/path-1/to_2/file@3.tar4"     | "a_b-3.d"   | "path-1/to_2/file@3.tar4"  | null
    "gs://bucket/file#123"                     | "bucket"    | "file"                     | 123
    "gs://bucket/file#0"                       | "bucket"    | "file"                     | 0
    "gs://a_b-3.d/path-1/to_2/file@3.tar4#987" | "a_b-3.d"   | "path-1/to_2/file@3.tar4"  | 987
  }

  @Unroll
  def "properly returns attributes"() {
    when:
    GoogleCloudStorageObject gcsObject = GoogleCloudStorageObject.fromReference(path)

    then:
    gcsObject.getReference() == path
    gcsObject.getName() == name
    gcsObject.getVersionString() == versionString

    where:
    path                                       | name                                    | versionString
    "gs://bucket/file"                         | "gs://bucket/file"                      | null
    "gs://a_b-3.d/file"                        | "gs://a_b-3.d/file"                     | null
    "gs://a_b-3.d/path-1/to_2/file@3.tar4"     | "gs://a_b-3.d/path-1/to_2/file@3.tar4"  | null
    "gs://bucket/file#123"                     | "gs://bucket/file"                      | "123"
    "gs://bucket/file#0"                       | "gs://bucket/file"                      | "0"
    "gs://a_b-3.d/path-1/to_2/file@3.tar4#987" | "gs://a_b-3.d/path-1/to_2/file@3.tar4"  | "987"
  }


  @Unroll
  def "throws an exception on invalid paths"() {
    when:
    GoogleCloudStorageObject gcsObject = GoogleCloudStorageObject.fromReference(path)

    then:
    thrown(IllegalArgumentException)

    where:
    path << [
      "abc/def",
      "gs://abc",
      "gs://abc/def#mnop"
    ]
  }
}
