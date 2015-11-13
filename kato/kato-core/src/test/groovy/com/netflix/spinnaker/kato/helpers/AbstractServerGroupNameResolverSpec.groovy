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

package com.netflix.spinnaker.kato.helpers

import spock.lang.Specification
import spock.lang.Unroll

class AbstractServerGroupNameResolverSpec extends Specification {

  static class TestServerGroupNameResolver extends AbstractServerGroupNameResolver {
    private final String serverGroupName

    TestServerGroupNameResolver(String serverGroupName) {
      this.serverGroupName = serverGroupName
    }

    @Override
    String getPreviousServerGroupName(String clusterName) {
      return serverGroupName
    }
  }

  @Unroll
  void "next ASG name should be #expected when previous is #previousServerGroupName and application is 'app', stack is '#stack', and details is '#details'"() {
    when:
    def serverGroupNameResolver = new TestServerGroupNameResolver(previousServerGroupName)

    then:
    serverGroupNameResolver.resolveNextServerGroupName('app', stack, details, false) == expected

    where:
    stack   | details     | previousServerGroupName   | expected
    null    | null        | 'app-v000'                | 'app-v001'
    'new'   | null        | null                      | 'app-new-v000'
    'test'  | null        | 'app-test-v009'           | 'app-test-v010'
    'dev'   | 'detail'    | 'app-dev-detail-v015'     | 'app-dev-detail-v016'
    'prod'  | 'c0usca'    | 'app-prod-c0usca-v000'    | 'app-prod-c0usca-v001'
  }

  void "should fail for invalid characters in the asg name"() {
    when:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", "east!", 0, false)

    then:
    IllegalArgumentException e = thrown()
    e.message == "(Use alphanumeric characters only)"
  }

  void "application, stack, and freeform details make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", "east", 0, false) == "foo-bar-east-v000"
  }

  void "push sequence should be ignored when specified so"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", "east", 0, true) == "foo-bar-east"
  }

  void "application, and stack make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", "bar", null, 1, false) == "foo-bar-v001"
  }

  void "application and version make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", null, null, 1, false) == "foo-v001"
  }

  void "application, and freeform details make up the asg name"() {
    expect:
    AbstractServerGroupNameResolver.generateServerGroupName("foo", null, "east", 1, false) == "foo--east-v001"
  }
}
