/*
 * Copyright 2020 Expedia, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
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

package com.netflix.spinnaker.clouddriver.ecs.names

import com.amazonaws.services.ecs.model.Service
import com.amazonaws.services.ecs.model.Tag
import com.netflix.spinnaker.moniker.Namer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static EcsTagNamer.APPLICATION
import static EcsTagNamer.CLUSTER
import static EcsTagNamer.STACK
import static EcsTagNamer.DETAIL
import static EcsTagNamer.SEQUENCE

class EcsTagNamerSpec extends Specification {

  @Shared
  Namer namer = new EcsTagNamer()

  @Unroll
  def "should derive correct moniker"() {
    given:
    def service = new Service(serviceName: name, tags: tags?.collect {new Tag(key: it.key, value: it.value) })
    def moniker = namer.deriveMoniker(new EcsResourceService(service))

    expect:
    with(moniker) {
      app == expectedApp
      cluster == expectedCluster
      stack == expectedStack
      detail == expectedDetail
      sequence == expectedSequence
    }

    where:
    name                                                      | tags                                  || expectedApp  | expectedCluster                                      | expectedStack     | expectedDetail | expectedSequence
    "cass-nccpintegration-random-junk-d0prod-z0useast1a-v003" | null                                  || "cass"       | "cass-nccpintegration-random-junk-d0prod-z0useast1a" | "nccpintegration" | "random-junk-d0prod-z0useast1a" | 3
    "cass-nccpintegration-random-junk-d0prod-z0useast1a-v003" | [:]                                   || "cass"       | "cass-nccpintegration-random-junk-d0prod-z0useast1a" | "nccpintegration" | "random-junk-d0prod-z0useast1a" | 3
    "cass-nccpintegration-random-junk-d0prod-z0useast1a-v003" | [(APPLICATION): "myApp"]              || "myApp"      | "cass-nccpintegration-random-junk-d0prod-z0useast1a" | "nccpintegration" | "random-junk-d0prod-z0useast1a" | 3
    "cass-nccpintegration-random-junk-v003"                   | [(CLUSTER): "myCluster"]              || "cass"       | "myCluster"                                          | "nccpintegration" | "random-junk" | 3
    "cass-nccpintegration-random-junk-v003"                   | [(STACK): "myStack"]                  || "cass"       | "cass-myStack"                                       | "myStack"         | "random-junk" | 3
    "cass-nccpintegration-random-junk-v003"                   | [(STACK): "myStack", (DETAIL): ""]    || "cass"       | "cass-myStack"                                       | "myStack"         | ""            | 3
    "cass-nccpintegration-random-junk-v003"                   | [(DETAIL): "myDetail"]                || "cass"       | "cass--myDetail"                                     | "nccpintegration" | "myDetail"    | 3
    "cass-nccpintegration-random-junk-v003"                   | [(SEQUENCE): "42"]                    || "cass"       | "cass-nccpintegration-random-junk"                   | "nccpintegration" | "random-junk" | 42
    "app"                                                     | [(STACK): "myStack", (SEQUENCE): "2"] || "app"        | "app-myStack"                                        | "myStack"         | null          | 2
    "app"                                                     | null                                  || "app"        | "app"                                                | null              | null          | null
    "app-cluster"                                             | null                                  || "app"        | "app-cluster"                                        | "cluster"         | null          | null
    "app-cluster"                                             | [(CLUSTER): "myCluster"]              || "app"        | "myCluster"                                          | "cluster"         | null          | null
    "app-v042"                                                | [(SEQUENCE): "13"]                    || "app"        | "app"                                                | null              | null          | 13
    "app-v042"                                                | [(DETAIL): "myDetail"]                || "app"        | "app--myDetail"                                      | null              | "myDetail"    | 42
    "awesomeapp--my-detail"                                   | null                                  || "awesomeapp" | "awesomeapp--my-detail"                              | null              | "my-detail"   | null
    "awesomeapp--my-detail"                                   | getAllMonikerTags(true)               || "myApp" | "myCluster"              | "myStack" | "myDetail" | 13
    "awesomeapp--my-detail"                                   | getAllMonikerTags(false)              || "myApp" | "myApp-myStack-myDetail" | "myStack" | "myDetail" | 13
  };

  def getAllMonikerTags(includeCluster = false) {
    def tags = [(APPLICATION): "myApp", (STACK): "myStack", (DETAIL): "myDetail", (SEQUENCE): "13"]
    if (includeCluster) {
      tags << [(CLUSTER): "myCluster"]
    }
    tags
  }
}
