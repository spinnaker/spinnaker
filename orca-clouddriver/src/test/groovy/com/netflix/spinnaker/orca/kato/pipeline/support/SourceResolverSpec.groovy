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
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroupResolver
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Unroll

class SourceResolverSpec extends Specification {

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
        [name: "test-v000", region: "us-west-1", disabled: false, instances: [1, 2, 3]],
        [name: "test-v001", region: "us-west-1", disabled: false, instances: [1, 2]],
        [name: "test-v003", region: "us-west-1", disabled: false, instances: [1]],
        [name: "test-v004", region: "us-west-1", disabled: true, instances: [1, 2, 3, 4]],
        [name: "test-v004", region: "us-west-1", instances: [1, 2, 3, 4, 5]]
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
    ]

    def resolver = Spy(SourceResolver) {
      (0..1) * getExistingAsgs("app", "test", "app-test", "aws") >> {
        return exampleAsgs[existingAsgsName]
      }
    }

    and:
    def context = exampleContexts[exampleContextName]
    def stage = new Stage(Execution.newPipeline("orca"), "test", context + [
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
      oortService: oort,
      mapper: mapper,
      resolver: new TargetServerGroupResolver(oortService: oort, mapper: mapper, retrySupport: retrySupport)
    )

    when:
    def stage = new Stage(
      Execution.newPipeline("orca"),
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
      'current_asg_dynamic') >> new Response('http://oort.com', 200, 'Okay', [], new TypedString('''\
    {
      "name": "app-test-v009",
      "region": "us-west-1",
      "createdTime": 1
    }'''.stripIndent()))

    source?.account == 'test'
    source?.region == 'us-west-1'
    source?.serverGroupName == 'app-test-v009'
    source?.asgName == 'app-test-v009'
  }

  void "should ignore target if source is explicitly specified"() {
    given:
    SourceResolver resolver = new SourceResolver(mapper: new ObjectMapper())

    when:
    def stage = new Stage(
      Execution.newPipeline("orca"),
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
    OortService oort = Mock(OortService)
    SourceResolver resolver = new SourceResolver(oortService: oort, mapper: new ObjectMapper())

    when:
    def existing = resolver.getExistingAsgs('foo', 'test', 'foo-test', 'aws')

    then:
    1 * oort.getCluster('foo', 'test', 'foo-test', 'aws') >> new Response('http://oort.com', 200, 'Okay', [], new TypedString('''\
    {
      "serverGroups": [{
        "name": "foo-test-v001",
        "region": "us-west-1",
        "createdTime": 4
      },{
        "name": "foo-test-v000",
        "region": "us-west-1",
        "createdTime": 3
      },{
        "name": "foo-test-v000",
        "region": "us-east-1",
        "createdTime": 2
      },{
        "name": "foo-test-v999",
        "region": "us-east-1",
        "createdTime": 1
      }]
    }'''.stripIndent()))
    existing*.name == ['foo-test-v999', 'foo-test-v000', 'foo-test-v000', 'foo-test-v001']
  }

}
