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

import com.netflix.spinnaker.clouddriver.openstack.deploy.ops.servergroup.ServerGroupConstants
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

class ServerGroupParametersSpec extends Specification {

  @Unroll
  def "resolves resource filename: #description"() {
    expect:
    ServerGroupParameters.resolveResourceFilename(params) == expected

    where:
    description                             | params                                | expected
    'from stack params if present'          | ['resource_filename': 'valueinparam'] | 'valueinparam'
    'from constants if not in stack params' | [:]                                   | ServerGroupConstants.SUBTEMPLATE_FILE
  }

  def "converts params object to map"() {
    given:
    ServerGroupParameters params = createServerGroupParams()
    Map expected = getMap()

    when:
    Map result = params.toParamsMap()

    then:
    //need to compare string values due to some map formatting issue
    result.sort{ a, b -> a.key <=> b.key }.toString() == expected.sort{ a, b -> a.key <=> b.key }.toString()
  }

  @Unroll
  def "toParamsMap - output excludes #mapKey when null in the input"() {
    given:
    ServerGroupParameters params = createServerGroupParams()
    params[serverGroupProperty] = null

    when:
    Map result = params.toParamsMap()

    then:
    //need to compare string values due to some map formatting issue
    ! result.containsKey(mapKey)

    where:
    description                    | mapKey              | serverGroupProperty
    "no zones present"             | "zones"             | "zones"
    "no scheduler hints present"   | "scheduler_hints"   | "schedulerHints"
    "no resource filename present" | "resource_filename" | "resourceFilename"
  }

  def "converts map to params object"() {
    given:
    ServerGroupParameters expected = createServerGroupParams()
    Map params = getMap()

    when:
    def result = ServerGroupParameters.fromParamsMap(params)

    then:
    result == expected
  }

  @Unroll
  def "converts unicode list to list: #input"() {
    when:
    List<String> result = ServerGroupParameters.unescapePythonUnicodeJsonList(input)

    then:
    result == expected

    where:
    input                             | expected
    'test'                            | ['test']
    'test, test2'                     | ['test', 'test2']
    '[test]'                          | ['test']
    '[u\'test\']'                     | ['test']
    'u\'test\''                       | ['test']
    '[u\'test\',u\'test\',u\'test\']' | ['test', 'test', 'test']
    "[u\'\',u'']"                     | ["", ""]
// Is this really how it should behave?
    null                              | []
    "[]"                              | [""]
// Shouldn't these work, too?
//    '["test"]'                        | ["test"]
//    '["test", "test"]'                | ["test", "test"]
  }

