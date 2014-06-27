/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.data.aws

import spock.lang.Specification
import spock.lang.Unroll

class KeysTest extends Specification {

  @Unroll
  def 'namespace string generation'(Keys.Namespace ns, String expected) {
    expect:
    ns.toString() == expected

    where:
    ns                                   | expected
    Keys.Namespace.APPLICATIONS          | "applications"
    Keys.Namespace.LAUNCH_CONFIGS        | "launchConfigs"
    Keys.Namespace.SERVER_GROUP_INSTANCE | "serverGroupInstance"
  }
}
