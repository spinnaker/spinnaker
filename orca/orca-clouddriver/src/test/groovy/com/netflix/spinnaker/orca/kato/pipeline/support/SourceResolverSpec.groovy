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

package com.netflix.spinnaker.orca.kato.pipeline.support

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Unroll

class SourceResolverSpec extends Specification {

  CloudDriverService cloudDriverService = Mock()

  @Unroll
  void "should populate deploy stage 'source' with latest ASG details if not explicitly specified"() {
    given:
    def exampleContexts = [
      empty               : [:],
      existingSource      : [source: [asgName: "test-v000", account: "test", region: "us-west-1"]],
      specifiedEmptySource: [source: [:]],
      useSourceCapacity   : [useSourceCapacity: true]
    ] as Map<String, Map>

    def exampleAsgs = [
      empty       : [],
      singleRegion: [
        [name: "test-v000", region: "us-west-1", disabled: false, instances: [[:], [:], [:]]],
        [name: "test-v001", region: "us-west-1", disabled: false, instances: [[:], [:]]],
        [name: "test-v003", region: "us-west-1", disabled: false, instances: [[:]]],
        [name: "test-v004", region: "us-west-1", disabled: true, instances: [[:], [:], [:], [:]]],
        [name: "test-v004", region: "us-west-1", instances: [[:], [:], [:], [:], [:]]]
      ],
      mixedRegions: [
        [name: "test-v000", region: "us-west-1", disabled: false],
        [name: "test-v001", region: "us-west-1", disabled: false],
        [name: "test-v003", region: "us-west-2", disabled: false]
      ],
      allDisabled : [
        [name: "test-v000", region: "us-west-1", disabled: true],
        [name: "test-v001", region: "us-west-1", disabled: true],
      ]
    ].collectEntries {k, v ->
      [(k): v.collect { ModelUtils.serverGroup(it) }]
    }

    def resolver = Spy(SourceResolver) {
      (0..1) * getExistingAsgs("app", "test", "app-test", "aws") >> {
        return exampleAsgs[existingAsgsName]
      }
    }

    and:
    def context = exampleContexts[exampleContextName]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "test", context + [
      application: "app", stack: "test", account: "test", availabilityZones: ["us-west-1": []]
    ])

    when:
    def source = resolver.getSource(stage)

    then:
    source?.asgName == expectedAsgName
    source?.account == expectedAccount
    source?.region == expectedRegion