  @Unroll
  def "converts #inType to map: #description"() {
    // Older versions of OpenStack return python-encoded dictionaries for several fields. Newer ones have converted them
    // to JSON. For forward and backward compatibility, we need to handle either.

    when:
    Map<String, String> result = ServerGroupParameters.unescapePythonUnicodeJsonMap(input)

    then:
    result == expected

    where:
    inType   | description               | input                                                       | expected
    'python' | 'one entry'               | '{u\'test\':u\'test\'}'                                     | ['test': 'test']
    'python' | 'multiple entries'        | '{u\'test\':u\'test\',u\'a\':u\'a\'}'                       | ['test': 'test', 'a': 'a']
    'python' | 'spaces in value'         | '{u\'test\': u\'this is a string\'}'                        | ['test': "this is a string"]
    'python' | 'comma in value'          | '{u\'test\':u\'test1,test2\'}'                              | ['test': 'test1,test2']
    'python' | 'colon in value'          | '{u\'url\':u\'http://localhost:8080\'}'                     | ['url': 'http://localhost:8080']
    'python' | 'value is Empty'          | '{u\'test\':u\'\'}'                                         | ['test': '']
    'python' | 'value is None'           | '{u\'test\': None}'                                         | ['test': null]
    'python' | 'multiple None values'    | "{u'test': \t None \t \n, u'test2': None\n}"                | ['test': null, 'test2': null]
    'python' | 'string contains "None"'  | '{u\'test\': u\'And None Either\'}'                         | ['test': "And None Either"]
    'python' | 'integer value'           | '{u\'port\': 1337}'                                         | ['port': 1337]
    'python' | 'single quotes in value'  | '{u\'test\': u"\'this is a string\'"}'                      | ['test': "'this is a string'"]
    'python' | '1 single quote in value' | '{u\'test\': u"Surf\'s up!"}'                               | ['test': "Surf\'s up!"]
    'python' | 'string ends in "u"'      | '{u\'SuperValu\':u\'test\',u\'a\':u\'a\'}'                  | ['SuperValu': 'test', 'a': 'a']
    'python' | 'json object in value'    | '{u\'health\':{u\'http\':u\'http://lh:80\',u\'a\':u\'b\'}}' | ['health': '{"http":"http://lh:80","a":"b"}']
    'python' | 'layers of objects'       | '{u\'a\': {u\'b\': {u\'c\': u\'d\', u\'e\': u\'f\'}}}'      | ['a': '{"b":{"c":"d","e":"f"}}']
    'json'   | 'empty map'               | '{}'                                                        | [:]
    'json'   | 'one entry'               | '{"test":"test"}'                                           | ['test': 'test']
    'json'   | 'multiple entries'        | '{"test":"test","a": "a"}'                                  | ['test': 'test', 'a': 'a']
    'json'   | 'spaces in value'         | '{"test": "this is a string"}'                              | ['test': "this is a string"]
    'json'   | 'comma in value'          | '{"test":"test1,test2"}'                                    | ['test': 'test1,test2']
    'json'   | 'colon in value'          | '{"url":"http://lh:80"}'                                    | ['url': 'http://lh:80']
    'json'   | 'value is Empty'          | '{"test": ""}'                                              | ['test': '']
    'json'   | 'value is null'           | '{"test": null}'                                            | ['test': null]
    'json'   | 'multiple null values'    | '{"test": \t null \t, "test2":\tnull\n}'                    | ['test': null, 'test2': null]
    'json'   | 'string contains "None"'  | '{"test": "And None Either"}'                               | ['test': "And None Either"]
    'json'   | 'integer value'           | '{"port": 1337}'                                            | ['port': 1337]
    'json'   | 'single quotes in value'  | '{"test": "\'this is a string\'"}'                          | ['test': "'this is a string'"]
    'json'   | '1 single quote in value' | '{"test": "Surf\'s up!"}'                                   | ['test': "Surf\'s up!"]
    'json'   | 'string ends in "u"'      | '{"SuperValu":"test","a":"a"}'                              | ['SuperValu': 'test', 'a': 'a']
    'json'   | 'json object in value'    | '{"health":{"http":"http://lh:80", "a": "b"}}'              | ['health': '{"http":"http://lh:80","a":"b"}']
    'json'   | 'layers of objects'       | '{"a": {"b": {"c": "d", "e": "f"}}}'                        | ['a': '{"b":{"c":"d","e":"f"}}']
  }

  @Ignore
  def createServerGroupParams() {
    ServerGroupParameters.Scaler scaleup = new ServerGroupParameters.Scaler(cooldown: 60, period: 60, adjustment: 1, threshold: 50)
    ServerGroupParameters.Scaler scaledown = new ServerGroupParameters.Scaler(cooldown: 60, period: 600, adjustment: -1, threshold: 15)
    new ServerGroupParameters(instanceType: "m1.medium",
                              image: "image",
                              maxSize: 5, minSize: 3, desiredSize: 4,
                              networkId: "net",
                              subnetId: "sub",
                              loadBalancers: ["poop"],
                              securityGroups: ["sg1"],
                              autoscalingType: ServerGroupParameters.AutoscalingType.CPU,
                              scaleup: scaleup,
                              scaledown: scaledown,
                              rawUserData: "echo foobar",
                              tags: ["foo": "bar"],
                              sourceUserDataType: 'Text',
                              sourceUserData: 'echo foobar',
                              zones: ["az1", "az2"],
                              schedulerHints: ["key": "value"],
                              resourceFilename: "fileMcFileface")
  }

  @Ignore
  def getMap() {
    [flavor: 'm1.medium',
      image: 'image',
      max_size: 5,
      min_size: 3,
      desired_size: 4,
      network_id: 'net',
      subnet_id: 'sub',
      load_balancers: 'poop',
      security_groups: 'sg1',
      autoscaling_type: 'cpu_util',
      scaleup_cooldown: 60,
      scaleup_adjustment: 1,
      scaleup_period: 60,
      scaleup_threshold: 50,
      scaledown_cooldown: 60,
      scaledown_adjustment: -1,
      scaledown_period: 600,
      scaledown_threshold: 15,
      source_user_data_type: 'Text',
      source_user_data: 'echo foobar',
      tags: '{"foo":"bar"}',
      user_data: "echo foobar",
      zones: "az1,az2",
      scheduler_hints: '{"key":"value"}',
      resource_filename: "fileMcFileface"
    ]
  }

}
