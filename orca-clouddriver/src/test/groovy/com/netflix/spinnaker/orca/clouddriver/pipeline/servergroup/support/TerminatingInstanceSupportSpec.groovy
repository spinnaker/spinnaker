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

package com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support

import com.netflix.spinnaker.orca.clouddriver.pipeline.instance.TerminatingInstance
import com.netflix.spinnaker.orca.clouddriver.pipeline.instance.TerminatingInstanceSupport
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TerminatingInstanceSupportSpec extends Specification {

  OortHelper oortHelper = Mock(OortHelper)
  @Subject support = new TerminatingInstanceSupport(oortHelper: oortHelper)

  @Unroll
  def "should lookup instances by server group name"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", [
          credentials    : "creds",
          region         : "north-pole",
          serverGroupName: "santa-claus"
      ] + stageMods)

    when:
      def results = support.remainingInstances(stage)

    then:
      getTSGCount * oortHelper.getTargetServerGroup("creds", "santa-claus", "north-pole", "aws") >> [
          new TargetServerGroup(instances: returnedInstances)
      ]
      results == expected

    where:
      stageMods                                                                                   | returnedInstances                                                      || expected
      [:]                                                                                         | []                                                                     || []
      [instanceIds: ["abc123"]]                                                                   | []                                                                     || []
      [instanceIds: ["abc123"]]                                                                   | [[name: "abc123", launchTime: 123], [name: "def456", launchTime: 456]] || [new TerminatingInstance(id: "abc123", launchTime: 123)]
      [instanceIds: ["abc123", "def456"]]                                                         | [[name: "abc123", launchTime: 123], [name: "def456", launchTime: 456]] || [new TerminatingInstance(id: "abc123", launchTime: 123), new TerminatingInstance(id: "def456", launchTime: 456)]
      [instance: "abc123"]                                                                        | []                                                                     || []
      [instance: "abc123"]                                                                        | [[name: "abc123", launchTime: 123], [name: "def456", launchTime: 456]] || [new TerminatingInstance(id: "abc123", launchTime: 123)]
      ["terminate.remaining.instances": [new TerminatingInstance(id: "abc123", launchTime: 123)]] | [[name: "abc123", launchTime: 123]]                                    || [new TerminatingInstance(id: "abc123", launchTime: 123)]
      ["terminate.remaining.instances": [new TerminatingInstance(id: "abc123", launchTime: 123)]] | [[name: "abc123", launchTime: 789]]                                    || []

      getTSGCount = stageMods.isEmpty() ? 0 : 1
  }

  @Unroll
  def "should lookup instances by searching"() {
    given:
    def stage = new Stage(Execution.newPipeline("orca"), "whatever", stageMods)


    when:
      def results = support.remainingInstances(stage)

    then:
      searchCount * oortHelper.getSearchResults("abc123", "instances", "aws") >> [[totalMatches: totalMatches]]
      results == expected

    where:
      stageMods                                                                                   | totalMatches | expected
      [:]                                                                                         | 0            | []
      [instanceIds: ["abc123"]]                                                                   | 0            | []
      [instanceIds: ["abc123"]]                                                                   | 1            | [new TerminatingInstance(id: "abc123", launchTime: null)]
      [instance: "abc123"]                                                                        | 0            | []
      [instance: "abc123"]                                                                        | 1            | [new TerminatingInstance(id: "abc123", launchTime: null)]
      ["terminate.remaining.instances": [new TerminatingInstance(id: "abc123", launchTime: 123)]] | 0            | []
      ["terminate.remaining.instances": [new TerminatingInstance(id: "abc123", launchTime: 123)]] | 1            | [new TerminatingInstance(id: "abc123", launchTime: 123)]

      searchCount = stageMods.isEmpty() ? 0 : 1
  }
}
