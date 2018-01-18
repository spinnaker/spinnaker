/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.deploy.description.servergroup

import spock.lang.Ignore
import spock.lang.Specification

class ServerGroupParametersSpec extends Specification {

  def "test toParamsMap"() {
    given:
    ServerGroupParameters params = createServerGroupParams()
    Map expected = getMap()


    when:
    Map result = params.toParamsMap()

    then:
    //need to compare string values due to some map formatting issue
    result.toString() == expected.toString()
  }

  def "test fromParamsMap"() {
    given:
    ServerGroupParameters expected = createServerGroupParams()
    Map params = getMap()

    when:
    def result = ServerGroupParameters.fromParamsMap(params)

    then:
    result == expected
  }

  def "test handle unicode list"() {
    when:
    List<String> result = ServerGroupParameters.unescapePythonUnicodeJsonList(input)

    then:
    result == expected

    where:
    input                             | expected
    'test'                            | ['test']
    '[test]'                          | ['test']
    '[u\'test\']'                     | ['test']
    'u\'test\''                       | ['test']
    '[u\'test\',u\'test\',u\'test\']' | ['test', 'test', 'test']
  }

  def "test handle unicode map"() {
    when:
    Map<String, String> result = ServerGroupParameters.unescapePythonUnicodeJsonMap(input)

    then:
    result == expected

    where:
    input                                 | expected
    '{"test":"test"}'                     | ['test': 'test']
    '{u\'test\':\'test\'}'                | ['test': 'test']
    '{u\'test\':u\'test\',u\'a\':u\'a\'}' | ['test': 'test', 'a': 'a']
  }

  @Ignore
  def createServerGroupParams() {
    ServerGroupParameters.Scaler scaleup = new ServerGroupParameters.Scaler(cooldown: 60, period: 60, adjustment: 1, threshold: 50)
    ServerGroupParameters.Scaler scaledown = new ServerGroupParameters.Scaler(cooldown: 60, period: 600, adjustment: -1, threshold: 15)
    new ServerGroupParameters(instanceType: "m1.medium", image: "image",
      maxSize: 5, minSize: 3, desiredSize: 4,
      networkId: "net", subnetId: "sub", loadBalancers: ["poop"],
      securityGroups: ["sg1"],
      autoscalingType: ServerGroupParameters.AutoscalingType.CPU,
      scaleup: scaleup, scaledown: scaledown, rawUserData: "echo foobar", tags: ["foo": "bar"],
      sourceUserDataType: 'Text', sourceUserData: 'echo foobar', resourceFilename: 'servergroup_resource', zones: ["az1", "az2"])
  }

  @Ignore
  def getMap() {
    [flavor               : 'm1.medium', image: 'image', max_size: 5, min_size: 3, desired_size: 4,
     network_id           : 'net', subnet_id: 'sub', load_balancers: 'poop', security_groups: 'sg1', autoscaling_type: 'cpu_util',
     scaleup_cooldown     : 60, scaleup_adjustment: 1, scaleup_period: 60, scaleup_threshold: 50,
     scaledown_cooldown   : 60, scaledown_adjustment: -1, scaledown_period: 600, scaledown_threshold: 15,
     source_user_data_type: 'Text', source_user_data: 'echo foobar', tags: '{"foo":"bar"}', user_data: "echo foobar", resource_filename: 'servergroup_resource', zones: "az1,az2"]
  }

}
