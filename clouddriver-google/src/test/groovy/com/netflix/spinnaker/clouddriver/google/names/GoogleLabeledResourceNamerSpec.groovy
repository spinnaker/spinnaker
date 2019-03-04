/*
 * Copyright 2019 Schibsted ASA.
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

package com.netflix.spinnaker.clouddriver.google.names

import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.moniker.Namer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import static com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer.APP
import static com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer.CLUSTER
import static com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer.STACK
import static com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer.DETAIL
import static com.netflix.spinnaker.clouddriver.google.names.GoogleLabeledResourceNamer.SEQUENCE

class GoogleLabeledResourceNamerSpec extends Specification {

  @Shared
  Namer namer = new GoogleLabeledResourceNamer()

  @Unroll
  def "should derive correct moniker"() {
    given:
    def resource = new GoogleServerGroup(name: name, instanceTemplateLabels: labels)
    def moniker = namer.deriveMoniker(resource)

    expect:
    with(moniker) {
      app == expectedApp
      cluster == expectedCluster
      stack == expectedStack
      detail == expectedDetail
      sequence == expectedSequence
    }

    where:
    name                                                      | labels                                    || expectedApp  | expectedCluster                                      | expectedStack     | expectedDetail | expectedSequence
    "cass-nccpintegration-random-junk-d0prod-z0useast1a-v003" | null                                      || "cass"       | "cass-nccpintegration-random-junk-d0prod-z0useast1a" | "nccpintegration" | "random-junk"  | 3
    "cass-nccpintegration-random-junk-d0prod-z0useast1a-v003" | [:]                                       || "cass"       | "cass-nccpintegration-random-junk-d0prod-z0useast1a" | "nccpintegration" | "random-junk"  | 3
    "cass-nccpintegration-random-junk-d0prod-z0useast1a-v003" | [(APP): "myApp"]                          || "myApp"      | "cass-nccpintegration-random-junk-d0prod-z0useast1a" | "nccpintegration" | "random-junk"  | 3
    "cass-nccpintegration-random-junk-v003"                   | [(CLUSTER): "myCluster"]                  || "cass"       | "myCluster"                                          | "nccpintegration" | "random-junk"  | 3
    "cass-nccpintegration-random-junk-v003"                   | [(STACK): "myStack"]                      || "cass"       | "cass-myStack"                                       | "myStack"         | "random-junk"  | 3
    "cass-nccpintegration-random-junk-v003"                   | [(STACK): "myStack", (DETAIL): ""]        || "cass"       | "cass-myStack"                                       | "myStack"         | null           | 3
    "cass-nccpintegration-random-junk-v003"                   | [(DETAIL): "myDetail"]                    || "cass"       | "cass--myDetail"                                     | "nccpintegration" | "myDetail"     | 3
    "cass-nccpintegration-random-junk-v003"                   | [(SEQUENCE): "42"]                        || "cass"       | "cass-nccpintegration-random-junk"                   | "nccpintegration" | "random-junk"  | 42
    "app"                                                     | [(STACK): "myStack", (SEQUENCE): "2"]     || "app"        | "app-myStack"                                        | "myStack"         | null           | 2
    "app"                                                     | null                                      || "app"        | "app"                                                | null              | null           | null
    "app-cluster"                                             | null                                      || "app"        | "app-cluster"                                        | "cluster"         | null           | null
    "app-cluster"                                             | [(CLUSTER): "myCluster"]                  || "app"        | "myCluster"                                          | "cluster"         | null           | null
    "app-v042"                                                | [(SEQUENCE): "13"]                        || "app"        | "app"                                                | null              | null           | 13
    "app-v042"                                                | [(DETAIL): "myDetail"]                    || "app"        | "app--myDetail"                                      | null              | "myDetail"     | 42
    "awesomeapp--my-detail"                                   | null                                      || "awesomeapp" | "awesomeapp--my-detail"                              | null              | "my-detail"    | null
    "awesomeapp--my-detail"                                   | getAllMonikerLabels(true)                 || "myApp"      | "myCluster"                                          | "myStack"         | "myDetail"     | 13
    "awesomeapp--my-detail"                                   | getAllMonikerLabels(false)                || "myApp"      | "myApp-myStack-myDetail"                             | "myStack"         | "myDetail"     | 13
  }

  def getAllMonikerLabels(includeCluster = false) {
    def labels = [(APP): "myApp", (STACK): "myStack", (DETAIL): "myDetail", (SEQUENCE): "13"]
    if (includeCluster) {
      labels << [(CLUSTER): "myCluster"]
    }
    labels
  }
}
