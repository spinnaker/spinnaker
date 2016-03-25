/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.cache

import spock.lang.Specification

class CacheResultBuilderSpec extends Specification {

  def "should build a cache result"() {
    given:
      CacheResultBuilder crb = new CacheResultBuilder()

      crb.namespace("applications").keep("appKey").with {
        attributes.santa = "clause"
        relationships["clusters"].add("clusterKey")
      }
      crb.namespace("clusters").keep("clusterKey").with {
        attributes.xmen = "wolverine"
        relationships["foo"].add("bar")
      }

    when:
      def result = crb.build()

    then:
      !result.cacheResults.empty
      result.cacheResults.applications[0].id == "appKey"
      result.cacheResults.applications[0].attributes.santa == "clause"
      result.cacheResults.applications[0].relationships.clusters[0] == "clusterKey"
      result.cacheResults.clusters[0].id == "clusterKey"
      result.cacheResults.clusters[0].attributes.xmen == "wolverine"
      result.cacheResults.clusters[0].relationships.foo[0] == "bar"
  }
}