    where:
    exampleContextName     | existingAsgsName || expectedAsgName || expectedAccount || expectedRegion
    "empty"                | "empty"          || null            || null            || null
    "specifiedEmptySource" | "empty"          || null            || null            || null
    "specifiedEmptySource" | "singleRegion"   || null            || null            || null
    "existingSource"       | "empty"          || "test-v000"     || "test"          || "us-west-1"
    "empty"                | "singleRegion"   || "test-v003"     || "test"          || "us-west-1"
    "empty"                | "mixedRegions"   || "test-v001"     || "test"          || "us-west-1"
    "useSourceCapacity"    | "singleRegion"   || "test-v000"     || "test"          || "us-west-1"
    "useSourceCapacity"    | "allDisabled"    || "test-v001"     || "test"          || "us-west-1"
  }

  void "should populate deploy stage 'source' with targeted server group if source is absent and target is explicitly specified"() {
    given:
    OortService oort = Mock(OortService)
    ObjectMapper mapper = new ObjectMapper()
    RetrySupport retrySupport = Spy(RetrySupport) {
      _ * sleep(_) >> { /* do nothing */ }
    }

    SourceResolver resolver = new SourceResolver(
      mapper: mapper,
      resolver: new TargetServerGroupResolver(oortService: oort, mapper: mapper, retrySupport: retrySupport)
    )

    when:
    def stage = new StageExecutionImpl(
      PipelineExecutionImpl.newPipeline("orca"),
      "test",
      [
        application: "app",
        credentials: "test",
        region: "us-west-1",
        target: "current_asg_dynamic",
        targetCluster: "app-test"
      ]
    )
    def source = resolver.getSource(stage)

    then:
    1 * oort.getTargetServerGroup(
      'app',
      'test',
      'app-test',
      'aws',
      'us-west-1',
      'current_asg_dynamic') >> Calls.response(new ServerGroup(
    [
      "name": "app-test-v009",
      "region": "us-west-1",
      "createdTime": 1
    ]))

    source?.account == 'test'
    source?.region == 'us-west-1'
    source?.serverGroupName == 'app-test-v009'
    source?.asgName == 'app-test-v009'
  }

  void "should populate deploy stage 'source' with targeted server group if source contains the location of the target"() {
    given:
    OortService oort = Mock()
    ObjectMapper mapper = new ObjectMapper()
    RetrySupport retrySupport = Spy(RetrySupport) {
      _ * sleep(_) >> { /* do nothing */ }
    }

    SourceResolver resolver = new SourceResolver(
      mapper: mapper,
      resolver: new TargetServerGroupResolver(oortService: oort, mapper: mapper, retrySupport: retrySupport)
    )

    when:
    def stage = new StageExecutionImpl(
      PipelineExecutionImpl.newPipeline("orca"),
      "test",
      [
        cloudProvider: "cloudfoundry",
        application: "app",
        credentials: "test1",
        region: "org > space",
        source: [clusterName: "app-test", account: "test2", region: "org2 > space2"],
        target: "current_asg_dynamic",
      ]
    )
    def source = resolver.getSource(stage)

    then:
    1 * oort.getTargetServerGroup(
      'app',
      'test2',
      'app-test',
      'cloudfoundry',
      'org2 > space2',
      'current_asg_dynamic') >> Calls.response(new ServerGroup(
    [
      "name": "app-test-v009",
      "region": "org2 > space2",
      "createdTime": 1
    ]))

    source?.account == 'test2'
    source?.region == 'org2 > space2'
    source?.serverGroupName == 'app-test-v009'
    source?.asgName == 'app-test-v009'
  }

  void "should ignore target if source is explicitly specified"() {
    given:
    SourceResolver resolver = new SourceResolver()

    when:
    def stage = new StageExecutionImpl(
      PipelineExecutionImpl.newPipeline("orca"),
      "test",
      [
        application: "app",
        credentials: "test",
        region: "us-west-1",
        source: [serverGroupName: "app-test-v000", asgName: "app-test-v000", account: "test", region: "us-west-1"],
        target: "current_asg_dynamic",
        targetCluster: "app-test"
      ]
    )
    def source = resolver.getSource(stage)

    then:
    source?.account == 'test'
    source?.region == 'us-west-1'
    source?.serverGroupName == 'app-test-v000'
    source?.asgName == 'app-test-v000'
  }

  void 'should sort oort server groups by createdTime'() {
    given:
    SourceResolver resolver = new SourceResolver(cloudDriverService: cloudDriverService)

    when:
    def existing = resolver.getExistingAsgs('foo', 'test', 'foo-test', 'aws')

    then:
    1 * cloudDriverService.getCluster('foo', 'test', 'foo-test', 'aws') >> ModelUtils.cluster(
    [
      "serverGroups": [[
        "name": "foo-test-v001",
        "region": "us-west-1",
        "createdTime": 4
      ],[
        "name": "foo-test-v000",
        "region": "us-west-1",
        "createdTime": 3
      ],[
        "name": "foo-test-v000",
        "region": "us-east-1",
        "createdTime": 2
      ],[
        "name": "foo-test-v999",
        "region": "us-east-1",
        "createdTime": 1
      ]]
    ])
    existing*.name == ['foo-test-v999', 'foo-test-v000', 'foo-test-v000', 'foo-test-v001']
  }

  void 'should prefer the largest oldest enabled server group'() {
    given:
    SourceResolver resolver = new SourceResolver(cloudDriverService: cloudDriverService, resolver: Mock(TargetServerGroupResolver))

    when:
    def source = resolver.getSource(stage)

    then:
    1 * cloudDriverService.getCluster('foo', 'test', 'foo-test', 'aws') >> serverGroups
    source != null

    source.serverGroupName == "foo-test-v000"
    source.account == 'test'
    source.region == "us-west-1"

    where:
    stage = new StageExecutionImpl(
      PipelineExecutionImpl.newPipeline("orca"),
      "test",
      [
        application: "foo",
        stack: "test",
        credentials: "test",
        region: "us-west-1",
        useSourceCapacity: true
      ]
    )

    serverGroups = ModelUtils.cluster(
    [
      "serverGroups": [[
        "name": "foo-test-v001",
        "region": "us-west-1",
        "createdTime": 4,
        "disabled": false,
        "instances": [ [:], [:], [:] ]
      ],[
        "name": "foo-test-v000",
        "region": "us-west-1",
        "disabled": false,
        "instances": [ [:], [:], [:] ],
        "createdTime": 3
      ],[
        "name": "foo-test-v000",
        "region": "us-east-1",
        "disabled": false,
        "instances": [ [:], [:], [:] ],
        "createdTime": 2
      ],[
        "name": "foo-test-v999",
        "region": "us-east-1",
        "disabled": false,
        "instances": [ [:], [:], [:] ],
        "createdTime": 1
      ]]
    ])
  }
}
