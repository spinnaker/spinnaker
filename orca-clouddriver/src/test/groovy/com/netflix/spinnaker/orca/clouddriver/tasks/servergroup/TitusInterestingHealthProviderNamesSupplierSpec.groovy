/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.servergroup

import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.kato.pipeline.support.SourceResolver
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class TitusInterestingHealthProviderNamesSupplierSpec extends Specification {
  def oortService = Mock(OortService)
  def mapper = OrcaObjectMapper.newInstance()

  @Subject
  def titusInterestingHealthProviderNamesSupplier = new TitusInterestingHealthProviderNamesSupplier(oortService, new SourceResolver(), mapper)

  @Unroll
  def "should only support if cloudProvider is titus and stage type in [enableServerGroup, cloneServerGroup]"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), stageType, stageContext)

    expect:
    titusInterestingHealthProviderNamesSupplier.supports(cloudProvider, stage) == supports

    where:
    cloudProvider | stageType           | stageContext                || supports
    "titus"       | "cloneServerGroup"  | [strategy: "rollingpush"]   || true
    "titus"       | "enableServerGroup" | [strategy: "rollingpush"]   || true
    "titus"       | "cloneServerGroup"  | [strategy: "rollingpush"]   || true
    "titus"       | "cloneServerGroup"  | [strategy: "redBlack"]      || true
    "aws"         | "cloneServerGroup"  | [strategy: "redBlack"]      || false
    "aws"         | "destroyServerGroup"| [:]                         || false
  }

  @Unroll
  def "should process interestingHealthNames by inspecting labels on titus serverGroup"() {
    given:
    def stage = new Stage<>(new Pipeline("orca"), "createServerGroup", stageContext)
    def response = mapper.writeValueAsString([
      application: "app",
      region: "region",
      account: "test",
      labels: labels
    ])

    and:
    1 * oortService.getServerGroupFromCluster(*_) >> new Response('oort', 200, 'ok', [], new TypedString(response))

    when:
    def interestingHealthProviderNames = titusInterestingHealthProviderNamesSupplier.process("titus", stage)

    then:
    interestingHealthProviderNames == expectedHealthNames

    where:
    stageContext                                            | labels                                                  || expectedHealthNames
    [application: "app", source: [asgName: "asgName-v000"]] | [interestingHealthProviderNames: "Titus"]               || ["Titus"]
    [application: "app", source: [asgName: "asgName-v000"]] | [interestingHealthProviderNames: "Titus,Discovery"]     || ["Titus", "Discovery"]
    [application: "app", source: [asgName: "asgName-v000"]] | [:]                                                     || null
    [application: "app", source: [asgName: "asgName-v000"]] | null                                                    || null
  }

}
